#!/usr/bin/env python3
"""LAB54: prevent miniature Waze on failed 4P swap; exact Surface recovery.
No Sender, Receiver, OEM, Waze task, QR or NAV classic code changes.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v54-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
g=stage/"boot/build.gradle"
app=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot"
sender=stage/"sender/build.gradle"
base=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
paths={n:app/n for n in (
 "NavFourSlotClusterABHost.java","NavFourSlotAndroidSurfaceDriver.java",
 "NavFourSlotExperimentalManager.java","NavFourSlotSessionCoordinator.java",
 "NavFourSlotSwapController.java")}
assert all(p.is_file() for p in [g,sender,base,*paths.values()])
v=g.read_text(encoding="utf8")
for a,b in (
 ("versionCode 57","versionCode 58"),
 ("versionName '0.1.55'","versionName '0.1.56'"),
 ("versionNameSuffix '-lab.53'","versionNameSuffix '-lab.54'")
):
    assert v.count(a)==1,("LAB53 baseline changed",a)
    v=v.replace(a,b,1)
visual=paths["NavFourSlotClusterABHost.java"].read_text(encoding="utf8")
driver=paths["NavFourSlotAndroidSurfaceDriver.java"].read_text(encoding="utf8")
manager=paths["NavFourSlotExperimentalManager.java"].read_text(encoding="utf8")
coord=paths["NavFourSlotSessionCoordinator.java"].read_text(encoding="utf8")
swap=paths["NavFourSlotSwapController.java"].read_text(encoding="utf8")
assert "originalWaze.setLayoutParams(new FrameLayout.LayoutParams(\n            physicalWidth,physicalHeight,Gravity.LEFT|Gravity.TOP));" in visual
assert "secondContainer.setAlpha(0f)" in visual
assert "originalWaze.setTransform(new Matrix())" in visual
assert "void layoutFor(boolean aOccupied,boolean bOccupied)" in visual
assert "private boolean checkIdentity(RegisteredDisplay r)" in driver
assert "if(!checkIdentity(selected)||" not in driver
assert "!checkIdentity(selected)||!proof.ownsDisplay(stream)" in driver
assert "!checkIdentity(occupant)" in driver
assert "d.getState()!=Display.STATE_OFF" in driver
assert "final long deadline=SystemClock.elapsedRealtime()+5000L" in driver
assert "host,stream.width,stream.height" not in manager.split("GeometryAndInput geo=",1)[1].split("Map<NavFourSlotPolicy.Slot,Surface> borrowed=",1)[0]
assert "void onUnverifiedRollback()" in manager
assert "NavOwnClusterProbe.layoutFourSlots(\n                            owner,token,true,false);" in manager
assert "void onUnverifiedRollback();" in coord
assert "touches.onUnverifiedRollback()" in coord
assert "Cerrá NAV con ⏻" in coord
assert "shortCause(Exception error)" in swap
assert 'NavWazeResumptionPolicy' in (app/"NavOwnClusterProbe.java").read_text(encoding="utf8")
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in base.read_text(encoding="utf8")
assert "applicationIdSuffix '.lab'" in v
g.write_text(v,encoding="utf8")
print("PASS LAB54 code58: exact VD reattach after STATE_OFF, no precommit geometry changes, Waze full width and safe 4P rollback")
