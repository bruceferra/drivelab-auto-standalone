package ar.com.rango5gps.drivelabauto.boot;

/**
 * LAB58 purely visual, app-agnostic rule for TEMPORARY fourth-panel parking.
 * The exact LAB57 Surface transfer/awaitFrame journal is not modified.
 * Android should still render the parked VD into its own TextureView,
 * but the pre-existing fullscreen stream remains visually in front.
 */
final class NavFourSlotParkingVisibilityPolicy {
    enum Front { A, B }
    private NavFourSlotParkingVisibilityPolicy(){}

    static Front frontWhenParking(boolean aOccupied,boolean bOccupied,
                                  NavFourSlotPolicy.Slot vacantDestination){
        if(vacantDestination==NavFourSlotPolicy.Slot.TABLERO_B&&
           aOccupied&&!bOccupied)return Front.A;
        if(vacantDestination==NavFourSlotPolicy.Slot.TABLERO_A&&
           !aOccupied&&bOccupied)return Front.B;
        throw new SecurityException("Estacionamiento solo en mitad del clúster vacía con app opuesta verificada");
    }
}
