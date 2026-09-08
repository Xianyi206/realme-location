from pathlib import Path
import os, subprocess
sdk = Path(os.environ.get('ANDROID_HOME', r'E:\AI\toolchain\android-sdk'))
subprocess.run(['cmd.exe', '/c', str(sdk/'cmdline-tools/latest/bin/sdkmanager.bat'),
    'emulator', 'system-images;android-35;default;x86_64', '--proxy=http', '--proxy_host=127.0.0.1', '--proxy_port=50101'],
    input='y\n'*50, text=True, check=True)
