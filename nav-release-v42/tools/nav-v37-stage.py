#!/usr/bin/env python3
"""NAV LAB37: owned VirtualDisplay real Waze launch trial, no foreign renderer."""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v37-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle";sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
s=boot.read_text(encoding="utf8")
for before,after in (
  ("versionCode 40","versionCode 41"),
  ("versionName '0.1.38'","versionName '0.1.39'"),
  ("versionNameSuffix '-lab.36'","versionNameSuffix '-lab.37'"),
):
  assert s.count(before)==1,("LAB36 stage unexpected",before)
  s=s.replace(before,after,1)
assert s.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in s
boot.write_text(s,encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
print("PASS NAV LAB37 code41; standalone-own Waze cluster only; unchanged Sender and OTA updater")
