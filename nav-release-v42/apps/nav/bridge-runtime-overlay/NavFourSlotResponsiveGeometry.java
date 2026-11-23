package ar.com.rango5gps.drivelabauto.boot;

/**
 * LAB58: app-agnostic geometry for the existing four-slot compositor.
 *
 * Every selected app has an OWNED VirtualDisplay. Its requested pixel
 * geometry comes from the ACTUAL host AFTER Android layout, regardless
 * of the app's name, original slot, or original source aspect ratio.
 * Apps that do not honor display-resize may letterbox internally; the
 * caller must preserve aspect ratio until the native viewport confirms.
 * NEVER create/release a VirtualDisplay, move tasks, or alter OEM views.
 */
final class NavFourSlotResponsiveGeometry {
    static final int DENSITY_DPI=213;
    static final class Target {
        final int width,height;
        Target(int w,int h){
            if(w<240||h<240||w>3000||h>2200)
                throw new SecurityException("Panel NAV fuera de rango");
            width=w;height=h;
        }
        boolean same(Target other){
            return other!=null&&width==other.width&&height==other.height;
        }
        @Override public String toString(){
            return width+"x"+height;
        }
    }

    private NavFourSlotResponsiveGeometry(){}

    static Target forSlot(NavFourSlotPolicy.State state,
                          NavFourSlotPolicy.Slot slot,
                          int clusterWidth,int clusterHeight,
                          int centralWidth,int centralHeight){
        NavFourSlotPolicy.validate(state);
        if(slot==null||state.at(slot)==null)
            throw new SecurityException("No redimensionar panel vacío");
        if(slot.touch){
            // All registered apps, including future selectable ones,
            // share the same policy. Fixed Spotify/Maps/Waze exceptions
            // made successive A/B moves alternately crop or letterbox.
            return new Target(centralWidth,centralHeight);
        }
        if(clusterWidth<1200||clusterHeight<480||
           clusterWidth>3000||clusterHeight>2200)
            throw new SecurityException("Clúster NAV físico inesperado");
        boolean a=state.at(NavFourSlotPolicy.Slot.TABLERO_A)!=null;
        boolean b=state.at(NavFourSlotPolicy.Slot.TABLERO_B)!=null;
        if(a&&b){
            int half=clusterWidth/2;
            return new Target(slot==NavFourSlotPolicy.Slot.TABLERO_A?
                half:clusterWidth-half,clusterHeight);
        }
        // If the other half is empty, display the only app at full
        // 1920x720 instead of reserving a blank 50% of the cluster.
        return new Target(clusterWidth,clusterHeight);
    }
}
