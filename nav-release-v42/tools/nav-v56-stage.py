#!/usr/bin/env python3
"""LAB56: responsive 960x720 cluster halves, original LIVE VD/task stays intact.
Apply ONLY after LAB55 staged. No changes to swap, Sender, Receiver, OEM.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v56-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
g=stage/"boot/build.gradle"
app=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot"
required=[g,app/"NavFourSlotExperimentalManager.java",
          app/"NavFourSlotResponsiveGeometry.java",
          app/"NavFourSlotAndroidSurfaceDriver.java",
          app/"NavFourSlotSwapController.java",
          app/"BootUpdater.java",stage/"sender/build.gradle"]
assert all(p.is_file() for p in required)
s=g.read_text(encoding="utf8")
for old,new in (
    ("versionCode 59","versionCode 60"),
    ("versionName '0.1.57'","versionName '0.1.58'"),
    ("versionNameSuffix '-lab.55'","versionNameSuffix '-lab.56'")
):
    assert s.count(old)==1,("LAB55 baseline differs",old)
    s=s.replace(old,new,1)
manager=(app/"NavFourSlotExperimentalManager.java").read_text(encoding="utf8")
geo=(app/"NavFourSlotResponsiveGeometry.java").read_text(encoding="utf8")
driver=(app/"NavFourSlotAndroidSurfaceDriver.java").read_text(encoding="utf8")
swap=(app/"NavFourSlotSwapController.java").read_text(encoding="utf8")
assert "vd.resize(target.width,target.height," in manager
assert "tv.getSurfaceTexture().setDefaultBufferSize(" in manager
assert "new android.graphics.Matrix()" in manager
assert "NavFourSlotClusterABHost.fit(" in manager
assert "NavFourSlotLiveRouting.snapshot(owner,token)" in manager
assert "live.state.revision!=verified.revision" in manager
assert "generation!=geometryGeneration" in manager
assert "forSlot(" in geo and "half" in geo
assert "new Target(centralWidth,centralHeight)" in geo
central=(app/"NavVirtualMosaicActivity.java").read_text(encoding="utf8")
assert "fourSlotManager.currentCentralGeometry(panel)" in central
assert "fourSlotManager.centralHostResized()" in central
assert "NavFourSlotResponsiveGeometry.Target currentCentralGeometry(" in manager
assert "selected.virtual.setSurface(surface)" in driver
assert "surfaceBindingsExact()" in driver
assert "selected.virtual.getSurface()!=surface" in driver
assert "h.lastFrameTime>timestamp" in driver
assert "st.getTimestamp()" in driver
assert "selected.virtual.setSurface(null)" not in driver
assert "occupant.virtual.setSurface(null)" not in driver
assert "java.util.ArrayList<NavFourSlotPolicy.Bind> undo" in swap
assert "versionName '0.2.0'" in (stage/"sender/build.gradle").read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in (app/"BootUpdater.java").read_text(encoding="utf8")
assert "applicationIdSuffix '.lab'" in s
g.write_text(s,encoding="utf8")
print("PASS LAB56 code60: responsive 960x720 cluster halves, original touch/VD/task and LAB55 swap retained")
