#!/usr/bin/env python3
"""NAV LAB51: reduce ONLY upper toolbar corner radius while preserving LAB50."""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v51-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
g=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
mosaic=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavVirtualMosaicActivity.java"
cluster=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavOwnClusterProbe.java"
receiver=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootReceiver.java"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert all(p.is_file() for p in (g,sender,mosaic,cluster,receiver,updater))
txt=g.read_text(encoding="utf8")
for old,new in (
    ("versionCode 54","versionCode 55"),
    ("versionName '0.1.52'","versionName '0.1.53'"),
    ("versionNameSuffix '-lab.50'","versionNameSuffix '-lab.51'")
):
    assert txt.count(old)==1,("unexpected LAB50 baseline",old)
    txt=txt.replace(old,new,1)
m=mosaic.read_text(encoding="utf8")
assert m.count("toolbarShape.setCornerRadius(dp(this,9))")==1
assert "toolbarShape.setCornerRadius(dp(this,21))" not in m
assert "rounded.setCornerRadius(dp(this,24))" in m
assert "NavMosaicGeometry.fromSurface(width,height)" in m
assert "NavOwnClusterProbe.toggleWazeDirect(" in m
c=cluster.read_text(encoding="utf8")
assert "host.setBackground(clusterRounded)" in c
assert "texture.setBackground(" not in c
assert "clusterRounded.setCornerRadius(19f*density)" in c
assert "Intent.ACTION_MY_PACKAGE_REPLACED.equals(event)" in receiver.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "applicationIdSuffix '.lab'" in txt
g.write_text(txt,encoding="utf8")
print("PASS LAB51 code55: ONLY navbar corner radius softened, LAB50 cluster+QR+Sender preserved")
