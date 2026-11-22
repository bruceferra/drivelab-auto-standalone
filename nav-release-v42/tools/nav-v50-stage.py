#!/usr/bin/env python3
"""LAB50 regression repair: Android TextureView may not accept backgrounds.
Place Waze corner clipping on NAV's FrameLayout only. Preserve QR OTA fix.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v50-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
g=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
own=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavOwnClusterProbe.java"
receiver=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootReceiver.java"
manifest=stage/"boot/src/main/AndroidManifest.xml"
screen=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavVirtualMosaicActivity.java"
assert all(x.is_file() for x in (g,sender,own,receiver,manifest,screen))
s=g.read_text(encoding="utf8")
for a,b in (
    ("versionCode 53","versionCode 54"),
    ("versionName '0.1.51'","versionName '0.1.52'"),
    ("versionNameSuffix '-lab.49'","versionNameSuffix '-lab.50'")
):
    assert s.count(a)==1,("LAB49 expected",a)
    s=s.replace(a,b,1)
cluster=own.read_text(encoding="utf8")
assert 'host.setBackground(clusterRounded)' in cluster
assert 'host.setClipToOutline(true)' in cluster
assert 'texture.setSurfaceTextureListener(this)' in cluster
assert 'texture.setBackground(' not in cluster
assert 'texture.setClipToOutline(true)' not in cluster
assert 'bandHeight=wazeTrial?clusterHeight:' in cluster
assert 'DriveLabNAVClusterOwn' in cluster
assert 'BridgeLocalAdb.startOwnClusterWaze(' in cluster
assert 'new Presentation(owner,hardware)' in cluster
assert 'NavOwnClusterProbe' in screen.read_text(encoding="utf8")
assert "Intent.ACTION_MY_PACKAGE_REPLACED.equals(event)" in receiver.read_text(encoding="utf8")
assert "android.intent.action.MY_PACKAGE_REPLACED" in manifest.read_text(encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "applicationIdSuffix '.lab'" in s
g.write_text(s,encoding="utf8")
print("PASS LAB50 code54: fix TextureView UnsupportedOperationException; keep LAB49 QR restore, no Sender/OEM touch")
