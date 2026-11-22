#!/usr/bin/env python3
"""Increment staged, original-signer Jetour NAV LAB28 to LAB29.
Only version fields; preserves signed app ID, pairing and Sender version.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-v29-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
text=boot.read_text(encoding="utf-8")
for old,new in (
    ("versionCode 32","versionCode 33"),
    ("versionName '0.1.30'","versionName '0.1.31'"),
    ("versionNameSuffix '-lab.28'","versionNameSuffix '-lab.29'"),
):
    assert text.count(old)==1,("unexpected LAB28 version",old)
    text=text.replace(old,new,1)
assert text.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in text
boot.write_text(text,encoding="utf-8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf-8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf-8")
print("PASS NAV LAB29 staged: 0.1.31-lab.29 code33; original applicationId/signer, Sender unchanged")
