"""Exercise the actual bundled picker in Chrome; no attendance page is contacted."""
from pathlib import Path
import json
import hashlib
import atexit
from datetime import datetime, timezone
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright, expect

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
evidence = root / 'build/map-tests'
evidence.mkdir(parents=True, exist_ok=True)
results = []
completed = False
tested_hashes = {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in assets.iterdir() if p.is_file()}
def receipt():
    (evidence / 'receipt.json').write_text(json.dumps({'created_at': datetime.now(timezone.utc).isoformat(), 'status': 'passed' if completed else 'failed', 'source_hashes': tested_hashes, 'cases_passed': len(results), 'scope': 'Bundled HTML/CSS/JS in desktop Chrome mobile viewports; native Android host untested'}, indent=2), encoding='utf-8')
atexit.register(receipt)

def asset(route):
    name = Path(urlparse(route.request.url).path).name
    path = assets / name
    if not path.is_file():
        route.abort()
        return
    mime = 'text/html' if name.endswith('.html') else 'text/css' if name.endswith('.css') else 'application/javascript'
    route.fulfill(path=path, content_type=mime)

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='chrome', headless=True)
    for viewport in ({'width': 360, 'height': 740}, {'width': 412, 'height': 915}, {'width': 1000, 'height': 760}, {'width': 740, 'height': 360}):
        context = browser.new_context(viewport=viewport, device_scale_factor=1)
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.route('https://appassets.androidplatform.net/assets/*', asset)
        page.goto('https://appassets.androidplatform.net/assets/map.html', wait_until='domcontentloaded')
        choose = page.get_by_role('button', name='使用这个位置')
        expect(choose).to_be_disabled()
        expect(page.locator('#coordinates')).to_have_text('还没有选择位置')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Horizontal overflow'
        page.get_by_role('button', name='Zoom in', exact=True).click()
        page.locator('.leaflet-tile-loaded').first.wait_for(timeout=25000)
        page.wait_for_timeout(500)
        box = page.locator('#map').bounding_box()
        page.wait_for_function('''({x, y}) => [...document.querySelectorAll('.leaflet-tile-loaded')].some(tile => {
            const r = tile.getBoundingClientRect();
            return tile.naturalWidth > 0 && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
        })''', arg={'x': box['x'] + box['width'] * .57, 'y': box['y'] + box['height'] * .53}, timeout=25000)
        page.mouse.click(box['x'] + box['width'] * .57, box['y'] + box['height'] * .53)
        expect(choose).to_be_enabled()
        text = page.locator('#coordinates').inner_text()
        assert '纬度' in text and '经度' in text
        cdp = context.new_cdp_session(page)
        cdp.send('Page.enable')
        navigations = []
        cdp.on('Page.frameRequestedNavigation', lambda data: navigations.append(data))
        choose.click()
        page.wait_for_timeout(200)
        pick = [item['url'] for item in navigations if item['url'].startswith('locationpicker://select')]
        assert pick, 'No coordinate handoff was requested'
        values = parse_qs(urlparse(pick[-1]).query)
        assert -90 <= float(values['lat'][0]) <= 90 and -180 <= float(values['lng'][0]) <= 180
        assert not errors, errors
        page.screenshot(path=str(evidence / f"picker-{viewport['width']}x{viewport['height']}.png"), full_page=True)
        results.append({'case': f"select and handoff {viewport}", 'passed': True, 'coordinates': text, 'tile_status': page.locator('#network').inner_text()})
        context.close()

    context = browser.new_context(viewport={'width': 360, 'height': 740})
    page = context.new_page()
    page.route('https://appassets.androidplatform.net/assets/*', asset)
    page.route('https://tile.openstreetmap.org/**', lambda route: route.abort())
    page.goto('https://appassets.androidplatform.net/assets/map.html?lat=31.2304&lng=121.4737')
    expect(page.locator('#coordinates')).to_contain_text('31.230400')
    expect(page.locator('#choose')).to_be_enabled()
    expect(page.locator('#network')).to_contain_text('地图暂不可用')
    results.append({'case': 'offline map preserves entered coordinates and explains fallback', 'passed': True})
    page.goto('https://appassets.androidplatform.net/assets/map.html')
    expect(page.locator('#network')).to_contain_text('地图暂不可用')
    box = page.locator('#map').bounding_box()
    page.mouse.click(box['x'] + box['width'] / 2, box['y'] + box['height'] / 2)
    expect(page.locator('#choose')).to_be_disabled()
    expect(page.locator('#network')).to_contain_text('该处地图尚未加载')
    results.append({'case': 'clicking unloaded map cannot select an arbitrary point', 'passed': True})
    for query in ('lat=NaN&lng=100', 'lat=91&lng=100', 'lat=&lng=', 'lat=30&lng=181', 'lat=30'):
        page.goto('https://appassets.androidplatform.net/assets/map.html?' + query)
        expect(page.locator('#choose')).to_be_disabled()
        results.append({'case': 'invalid initial coordinates: ' + query, 'passed': True})
    context.close()
    browser.close()

(evidence / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
completed = True
print('PASS', len(results), 'map cases', flush=True)
