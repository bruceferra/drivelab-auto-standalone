#!/usr/bin/env python3
"""LAB38 one-shot Sender route replacement in NAV-owned Maps central, no other app changes."""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v38-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
data=boot.read_text(encoding="utf8")
for old,new in (
  ("versionCode 41","versionCode 42"),
  ("versionName '0.1.39'","versionName '0.1.40'"),
  ("versionNameSuffix '-lab.37'","versionNameSuffix '-lab.38'"),
):
  assert data.count(old)==1,("LAB37 version drift",old)
  data=data.replace(old,new,1)
assert data.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in data
boot.write_text(data,encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
print("PASS NAV LAB38 code42: owned central Maps route dialog; Sender/OTA untouched")
