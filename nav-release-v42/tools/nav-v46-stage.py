#!/usr/bin/env python3
"""NAV LAB46: staged Jetour-looking fullbleed UI, touch and home only."""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v46-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
mosaic=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavVirtualMosaicActivity.java"
home=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootActivity.java"
assert all(p.is_file() for p in (boot,sender,updater,mosaic,home))
text=boot.read_text(encoding="utf8")
for old,new in (
    ("versionCode 49","versionCode 50"),
    ("versionName '0.1.47'","versionName '0.1.48'"),
    ("versionNameSuffix '-lab.45'","versionNameSuffix '-lab.46'")):
    assert text.count(old)==1,("Unexpected base version",old)
    text=text.replace(old,new,1)
assert "applicationIdSuffix '.lab'" in text
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
screen=mosaic.read_text(encoding="utf8")
for phrase in (
  "updateTouchLabel(", "tryEnableSavedTouch(", "NavMosaicTouchService.connected()",
  "setClipToOutline(true)", "applyPanelCrop(", "normalizedCoverPoint(",
  "COVER_PANELS", "AUTO_TOUCH",
  'settingsButton=quickButton("⚙"', 'quickClusterButton=quickButton(',
  "root.addView(bar", "onSurfaceTextureDestroyed("):
    assert phrase in screen,("Missing LAB46 UI behavior",phrase)
assert "frame.setPadding(dp(this,1),0" in screen or "frame.setPadding(0,0,0,0)" in screen
assert "new TextView" in screen
home_src=home.read_text(encoding="utf8")
assert "⌂  VOLVER AL MOSAICO" in home_src
assert "home.setOnClickListener(v->finish())" in home_src
boot.write_text(text,encoding="utf8")
print("PASS NAV LAB46 code50; fullbleed rounded frame, default granted touch, persistent home, original Sender untouched")
