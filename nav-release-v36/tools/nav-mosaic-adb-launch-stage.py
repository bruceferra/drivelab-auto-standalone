#!/usr/bin/env python3
"""Install a session-scoped, narrowly allowlisted ADB launch into NAV's OWN
virtual displays, mirroring T1N145's documented shell-based launch mechanism.

This is deliberately NOT a generic ADB shell. Only installed map/Spotify
LAUNCHER components; only in-process, live NAV-owned public VDs 2..99; only
on a user-tapped LAB start; no stack movement/removal, root or OEM writes.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-mosaic-adb-launch-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
adb=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeLocalAdb.java"
if not adb.is_file():
    raise SystemExit("ABORT: missing original BridgeLocalAdb staged source")
s=adb.read_text(encoding="utf-8")
anchor="""    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe)throws Exception{"""
insert=r"""    /**
     * Start an installed launch component via already-consented local ADB.
     * Android app UID on Jetour may not call startActivity on a foreign
     * virtual display. T1N145 uses an equivalent shell am start --display
     * approach; the NAV-owned destination is checked twice, never arbitrary.
     */
    static String startOwnedVirtualApp(Context context,int displayId,
            String pkg,String component,long token)throws Exception{
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(live==null||live.token!=token||displayId<2||displayId>99||
           !((displayId==live.displayId&&pkg.equals(live.mapPackage))||
             (displayId==live.mediaDisplayId&&
                 "com.spotify.music".equals(pkg)))||
           component==null||
           !component.matches(java.util.regex.Pattern.quote(pkg)+
               "/\\.?[A-Za-z_][A-Za-z0-9_.$]+"))
            throw new SecurityException(
                "NAV ADB: componente o display no pertenece a la sesión actual");
        String command="am start --display "+displayId+
            " -a android.intent.action.MAIN"+
            " -c android.intent.category.LAUNCHER"+
            " --activity-single-top -n "+component;
        String response=shellInternal(context,command,false,true);
        if(response==null||
           !(response.contains("Starting: Intent")||
             response.contains("Activity not started,"))||
           response.contains("Error:")||
           response.contains("Exception")||
           response.contains("Permission Denial"))
            throw new SecurityException(
                "NAV ADB: Android no confirmó apertura de "+pkg+
                " en pantalla virtual propia");
        return "ADB confirmó solicitud a "+pkg+" en display "+displayId;
    }

    private static boolean exactOwnedVirtualLaunch(Context context,
            String command,boolean authorizedCategory){
        if(!authorizedCategory||!
           "ar.com.rango5gps.drivelabauto.boot.lab".equals(
               context.getPackageName()))return false;
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile(
            "am start --display ([2-9]|[1-9][0-9]) "+
            "-a android\\.intent\\.action\\.MAIN "+
            "-c android\\.intent\\.category\\.LAUNCHER "+
            "--activity-single-top -n "+
            "(com\\.google\\.android\\.apps\\.maps|com\\.waze|"+
            "com\\.spotify\\.music)/"+
            "(\\.?[A-Za-z_][A-Za-z0-9_.$]+)").matcher(command);
        if(!matcher.matches())return false;
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(live==null)return false;
        int id=Integer.parseInt(matcher.group(1));
        String pkg=matcher.group(2);
        return id==live.displayId&&pkg.equals(live.mapPackage)||
               id==live.mediaDisplayId&&"com.spotify.music".equals(pkg);
    }
""";
if s.count(anchor)!=1 or "startOwnedVirtualApp" in s:
    raise SystemExit("ABORT: changed ADB anchor or duplicate launch method")
s=s.replace(anchor,insert+anchor,1)
anchor="""        if(!(exactWazeMove||exactT1MapDelivery||overlayCommand||readStacks||removeValidatedStack||"""
replacement="""        boolean exactNavMosaicLaunch=exactOwnedVirtualLaunch(
            context,command,verifiedT1);
        if(!(exactWazeMove||exactT1MapDelivery||exactNavMosaicLaunch||
             overlayCommand||readStacks||removeValidatedStack||"""
if s.count(anchor)!=1:raise SystemExit("ABORT: ADB allowlist changed")
s=s.replace(anchor,replacement,1)
adb.write_text(s,encoding="utf-8")
print("PASS: scoped user-triggered ADB third-party app launch only on NAV-owned VirtualDisplay; no generic shell")
