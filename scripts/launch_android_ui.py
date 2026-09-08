from pathlib import Path
import os,subprocess
root=Path(__file__).resolve().parents[1]/'build/android-runtime'
avds=root/'avds';avd=avds/'DingDianUi.avd';avd.mkdir(parents=True,exist_ok=True)
(avds/'DingDianUi.ini').write_text('avd.ini.encoding=UTF-8\npath='+str(avd)+'\ntarget=android-35\n',encoding='utf-8')
config={'avd.ini.encoding':'UTF-8','abi.type':'x86_64','hw.cpu.arch':'x86_64','hw.cpu.ncore':'4','hw.ramSize':'2048',
 'hw.lcd.width':'1080','hw.lcd.height':'2340','hw.lcd.density':'440','hw.keyboard':'no','hw.gpu.enabled':'yes','hw.gpu.mode':'swiftshader',
 'image.sysdir.1':str(root/'image/x86_64'),'tag.id':'default','disk.dataPartition.size':'2G','showDeviceFrame':'no','hw.gps':'yes'}
(avd/'config.ini').write_text('\n'.join(k+'='+v for k,v in config.items())+'\n',encoding='utf-8')
env=dict(os.environ);env['ANDROID_AVD_HOME']=str(avds);env['ANDROID_HOME']=str(root);env['ANDROID_SDK_ROOT']=str(root)
subprocess.run([str(root/'emulator/emulator.exe'),'-avd','DingDianUi','-port','5580','-no-window','-no-audio','-no-snapshot','-gpu','swiftshader','-no-boot-anim'],env=env,check=True)
