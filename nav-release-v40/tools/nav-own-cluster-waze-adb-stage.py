#!/usr/bin/env python3
"""Stage exact manually approved Waze start on a NAV-created Cluster VID.
Source is staged only after central mosaic ADB allowlist; no generic shell,
no foreign launcher surface/service, no task moves or OEM writes.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:raise SystemExit("usage: nav-own-cluster-waze-adb-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
adb=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeLocalAdb.java"
assert adb.is_file()
s=adb.read_text(encoding="utf8")
old="""    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe,boolean verifiedT1)throws Exception{"""
new="""    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe,boolean verifiedT1)throws Exception{
        return shellInternal(context,command,verifiedWazeProbe,verifiedT1,false);
    }
    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe,boolean verifiedT1,
            boolean verifiedNavOwnCluster)throws Exception{"""
assert s.count(old)==1, "ADB overload is not original staged source"
s=s.replace(old,new,1)
anchor="""    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe)throws Exception{"""
addon=r"""    /**
     * Called ONLY after a manually confirmed parked test and double-read
     * ownership of NAV's third virtual display. Never route an existing
     * map task or send Waze to physical displays 0/1.
     */
    static String startOwnClusterWaze(Context context,int id,
            String component,long token)throws Exception{
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(live==null||live.token!=token||
           !NavOwnClusterProbe.ownsLiveWazeDisplay(context,id,token))
            throw new SecurityException(
                "NAV Cluster: pantalla virtual propia no verificada");
        String command=NavOwnClusterWazePolicy.startCommand(
            id,live.displayId,live.mediaDisplayId,component);
        return shellInternal(context,command,false,false,true);
    }
    private static boolean exactNavClusterWazeLaunch(
            Context context,String command,boolean authorized){
        if(!authorized||
           !"ar.com.rango5gps.drivelabauto.boot.lab".equals(
               context.getPackageName()))return false;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
            "am start --display ([2-9]|[1-9][0-9]) "+
            "--activity-new-task --activity-multiple-task "+
            "-a android\\.intent\\.action\\.MAIN "+
            "-c android\\.intent\\.category\\.LAUNCHER "+
            "-n com\\.waze/"+
            "(\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200})").matcher(command);
        if(!m.matches())return false;
        int ownId=Integer.parseInt(m.group(1));
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        return live!=null&&NavOwnClusterProbe.ownsLiveWazeDisplay(
            context,ownId,live.token);
    }
"""
assert s.count(anchor)==1 and "startOwnClusterWaze(" not in s
s=s.replace(anchor,addon+anchor,1)
old="""        boolean exactNavMosaicLaunch=exactOwnedVirtualLaunch(
            context,command,verifiedT1);
        if(!(exactWazeMove||exactT1MapDelivery||exactNavMosaicLaunch||"""
new="""        boolean exactNavMosaicLaunch=exactOwnedVirtualLaunch(
            context,command,verifiedT1);
        boolean exactNavClusterWaze=exactNavClusterWazeLaunch(
            context,command,verifiedNavOwnCluster);
        if(!(exactWazeMove||exactT1MapDelivery||exactNavMosaicLaunch||
             exactNavClusterWaze||"""
assert s.count(old)==1,"unexpected NAV ADB whitelist layout"
s=s.replace(old,new,1)
adb.write_text(s,encoding="utf8")
print("PASS own-only NAV Cluster Waze ADB whitelist; no generic shell or task move")
