package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;

/** Physical A/B panel targeting. Wheel key codes and per-app profiles never
 * change when swapping apps, changing widths, or applying a split layout. */
final class T1WheelTarget {
    private static final String KEY="active_panel";
    private static final String GENERIC="wheel_generic_enabled_";
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";
    private T1WheelTarget(){}
    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(T1WazeWheelService.PREFS,Context.MODE_PRIVATE);
    }
    static boolean panelB(Context c){
        return "B".equals(prefs(c).getString(KEY,"A"));
    }
    static void select(Context c,boolean b){
        prefs(c).edit().putString(KEY,b?"B":"A").apply();
    }
    static String name(Context c){return panelB(c)?"PANEL B":"PANEL A";}
    private static boolean legacyInvert(Context c){
        return T1ClusterLayoutPolicy.MODE_SPLIT.equals(
            T1ClusterLayoutPolicy.mode(c))&&
            T1ClusterLayoutPolicy.inverted(c);
    }
    static String packageName(Context c){
        boolean right=panelB(c);
        if(T1ClusterLayoutPolicy.MODE_FULL.equals(
            T1ClusterLayoutPolicy.mode(c)))
            return T1ClusterLayoutPolicy.singlePackage(c);
        return T1ClusterLayoutPolicy.appInPanel(c,
            legacyInvert(c)?!right:right);
    }
    static String application(Context c){
        if(T1ClusterLayoutPolicy.MODE_FULL.equals(
            T1ClusterLayoutPolicy.mode(c)))
            return T1ClusterLayoutPolicy.singleLabel(c);
        return T1ClusterLayoutPolicy.labelInPanel(c,
            legacyInvert(c)?!panelB(c):panelB(c));
    }
    static boolean selectedPanelEnabled(Context c){
        if(T1ClusterLayoutPolicy.MODE_FULL.equals(
            T1ClusterLayoutPolicy.mode(c)))return true;
        return panelB(c)?T1ClusterLayoutPolicy.panelBOn(c):
            T1ClusterLayoutPolicy.panelAOn(c);
    }
    static boolean mapsAllowed(Context c){
        return MAPS.equals(packageName(c))&&mapsPresent(c);
    }
    static boolean mapsPresent(Context c){
        return selectedPanelEnabled(c)&&
            T1ClusterWazeRouter.enabled(c)&&
            MAPS.equals(packageName(c))&&
            T1ClusterWazeRouter.selected(c)!=null;
    }
    static boolean wazePresent(Context c){
        return selectedPanelEnabled(c)&&
            T1ClusterWazeRouter.enabled(c)&&
            WAZE.equals(packageName(c))&&
            T1ClusterWazeRouter.selected(c)!=null;
    }
    static boolean genericCandidate(Context c){
        String pkg=packageName(c);
        return selectedPanelEnabled(c)&&
            !WAZE.equals(pkg)&&!MAPS.equals(pkg)&&
            T1ClusterWazeRouter.isSelectablePackage(c,pkg);
    }
    static boolean genericEnabled(Context c){
        return genericCandidate(c)&&
            prefs(c).getBoolean(GENERIC+packageName(c),false);
    }
    static void setGenericEnabled(Context c,boolean enabled){
        if(!genericCandidate(c))return;
        prefs(c).edit().putBoolean(GENERIC+packageName(c),enabled).apply();
    }
}
