#!/usr/bin/env python3
"""LAB53 candidate, opt-in four live physical slots using existing 3 NAV VDs.
No automatic four-slot mode. Do NOT update Sender, Receiver or Jetour OEM.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v53-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
g=stage/"boot/build.gradle"
app=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot"
sender=stage/"sender/build.gradle"
updater=app/"BootUpdater.java"
files=[g,sender,updater]+[app/x for x in (
    "NavVirtualMosaicActivity.java",
    "NavOwnClusterProbe.java","NavMosaicTouchService.java",
    "NavFourSlotPolicy.java","NavFourSlotSwapController.java",
    "NavFourSlotTaskInventory.java","NavFourSlotAndroidSurfaceDriver.java",
    "NavFourSlotClusterABHost.java","NavFourSlotControls.java",
    "NavFourSlotLiveRouting.java","NavFourSlotSessionCoordinator.java",
    "NavFourSlotExperimentalManager.java")]
assert all(p.is_file() for p in files),("ABORT missing NAV modules",
                                      [str(x) for x in files if not x.exists()])
s=g.read_text(encoding="utf8")
for old,new in (
    ("versionCode 56","versionCode 57"),
    ("versionName '0.1.54'","versionName '0.1.55'"),
    ("versionNameSuffix '-lab.52'","versionNameSuffix '-lab.53'")
):
    assert s.count(old)==1,("LAB52 signed baseline changed",old)
    s=s.replace(old,new,1)
ui=(app/"NavVirtualMosaicActivity.java").read_text(encoding="utf8")
touch=(app/"NavMosaicTouchService.java").read_text(encoding="utf8")
probe=(app/"NavOwnClusterProbe.java").read_text(encoding="utf8")
runtime=(app/"NavFourSlotExperimentalManager.java").read_text(encoding="utf8")
driver=(app/"NavFourSlotAndroidSurfaceDriver.java").read_text(encoding="utf8")
for needle in (
    'fourSlotButton=quickButton("↔ 4P",bar,1.7f)',
    'enableFourSlotExperiment()',
    'new NavFourSlotExperimentalManager(',
    'fourSlotManager.blockedInput()',
    'fourSlotManager.frameCentral(index)',
    'fourSlotManager.centralStream(panel)',
    'fourSlotManager.canSafelyClose()',
    'NavMosaicGeometry.fromSurface(width,height)',
    'toolbarShape.setCornerRadius(dp(this,9))'
):
    assert needle in ui,("LAB53 UI missing",needle)
for needle in (
    'NavFourSlotLiveRouting.armed(token)',
    'NavFourSlotLiveRouting.central(context,token,panel)',
    'if(selected==null)return false',
    'current.topApplicationIs(display,pkg)'
):
    assert needle in touch,("LAB53 touch missing",needle)
for needle in (
    'static FourSlotAnchor fourSlotAnchor(',
    'static boolean prepareFourSlots(',
    'static boolean restoreSingleFourSlot(',
    'fourHost.prepareSplit()',
    'NavWazeResumptionPolicy.Kind.RECLAIM',
    'BridgeLocalAdb.moveNewWazeStackToOwn(',
    'host.setBackground(clusterRounded)',
):
    assert needle in probe,("LAB53 Waze/cluster missing",needle)
assert 'texture.setBackground(' not in probe
assert 'new NavFourSlotSessionCoordinator(' in runtime
assert 'NavFourSlotTaskInventory.exact(' in runtime
assert 'NavFourSlotLiveRouting.snapshot(owner,token)' in runtime
assert 'new NavFourSlotAndroidSurfaceDriver(' in runtime
assert 'borrowed.put(NavFourSlotPolicy.Slot.CENTRAL_A,mapsSink)' in runtime
assert 'borrowed.put(NavFourSlotPolicy.Slot.TABLERO_A,' in runtime
assert 'ownerProvided=fromOwner!=null' in driver
assert 'if(!ownerProvided)ownedSurface.release()' in driver
assert 'selected.virtual.setSurface(surface)' in driver
assert 'occupant.virtual.setSurface(null)' in driver
assert 'NavFourSlotTaskInventory.exact(' in driver
assert "versionName '0.2.0'" in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
assert "applicationIdSuffix '.lab'" in s
g.write_text(s,encoding="utf8")
print("PASS LAB53 code57: opt-in real three-stream/four-slot Android surface trial, Waze recovery and QR preserved. Default classic NAV unchanged.")
