package ar.com.rango5gps.drivelabauto.boot;

/**
 * LAB56: geometry ONLY for the already verified four-slot compositor.
 *
 * An existing VirtualDisplay needs the native size of its CURRENT
 * TextureView host to allow Waze/Maps/Spotify to reflow their UI.
 * The LAB55 task identity, transfer transaction and Surface ownership
 * remain unchanged; this policy must NEVER move a task or detach a VD.
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
            NavFourSlotPolicy.Stream stream=state.at(slot);
            // LAB57: Spotify starts in a narrow native central B VD.
            // Repeatedly resizing that same Spotify task to wide
            // Central A produces clipped/scrolled responsive UI on the
            // Jetour. Keep its stable ORIGINAL VD size in BOTH central
            // panels and letterbox the host with center-inside Matrix.
            // Original display/task IDs and their A/B swaps are untouched.
            if("spotify".equals(stream.id))
                return new Target(stream.width,stream.height);
            // Maps and Waze already passed real physical A/B swaps and
            // should remain responsive to the current host geometry.
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
        return new Target(clusterWidth,clusterHeight);
    }
}
