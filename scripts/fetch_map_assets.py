"""Vendor the pinned Leaflet distribution, verifying upstream documented SRI."""
from pathlib import Path
import base64
import hashlib
import urllib.request

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
hashes = {
    'leaflet.js': '20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=',
    'leaflet.css': 'p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=',
}
for name, expected in hashes.items():
    req = urllib.request.Request('https://unpkg.com/leaflet@1.9.4/dist/' + name, headers={'User-Agent': 'DingDianHelper-Build/1.0'})
    data = urllib.request.urlopen(req, timeout=25).read()
    actual = base64.b64encode(hashlib.sha256(data).digest()).decode()
    if actual != expected:
        raise RuntimeError(f'Integrity mismatch for {name}')
    (assets / name).write_bytes(data)
    print(f'Verified {name}: {actual}', flush=True)
license_data = urllib.request.urlopen('https://raw.githubusercontent.com/Leaflet/Leaflet/v1.9.4/LICENSE', timeout=25).read()
(root / 'THIRD_PARTY_LEAFLET.txt').write_bytes(license_data)
print('Leaflet license saved', flush=True)
