#!/usr/bin/env python3
"""Stage original-signer NAV LAB33 from LAB32 for parked-only Cluster layout testing.

LAB32 working touch and dual virtual displays remain intact.
No install, OTA pointer or Sender changes here.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-v33-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
text=boot.read_text(encoding="utf-8")
for old,new in (
    ("versionCode 36","versionCode 37"),
    ("versionName '0.1.34'","versionName '0.1.35'"),
    ("versionNameSuffix '-lab.32'","versionNameSuffix '-lab.33'"),
):
    assert text.count(old)==1,("Unexpected LAB32 version",old)
    text=text.replace(old,new,1)
assert text.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in text
boot.write_text(text,encoding="utf-8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf-8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf-8")
print("PASS NAV LAB33 staged: 0.1.35-lab.33 code37; original app signer, Sender unchanged")
