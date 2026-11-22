package ar.com.rango5gps.drivelabauto.boot;

/** Regression tests for exact read-only Android NAV stack verification. */
public final class NavFourSlotTaskInventoryTest {
    private static void check(boolean ok,String label){
        if(!ok)throw new AssertionError(label);
    }
    private static NavFourSlotPolicy.Stream map(){
        return new NavFourSlotPolicy.Stream("maps",
            "com.google.android.apps.maps",6,226,66,1250,980,777L);
    }
    private static String sample(){
        return "Stack id=1 bounds=[0,0][1920,1080] displayId=0\n"+
            " taskId=200: com.jetour.home/.Main\n"+
            "Stack id=66 bounds=[0,0][1250,980] displayId=6\n"+
            " taskId=226: com.google.android.apps.maps/.MapsActivity\n"+
            "Stack id=67 bounds=[0,0][620,980] displayId=7\n"+
            " taskId=227: com.spotify.music/com.spotify.MainActivity\n"+
            "RootTask id=74 displayId=8\n"+
            " taskId=3319: com.waze/com.waze.MainActivity\n";
    }
    public static void main(String[] args){
        String full=sample();
        check(NavFourSlotTaskInventory.exact(full,map()),
            "exact central Maps task is present");
        check(!NavFourSlotTaskInventory.exact(
            full.replace("displayId=6","displayId=0"),map()),
            "reject moved physical task");
        check(!NavFourSlotTaskInventory.exact(
            full.replace("Stack id=66","Stack id=99"),map()),
            "reject changed stack");
        check(!NavFourSlotTaskInventory.exact(
            full.replace("taskId=226","taskId=229"),map()),
            "reject changed task ID");
        check(!NavFourSlotTaskInventory.exact(
            full.replace("com.google.android.apps.maps/.MapsActivity",
                         "com.spotify.music/.Main"),map()),
            "reject foreign app in NAV slot");
        check(!NavFourSlotTaskInventory.exact(
            full.replace(
                "Stack id=67",
                " taskId=456: com.google.android.apps.maps/.Other\n"+
                "Stack id=67"),
            map()),"reject extra NAV task");
        check(!NavFourSlotTaskInventory.exact(
            full+"RootTask id=75 displayId=6\n"+
            " taskId=457: com.google.android.apps.maps/.Extra\n",
            map()),"reject second stack inside NAV display");
        check(!NavFourSlotTaskInventory.exact(
            full+"RootTask id=99 displayId=10\n"+
            " taskId=226: com.google.android.apps.maps/.MapsActivity\n",
            map()),"reject duplicated task IDs");
        check(!NavFourSlotTaskInventory.exact(
            "Stack id=66 displayId=6\n",map()),
            "reject incomplete inventory");
        check(!NavFourSlotTaskInventory.exact(null,map()),
            "reject null inventory");
        check(NavFourSlotTaskInventory.exact(full,
            new NavFourSlotPolicy.Stream("waze","com.waze",
                8,3319,74,1920,720,777L)),
            "Cluster-owned Waze task is grounded");
        System.out.println("PASS NavFourSlotTaskInventoryTest");
    }
}
