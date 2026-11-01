"""Owned emulator integration: update the old APK, preserve data, import content URI, play."""
from pathlib import Path
import os,subprocess,json,time
ROOT=Path(__file__).resolve().parents[1];ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
SERIAL=os.environ.get('INTERVAL_TEST_DEVICE','emulator-5554')
if not SERIAL.startswith('emulator-'):raise SystemExit('This fixture test writes disposable data; use an emulator only')
def adb(*args,data=None,check=True):
    for attempt in range(10):
        p=subprocess.run([str(ADB),'-s',SERIAL,*args],input=data,capture_output=True,timeout=35)
        if p.returncode and b'device offline' in p.stderr and attempt<9:time.sleep(1);continue
        break
    if check and p.returncode:raise RuntimeError(p.stderr.decode(errors='replace')+p.stdout.decode(errors='replace'))
    return p.stdout
def private_write(name,data):
    adb('shell','run-as','studio.interval','sh','-c',"'cat > "+name+"'",data=data)
def private_read(name):return adb('exec-out','run-as','studio.interval','cat',name,check=False)
out=ROOT/'build/godot-android-tests';out.mkdir(exist_ok=True)
# Start from the old client and seed the same private files it writes.
adb('shell','am','force-stop','studio.interval')
print(adb('install','-r',str(out/'fixture.apk')).decode(),flush=True)
seed=adb('shell','am','instrument','-w','studio.interval.godot.tests/.SeedLegacy').decode()
if 'PASS:' not in seed:raise SystemExit(seed)
print(seed,flush=True)
print(adb('install','-r',str(ROOT/'dist/MusounD-0.13.0.apk')).decode(),flush=True)
print(adb('install','-r',str(out/'fixture.apk')).decode(),flush=True)
for aspect,size in [('16x9','1280x720'),('4x3','1024x768')]:
    adb('shell','am','force-stop','studio.interval');adb('shell','wm','size',size)
    report='migration-'+aspect+'-'+str(time.time_ns())+'.json';request={'report':'user://'+report}
    if aspect=='16x9':request.update(legacy_score=333333,**{'import':'content://studio.interval.godot.tests.fixture/flow.interval'})
    private_write('files/diagnostic-request.json',json.dumps(request).encode())
    adb('logcat','-c');adb('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-p','studio.interval')
    deadline=time.monotonic()+100;result=None
    while time.monotonic()<deadline:
        data=private_read('files/'+report)
        if data.strip():
            try:result=json.loads(data);break
            except json.JSONDecodeError:pass
        time.sleep(1)
    logs=adb('logcat','-d','-s','godot','Godot','AndroidRuntime').decode(errors='replace');(out/('runtime-'+aspect+'.log')).write_text(logs,encoding='utf-8')
    if result is None:raise SystemExit('No Android report; inspect '+str(out/('runtime-'+aspect+'.log')))
    (out/('migration-'+aspect+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    for label in ['title','songs','settings','game','pause','results']:
        image=private_read('files/'+report.removesuffix('.json')+'-'+label+'.png')
        if image.startswith(b'\x89PNG'):(out/(aspect+'-'+label+'.png')).write_bytes(image)
    print(aspect,result,flush=True)
    if not result['ok']:raise SystemExit('Android diagnostic failed')
adb('shell','wm','size','reset')
print('PASS: Android update, native content URI import, both aspect ratios and playback')
