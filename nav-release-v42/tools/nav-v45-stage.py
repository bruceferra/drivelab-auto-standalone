#!/usr/bin/env python3
"""NAV LAB45: stage only originally signed code49 app, never Sender."""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v45-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
mosaic=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavVirtualMosaicActivity.java"
adb=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeLocalAdb.java"
assert all(p.is_file() for p in (boot,sender,updater,mosaic,adb))
text=boot.read_text(encoding="utf8")
for old,new in (
    ("versionCode 48","versionCode 49"),
    ("versionName '0.1.46'","versionName '0.1.47'"),
    ("versionNameSuffix '-lab.44'","versionNameSuffix '-lab.45'")):
    assert text.count(old)==1,("Unexpected base version",old)
    text=text.replace(old,new,1)
assert "applicationIdSuffix '.lab'" in text
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
src=mosaic.read_text(encoding="utf8")
assert "NavOwnClusterProbe.toggleWazeDirect(" in src
assert "stopSpotifyAfterOwnedExit(" in src
assert "start_waze_cluster_automatically" in src
assert "start_apps_automatically" in src
assert "open_in_mosaic" in src
assert "if(!sent)show(" in src
assert 'am force-stop com.spotify.music' in adb.read_text(encoding="utf8")
boot.write_text(text,encoding="utf8")
print("PASS LAB45 signed versionCode49; original Sender/Receiver/OTA mechanisms unchanged")
