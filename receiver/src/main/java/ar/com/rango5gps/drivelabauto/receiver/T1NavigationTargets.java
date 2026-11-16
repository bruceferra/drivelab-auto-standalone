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
            String result=T1ClusterWazeRouter.routeGoogleMaps(c,
                intent("com.google.android.apps.maps",base,value));
            T1ClusterWazeRouter.status(c,result);
            return new Result(submitted(result),
                "Google Maps "+(submitted(result)?"SOLICITADO":"NO ABIERTO")+
                " en Display 1 · "+result+
                " · verificar navegación real");
        }
        if(!BridgeRouteTarget.BOTH.equals(target))
            return new Result(false,"Destino de mapa no admitido");
        if(!T1ClusterLayoutPolicy.active(c)||
            !T1ClusterLayoutPolicy.MODE_SPLIT.equals(
                T1ClusterLayoutPolicy.mode(c))||
            !"com.google.android.apps.maps".equals(
                T1ClusterLayoutPolicy.secondPackage(c)))
            return new Result(false,
                "Para AMBOS activá primero en P el diseño dividido "+
                "Waze izquierda / Google Maps derecha, verificado por ADB.");
        Display display=T1ClusterWazeRouter.selected(c);
        if(display==null)return new Result(false,"Display 1 no disponible");
        // Both deep links are sent to their OWN existing freeform panes.
        // No shell geometry, fullscreen fallback or OEM commands.
        String left=T1ClusterLayoutPolicy.start(c,display,
            intent("com.waze",base,value),
            T1ClusterLayoutPolicy.geometry(c,display,false),"Waze");
        if(!submitted(left)){
            T1ClusterWazeRouter.status(c,left);
            return new Result(false,"Waze izquierdo no solicitado: "+left);
        }
        String right=T1ClusterLayoutPolicy.start(c,display,
            intent("com.google.android.apps.maps",base,value),
            T1ClusterLayoutPolicy.geometry(c,display,true),"Google Maps");
        boolean both=submitted(right);
        String status=both?
            "Waze y Google Maps SOLICITADOS en sus dos mitades. "+
            "Verificar físicamente que AMBOS estén navegando":
            "Waze solicitado; Google Maps no abierto: "+right;
        T1ClusterWazeRouter.status(c,status);
        return new Result(both,status);
    }
}
