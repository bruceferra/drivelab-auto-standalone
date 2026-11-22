#!/usr/bin/env python3
"""LAB45: add one strictly opt-in, fully scoped Spotify shutdown ADB command.
Execute AFTER the existing LAB44 Waze/Maps ADB whitelist has been staged.
Never allow arbitrary force-stop, another package, or a physical display.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-spotify-explicit-close-stage.py STAGE")
stage=Path(sys.argv[1]).resolve()
p=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeLocalAdb.java"
if not p.is_file(): raise SystemExit("ABORT: BridgeLocalAdb stage missing")
s=p.read_text(encoding="utf8")
old="""    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe,boolean verifiedT1,
            boolean verifiedNavOwnCluster)throws Exception{"""
new="""    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe,boolean verifiedT1,
            boolean verifiedNavOwnCluster)throws Exception{
        return shellInternal(context,command,verifiedWazeProbe,
            verifiedT1,verifiedNavOwnCluster,false);
    }
    private static String shellInternal(Context context,String command,
            boolean verifiedWazeProbe,boolean verifiedT1,
            boolean verifiedNavOwnCluster,
            boolean verifiedSpotifyStop)throws Exception{"""
if s.count(old)!=1: raise SystemExit("ABORT: LAB44 five-argument ADB contract missing")
s=s.replace(old,new,1)
anchor="""    private static boolean exactNavWazeDelivery(
"""
method="""    /**
     * Only after a separate explicit user confirmation in NAV's own UI
     * AND successful closure of BOTH NAV-only app tasks. This stops the
     * entire Spotify package (including playback services); unlike
     * removing one task, it can stop unrelated Spotify playback.
     */
    static String stopSpotifyAfterOwnedExit(
            Context context,int map,int media,long token)throws Exception{
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(!"ar.com.rango5gps.drivelabauto.boot.lab".equals(
                context.getPackageName())||
           live==null||live.token!=token||
           live.displayId!=map||live.mediaDisplayId!=media||
           map<2||map>99||media<2||media>99||map==media)
            throw new SecurityException(
                "Detención Spotify: sesión NAV propietaria no confirmada");
        // Caller has already verified both original NAV tasks are gone.
        // The ONLY extra shell command is exact package com.spotify.music.
        return shellInternal(context,
            "am force-stop com.spotify.music",false,false,false,true);
    }
"""
if s.count(anchor)!=1: raise SystemExit("ABORT: Waze route anchor missing")
s=s.replace(anchor,method+anchor,1)
old="""        if(!(exactWazeMove||exactT1MapDelivery||exactNavMosaicLaunch||
             exactNavClusterWaze||exactNavClusterMove||exactNavWazeRoute||"""
new="""        boolean exactSpotifyExit=verifiedSpotifyStop&&ownLab&&
            "am force-stop com.spotify.music".equals(command)&&
            NavMosaicSessionRegistry.snapshot(context)!=null;
        if(!(exactWazeMove||exactT1MapDelivery||exactNavMosaicLaunch||
             exactNavClusterWaze||exactNavClusterMove||exactNavWazeRoute||
             exactSpotifyExit||"""
if s.count(old)!=1: raise SystemExit("ABORT: ADB whitelist gate changed")
s=s.replace(old,new,1)
if s.count("am force-stop com.spotify.music")!=2:
    raise SystemExit("ABORT: unexpected Spotify force-stop signature")
p.write_text(s,encoding="utf8")
print("PASS LAB45: explicit whole-app Spotify stop only from owned NAV session, no arbitrary force-stop")
