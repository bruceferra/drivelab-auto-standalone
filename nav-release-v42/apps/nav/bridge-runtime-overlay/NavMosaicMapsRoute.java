package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * Explicit Sender -> already-running NAV Maps destination consent.
 * ONLY the exact currently-owned virtual Maps display receives this
 * short-lived, one-use old-route exit + one-use start authorization.
 *
 * Does not launch apps, move tasks, touch OEM, or grant wheel permission.
 */
final class NavMosaicMapsRoute {
    private static final String PREF="nav_mosaic_maps_route_v1";
    private static final String STATUS="status";
    private static final long TTL=14000L;
    private static long serial;
    private static Request active;
    private NavMosaicMapsRoute(){}

    static final class Snapshot {
        final long serial,token;
        final int display;
        final boolean exitPending,startPending;
        Snapshot(Request r){
            serial=r.serial;token=r.token;display=r.display;
            exitPending=!r.exitDone;startPending=!r.startDone;
        }
    }
    private static final class Request {
        final long serial,token,createdAt;
        final int display;
        boolean exitDone,startDone;
        Request(long serial,long token,int display){
            this.serial=serial;this.token=token;this.display=display;
            createdAt=SystemClock.elapsedRealtime();
        }
    }
    static synchronized void arm(Context context,long token,int display){
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(live==null||live.token!=token||live.displayId!=display||
           display<2||display>99||
           !NavMosaicSessionRegistry.MAPS.equals(live.mapPackage))
            throw new SecurityException(
                "Sender no autorizó reemplazar ruta fuera de Maps NAV");
        active=new Request(++serial,token,display);
        if(NavMosaicTouchService.connected()){
            status(context,"Destino nuevo entregado a Maps NAV; "+
                "verificando reemplazo de ruta anterior.");
            NavMosaicTouchService.navigationIntentArrived(context);
        }else{
            status(context,"Destino recibido por Maps NAV, pero la "+
                "Accesibilidad del táctil NAV está desconectada. "+
                "Confirmá el diálogo manualmente.");
        }
    }
    static synchronized Snapshot snapshot(Context context){
        Request a=active;
        if(a==null)return null;
        long age=SystemClock.elapsedRealtime()-a.createdAt;
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(age<0||age>TTL||live==null||live.token!=a.token||
           live.displayId!=a.display||
           !NavMosaicSessionRegistry.MAPS.equals(live.mapPackage)){
            active=null;
            if(age>TTL)status(context,
                "Maps NAV: expiró la confirmación; comprobar ruta manualmente.");
            return null;
        }
        return new Snapshot(a);
    }
    private static boolean isCurrent(Context context,Snapshot expected){
        Snapshot now=snapshot(context);
        return expected!=null&&now!=null&&
            now.serial==expected.serial&&now.token==expected.token&&
            now.display==expected.display;
    }
    static synchronized boolean consumeExit(
            Context context,Snapshot expected){
        if(!isCurrent(context,expected)||active.exitDone)return false;
        active.exitDone=true;
        return true;
    }
    static synchronized boolean consumeStart(
            Context context,Snapshot expected){
        if(!isCurrent(context,expected)||active.startDone)return false;
        active.startDone=true;
        return true;
    }
    static synchronized void abort(Context context,Snapshot expected,
            String reason){
        if(!isCurrent(context,expected))return;
        active=null;
        status(context,reason);
    }
    static synchronized void complete(Context context,Snapshot expected,
            String message){
        if(!isCurrent(context,expected))return;
        active=null;
        status(context,message);
    }
    static synchronized void status(Context context,String message){
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString(STATUS,message).apply();
    }
    static String status(Context context){
        SharedPreferences prefs=context.getSharedPreferences(
            PREF,Context.MODE_PRIVATE);
        return prefs.getString(STATUS,"Sin destino nuevo para Maps NAV.");
    }
}
