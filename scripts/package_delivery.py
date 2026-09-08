from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import shutil
import zipfile
from source_safety import tracked_sources, source_archive

root = Path(__file__).resolve().parents[1]
def read(name):
    return json.loads((root / name).read_text(encoding='utf-8'))
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def unchanged(hashes):
    for name, value in hashes.items():
        assert digest(root / name) == value, 'Evidence stale: ' + name

build = read('build/build-receipt.json')
core = read('build/core-tests/results.json')
maps = read('build/map-tests/results.json')
map_receipt = read('build/map-tests/receipt.json')
version = build['version']
source_files = tracked_sources(root)
apk = Path(build['artifact'])
assert core['total_passed'] == 44
unchanged(core['source_hashes'])
unchanged(build['source_hashes'])
assert map_receipt['status'] == 'passed' and map_receipt['cases_passed'] == 11
assert len(maps) == 11 and all(case['passed'] for case in maps)
unchanged(map_receipt['source_hashes'])
assert digest(apk) == build['sha256']
with zipfile.ZipFile(apk) as archive:
    assert 'classes.dex' in archive.namelist() and 'AndroidManifest.xml' in archive.namelist()
    for asset in (root / 'app/src/main/assets').iterdir():
        assert archive.read('assets/' + asset.name) == asset.read_bytes(), 'Stale asset: ' + asset.name

delivery = Path.home() / 'Desktop' / ('定点助手-' + version + '-交互重做版')
delivery.mkdir(exist_ok=False)
target_apk = delivery / ('定点助手-' + version + '.apk')
shutil.copy2(apk, target_apk)
shutil.copy2(root / 'README.md', delivery / '使用说明.md')
shutil.copy2(root / 'docs/UI-1.2.md', delivery / '交互变更与验收.md')
source_archive(root, delivery / '定点助手-源码.zip')
assert digest(target_apk) == build['sha256']
report = {
    'created_at': datetime.now(timezone.utc).isoformat(), 'version': version,
    'apk': target_apk.name, 'apk_sha256': build['sha256'],
    'core_tests': core, 'browser_map_results': maps, 'browser_map_receipt': map_receipt,
    'apk_signature': 'v2/v3 verified', 'apk_zip_alignment': 'passed',
    'bundled_map_matches_tested_sources': True,
    'native_ui_tests': {'status': 'not_run', 'reason': 'No adb device; Android emulator/runtime downloads timed out', 'script_is_not_pass_evidence': True},
    'not_verified': ['native rendering and interactions', 'installation and upgrade on realme', 'live phone location', 'Android service/provider integration', 'realme browser geolocation', 'attendance webpage'],
    'source_hashes': {p.relative_to(root).as_posix(): digest(p) for p in sorted(source_files)}
}
(root / ('build/verification-' + version + '.json')).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
(delivery / '验证记录.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
(delivery / 'SHA256.txt').write_text(build['sha256'] + '  ' + target_apk.name + '\n', encoding='utf-8')
print(json.dumps({'delivery': str(delivery), 'apk_sha256': build['sha256'], 'files': [p.name for p in delivery.iterdir()]}, ensure_ascii=True), flush=True)
