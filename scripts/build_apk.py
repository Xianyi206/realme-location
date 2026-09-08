"""Build a signed standalone Java Android APK using only the official SDK/JDK.

No dependency resolution or global SDK changes; local development signing only.
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import zipfile
import uuid
import secrets
from toolchain import configured_path
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

root = Path(__file__).resolve().parents[1]
sdk = configured_path('ANDROID_HOME')
java = configured_path('JAVA_HOME') / 'bin'
bt = sdk / 'build-tools' / '35.0.0'
android = sdk / 'platforms' / 'android-35' / 'android.jar'
build = root / 'build' / ('apk-' + uuid.uuid4().hex[:10])
source_hashes = {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
    for p in (root / 'app').rglob('*') if p.is_file()}
for folder in ('generated', 'classes', 'dex'):
    (build / folder).mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run(list(map(str, args)), cwd=root, check=True)

run(bt / 'aapt2.exe', 'compile', '--dir', root / 'app/src/main/res', '-o', build / 'resources.zip')
run(bt / 'aapt2.exe', 'link', '-I', android, '--manifest', root / 'app/src/main/AndroidManifest.xml',
    '--java', build / 'generated', '--auto-add-overlay', '-A', root / 'app/src/main/assets',
    '-o', build / 'resources.apk', build / 'resources.zip')
sources = sorted((root / 'app/src/main/java').rglob('*.java')) + sorted((build / 'generated').rglob('*.java'))
run(java / 'javac.exe', '--release', '8', '-encoding', 'UTF-8', '-classpath', android,
    '-d', build / 'classes', *sources)
classes = sorted((build / 'classes').rglob('*.class'))
run(java / 'java.exe', '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', android,
    '--min-api', '26', '--output', build / 'dex', *classes)
with zipfile.ZipFile(build / 'resources.apk') as resources, zipfile.ZipFile(build / 'unsigned.apk', 'w') as apk:
    for info in resources.infolist():
        apk.writestr(info, resources.read(info.filename))
    for dex in (build / 'dex').glob('*.dex'):
        apk.write(dex, dex.name, compress_type=zipfile.ZIP_DEFLATED)
run(bt / 'zipalign.exe', '-f', '-p', '4', build / 'unsigned.apk', build / 'aligned.apk')
keys = root / '.keys'
keys.mkdir(exist_ok=True)
key = keys / 'local-development.p12'
password_file = keys / 'development-password.txt'
password = os.environ.get('DD_KEYSTORE_PASSWORD')
if not password and password_file.exists():
    password = password_file.read_text(encoding='utf-8').strip()
if not password:
    if key.exists():
        raise RuntimeError('Existing signing key: supply DD_KEYSTORE_PASSWORD; the key will not be replaced')
    password = secrets.token_urlsafe(32)
    with password_file.open('x', encoding='utf-8') as output_password:
        output_password.write(password)
    if os.name != 'nt':
        password_file.chmod(0o600)
os.environ['DD_KEYSTORE_PASSWORD'] = password
if not key.exists():
    run(java / 'keytool.exe', '-genkeypair', '-keystore', key, '-storetype', 'PKCS12',
        '-storepass:env', 'DD_KEYSTORE_PASSWORD', '-keypass:env', 'DD_KEYSTORE_PASSWORD', '-alias', 'local-development',
        '-keyalg', 'RSA', '-keysize', '3072', '-validity', '3650',
        '-dname', 'CN=Local Development, OU=Personal App, O=DingDian Helper, C=CN')
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
version = manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
output = root / 'build' / ('DingDianHelper-' + version + '.apk')
run(java / 'java.exe', '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key,
    '--ks-key-alias', 'local-development', '--ks-pass', 'env:DD_KEYSTORE_PASSWORD', '--key-pass', 'env:DD_KEYSTORE_PASSWORD',
    '--out', output, build / 'aligned.apk')
run(java / 'java.exe', '-jar', bt / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', output)
run(bt / 'zipalign.exe', '-c', '4', output)
assert all(hashlib.sha256((root / name).read_bytes()).hexdigest() == digest for name, digest in source_hashes.items()), 'Application sources changed during build'
receipt = {'artifact': str(output), 'version': version, 'bytes': output.stat().st_size,
    'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
    'built_at': datetime.now(timezone.utc).isoformat(),
    'min_sdk': 26, 'target_sdk': 35, 'signature': 'local development; v2/v3 verified',
    'device_tested': False, 'source_hashes': source_hashes}
(root / 'build/build-receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
print(json.dumps(receipt, indent=2), flush=True)
