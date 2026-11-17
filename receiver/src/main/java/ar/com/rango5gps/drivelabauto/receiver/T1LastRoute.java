package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget;

/** Last explicit route requested by Sender or OEM microphone, NOT full turn-by-turn
 * state. App-private, short-lived cache to restore after CLUSTER OFF removed
 * foreign Waze/Maps tasks. No background nav, no ADB and no center display.
 */
final class T1LastRoute {
    private static final String PREFS="t1_last_cluster_route_v1";
    private static final String TARGET="target",BASE="base",VALUE="value",AT="at";
    private static final long MAX_AGE_MS=12L*60L*60L*1000L;
    private T1LastRoute(){}
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(
            PREFS,Context.MODE_PRIVATE);
    }
    static void remember(Context c,String target,String base,String value){
        if((!BridgeRouteTarget.WAZE.equals(target)&&
            !BridgeRouteTarget.MAPS.equals(target)&&
            !BridgeRouteTarget.BOTH.equals(target))||
            (!"coord".equals(base)&&!"search".equals(base))||
            value==null||value.trim().isEmpty()||value.length()>1536)return;
        prefs(c).edit().putString(TARGET,target).putString(BASE,base)
            .putString(VALUE,value).putLong(AT,System.currentTimeMillis())
            .apply();
    }
    static String restore(Context c){
        if(!T1ClusterWazeRouter.enabled(c))
            return "No se restauró ruta: clúster OFF";
        if(T1ClusterWazeRouter.selected(c)==null)
            return "No se restauró ruta: Display de clúster no disponible";
        if(T1ClusterLayoutPolicy.pendingUnverified(c)&&
           !T1ClusterLayoutPolicy.savedDualSplit(c))
            return "Ruta pendiente: diseño del clúster sin verificar";
        SharedPreferences p=prefs(c);
        long age=System.currentTimeMillis()-p.getLong(AT,0L);
        if(age<0||age>MAX_AGE_MS){
            p.edit().clear().apply();
            return "Clúster ON: sin destino reciente que restaurar";
        }
        String target=p.getString(TARGET,"");
        String base=p.getString(BASE,"");
        String value=p.getString(VALUE,"");
        if(value.isEmpty()||(!"coord".equals(base)&&!"search".equals(base)))
            return "Clúster ON: no hay destino válido guardado";
        if(BridgeRouteTarget.MAPS.equals(target)||
           BridgeRouteTarget.BOTH.equals(target)){
            T1NavigationTargets.Result result=T1NavigationTargets.send(
                c,target,base,value,true);
            return result.attempted?
                "Ruta anterior solicitada nuevamente en el clúster · "+
                    "revisar navegación real":
                "No se restauró la ruta: "+result.status;
        }
        if(BridgeRouteTarget.WAZE.equals(target)){
            Uri uri=Uri.parse("waze://?"+("coord".equals(base)?"ll=":"q=")+
                Uri.encode(value,"coord".equals(base)?",":"")+"&navigate=yes");
            Intent intent=new Intent(Intent.ACTION_VIEW,uri)
                .setPackage("com.waze")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            boolean accepted=T1ClusterWazeRouter.routeDestination(c,intent);
            return accepted?"Ruta Waze anterior solicitada nuevamente":
                "No se restauró Waze: "+T1ClusterWazeRouter.prefs(c).getString(
                    T1ClusterWazeRouter.STATUS,"sin diagnóstico");
        }
        return "Clúster ON: no hay destino guardado";
    }
}
