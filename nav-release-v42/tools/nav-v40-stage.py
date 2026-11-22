#!/usr/bin/env python3
"""NAV LAB40: isolated fresh Waze task and redacted read-only display trace."""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v40-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle";sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert boot.is_file() and sender.is_file() and updater.is_file()
s=boot.read_text(encoding="utf8")
for old,new in (
  ("versionCode 43","versionCode 44"),
  ("versionName '0.1.41'","versionName '0.1.42'"),
  ("versionNameSuffix '-lab.39'","versionNameSuffix '-lab.40'"),
):
  assert s.count(old)==1,("LAB39 version drift",old)
  s=s.replace(old,new,1)
assert s.count("manifestPlaceholders.bridgeLabel = 'DriveLab NAV'")==2
assert "applicationIdSuffix '.lab'" in s
boot.write_text(s,encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
print("PASS NAV LAB40 code44: fresh Waze task and redacted display trace, Sender untouched")
