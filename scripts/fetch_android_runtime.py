from pathlib import Path
import urllib.request, hashlib, zipfile, concurrent.futures

root=Path(__file__).resolve().parents[1]/'build/android-runtime'
root.mkdir(parents=True,exist_ok=True)
items=[
 ('https://dl.google.com/android/repository/emulator-windows_x64-15917651.zip',441926448,'54fa750822ff462d57e04fc8e98e60f08df2bb61',root),
 ('https://dl.google.com/android/repository/sys-img/android/x86_64-35_r02.zip',782404023,'2d857d170c0d1b827149565da34b3383e5306f7f',root/'image')]
def fetch(item):
    url,size,digest,dest=item; archive=root/url.rsplit('/',1)[1]
    if not archive.exists() or archive.stat().st_size!=size:
        with urllib.request.urlopen(url,timeout=40) as response, archive.open('wb') as output:
            count=0; milestone=0
            while True:
                chunk=response.read(1024*1024)
                if not chunk:break
                output.write(chunk);count+=len(chunk)
                if count//(50*1024*1024)>milestone:
                    milestone=count//(50*1024*1024);print(archive.name,count,'/',size,flush=True)
    hasher=hashlib.sha1()
    with archive.open('rb') as data:
        for chunk in iter(lambda:data.read(1024*1024),b''):hasher.update(chunk)
    assert archive.stat().st_size==size and hasher.hexdigest()==digest,'Official archive integrity failed'
    dest.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            target=(dest/info.filename).resolve()
            assert target.is_relative_to(dest.resolve()),'Unsafe archive path'
        z.extractall(dest)
    print('Verified and extracted',archive.name,flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    list(pool.map(fetch,items))
