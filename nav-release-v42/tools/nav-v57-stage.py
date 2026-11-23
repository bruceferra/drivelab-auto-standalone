#!/usr/bin/env python3
"""LAB57 signed candidate ONLY: native Spotify central A/B safe geometry
and fresh frame checks at EACH hop of occupied A/B swap. Not OTA.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v57-stage.py STAGE")
root=Path(sys.argv[1]).resolve()
gradle=root/"boot/build.gradle"
app=root/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot"
src=(app/"NavFourSlotSwapController.java").read_text(encoding="utf8")
geometry=(app/"NavFourSlotResponsiveGeometry.java").read_text(encoding="utf8")
manager=(app/"NavFourSlotExperimentalManager.java").read_text(encoding="utf8")
driver=(app/"NavFourSlotAndroidSurfaceDriver.java").read_text(encoding="utf8")
assert src.count("if(!d.awaitFrame(stream,next.slot))")==1
assert "Panel temporal/final sin cuadro nuevo para " in src
assert "undo.add(new NavFourSlotPolicy.Bind(next.stream,old));" in src
assert "if(!d.awaitFrame(stream,next.slot))" in src.split("undo.add(new NavFourSlotPolicy.Bind(next.stream,old));",1)[1]
assert 'if("spotify".equals(stream.id))' in geometry
assert "new Target(stream.width,stream.height)" in geometry
assert "new Target(centralWidth,centralHeight)" in geometry
assert "vd.resize(target.width,target.height" in manager
assert "NavFourSlotClusterABHost.fit(" in manager
assert "surfaceBindingsExact()" in driver
assert "h.lastFrameTime>timestamp" in driver
assert "selected.virtual.setSurface(null)" not in driver
assert "occupant.virtual.setSurface(null)" not in driver
assert "versionName '0.2.0'" in (root/"sender/build.gradle").read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in (app/"BootUpdater.java").read_text(encoding="utf8")
s=gradle.read_text(encoding="utf8")
for old,new in (
    ("versionCode 60","versionCode 61"),
    ("versionName '0.1.58'","versionName '0.1.59'"),
    ("versionNameSuffix '-lab.56'","versionNameSuffix '-lab.57'"),
):
    assert s.count(old)==1,("LAB56 stage not preserved",old)
    s=s.replace(old,new,1)
assert "applicationIdSuffix '.lab'" in s
gradle.write_text(s,encoding="utf8")
print("PASS LAB57 code61: Spotify native Central A/B and fresh frame per parked swap hop; original VDs/tasks unchanged")
