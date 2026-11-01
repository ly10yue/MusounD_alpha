from pathlib import Path
import os,subprocess,shutil
root=Path(__file__).resolve().parents[1];sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk';jdk=Path(os.environ.get('JAVA_HOME','C:/Program Files/Android/Android Studio/jbr'))
bt=sorted((sdk/'build-tools').iterdir(),key=lambda p:[int(x) for x in p.name.split('.')])[-1];platform=sorted((sdk/'platforms').iterdir())[-1]/'android.jar'
out=root/'build/godot-android-tests';(out/'classes').mkdir(parents=True,exist_ok=True);(out/'dex').mkdir(exist_ok=True);(out/'assets').mkdir(exist_ok=True)
shutil.copyfile(root/'dist/0.12.0/Desultory-Signals-AT-Flow.interval',out/'assets/flow.interval')
shutil.copyfile(root/'app/src/main/assets/demo.json',out/'assets/demo.json')
shutil.copyfile(root/'app/src/main/assets/desultory-signals.ogg',out/'assets/music.ogg')
env=dict(os.environ);env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env['PATH']
def run(args):subprocess.run([str(x) for x in args],check=True,env=env,capture_output=True)
run([jdk/'bin/javac.exe','--release','8','-encoding','UTF-8','-cp',platform,'-d',out/'classes',*list((root/'tests/godot_android').glob('*.java'))])
run([jdk/'bin/jar.exe','cf',out/'classes.jar','-C',out/'classes','.'])
run([bt/'d8.bat','--lib',platform,'--min-api','26','--output',out/'dex',out/'classes.jar'])
run([bt/'aapt2.exe','link','-o',out/'base.apk','-I',platform,'--manifest',root/'tests/godot_android/AndroidManifest.xml','-A',out/'assets'])
run([jdk/'bin/jar.exe','uf',out/'base.apk','-C',out/'dex','classes.dex'])
run([bt/'zipalign.exe','-f','4',out/'base.apk',out/'aligned.apk'])
run([bt/'apksigner.bat','sign','--ks',root/'build/debug.jks','--ks-pass','pass:android','--out',out/'fixture.apk',out/'aligned.apk'])
print(out/'fixture.apk')
