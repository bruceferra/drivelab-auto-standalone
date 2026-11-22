#!/usr/bin/env python3
"""LAB48: stop zoom/crop and route gestures using NAV real VirtualDisplay size.
Never alter Sender, Receiver, the route protocol, OEM or T1Launcher.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v48-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
mosaic=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavVirtualMosaicActivity.java"
touch=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavMosaicTouchService.java"
geometry=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavMosaicGeometry.java"
assert all(p.is_file() for p in (boot,sender,updater,mosaic,touch,geometry))
s=boot.read_text(encoding="utf8")
for old,new in (
    ("versionCode 51","versionCode 52"),
    ("versionName '0.1.49'","versionName '0.1.50'"),
    ("versionNameSuffix '-lab.47'","versionNameSuffix '-lab.48'"),
):
    assert s.count(old)==1,("LAB47 version unexpectedly changed",old)
    s=s.replace(old,new,1)
screen=mosaic.read_text(encoding="utf8")
for feature in (
    "NavMosaicGeometry.fromSurface(width,height)",
    "p.sourceWidth=dw;p.sourceHeight=dh",
    "texture.setDefaultBufferSize(dw,dh)",
    "p.sourceWidth,p.sourceHeight",
    "NavMosaicGeometry.fit(",
    "fillModeButton.setText(\"PANELES · REENCUADRAR SIN RECORTAR\")",
    "NavOwnClusterProbe.toggleWazeDirect(",
    "stopSpotifyAfterOwnedExit(",
    'quickClusterButton=quickButton("○  CLÚSTER OFF"',
    "setFullMosaic(true)",
):
    assert feature in screen,("LAB48 feature missing",feature)
assert "int dw=index==0?1280:480" not in screen
assert "int dh=index==0?720:800" not in screen
assert "Math.max(1f,original/screen)" not in screen
adapter=touch.read_text(encoding="utf8")
assert "owned.getRealSize(dimensions)" in adapter
assert ('expectedName=panel==0?"DriveLabNAVMapa":"DriveLabNAVSpotify"' in adapter or
        'String expectedName=panel==0?' in adapter)
# Experimental LAB53 keeps the same exact display-name guard, now dynamic
# when a four-slot owner explicitly activates verified surface swaps.
assert "NavMosaicGeometry.fromSurface(" in adapter
assert "int width=panel==0?1280:480" not in adapter
assert "int height=panel==0?720:800" not in adapter
assert "applicationIdSuffix '.lab'" in s
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
boot.write_text(s,encoding="utf8")
print("PASS LAB48 code52: native-size virtual panels, no cropping, actual-coordinate touch, Sender intact")
