#!/usr/bin/env python3
"""DriveLab NAV LAB35 signed candidate (NO OTA). Source built from pinned LAB34 and private NAV feature revision."""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v35-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
text=boot.read_text(encoding="utf8")
for old,new in (
  ("versionCode 38","versionCode 39"),
  ("versionName '0.1.36'","versionName '0.1.37'"),
  ("versionNameSuffix '-lab.34'","versionNameSuffix '-lab.35'"),
):
  assert text.count(old)==1,("LAB34 stage changed",old)
  text=text.replace(old,new,1)
assert text.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in text
boot.write_text(text,encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
print("PASS NAV LAB35 staged v0.1.37-lab.35 code39; unchanged Sender and legacy OTA")
