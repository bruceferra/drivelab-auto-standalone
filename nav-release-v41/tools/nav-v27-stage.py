#!/usr/bin/env python3
"""NEXT NAV LAB v27 candidate only: advance Jetour's installed signed version.

Never change the pinned Bridge v24 source, certificate, Android package,
Sender APK, pairing QR, T1Launcher or live OTA manifests. The installed NAV
v26 updater is able to accept 0.1.29-lab.27 (tag bridge-lab-v27).
Run ONLY after branding -> v26 compatibility -> NAV source staging.
"""
from pathlib import Path
import re
import sys

if len(sys.argv)!=2:
    raise SystemExit("usage: nav-v27-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
sender=stage/"sender/build.gradle"
assert boot.is_file() and updater.is_file() and sender.is_file()
b=boot.read_text(encoding="utf-8")
for old,new in (
    ("versionCode 30","versionCode 31"),
    ("versionName '0.1.28'","versionName '0.1.29'"),
    ("versionNameSuffix '-lab.26'","versionNameSuffix '-lab.27'"),
):
    assert b.count(old)==1,("unexpected source stage",old)
    b=b.replace(old,new,1)
assert "applicationIdSuffix '.lab'" in b
assert b.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
boot.write_text(b,encoding="utf-8")
u=updater.read_text(encoding="utf-8")
expected=r'0\\.(?:1|2)\\.[0-9]+-lab\\.[0-9]+'
assert u.count(expected)==2, "future-safe updater allowlist changed"
assert "bridge-lab-ota/bridge-lab.json" in u
assert "LAB_PACKAGE=PACKAGE+\".lab\"" in u
assert re.fullmatch(r"0\.(?:1|2)\.[0-9]+-lab\.[0-9]+","0.1.29-lab.27")
assert re.fullmatch(r"bridge-lab-v[0-9]+","bridge-lab-v27")
assert sender.read_text(encoding="utf-8").count("versionName '0.2.0'")==1, "Sender must remain installed v0.2.0"
print("PASS Jetour NAV v27 staged: 0.1.29-lab.27 code 31; old original certificate/package, separate Sender 0.2.0, no OTA published")
