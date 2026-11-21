#!/usr/bin/env python3
"""Advance disposable, original-certificate NAV LAB27 build to LAB28.

Runs only after nav-v27-stage.py in the isolated Android build. Never alters
the immutable Bridge source, Sender, signing key or live OTA pointer.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-v28-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
text=boot.read_text(encoding="utf-8")
for old,new in (
    ("versionCode 31","versionCode 32"),
    ("versionName '0.1.29'","versionName '0.1.30'"),
    ("versionNameSuffix '-lab.27'","versionNameSuffix '-lab.28'"),
):
    assert text.count(old)==1,("unexpected LAB27 staging version",old)
    text=text.replace(old,new,1)
assert text.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in text
boot.write_text(text,encoding="utf-8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf-8")
u=updater.read_text(encoding="utf-8")
assert u.count(r'0\\.(?:1|2)\\.[0-9]+-lab\\.[0-9]+')==2
assert "bridge-lab-ota/bridge-lab.json" in u
print("PASS NAV LAB28: original LAB package and signer, 0.1.30-lab.28 code32, Sender unchanged")
