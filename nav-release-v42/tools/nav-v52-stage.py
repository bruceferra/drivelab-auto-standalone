#!/usr/bin/env python3
"""LAB52: exact previous Waze task recovery on CLUSTER ON / NAV OTA.

Do not stage the unintegrated four-pane experimental code. Preserve LAB51
app geometry, cluster Presentation, QR, Sender/Receiver and OEM behavior.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-v52-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
g=stage/"boot/build.gradle"
sender=stage/"sender/build.gradle"
own=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavOwnClusterProbe.java"
resume=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavWazeResumptionPolicy.java"
route=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavWazeRoutePolicy.java"
mosaic=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavVirtualMosaicActivity.java"
bootreceiver=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootReceiver.java"
updater=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java"
assert all(p.is_file() for p in (g,sender,own,resume,route,mosaic,bootreceiver,updater))
txt=g.read_text(encoding="utf8")
for old,new in (
    ("versionCode 55","versionCode 56"),
    ("versionName '0.1.53'","versionName '0.1.54'"),
    ("versionNameSuffix '-lab.51'","versionNameSuffix '-lab.52'")
):
    assert txt.count(old)==1,("LAB51 signed baseline changed",old)
    txt=txt.replace(old,new,1)
probe=own.read_text(encoding="utf8")
policy=resume.read_text(encoding="utf8")
assert "NavWazeResumptionPolicy.inspect(" in probe
assert "NavWazeResumptionPolicy.Kind.RECLAIM" in probe
assert "NavWazeResumptionPolicy.Kind.CONFIRM_LEGACY" in probe
assert 'putInt("task_id",launchedId)' in probe
assert 'putLong("verified_at",System.currentTimeMillis())' in probe
assert 'if(failure==null&&verifiedWazeTaskId>10)' in probe
assert 'launchOwnWaze(true)' in probe
assert 'BridgeLocalAdb.moveNewWazeStackToOwn(' in probe
assert 'NavWazeRelocationPolicy.relocated(' in probe
assert 'NavWazeRoutePolicy.Target senderRouteTarget(' in probe
assert 'MAX_AGE_MS=14L*24*3600*1000' in policy
assert 'return bad("Waze está en otro display; no se toca")' in policy
assert "host.setBackground(clusterRounded)" in probe
assert "texture.setBackground(" not in probe
assert 'toolbarShape.setCornerRadius(dp(this,9))' in mosaic.read_text(encoding="utf8")
assert 'NavMosaicGeometry.fromSurface(width,height)' in mosaic.read_text(encoding="utf8")
assert 'Intent.ACTION_MY_PACKAGE_REPLACED.equals(event)' in bootreceiver.read_text(encoding="utf8")
assert 'versionName \'0.2.0\'' in sender.read_text(encoding="utf8")
assert "bridge-lab-ota/bridge-lab.json" in updater.read_text(encoding="utf8")
assert "applicationIdSuffix '.lab'" in txt
g.write_text(txt,encoding="utf8")
print("PASS LAB52 code56: same verified Waze task across OTA, legacy one-time consent, existing Sender target, no 4-pane/OEM changes")
