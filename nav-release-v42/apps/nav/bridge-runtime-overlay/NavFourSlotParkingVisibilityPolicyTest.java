package ar.com.rango5gps.drivelabauto.boot;

/** Regression: Spotify displayed in CLUSTER B for one second while
 * Central A/B swap was parking it. It must remain behind existing
 * fullscreen A until transfer and frame verification complete.
 */
public final class NavFourSlotParkingVisibilityPolicyTest {
    private static void yes(boolean v,String message){
        if(!v)throw new AssertionError(message);
    }
    private static void denied(boolean a,boolean b,
                               NavFourSlotPolicy.Slot target){
        try{
            NavFourSlotParkingVisibilityPolicy.frontWhenParking(a,b,target);
            throw new AssertionError("Unsafe/unavailable temporary target accepted");
        }catch(SecurityException expected){}
    }
    public static void main(String[] args){
        yes(NavFourSlotParkingVisibilityPolicy.frontWhenParking(
                true,false,NavFourSlotPolicy.Slot.TABLERO_B)==
            NavFourSlotParkingVisibilityPolicy.Front.A,
            "Parking B stays behind existing full-screen A, NO FLASH");
        yes(NavFourSlotParkingVisibilityPolicy.frontWhenParking(
                false,true,NavFourSlotPolicy.Slot.TABLERO_A)==
            NavFourSlotParkingVisibilityPolicy.Front.B,
            "Parking A stays behind existing full-screen B, NO FLASH");
        denied(true,false,NavFourSlotPolicy.Slot.TABLERO_A);
        denied(false,true,NavFourSlotPolicy.Slot.TABLERO_B);
        denied(true,true,NavFourSlotPolicy.Slot.TABLERO_B);
        denied(false,false,NavFourSlotPolicy.Slot.TABLERO_B);
        denied(true,false,NavFourSlotPolicy.Slot.CENTRAL_B);
        System.out.println("PASS NAV58: transient 4P parking stays invisible behind verified fullscreen cluster app");
    }
}
