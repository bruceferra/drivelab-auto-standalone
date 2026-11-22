#!/usr/bin/env python3
"""Gated Waze-on-existing-T1Mapa candidate. No third NAV display, no OTA.

This script runs in the disposable Bridge build, after NAV-owned ADB launch.
It adds only one exact launcher command for a verified existing T1Launcher
VirtualDisplay; never introduces general shell access or OEM controls.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-existing-t1mapa-waze-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
adb=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeLocalAdb.java"
assert adb.is_file(), "Only staged immutable v24 source is allowed"
s=adb.read_text(encoding="utf8")
anchor="""    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe)throws Exception{"""
addon=r"""    /** Owner-triggered T1Launcher renderer reuse, NEVER a new display. */
    static String startWazeInExistingT1Mapa(Context context,int target,
            String component,long token)throws Exception{
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(live==null||live.token!=token||
           !NavExistingT1MapaWaze.ownsVirtualDisplay(context,target,token))
            throw new SecurityException("T1Mapa no es un destino externo vigente");
        String command=NavExistingT1MapaPolicy.startCommand(
            target,component,live.displayId,live.mediaDisplayId);
        return shellInternal(context,command,false,true);
    }
    private static boolean exactExternalT1MapaWaze(
            Context context,String command,boolean authorized){
        if(!authorized||
           !"ar.com.rango5gps.drivelabauto.boot.lab".equals(
               context.getPackageName()))return false;
        java.util.regex.Matcher match=java.util.regex.Pattern.compile(
            "am start --display ([2-9]|[1-9][0-9]) "+
            "-a android\\.intent\\.action\\.MAIN "+
            "-c android\\.intent\\.category\\.LAUNCHER "+
            "--activity-single-top -n com\\.waze/"+
            "(\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200})"
        ).matcher(command);
        if(!match.matches())return false;
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        return live!=null&&NavExistingT1MapaWaze.ownsVirtualDisplay(
            context,Integer.parseInt(match.group(1)),live.token);
    }

"""
assert s.count(anchor)==1 and "startWazeInExistingT1Mapa(" not in s
s=s.replace(anchor,addon+anchor,1)
needle="""        boolean exactNavMosaicLaunch=exactOwnedVirtualLaunch(
            context,command,verifiedT1);
        if(!(exactWazeMove||exactT1MapDelivery||exactNavMosaicLaunch||"""
replacement="""        boolean exactNavMosaicLaunch=exactOwnedVirtualLaunch(
            context,command,verifiedT1);
        boolean exactExternalWaze=exactExternalT1MapaWaze(
            context,command,verifiedT1);
        if(!(exactWazeMove||exactT1MapDelivery||exactNavMosaicLaunch||
             exactExternalWaze||"""
assert s.count(needle)==1, "Original NAV-owned ADB whitelist changed"
s=s.replace(needle,replacement,1)
adb.write_text(s,encoding="utf8")
print("PASS scoped T1Mapa Waze ADB command; no Display 0/1 or generic ADB")
