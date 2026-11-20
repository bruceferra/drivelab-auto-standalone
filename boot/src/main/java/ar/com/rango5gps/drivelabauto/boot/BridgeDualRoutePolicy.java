package ar.com.rango5gps.drivelabauto.boot;

/** A Sender destination target and the map's current requested display
 * are INDEPENDENT. One route to BOTH always yields two separate deep links.
 * Does not use the foreground app or toggle steering-wheel permissions.
 */
final class BridgeDualRoutePolicy {
    static final String BOTH="both";
    static final String WAZE="com.waze";
    static final String MAPS="com.google.android.apps.maps";
    static final class Target {
        final String pkg;
        final int displayId;
        Target(String p,int d){pkg=p;displayId=d;}
    }
    private BridgeDualRoutePolicy(){}
    static boolean selectedForTablero(String pkg,String tabSelection){
        return WAZE.equals(pkg)||MAPS.equals(pkg)?
            pkg.equals(tabSelection)||BOTH.equals(tabSelection):false;
    }
    static Target[] plan(String requested,String tabSelection,
                         boolean tableroEnabled){
        if(!"waze".equals(requested)&&!"maps".equals(requested)&&
           !BOTH.equals(requested))
            return new Target[0];
        if(!WAZE.equals(tabSelection)&&!MAPS.equals(tabSelection)&&
           !BOTH.equals(tabSelection))tabSelection=MAPS;
        String[] apps=BOTH.equals(requested)?
            new String[]{WAZE,MAPS}:
            new String[]{"maps".equals(requested)?MAPS:WAZE};
        Target[] result=new Target[apps.length];
        for(int i=0;i<apps.length;i++){
            boolean cluster=tableroEnabled&&
                selectedForTablero(apps[i],tabSelection);
            result[i]=new Target(apps[i],cluster?1:0);
        }
        return result;
    }
}
