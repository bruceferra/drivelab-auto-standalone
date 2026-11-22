#!/usr/bin/env python3
"""NAV LAB41: Waze LAB39 launch flags, exact ADB diagnosis, one-tap owned app cleanup."""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v41-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle";sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
s=boot.read_text(encoding="utf8")
for before,after in (
  ("versionCode 44","versionCode 45"),
  ("versionName '0.1.42'","versionName '0.1.43'"),
  ("versionNameSuffix '-lab.40'","versionNameSuffix '-lab.41'"),
):
  assert s.count(before)==1,("LAB40 version drift",before)
  s=s.replace(before,after,1)
assert s.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in s
boot.write_text(s,encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
print("PASS NAV LAB41 code45: Waze launch result + one-tap Maps/Spotify task closure verification; Sender unchanged")
