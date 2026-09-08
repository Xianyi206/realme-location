from pathlib import Path
import concurrent.futures, subprocess, hashlib, zipfile, os
root=Path(__file__).resolve().parents[1]/'build/android-runtime'
items=[('https://dl.google.com/android/repository/emulator-windows_x64-15917651.zip',441926448,'54fa750822ff462d57e04fc8e98e60f08df2bb61',root),('https://dl.google.com/android/repository/sys-img/android/x86_64-35_r02.zip',782404023,'2d857d170c0d1b827149565da34b3383e5306f7f',root/'image')]
jobs=[];plans=[];chunk=4*1024*1024
for url,size,digest,dest in items:
    archive=root/url.rsplit('/',1)[1];prefix=archive.stat().st_size if archive.exists() else 0
    parts=root/(archive.name+'.parts');parts.mkdir(exist_ok=True);segments=[]
    for start in range(prefix,size,chunk):
        end=min(start+chunk,size)-1;path=parts/f'{start}.part';segments.append(path);jobs.append((url,start,end,path))
    plans.append((archive,size,digest,dest,segments))
def fetch(job):
    url,start,end,path=job
    if path.exists() and path.stat().st_size==end-start+1:return
    result=subprocess.run(['curl.exe','-sS','-L','--fail','--retry','2','--max-time','90','--range',f'{start}-{end}','-o',str(path),url+f'?ddrange={start}'],capture_output=True)
    if result.returncode or path.stat().st_size!=end-start+1:raise RuntimeError(f'Range failed {start}: {result.stderr.decode(errors="replace")}')
with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
    for n,_ in enumerate(pool.map(fetch,jobs),1):
        if n%20==0:print('Downloaded ranges',n,'/',len(jobs),flush=True)
for archive,size,digest,dest,segments in plans:
    with archive.open('ab') as output:
        for segment in segments:
            with segment.open('rb') as data:
                for b in iter(lambda:data.read(1024*1024),b''):output.write(b)
    h=hashlib.sha1()
    with archive.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    assert archive.stat().st_size==size and h.hexdigest()==digest,'Archive integrity mismatch'
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():assert (dest/info.filename).resolve().is_relative_to(dest.resolve())
        z.extractall(dest)
    print('Verified and extracted',archive.name,flush=True)
