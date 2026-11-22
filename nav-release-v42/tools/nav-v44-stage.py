#!/usr/bin/env python3
"""DriveLab NAV LAB44: existing Waze task route + manual Waze lifecycle.
Staging ONLY. CI must pass, sign with original Android cert, then publish OTA."""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v44-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
route=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavWazeRoutePolicy.java"
assert boot.is_file() and sender.is_file() and updater.is_file() and route.is_file()
s=boot.read_text(encoding="utf8")
for before,after in (
    ("versionCode 47","versionCode 48"),
    ("versionName '0.1.45'","versionName '0.1.46'"),
    ("versionNameSuffix '-lab.43'","versionNameSuffix '-lab.44'"),
):
    assert s.count(before)==1,("LAB43 base version mismatch",before)
    s=s.replace(before,after,1)
assert "applicationIdSuffix '.lab'" in s
assert s.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
boot.write_text(s,encoding="utf8")
print("PASS LAB44 code48: Waze route task-only, manual close, sender untouched and OTA pointer unchanged")
