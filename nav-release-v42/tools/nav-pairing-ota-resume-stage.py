#!/usr/bin/env python3
"""LAB49: Restore SAME encrypted Xiaomi pairing listener after NAV OTA update.
This modifies only the disposable signed NAV staging tree, never Sender data,
Receiver, Jetour OEM, or the existing stored QR/AES secret.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-pairing-ota-resume-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootReceiver.java"
manifest=stage/"boot/src/main/AndroidManifest.xml"
link=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootLinkService.java"
config=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootConfig.java"
if not all(p.is_file() for p in (boot,manifest,link,config)):
    raise SystemExit("ABORT LAB49: staged original Boot package incomplete")
s=boot.read_text(encoding="utf8")
old="""        if(intent==null||
            !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;"""
new="""        if(intent==null)return;
        final String event=intent.getAction();
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(event)){
            // An OTA replacement kills the old NAV process and its LAN
            // service. Android preserves SharedPreferences for in-place
            // same-ID/same-signature updates, but DOES NOT automatically
            // restart the killed foreground listener. The Xiaomi still
            // possesses the AES key: never regenerate it or ask for QR.
            if(!BootConfig.enabled(context))return;
            final String saved=BootLinkService.prefs(context)
                .getString("secret","");
            try{
                if(ar.com.rango5gps.drivelabauto.common.BridgeCrypto
                    .unpackKey(saved).length!=16)return;
            }catch(Exception missingOrInvalid){return;}
            try{
                context.startForegroundService(new Intent(context,
                    BootLinkService.class).setAction(BootLinkService.START));
                BootConfig.prefs(context).edit().putString(
                    "last_status",
                    "OTA NAV: clave Xiaomi preservada, enlace cifrado "+
                    "reactivado SIN QR ni nueva autorización").apply();
            }catch(Exception denied){
                BootConfig.prefs(context).edit().putString(
                    "last_status",
                    "OTA NAV: vínculo conservado, pero Android bloqueó "+
                    "reanudar enlace ("+
                    denied.getClass().getSimpleName()+"). "+
                    "Abrí NAV; no reescanees QR.").apply();
            }
            return;
        }
        if(!Intent.ACTION_BOOT_COMPLETED.equals(event))return;"""
assert s.count(old)==1,"ABORT: BootReceiver original BOOT_COMPLETED gate changed"
assert "MY_PACKAGE_REPLACED" not in s
s=s.replace(old,new,1)
boot.write_text(s,encoding="utf8")
m=manifest.read_text(encoding="utf8")
anchor='<action android:name="android.intent.action.BOOT_COMPLETED" />'
new_anchor=anchor+'''
                <!-- OTA same-package replacement: resume existing LAN
                     listener only, never start any display/application. -->
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />'''
assert m.count(anchor)==1,"ABORT: manifest boot receiver anchor changed"
assert "android.intent.action.MY_PACKAGE_REPLACED" not in m
m=m.replace(anchor,new_anchor,1)
manifest.write_text(m,encoding="utf8")
# Guard existing secret ownership. A package replace MUST NOT remove or
# regenerate the key. The only lifecycle modification is the receiver.
source=link.read_text(encoding="utf8")
assert 'static final String PREFS="boot_receiver_lan_v1"' in source
assert '.putString("secret",BridgeCrypto.packKey(p.secret))' in source
assert 'if(!BootConfig.prefs(this).edit()' in source
assert 'static final String PREFS="drivelab_boot_mosaic_config_v1"' in config.read_text(encoding="utf8")
assert 'BootLinkService.START' in s
assert "PAIR_QR" not in s
assert 'BootAutoService.class' in s
assert 'BridgeStandaloneWindows.TAB_CONFIRMED,false' in s
print("PASS LAB49: saved AES key stays intact; only system MY_PACKAGE_REPLACED resumes encrypted LAN listener, no QR, OEM/UI or maps")
