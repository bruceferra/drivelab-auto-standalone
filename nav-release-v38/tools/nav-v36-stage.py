#!/usr/bin/env python3
"""NAV LAB36: standalone Cluster surface candidate, no external launcher, NO OTA."""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v36-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
data=boot.read_text(encoding="utf8")
for old,new in (
  ("versionCode 38","versionCode 40"),
  ("versionName '0.1.36'","versionName '0.1.38'"),
  ("versionNameSuffix '-lab.34'","versionNameSuffix '-lab.36'"),
):
  assert data.count(old)==1,("LAB34 to LAB36 bump unexpected",old)
  data=data.replace(old,new,1)
assert data.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in data
boot.write_text(data,encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
print("PASS NAV LAB36 code40; standalone-owned Cluster; original signature; Sender unchanged")
