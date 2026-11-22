#!/usr/bin/env python3
"""NAV LAB34: read-only independent Waze/Maps inventory, skipping rejected LAB33.

Stage from signed LAB32 chain; retain OEM, NAV touch, original signer and Sender.
No production OTA modification in this script.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-v34-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
text=boot.read_text(encoding="utf-8")
for old,new in (
    ("versionCode 36","versionCode 38"),
    ("versionName '0.1.34'","versionName '0.1.36'"),
    ("versionNameSuffix '-lab.32'","versionNameSuffix '-lab.34'"),
):
    assert text.count(old)==1,("Unexpected LAB32 stage",old)
    text=text.replace(old,new,1)
assert text.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in text
boot.write_text(text,encoding="utf-8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf-8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf-8")
print("PASS NAV LAB34 staged: 0.1.36-lab.34 code38; no LAB33 mirror; original signer and Sender unchanged")
