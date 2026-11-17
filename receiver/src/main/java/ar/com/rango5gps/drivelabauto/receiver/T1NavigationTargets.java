package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Display;
import ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget;

/** Encrypted Uber destination targeted to a real cluster navigation app.
 * This launches intents only. Never resize/restart tasks by shell while
 * driving. Dual navigation requires the PREVIOUSLY approved split layout.
 */
final class T1NavigationTargets {
    static final class Result {
        final boolean attempted;
        final String status;
        Result(boolean ok,String text){attempted=ok;status=text;}
    }
    private T1NavigationTargets(){}

    /** Visible user-configured dual map layout, even if an unrelated ADB
     * close/resize could not verify geometry. CLÚSTER ON already recovers
     * saved panes from that pending state; voice must use the SAME panels.
     * Neither this predicate nor dual dispatch changes approval/geometry.
     */
    static boolean dualMapPanelsConfigured(Context c){
        if(!T1ClusterWazeRouter.enabled(c)||
           T1ClusterWazeRouter.selected(c)==null||
           !T1ClusterLayoutPolicy.MODE_SPLIT.equals(
                T1ClusterLayoutPolicy.mode(c))||
           !T1ClusterLayoutPolicy.panelAOn(c)||
           !T1ClusterLayoutPolicy.panelBOn(c))return false;
        String a=T1ClusterLayoutPolicy.firstPackage(c);
        String b=T1ClusterLayoutPolicy.secondPackage(c);
        return ("com.waze".equals(a)&&
                "com.google.android.apps.maps".equals(b))||
               ("com.waze".equals(b)&&
                "com.google.android.apps.maps".equals(a));
    }
    private static Intent intent(String pkg,String base,String value){
        boolean coord="coord".equals(base);
        Uri uri="com.waze".equals(pkg)?
            Uri.parse("waze://?"+(coord?"ll=":"q=")+
                Uri.encode(value,coord?",":"")+"&navigate=yes"):
            Uri.parse("google.navigation:q="+
                Uri.encode(value,coord?",":"")+"&mode=d");
        return new Intent(Intent.ACTION_VIEW,uri)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    private static boolean submitted(String status){
        return status.startsWith("APP SOLICITADA EN VENTANA:")||
            status.startsWith("APP SOLICITADA:");
    }
    static Result send(Context c,String target,String base,
                       String value,boolean cluster){
        if(!cluster)
            return new Result(false,"CLÚSTER OFF: elegiste "+
                target+"; no se abrió navegación en pantalla central.");
        if(T1ClusterWazeRouter.selected(c)==null)
            return new Result(false,"Display del clúster no disponible");
        if(BridgeRouteTarget.MAPS.equals(target)){
            T1GoogleMapsReplacement.arm(c);
            String result=T1ClusterWazeRouter.routeGoogleMaps(c,
                intent("com.google.android.apps.maps",base,value));
            if(!submitted(result))T1GoogleMapsReplacement.cancel(c);
            T1ClusterWazeRouter.status(c,result);
            return new Result(submitted(result),
                "Google Maps "+(submitted(result)?"SOLICITADO":"NO ABIERTO")+
                " en Display 1 · "+result+
                " · verificar navegación real");
        }
        if(!BridgeRouteTarget.BOTH.equals(target))
            return new Result(false,"Destino de mapa no admitido");
        if(!dualMapPanelsConfigured(c))
            return new Result(false,
                "AMBOS requiere CLÚSTER ON y Waze/Google Maps "+
                "en dos paneles. No cambió el diseño.");
        // Target each selected app in its own existing freeform bounds,
        // regardless of A/B orientation or vendor ADB bounds reporting.
        // Never launch fullscreen or touch an unrelated OEM task.
        String left=T1ClusterLayoutPolicy.routeWazeIfActive(c,
            intent("com.waze",base,value));
        if(!submitted(left)){
            T1ClusterWazeRouter.status(c,left);
            return new Result(false,"Waze no solicitado: "+left);
        }
        T1GoogleMapsReplacement.arm(c);
        String right=T1ClusterWazeRouter.routeGoogleMaps(c,
            intent("com.google.android.apps.maps",base,value));
        boolean both=submitted(right);
        if(!both)T1GoogleMapsReplacement.cancel(c);
        String status=both?
            "Waze y Google Maps SOLICITADOS en sus dos mitades. "+
            "Verificar físicamente que AMBOS estén navegando":
            "Waze solicitado; Google Maps no abierto: "+right;
        T1ClusterWazeRouter.status(c,status);
        return new Result(both,status);
    }
}
