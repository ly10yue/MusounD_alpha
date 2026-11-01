"""Build native clients with the user's Godot install; no changes to the web client."""
from pathlib import Path
import argparse,subprocess,os,shutil,filecmp
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--godot',default=str(Path.home()/'Desktop/Godot_v4.7.1-stable_win64.exe/Godot_v4.7.1-stable_win64.exe'));p.add_argument('--target',choices=['Windows','Android','all'],default='all');a=p.parse_args()
project=ROOT/'godot';out=ROOT/'dist/godot-0.13.0';out.mkdir(parents=True,exist_ok=True)
temporary=ROOT/'build/godot-tmp';temporary.mkdir(parents=True,exist_ok=True)
for source in (ROOT/'app/src/main/assets').rglob('*'):
    if source.is_file():
        target=project/'assets'/source.relative_to(ROOT/'app/src/main/assets');target.parent.mkdir(parents=True,exist_ok=True)
        if not target.exists() or not filecmp.cmp(source,target,shallow=False):shutil.copy2(source,target)
keystore=ROOT/'build/debug.jks'
if a.target in ('all','Android') and not keystore.exists():
    jdk=Path(os.environ.get('JAVA_HOME','C:/Program Files/Android/Android Studio/jbr'))
    keytool=str(jdk/'bin/keytool.exe') if (jdk/'bin/keytool.exe').exists() else shutil.which('keytool')
    if not keytool:raise SystemExit('Install a JDK before exporting Android')
    subprocess.run([keytool,'-genkeypair','-keystore',str(keystore),'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Android,C=US','-keyalg','RSA','-keysize','2048','-validity','10000'],check=True,capture_output=True)
override=project/'override.cfg';saved=ROOT/'build/godot-test-override.cfg';env=dict(os.environ);env.update(TEMP=str(temporary),TMP=str(temporary))
env.update(GODOT_ANDROID_KEYSTORE_DEBUG_PATH=str(ROOT/'build/debug.jks'),GODOT_ANDROID_KEYSTORE_DEBUG_USER='androiddebugkey',GODOT_ANDROID_KEYSTORE_DEBUG_PASSWORD='android')
if override.exists():shutil.move(override,saved)
try:
    for target in (['Windows','Android'] if a.target=='all' else [a.target]):
        output=out/'MusounD.exe' if target=='Windows' else ROOT/'dist/MusounD-0.13.0.apk'
        command=[a.godot,'--headless','--path',str(project),'--log-file',str(ROOT/f'build/godot-export-{target}.log'),'--export-debug' if target=='Android' else '--export-release',target,str(output)]
        result=subprocess.run(command,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',creationflags=subprocess.CREATE_NO_WINDOW)
        (ROOT/f'build/godot-export-{target}-output.log').write_text(result.stdout+result.stderr,encoding='utf-8')
        if result.returncode or not output.exists() or 'SCRIPT ERROR' in result.stderr or "Couldn't save project.binary" in result.stderr:raise SystemExit(f'{target} build failed; see build/godot-export-{target}-output.log')
        print(f'{target}: {output} ({output.stat().st_size} bytes)',flush=True)
finally:
    if saved.exists():shutil.move(saved,override)
