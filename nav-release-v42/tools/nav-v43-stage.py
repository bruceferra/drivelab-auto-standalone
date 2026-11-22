#!/usr/bin/env python3
"""DriveLab NAV LAB43: staged public Waze launcher plus verified own-display relocation.
Never publish directly; LAB43 APK must pass full Android build, QA and original signer."""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v43-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
s=boot.read_text(encoding="utf8")
for before,after in (
    ("versionCode 46","versionCode 47"),
    ("versionName '0.1.44'","versionName '0.1.45'"),
    ("versionNameSuffix '-lab.42'","versionNameSuffix '-lab.43'"),
):
    assert s.count(before)==1,("LAB42 base version mismatch",before)
    s=s.replace(before,after,1)
assert "applicationIdSuffix '.lab'" in s
assert s.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
boot.write_text(s,encoding="utf8")
print("PASS staged LAB43 code47; existing Sender, Receiver and OTA pointer unchanged")
