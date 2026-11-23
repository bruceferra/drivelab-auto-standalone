#!/usr/bin/env python3
"""LAB58 source staging ONLY. Needs signed Windows-local build; NO hosted Actions.
Universal pixel-size negotiation after physical NAV host layout. LAB57
verified Surface swaps and owner display tasks must be byte-identical.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v58-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
g=stage/"boot/build.gradle"
app=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot"
manager=(app/"NavFourSlotExperimentalManager.java").read_text(encoding="utf8")
geo=(app/"NavFourSlotResponsiveGeometry.java").read_text(encoding="utf8")
swap=(app/"NavFourSlotSwapController.java").read_text(encoding="utf8")
driver=(app/"NavFourSlotAndroidSurfaceDriver.java").read_text(encoding="utf8")
touch=(app/"NavVirtualMosaicActivity.java").read_text(encoding="utf8")
host=(app/"NavFourSlotClusterABHost.java").read_text(encoding="utf8")
parking=(app/"NavFourSlotParkingVisibilityPolicy.java").read_text(encoding="utf8")
assert "frontWhenParking(" in parking
assert "NavFourSlotParkingVisibilityPolicy.frontWhenParking(" in host
assert "originalWaze.bringToFront()" in host
assert "secondContainer.bringToFront()" in host
assert 'visibleA=aOccupied;' in host
assert 'visibleB=bOccupied;' in host
assert "addOnLayoutChangeListener(geometryLayoutListener)" in manager
assert "removeOnLayoutChangeListener(geometryLayoutListener)" in manager
assert "convergeGeometry(serial,generation,0)" in manager
assert "main.postDelayed(()->convergeGeometry(" in manager
assert "getRealSize(physical)" in manager
assert "vd.resize(target.width,target.height,nativeDensity(vd))" in manager
assert "ownerDisplays.get(content.id)" in manager
assert "geometrySettling" in manager
assert 'if(geometrySettling){' in manager
assert 'lastResizeRequest.get(content.id)' in manager
assert "NavFourSlotClusterABHost.fit(tv,actual.width,actual.height)" in manager
assert 'if("spotify".equals(stream.id))' not in geo
assert "return new Target(centralWidth,centralHeight)" in geo
assert "new Target(clusterWidth,clusterHeight)" in geo
assert "fourSlotManager.currentCentralGeometry(panel)" in touch
assert "fourSlotManager.centralHostResized()" in touch
assert "if(!d.awaitFrame(stream,next.slot))" in swap # LAB57 safety
assert "selected.virtual.setSurface(surface)" in driver
assert "surfaceBindingsExact()" in driver
assert "h.lastFrameTime>timestamp" in driver
assert "selected.virtual.setSurface(null)" not in driver
assert "versionName '0.2.0'" in (stage/"sender/build.gradle").read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in (app/"BootUpdater.java").read_text(encoding="utf8")
s=g.read_text(encoding="utf8")
for old,new in (
    ("versionCode 61","versionCode 62"),
    ("versionName '0.1.59'","versionName '0.1.60'"),
    ("versionNameSuffix '-lab.57'","versionNameSuffix '-lab.58'"),
):
    assert s.count(old)==1,("LAB57 version baseline differs",old)
    s=s.replace(old,new,1)
assert "applicationIdSuffix '.lab'" in s
g.write_text(s,encoding="utf8")
print("PASS LAB58 staging code62: generic real VD/host geometry and bounded Android resize convergence; LAB57 transfer unchanged; Windows-local signed build still required")
