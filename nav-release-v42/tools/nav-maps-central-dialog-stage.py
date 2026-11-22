#!/usr/bin/env python3
"""LAB38: arm exact Google Maps route-replacement consent after an
authenticated Sender destination reaches NAV's EXISTING central Maps task.

Do NOT change dispatch target, Sender, wheel, OEM, or another launcher.
The opt-in NAV touch accessibility service handles ONLY NAV-owned
VirtualDisplay windows, using an exact dialog and unique action.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-maps-central-dialog-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
boot=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootLinkService.java"
touch=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavMosaicTouchService.java"
route=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/NavMosaicMapsRoute.java"
assert boot.is_file() and touch.is_file() and route.is_file()
s=boot.read_text(encoding="utf8")
old="""                    routeAccepted=outcome.startsWith(
                        "ENTREGADO AL GOOGLE MAPS EXISTENTE DE NAV");"""
new="""                    routeAccepted=outcome.startsWith(
                        "ENTREGADO AL GOOGLE MAPS EXISTENTE DE NAV");
                    if(routeAccepted){
                        // One-shot permission ONLY after Android confirmed
                        // the route was delivered to THIS exact NAV task.
                        // The destination itself and its task are unchanged.
                        try{
                            NavMosaicMapsRoute.arm(
                                this,nav.token,nav.displayId);
                        }catch(SecurityException stale){
                            NavMosaicMapsRoute.status(this,
                                "Maps NAV recibió el destino, pero la sesión "+
                                "ya no es verificable. Confirmá manualmente.");
                        }
                    }"""
assert s.count(old)==1 and "NavMosaicMapsRoute.arm(" not in s, (
    "Existing NAV map dispatch changed; refusing to edit route delivery")
s=s.replace(old,new,1)
assert "BridgeLocalAdb.deliverToExistingNavMaps(" in s
assert "if(nav!=null&&!BridgeRouteTarget.T1_LAUNCHER.equals(target))" in s
assert "routeAccepted=outcome.startsWith(" in s
assert "BridgeGoogleMapsReplacement.arm(this,displayId)" in s
boot.write_text(s,encoding="utf8")
t=touch.read_text(encoding="utf8")
r=route.read_text(encoding="utf8")
assert "NavMosaicMapsRoute.snapshot(this)" in t
assert "getWindowsOnAllDisplays()" in t
assert "NavMosaicSessionRegistry.MAPS.equals(" in t
assert "BridgeGoogleMapsReplacement.exitQuestion(" in t
assert "BridgeGoogleMapsReplacement.startAction(" in t
assert "NavMosaicMapsRoute.consumeExit(this,request)" in t
assert "NavMosaicMapsRoute.consumeStart(this,request)" in t
assert "performAction(" in t and "ACTION_CLICK" in t
assert "NavMosaicSessionRegistry.snapshot(context)" in r
assert "SystemClock.elapsedRealtime()" in r
assert "TTL=14000L" in r
assert "NavMosaicTouchService.navigationIntentArrived(context)" in r
for forbidden in ("T1Launcher","T1Mapa","force-stop","move-task",
                  "performGlobalAction","dispatchGesture("):
    assert forbidden not in r,forbidden
print("PASS Sender->existing NAV virtual Maps only, fresh 14s route consent; no duplicate maps or foreign controls")
