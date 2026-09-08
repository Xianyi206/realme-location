from pathlib import Path
import os
import subprocess
import json
import hashlib
import re
from datetime import datetime, timezone

root = Path(__file__).resolve().parents[1]
java = Path(os.environ.get('JAVA_HOME', r'E:\AI\toolchain\jdk17')) / 'bin'
out = root / 'build' / 'core-tests'
out.mkdir(parents=True, exist_ok=True)
sources = [root / 'app/src/main/java/local/position/helper/GeoPoint.java']
sources += [root / ('app/src/main/java/local/position/helper/' + name + '.java') for name in ('SavedPlaces', 'LiveFix')]
session = root / 'app/src/main/java/local/position/helper/MockSession.java'
if session.exists():
    sources.append(session)
sources += list((root / 'tests').glob('*.java'))
subprocess.run([str(java / 'javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-d', str(out), *map(str, sources)], check=True)
suites = []
for source in sorted((root / 'tests').glob('*.java')):
    result = subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'local.position.helper.' + source.stem],
        capture_output=True, text=True, encoding='utf-8')
    print(result.stdout, end='')
    if result.stderr:
        print(result.stderr)
    result.check_returncode()
    count = re.search(r'PASS (\d+) [^\n]* cases', result.stdout)
    if not count:
        raise RuntimeError('Missing test result count: ' + source.stem)
    suites.append({'suite': source.stem, 'passed_cases': int(count.group(1)), 'output': result.stdout})
report = {'created_at': datetime.now(timezone.utc).isoformat(), 'suites': suites,
    'total_passed': sum(s['passed_cases'] for s in suites),
    'source_hashes': {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}}
(out / 'results.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
