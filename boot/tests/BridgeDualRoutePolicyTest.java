package ar.com.rango5gps.drivelabauto.boot;

/** Both destinations must receive a SEPARATE deep link independent of
 * foreground and prior open/closed state, with owner-selected displays.
 */
public final class BridgeDualRoutePolicyTest {
    private static void check(boolean valid,String description){
        if(!valid)throw new AssertionError(description);
    }
    private static void expect(String destination,String tabSelection,
                               boolean enabled,int... displays){
        BridgeDualRoutePolicy.Target[] routes=BridgeDualRoutePolicy.plan(
            destination,tabSelection,enabled);
        check(routes.length==displays.length,
            destination+"/"+tabSelection+" route count");
        for(int i=0;i<displays.length;i++)
            check(routes[i].displayId==displays[i],
                destination+"/"+tabSelection+" display "+i);
        if("both".equals(destination)){
            check(BridgeDualRoutePolicy.WAZE.equals(routes[0].pkg),
                "BOTH always sends to Waze even if hidden/closed");
            check(BridgeDualRoutePolicy.MAPS.equals(routes[1].pkg),
                "BOTH always sends to Google Maps even if hidden/closed");
        }
    }
    public static void main(String[] args){
        // Both on central: exact same destination to BOTH apps on Display 0.
        expect("both",BridgeDualRoutePolicy.WAZE,false,0,0);
        expect("both",BridgeDualRoutePolicy.MAPS,false,0,0);
        expect("both",BridgeDualRoutePolicy.BOTH,false,0,0);
        // Split: Waze cluster / Maps central, or vice versa.
        expect("both",BridgeDualRoutePolicy.WAZE,true,1,0);
        expect("both",BridgeDualRoutePolicy.MAPS,true,0,1);
        // Both on cluster still means TWO destinations, not one.
        expect("both",BridgeDualRoutePolicy.BOTH,true,1,1);
        // Sender's Waze and Maps choices stay independent of display.
        expect("waze",BridgeDualRoutePolicy.BOTH,true,1);
        expect("maps",BridgeDualRoutePolicy.BOTH,true,1);
        expect("waze",BridgeDualRoutePolicy.MAPS,true,0);
        expect("maps",BridgeDualRoutePolicy.WAZE,true,0);
        expect("waze",BridgeDualRoutePolicy.WAZE,true,1);
        expect("maps",BridgeDualRoutePolicy.MAPS,true,1);
        check(BridgeDualRoutePolicy.plan("invalid",
            BridgeDualRoutePolicy.BOTH,true).length==0,
            "Do not assume an invalid Sender target");
        check(!BridgeDualRoutePolicy.selectedForTablero(
            "com.jetour.cluster",BridgeDualRoutePolicy.BOTH),
            "Never send OEM package to cluster");
        System.out.println("PASS Waze/Maps/BOTH selection independent of center/cluster/foreground");
    }
}
