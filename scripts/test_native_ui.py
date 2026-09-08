"""Black-box Android UI interaction through adb, restricted to our disposable emulator."""
from pathlib import Path
import subprocess,time,re,json,xml.etree.ElementTree as ET,hashlib
import os
from toolchain import configured_path

root=Path(__file__).resolve().parents[1]
adb=configured_path('ANDROID_HOME')/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')
serial='emulator-5580';package='local.position.helper'
out=root/'build/native-ui-tests';out.mkdir(parents=True,exist_ok=True)
results=[]
(out/'results.json').write_text('{"status":"running"}',encoding='utf-8')
def run(*args,timeout=25,binary=False):
    r=subprocess.run([str(adb),'-s',serial,*args],capture_output=True,timeout=timeout)
    if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace')+r.stdout.decode(errors='replace'))
    return r.stdout if binary else r.stdout.decode('utf-8',errors='replace')
def shell(*args):return run('shell',*map(str,args))
def dump():
    shell('uiautomator','dump','/sdcard/dd-ui.xml')
    xml=run('exec-out','cat','/sdcard/dd-ui.xml')
    (out/'latest.xml').write_text(xml,encoding='utf-8')
    return ET.fromstring(xml)
def bounds(node):return list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
def find(label,scroll=False):
    for attempt in range(6 if scroll else 3):
        tree=dump()
        hits=[n for n in tree.iter('node') if n.get('text')==label or n.get('content-desc')==label]
        for n in reversed(hits):
            b=bounds(n)
            if n.get('enabled')=='true' and b[2]>b[0] and b[3]>b[1]:return n
        if scroll:shell('input','swipe','530','1700','530','600','300')
        else:time.sleep(.4)
    raise AssertionError('Cannot find '+label)
def click(label,scroll=False):
    b=bounds(find(label,scroll));shell('input','tap',(b[0]+b[2])//2,(b[1]+b[3])//2);time.sleep(.25)
def fill(label,value):
    node=find(label);b=bounds(node);shell('input','tap',(b[0]+b[2])//2,(b[1]+b[3])//2)
    shell('input','keyevent','123');shell('input','keyevent',*(['67']*65));shell('input','text',value)
def present(value):return any(value in n.get('text','') or value==n.get('content-desc') for n in dump().iter('node'))
def screenshot(name):
    (out/(name+'.png')).write_bytes(run('exec-out','screencap','-p',binary=True))
    (out/(name+'.xml')).write_text(run('exec-out','cat','/sdcard/dd-ui.xml'),encoding='utf-8')
def passed(name):results.append({'case':name,'passed':True});print('PASS',name,flush=True)
def home():click('导航：定位')

assert run('get-state').strip()=='device'
assert shell('getprop','ro.kernel.qemu').strip()=='1','Never run this test against a real phone'
apk=root/'build/DingDianHelper-1.2.0.apk'
run('install','-r',str(apk),timeout=30)
shell('pm','clear',package) # Only our disposable emulator and this app's test fixtures.
shell('am','force-stop',package)
shell('am','start','-n',package+'/.MainActivity');time.sleep(1)
if present('稍后设置，先看看'):
    screenshot('01-first-setup');click('稍后设置，先看看')
find('导航：地点');find('导航：设置');find('选择目标地点');screenshot('02-home-empty')
passed('first run can defer setup; home has navigation and a visible primary action')
click('导航：地点');click('新增地点')
click('保存地点');assert present('请输入地点名称');passed('empty name stays in editor with inline validation')
fill('例如：公司门口','Office');fill('-90 到 90','31.2304');fill('-180 到 180','121.4737')
screenshot('03-editor-keyboard');click('保存地点');find('Office');screenshot('04-places')
passed('create a named point using native form with keyboard visible')
click('Office');fill('例如：公司门口','Discarded');shell('input','keyevent','4');time.sleep(.2)
if not present('放弃尚未保存的修改？'):click('返回')
find('继续编辑');click('放弃修改');find('Office');assert not present('Discarded')
passed('back navigation confirms and discards unsaved edits')
click('Office');fill('例如：公司门口','OfficeUpdated');click('保存地点');find('OfficeUpdated')
passed('editing a saved place updates its visible list entry')
fill('搜索地点名称','NoMatch');assert present('没有找到');screenshot('05-search-empty')
fill('搜索地点名称','Office');find('OfficeUpdated');click('使用');find('OfficeUpdated');find('开始模拟')
screenshot('06-home-selected');passed('search and use returns to home without starting simulation')
click('开始模拟');find('定位准备');passed('start with missing permission routes to actionable settings')
for permission in ('android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION','android.permission.POST_NOTIFICATIONS'):
    shell('pm','grant',package,permission)
shell('appops','set',package,'android:mock_location','allow')
shell('settings','put','secure','location_mode','3')
home();click('开始模拟');time.sleep(2);assert present('正在模拟');find('停止模拟');screenshot('07-running')
locations=shell('dumpsys','location');(out/'location-running.txt').write_text(locations,encoding='utf-8')
assert '31.2304' in locations and '121.4737' in locations
passed('start sends selected coordinates to Android provider and exposes stop')
click('导航：地点');find('停止模拟');screenshot('08-running-in-places');click('停止模拟');time.sleep(1);home();assert present('未在模拟')
passed('stop remains reachable from the places page and updates service state')
click('导航：地点');click('新增地点');fill('例如：公司门口','Second');fill('-90 到 90','91');fill('-180 到 180','120');click('保存地点');assert present('纬度应为')
fill('-90 到 90','22.54');click('保存地点');find('Second');find('OfficeUpdated')
passed('invalid coordinates are rejected and two valid saved locations coexist')
shell('am','force-stop',package);shell('am','start','-n',package+'/.MainActivity');time.sleep(.5);click('导航：地点');find('Second');find('OfficeUpdated')
passed('saved locations survive app process restart')
click('管理地点：Second');click('删除地点');click('取消');find('Second')
click('管理地点：Second');click('删除地点');click('删除');assert not present('Second');find('OfficeUpdated')
passed('delete has cancellation and affects only the chosen location')
click('导航：设置');screenshot('09-settings');home()
shell('settings','put','system','font_scale','1.3');time.sleep(1);shell('am','force-stop',package);shell('am','start','-n',package+'/.MainActivity');time.sleep(.6)
find('开始模拟');find('导航：地点');screenshot('10-large-font');passed('home primary action and navigation remain reachable at 1.3 font scale')
shell('settings','put','system','font_scale','1.0')
report={'device':serial,'android_api':shell('getprop','ro.build.version.sdk').strip(),'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
 'cases':results,'limitations':['realme hardware and ROM not tested','actual attendance webpage not tested','live physical GNSS not tested']}
report['status']='passed'
(out/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS',len(results),'native Android scenarios',flush=True)
