package ar.com.rango5gps.drivelabauto.boot;

import java.util.List;

/** Pure-JVM contract: read-only T1Launcher/Waze inventory on Android 11. */
public final class NavClusterTaskInventoryTest {
    private static final String MAPS=
        "com.google.android.apps.maps/com.google.android.maps.MapsActivity";
    private static final String WAZE="com.waze/.MainActivity";
    private static String task(int stack,int display,int id,String pkg){
        return "Stack id="+stack+" bounds=null displayId="+display+
            " userId=0\n  taskId="+id+": "+pkg+" bounds=null\n";
    }
    private static void ok(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        // Virtual display IDs 13,14 are owned by NAV; Waze lives on
        // a DIFFERENT display ID, as T1Launcher might own T1Mapa.
        String report=task(120,0,512,MAPS)+
            task(140,1,610,"com.jetour.hmi/.Dashboard")+
            task(200,13,711,MAPS)+
            task(201,14,712,"com.spotify.music/.MainActivity")+
            task(305,19,910,WAZE);
        NavClusterTaskInventory.Snapshot a=
            NavClusterTaskInventory.parse(report,13,14);
        NavClusterTaskInventory.Snapshot b=
            NavClusterTaskInventory.parse(report,13,14);
        ok(a.valid&&a.same(b),"Double-read stable task inventory");
        List<NavClusterTaskInventory.Task> ext=
            a.external(NavClusterTaskInventory.WAZE);
        ok(ext.size()==1&&ext.get(0).displayId==19&&
           ext.get(0).taskId==910&&ext.get(0).dedicatedStack,
           "One Waze outside both NAV virtual displays");
        ok(a.external(NavClusterTaskInventory.MAPS).isEmpty(),
           "NAV Maps and physical display 0 do NOT qualify as external");
        ok(a.count(NavClusterTaskInventory.MAPS)==2,
           "Counts distinguish physical and NAV Maps windows");
        ok(!a.same(NavClusterTaskInventory.parse(
            report.replace("taskId=910","taskId=911"),13,14)),
           "Task ID change forbids use of stale diagnostics");
        ok(NavClusterTaskInventory.parse(
            task(301,19,910,WAZE)+
            "  taskId=920: com.jetour.hmi/.App bounds=null\n",
            13,14).external(NavClusterTaskInventory.WAZE).isEmpty(),
           "Mixed Waze+OEM stack cannot be treated as isolated");
        ok(NavClusterTaskInventory.parse(
            task(301,1,910,WAZE),13,14)
            .external(NavClusterTaskInventory.WAZE).isEmpty(),
           "Physical Cluster Display 1 NEVER becomes a NAV destination");
        ok(NavClusterTaskInventory.parse(
            task(301,13,910,WAZE),13,14)
            .external(NavClusterTaskInventory.WAZE).isEmpty(),
           "Existing NAV virtual Maps display is not an external target");
        ok(!NavClusterTaskInventory.parse(
            task(301,19,910,WAZE)+task(302,20,910,WAZE),
            13,14).valid,"Duplicate task IDs fail closed");
        ok(!NavClusterTaskInventory.parse(
            "Stack id=301 displayId=abc\n  taskId=910: "+WAZE+"\n",
            13,14).valid,"Malformed display headers fail closed");
        ok(!NavClusterTaskInventory.parse(report,13,13).valid,
           "NAV map/media displays must be distinct");
        ok(!NavClusterTaskInventory.parse(null,13,14).valid,
           "Missing ADB output is not evidence");
        String twice=report+task(306,20,915,WAZE);
        ok(NavClusterTaskInventory.parse(twice,13,14)
            .external(NavClusterTaskInventory.WAZE).size()==2,
           "Multiple external Waze tasks are reported as ambiguous");
        System.out.println("PASS NAV Cluster double-read map/Waze inventory: "+
            "external virtual only, no display 0/1, mixed OEM and duplicate fail-closed");
    }
}
