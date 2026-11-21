package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

/**
 * In-memory identity of the NAV-created map virtual display. Never persists
 * IDs: Android can assign different IDs on every process/ignition cycle.
 * Inactive after owner leaves the central LAB activity; no T1Launcher link.
 */
final class NavMosaicSessionRegistry {
    private static final String LAB="ar.com.rango5gps.drivelabauto.boot.lab";
    static final String MAPS="com.google.android.apps.maps";
    static final String WAZE="com.waze";
    static final class Session {
        final int displayId, mediaDisplayId;
        final String mapPackage;
        final long token;
        Session(int id,int media,String pkg,long nonce){
            displayId=id;mediaDisplayId=media;mapPackage=pkg;token=nonce;
        }
    }
    private static volatile Session current;
    private static volatile long wheelToken;
    // Optional third virtual display, for a SEPARATE Cluster Waze task.
    // Never assign a physical (0/1) display to this slot.
    private static volatile int clusterWazeDisplayId=-1;
    private NavMosaicSessionRegistry(){}

    static synchronized void publish(Context context,int id,int mediaId,String pkg,long token){
        if(!LAB.equals(context.getPackageName())||
           id<2||id>99||mediaId<2||mediaId>99||id==mediaId||token<=0||
           (!MAPS.equals(pkg)&&!WAZE.equals(pkg))||
           !publicDisplay(context,id)||!publicDisplay(context,mediaId))
            throw new SecurityException("NAV display identity invalid");
        current=new Session(id,mediaId,pkg,token);
        clusterWazeDisplayId=-1;
    }
    static synchronized void publishClusterWaze(
            Context context,long token,int id){
        Session live=snapshot(context);
        if(live==null||live.token!=token||
           !MAPS.equals(live.mapPackage)||
           clusterWazeDisplayId!=-1||
           id<2||id>99||
           id==live.displayId||id==live.mediaDisplayId||
           !publicDisplay(context,id))
            throw new SecurityException(
                "NAV Cluster Waze: third virtual display not owned");
        clusterWazeDisplayId=id;
    }
    static synchronized void clearClusterWaze(long token,int id){
        Session live=current;
        if(live!=null&&live.token==token&&clusterWazeDisplayId==id)
            clusterWazeDisplayId=-1;
    }
    static boolean ownsClusterWaze(Context context,long token,int id,String pkg){
        Session live=snapshot(context);
        return WAZE.equals(pkg)&&live!=null&&live.token==token&&
            clusterWazeDisplayId==id&&
            id!=live.displayId&&id!=live.mediaDisplayId&&
            publicDisplay(context,id);
    }
    static synchronized void clear(long token){
        Session old=current;
        if(old!=null&&old.token==token){
            current=null;
            clusterWazeDisplayId=-1;
            if(wheelToken==token)wheelToken=0L;
        }
    }
    static synchronized boolean setWheel(Context context,long token,boolean enabled){
        Session live=snapshot(context);
        if(live==null||live.token!=token)return false;
        wheelToken=enabled?token:0L;
        return true;
    }
    static int manuallyApprovedWheelMapDisplay(Context context,String pkg){
        Session live=snapshot(context);
        return live!=null&&wheelToken==live.token&&
            MAPS.equals(live.mapPackage)&&
            MAPS.equals(pkg)?live.displayId:-1;
    }
    static Session snapshot(Context context){
        Session found=current;
        if(found==null||!LAB.equals(context.getPackageName())||
           !publicDisplay(context,found.displayId)||
           !publicDisplay(context,found.mediaDisplayId))return null;
        return current==found?found:null;
    }
    private static boolean publicDisplay(Context context,int id){
        DisplayManager manager=(DisplayManager)
            context.getSystemService(Context.DISPLAY_SERVICE);
        Display d=manager==null?null:manager.getDisplay(id);
        return d!=null&&d.getState()!=Display.STATE_OFF&&
            d.getDisplayId()>=2&&d.getDisplayId()<=99&&
            (d.getFlags()&Display.FLAG_PRIVATE)==0;
    }
}
