#!/usr/bin/env python3
"""Attach strict Sender routes into existing NAV Maps and cluster Waze tasks without modifying
immutable Bridge LAB v24, its QR protocol, old T1Launcher target or wheel.

The original BridgeLocalAdb already permits one narrowly whitelisted
'am start --display N --task T --activity-single-top' only for exact Maps URI.
This adds TWO task snapshots tied to the process-local NAV virtual display ID
before making that existing whitelisted request. Never falls back to an
ordinary startActivity when the NAV mosaic is active.
"""
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: nav-mosaic-route-stage.py STAGE SOURCE_REPO")
stage=Path(sys.argv[1]).resolve()
repo=Path(sys.argv[2]).resolve()
base=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot"
if not (stage/"boot/build.gradle").is_file() or not (base/"BootLinkService.java").is_file():
    raise SystemExit("ABORT: missing disposable LAB staging tree")

registry=repo/"apps/nav/bridge-runtime-overlay/NavMosaicSessionRegistry.java"
if not registry.is_file() or (base/"NavMosaicSessionRegistry.java").exists():
    raise SystemExit("ABORT: NAV session registry missing or duplicate")
shutil.copy2(registry,base/"NavMosaicSessionRegistry.java")

adb=base/"BridgeLocalAdb.java"
s=adb.read_text(encoding="utf-8")
anchor="""    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe)throws Exception{"""
insert="""    /**
     * Sender -> independent NAV virtual Maps session (not T1Launcher).
     * Only the one task on NAV's live virtual display may receive the URI.
     * A second read rejects task moves, app changes or display reuse.
     * An ordinary startActivity would risk duplicating Maps: NEVER fallback.
     */
    static String deliverToExistingNavMaps(Context context,String uri,
            int ownedDisplay,long sessionToken)throws Exception{
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(live==null||live.displayId!=ownedDisplay||
           live.token!=sessionToken||
           !NavMosaicSessionRegistry.MAPS.equals(live.mapPackage))
            throw new SecurityException("NAV: no active owned Maps display");
        String first=shell(context,"am stack list");
        ar.com.rango5gps.drivelabnav.core.NavMosaicTaskPolicy.Target expected=
            ar.com.rango5gps.drivelabnav.core.NavMosaicTaskPolicy.find(
                first,ownedDisplay);
        if(expected==null)
            throw new SecurityException("NAV Maps: no single existing task on OWN virtual display");
        String second=shell(context,"am stack list");
        ar.com.rango5gps.drivelabnav.core.NavMosaicTaskPolicy.Target now=
            ar.com.rango5gps.drivelabnav.core.NavMosaicTaskPolicy.find(
                second,ownedDisplay);
        if(!ar.com.rango5gps.drivelabnav.core.NavMosaicTaskPolicy.same(expected,now))
            throw new SecurityException("NAV Maps: task changed; no route sent");
        NavMosaicSessionRegistry.Session beforeSend=
            NavMosaicSessionRegistry.snapshot(context);
        if(beforeSend==null||beforeSend.token!=sessionToken||
           beforeSend.displayId!=ownedDisplay)
            throw new SecurityException("NAV mosaic ended before destination");
        String command=ar.com.rango5gps.drivelabnav.core.NavMosaicTaskPolicy.command(
            now,uri,ownedDisplay);
        // Reuses the ORIGINAL BridgeLocalAdb exact-display/task/URI allowlist.
        // This 'verifiedT1' argument is the *existing shell command category*;
        // actual target is always the freshly verified NAV-owned display.
        String reply=shellInternal(context,command,false,true);
        return ar.com.rango5gps.drivelabnav.core.NavMosaicTaskPolicy.delivered(reply)?
            "ENTREGADO AL GOOGLE MAPS EXISTENTE DE NAV":
            "NAV MAPS: ANDROID NO CONFIRMÓ ENTREGA AL MISMO TOP TASK";
    }
"""
if s.count(anchor)!=1 or "deliverToExistingNavMaps" in s:
    raise SystemExit("ABORT: staged ADB method anchor unexpected")
adb.write_text(s.replace(anchor,insert+anchor,1),encoding="utf-8")

service=base/"BootLinkService.java"
s=service.read_text(encoding="utf-8")
anchor="        if(BridgeRouteTarget.T1_LAUNCHER.equals(target)){"
insert="""        // DriveLab NAV owns its virtual display only while the central
        // mosaic is alive. Preserve the explicit T1Launcher trial target.
        // For other modes, a live NAV session MUST NOT fall back to a new
        // Maps activity on Display 0/1; report a scoped, non-sensitive result.
        NavMosaicSessionRegistry.Session nav=
            NavMosaicSessionRegistry.snapshot(this);
        if(nav!=null&&!BridgeRouteTarget.T1_LAUNCHER.equals(target)){
            boolean wantMaps=BridgeRouteTarget.MAPS.equals(target)||
                BridgeRouteTarget.BOTH.equals(target);
            boolean wantWaze=BridgeRouteTarget.WAZE.equals(target)||
                BridgeRouteTarget.BOTH.equals(target);
            int expected=(wantMaps?1:0)+(wantWaze?1:0);
            int accepted=0;
            StringBuilder outcome=new StringBuilder();
            if(wantWaze){
                try{
                    String uri="waze://?"+
                        ("coord".equals(kind)?"ll=":"q=")+
                        Uri.encode(packet.value,"coord".equals(kind)?",":"")+
                        "&navigate=yes";
                    String result=BridgeLocalAdb.deliverToExistingNavWaze(this,uri);
                    if(result.startsWith(
                        "ENTREGADO AL WAZE EXISTENTE DEL CLÚSTER NAV"))
                        accepted++;
                    outcome.append(result);
                }catch(Exception unavailable){
                    outcome.append("WAZE NAV: NO ENTREGADO (")
                        .append(unavailable.getClass().getSimpleName())
                        .append("). Sin crear otro Waze.");
                }
            }
            if(wantMaps){
                if(wantWaze)outcome.append(" | ");
                if(!NavMosaicSessionRegistry.MAPS.equals(nav.mapPackage)){
                    outcome.append("MAPS NAV: mosaico no está en Maps");
                }else try{
                    String uri="google.navigation:q="+
                        Uri.encode(packet.value,"coord".equals(kind)?",":"")+
                        "&mode=d";
                    String result=BridgeLocalAdb.deliverToExistingNavMaps(
                        this,uri,nav.displayId,nav.token);
                    if(result.startsWith(
                        "ENTREGADO AL GOOGLE MAPS EXISTENTE DE NAV"))
                        accepted++;
                    outcome.append(result);
                }catch(Exception unavailable){
                    outcome.append("MAPS NAV: NO ENTREGADO (")
                        .append(unavailable.getClass().getSimpleName())
                        .append("). Sin abrir otro mapa.");
                }
            }
            boolean all=expected>0&&accepted==expected;
            prefs(this).edit().putString("last_route",outcome.toString())
                .putInt("received",prefs(this).getInt("received",0)+
                    (all?1:0)).apply();
            // An ACK confirms existing-task delivery only. The driver
            // must verify the actual route and map visibility on the T1.
            return all;
        }
"""
if s.count(anchor)!=1 or "deliverToExistingNavMaps(" in s:
    raise SystemExit("ABORT: staged BootLinkService anchor unexpected")
service.write_text(s.replace(anchor,insert+anchor,1),encoding="utf-8")
print("PASS: Sender Maps/Waze/BOTH gated by exact NAV-owned task; no other Waze or T1Launcher changes")
