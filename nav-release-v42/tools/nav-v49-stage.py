#!/usr/bin/env python3
"""LAB49 code53: paired Bridge resume after OTA + Jetour rounder surfaces.
Keep all three Android package IDs/OTA channels and sender intact.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v49-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
mosaic=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavVirtualMosaicActivity.java"
cluster=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavOwnClusterProbe.java"
receiver=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootReceiver.java"
activity=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootActivity.java"
manifest=stage/"boot/src/main/AndroidManifest.xml"
assert all(p.is_file() for p in (boot,sender,updater,mosaic,cluster,receiver,activity,manifest))
txt=boot.read_text(encoding="utf8")
for old,new in (
    ("versionCode 52","versionCode 53"),
    ("versionName '0.1.50'","versionName '0.1.51'"),
    ("versionNameSuffix '-lab.48'","versionNameSuffix '-lab.49'"),
):
    assert txt.count(old)==1,("LAB48 signed source mismatch",old)
    txt=txt.replace(old,new,1)
screen=mosaic.read_text(encoding="utf8")
assert "rounded.setCornerRadius(dp(this,24))" in screen
assert "toolbarShape.setCornerRadius(dp(this,21))" in screen
assert "bar.setClipToOutline(true)" in screen
assert "NavMosaicGeometry.fromSurface(width,height)" in screen
assert "normalizedCoverPoint(" in screen
assert "NavOwnClusterProbe.toggleWazeDirect(" in screen
assert "clusterRounded.setCornerRadius(19f*density)" in cluster.read_text(encoding="utf8")
assert ("host.setClipToOutline(true)" in cluster.read_text(encoding="utf8") or
        "texture.setClipToOutline(true)" in cluster.read_text(encoding="utf8"))
r=receiver.read_text(encoding="utf8")
a=activity.read_text(encoding="utf8")
m=manifest.read_text(encoding="utf8")
assert "Intent.ACTION_MY_PACKAGE_REPLACED.equals(event)" in r
assert "BootLinkService.prefs(context)" in r
assert "BridgeCrypto" in r and "unpackKey(saved).length!=16" in r
assert "BootLinkService.START" in r
assert "BootLinkService.prefs(this)" in a
assert "unpackKey(saved).length==16" in a
assert "android.intent.action.MY_PACKAGE_REPLACED" in m
assert "android.intent.action.BOOT_COMPLETED" in m
assert "PAIR_QR" not in r
assert "applicationIdSuffix '.lab'" in txt
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
boot.write_text(txt,encoding="utf8")
print("PASS LAB49 code53: QR secret preserved, OTA reconnect + app-opening fallback, three rounded surfaces, no Sender change")
