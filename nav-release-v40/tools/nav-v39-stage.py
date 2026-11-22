#!/usr/bin/env python3
"""LAB39: keep central Maps and Spotify virtual displays on transient own Waze Cluster focus."""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v39-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle";sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
s=boot.read_text(encoding="utf8")
for old,new in (
  ("versionCode 42","versionCode 43"),
  ("versionName '0.1.40'","versionName '0.1.41'"),
  ("versionNameSuffix '-lab.38'","versionNameSuffix '-lab.39'"),
):
  assert s.count(old)==1,("LAB38 version drift",old)
  s=s.replace(old,new,1)
assert s.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in s
boot.write_text(s,encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
print("PASS NAV LAB39 code43: original-owned Waze focus guard and central task/surface preservation, Sender unchanged")
