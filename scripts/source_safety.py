"""Small repository-specific publication gate, not a comprehensive secret scanner."""
from pathlib import Path, PurePosixPath
import re
import subprocess
import zipfile

BLOCKED_DIRS = {'.git', '.keys', 'build', '.gradle', '__pycache__', '.idea', '.vscode'}
BLOCKED_EXTENSIONS = {'.p12', '.pfx', '.pem', '.key', '.jks', '.keystore', '.apk', '.aab', '.zip', '.log', '.pyc'}
PATTERNS = {
    'private-key': rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----',
    'github-token': rb'\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,})\b',
    'aws-access-key': rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
    'api-key': rb'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,}\b',
    'user-home-path': rb'[A-Za-z]:[\\/]Users[\\/][^\s\\/]+',
}

def issues(name, data):
    path = PurePosixPath(name)
    found = []
    if path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name:
        found.append('unsafe-path')
    if (any(p in BLOCKED_DIRS for p in path.parts)
            or path.suffix.lower() in BLOCKED_EXTENSIONS
            or path.name == '.env' or path.name.startswith('.env.')
            or path.name in {'local.properties', 'settings.xml'}
            or (path.name.startswith('saved-places') and path.suffix == '.bin')):
        found.append('private-or-generated-file')
    found.extend(label for label, pattern in PATTERNS.items() if re.search(pattern, data))
    return found

def tracked_sources(root):
    result = subprocess.run(['git', 'ls-files', '--stage', '-z'], cwd=root, check=True, capture_output=True)
    files = []
    for entry in result.stdout.decode('utf-8').split('\0'):
        if not entry:
            continue
        meta, name = entry.split('\t', 1)
        mode, _, stage = meta.split()
        if mode not in {'100644', '100755'} or stage != '0':
            raise ValueError('Unsupported git entry: ' + name)
        path = root / name
        if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
            raise ValueError('Unsafe source path: ' + name)
        data = path.read_bytes()
        failures = issues(name, data)
        if failures:
            raise ValueError(name + ': ' + ', '.join(failures))
        files.append(path)
    if not files:
        raise ValueError('No tracked sources')
    return files

def source_archive(root, destination):
    files = tracked_sources(root)
    snapshot = []
    for path in files:
        name = path.relative_to(root).as_posix()
        data = path.read_bytes()
        failures = issues(name, data)
        if failures:
            raise ValueError(name + ': ' + ', '.join(failures))
        snapshot.append((name, data))
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in snapshot:
            archive.writestr('realme-location/' + name, data)
    return files

if __name__ == '__main__':
    print('PASS publication gate:', len(tracked_sources(Path(__file__).resolve().parents[1])), 'tracked files')
