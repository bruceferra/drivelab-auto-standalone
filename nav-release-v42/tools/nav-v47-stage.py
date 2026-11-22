#!/usr/bin/env python3
"""LAB47: cosmetic transparent OEM-style controls on already working LAB46 NAV.
Four-slot feature is a separate source-only prototype, NOT part of this APK.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v47-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
mosaic=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavVirtualMosaicActivity.java"
assert all(p.is_file() for p in (boot,sender,updater,mosaic))
s=boot.read_text(encoding="utf8")
for old,new in (
    ("versionCode 50","versionCode 51"),
    ("versionName '0.1.48'","versionName '0.1.49'"),
    ("versionNameSuffix '-lab.46'","versionNameSuffix '-lab.47'"),
):
    assert s.count(old)==1,("LAB46 baseline changed",old)
    s=s.replace(old,new,1)
screen=mosaic.read_text(encoding="utf8")
for feature in (
    "new RippleDrawable(", "transparent.setColor(Color.TRANSPARENT)",
    "bar.setBackgroundColor(Color.TRANSPARENT)",
    "frame.setPadding(0,0,0,0)",
    "body.setPadding(0,0,0,0)",
    "quickClusterButton.setLetterSpacing(",
    "NavOwnClusterProbe.hasActiveWazeTrial()",
    "NavOwnClusterProbe.toggleWazeDirect(",
    "normalizedCoverPoint(", "applyPanelCrop(",
    "tryEnableSavedTouch(", "stopSpotifyAfterOwnedExit(",
):
    assert feature in screen,("missing Jetour design feature",feature)
assert "ColorStateList.valueOf(0xFF142C38)" not in screen
assert "frame.setPadding(dp(this,1),0" not in screen
assert "applicationIdSuffix '.lab'" in s
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
boot.write_text(s,encoding="utf8")
print("PASS LAB47 code51: transparent seamless controls and full-bleed panels; old Sender intact")
