#!/usr/bin/env python3
"""LAB55: no-pause direct NAV VirtualDisplay transfer with fourth-slot parking.
Only 4P opt-in; preserve the classic LAB52 mosaic, signed package, OTA and OEM.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2: raise SystemExit("usage: nav-v55-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
g=stage/"boot/build.gradle"
app=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot"
sender=stage/"sender/build.gradle"
updater=app/"BootUpdater.java"
files={n:app/n for n in (
    "NavFourSlotPolicy.java","NavFourSlotSwapController.java",
    "NavFourSlotAndroidSurfaceDriver.java","NavFourSlotClusterABHost.java",
    "NavOwnClusterProbe.java","NavFourSlotExperimentalManager.java")}
assert all(p.is_file() for p in (g,sender,updater,*files.values()))
s=g.read_text(encoding="utf8")
for old,new in (
    ("versionCode 58","versionCode 59"),
    ("versionName '0.1.56'","versionName '0.1.57'"),
    ("versionNameSuffix '-lab.54'","versionNameSuffix '-lab.55'")
):
    assert s.count(old)==1,("LAB54 baseline not staged",old)
    s=s.replace(old,new,1)
policy=files["NavFourSlotPolicy.java"].read_text(encoding="utf8")
swap=files["NavFourSlotSwapController.java"].read_text(encoding="utf8")
driver=files["NavFourSlotAndroidSurfaceDriver.java"].read_text(encoding="utf8")
cluster=files["NavFourSlotClusterABHost.java"].read_text(encoding="utf8")
probe=files["NavOwnClusterProbe.java"].read_text(encoding="utf8")
manager=files["NavFourSlotExperimentalManager.java"].read_text(encoding="utf8")
assert "new Bind(b,spare),new Bind(a,to),new Bind(b,from)" in policy
assert "Intercambio sin panel libre" in policy
assert "java.util.ArrayList<NavFourSlotPolicy.Bind> undo=" in swap
assert "boolean uncertainBind=false" in swap
assert "finalSlot==null||!d.awaitFrame(stream,finalSlot)" in swap
assert "selected.virtual.setSurface(surface)" in driver
assert "selected.virtual.setSurface(null)" not in driver
assert "occupant.virtual.setSurface(null)" not in driver
assert "Destino NAV ocupado: usar cuarto espacio libre" in driver
assert "void previewSecond()" in cluster
assert "static boolean previewFourSlotB(" in probe
assert "NavOwnClusterProbe.previewFourSlotB(" in manager
assert "host.setAlpha(1f)" in manager
assert "NavFourSlotClusterABHost.fit(" not in manager.split(
    "GeometryAndInput geo=",1)[1].split(
    "Map<NavFourSlotPolicy.Slot,Surface> borrowed=",1)[0]
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
assert "applicationIdSuffix '.lab'" in s
g.write_text(s,encoding="utf8")
print("PASS LAB55 code59: no setSurface(null); parking on free fourth slot; 4P opt-in only")
