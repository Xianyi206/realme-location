import sys
import tempfile
import unittest
import subprocess
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from source_safety import issues, source_archive

class SourceSafetyTests(unittest.TestCase):
    def test_known_secret_formats_are_blocked_without_printing_values(self):
        self.assertIn('github-token', issues('code.py', ('ghp_' + 'a' * 36).encode()))
        self.assertIn('private-key', issues('code.py', b'-----BEGIN ' + b'PRIVATE KEY-----'))

    def test_private_and_unsafe_paths_are_blocked(self):
        for name in ('.keys/signing.p12', '.env', 'app/.env.local', 'app/saved-places-v1.bin', 'docs/private.pem', '../escape.txt', '/absolute.txt'):
            self.assertTrue(issues(name, b''), name)

    def test_normal_source_and_public_hash_are_allowed(self):
        self.assertEqual([], issues('app/src/main/assets/map.js', b'const hash = "' + b'a' * 64 + b'";'))

    def test_user_home_path_is_detected(self):
        sample = ':'.join(('C', '')) + '\\' + 'Users' + '\\' + 'example' + '\\' + 'project'
        self.assertIn('user-home-path', issues('report.json', sample.encode()))

    def test_archive_excludes_untracked_and_rejects_force_added_secret(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            def git(*args):
                subprocess.run(['git', *args], cwd=root, check=True, capture_output=True)
            git('init')
            (root / 'README.md').write_text('public source', encoding='utf-8')
            git('add', 'README.md')
            (root / '.env').write_text('private setting', encoding='utf-8')
            source_archive(root, root / 'source.zip')
            with zipfile.ZipFile(root / 'source.zip') as archive:
                self.assertEqual(['realme-location/README.md'], archive.namelist())
            git('add', '-f', '.env')
            with self.assertRaisesRegex(ValueError, 'private-or-generated-file'):
                source_archive(root, root / 'blocked.zip')
            self.assertFalse((root / 'blocked.zip').exists())

if __name__ == '__main__':
    unittest.main()
