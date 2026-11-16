package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;

/** Panel A/B names are logical windows, not Android display IDs.
 * Other applications remain view-only until explicit compatibility work.
 */
final class T1WheelTarget {
    private static final String KEY="active_panel";
    private T1WheelTarget(){}
    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(T1WazeWheelService.PREFS,Context.MODE_PRIVATE);
    }
    static boolean panelB(Context c){return "B".equals(prefs(c).getString(KEY,"A"));}
    static void select(Context c,boolean b){
        prefs(c).edit().putString(KEY,b?"B":"A").apply();
    }
    static String name(Context c){return panelB(c)?"PANEL B":"PANEL A";}
    static String application(Context c){
        return panelB(c)?T1ClusterLayoutPolicy.secondLabel(c):"Waze";
    }
    static boolean mapsAllowed(Context c){
        return panelB(c)&&T1ClusterWazeRouter.enabled(c)&&
            T1ClusterLayoutPolicy.active(c)&&
            T1ClusterLayoutPolicy.MODE_SPLIT.equals(T1ClusterLayoutPolicy.mode(c))&&
            "com.google.android.apps.maps".equals(T1ClusterLayoutPolicy.secondPackage(c))&&
            T1ClusterWazeRouter.selected(c)!=null;
    }
}
