#!/usr/bin/env python3
"""Emergency OTA compatibility patch for the *staged* DriveLab NAV Bridge only.

Bridge LAB v24 hardcodes 0.1.x-lab.N in BootUpdater.labOffer().
The previous NAV v25 (0.2.0-lab.25) was validly signed but impossible to
discover from the installed v24. Never touch the original v24 source, app ID,
certificate, Android data, Sender, T1Launcher or Cloud.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: bridge-v26-ota-compat.py STAGING_DIR")
stage = Path(sys.argv[1]).resolve()
gradle = stage / "boot/build.gradle"
updater = stage / "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert gradle.is_file() and updater.is_file(), "not staged Bridge code"

g = gradle.read_text(encoding="utf-8")
for old, new in (
    ("versionCode 29", "versionCode 30"),
    ("versionName '0.2.0'", "versionName '0.1.28'"),
    ("versionNameSuffix '-lab.25'", "versionNameSuffix '-lab.26'"),
):
    assert g.count(old) == 1, ("unexpected staged Bridge Gradle", old)
    g = g.replace(old, new)
assert "manifestPlaceholders.bridgeLabel = 'DriveLab NAV'" in g
assert "applicationIdSuffix '.lab'" in g
gradle.write_text(g, encoding="utf-8")

s = updater.read_text(encoding="utf-8")
# Upgrade the updater *inside v26* so future NAV releases can use 0.2.x,
# while versionName on v26 remains in the v24 updater's strict allowlist.
old_pattern = r'0\\.1\\.[0-9]+-lab\\.[0-9]+'
new_pattern = r'0\\.(?:1|2)\\.[0-9]+-lab\\.[0-9]+'
assert s.count(old_pattern) == 2, ("unexpected original OTA allowlist", s.count(old_pattern))
s = s.replace(old_pattern, new_pattern)
assert "bridge-lab-ota/bridge-lab.json" in s
assert "LAB_PACKAGE=PACKAGE+\".lab\"" in s
updater.write_text(s, encoding="utf-8")

# The existing v24 updater must accept the newly published manifest.
import re
assert re.fullmatch(r"0\.1\.[0-9]+-lab\.[0-9]+", "0.1.28-lab.26")
assert not re.fullmatch(r"0\.1\.[0-9]+-lab\.[0-9]+", "0.2.0-lab.25")
assert re.fullmatch(r"0\.(?:1|2)\.[0-9]+-lab\.[0-9]+", "0.2.1-lab.27")
print("PASS Bridge v26: code30, 0.1.28-lab.26 valid for installed v24, NAV label, future 0.2.x enabled")
