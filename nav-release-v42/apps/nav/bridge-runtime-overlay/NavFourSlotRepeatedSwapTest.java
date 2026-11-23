package ar.com.rango5gps.drivelabauto.boot;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** LAB56 regression from the real car:
 * Maps+Waze in cluster A/B, Spotify at Central B. Move Spotify B->A->B;
 * then exchange Central B with Waze, and return Waze A->B.
 * Every label must match the ONLY original stream bound to that Surface.
 */
public final class NavFourSlotRepeatedSwapTest {
    private static final long TOKEN=771;
    private static final class Fake implements NavFourSlotSwapController.Driver {
        final Map<String,NavFourSlotPolicy.Slot> surfaces=new HashMap<>();
        final Map<String,Integer> displayIds=new HashMap<>();
        long frameSerial=1;
        boolean staleFrame=false,duplicateFeed=false;
        Fake(NavFourSlotPolicy.State s){
            for(NavFourSlotPolicy.Slot slot:NavFourSlotPolicy.Slot.values()){
                NavFourSlotPolicy.Stream stream=s.at(slot);
                if(stream!=null){
                    surfaces.put(stream.id,slot);
                    displayIds.put(stream.id,stream.displayId);
                }
            }
        }
        @Override public boolean unchanged(NavFourSlotPolicy.Stream stream){
            return stream.session==TOKEN&&
                stream.displayId==displayIds.get(stream.id)&&
                uniqueSurfaces();
        }
        private boolean uniqueSurfaces(){
            Set<NavFourSlotPolicy.Slot> uniq=new HashSet<>(surfaces.values());
            return uniq.size()==surfaces.size()&&!duplicateFeed;
        }
        @Override public boolean ownsSlot(
                NavFourSlotPolicy.Slot slot,long token){
            return slot!=null&&token==TOKEN;
        }
        @Override public void bind(NavFourSlotPolicy.Stream stream,
                                   NavFourSlotPolicy.Slot slot){
            if(surfaces.containsValue(slot))
                throw new SecurityException("occupied slot cannot have two producers");
            surfaces.put(stream.id,slot);frameSerial++;
        }
        @Override public boolean awaitFrame(NavFourSlotPolicy.Stream stream,
                                             NavFourSlotPolicy.Slot target){
            return !staleFrame&&surfaces.get(stream.id)==target&&
                uniqueSurfaces()&&frameSerial>1;
        }
        void verify(NavFourSlotPolicy.State s){
            if(!uniqueSurfaces())throw new AssertionError("DUPLICATED VISUAL SURFACE");
            for(NavFourSlotPolicy.Slot slot:NavFourSlotPolicy.Slot.values()){
                NavFourSlotPolicy.Stream v=s.at(slot);
                for(Map.Entry<String,NavFourSlotPolicy.Slot> x:surfaces.entrySet())
                    if(x.getValue()==slot&&!x.getKey().equals(s.idAt(slot)))
                        throw new AssertionError("wrong app rendered for "+slot);
                if(v!=null&&surfaces.get(v.id)!=slot)
                    throw new AssertionError("label != actual surface for "+slot);
            }
        }
    }
    private static void yes(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    private static NavFourSlotPolicy.State original(){
        Map<String,NavFourSlotPolicy.Stream> v=new HashMap<>();
        v.put("maps",new NavFourSlotPolicy.Stream("maps",
            "com.google.android.apps.maps",6,220,60,1250,820,TOKEN));
        v.put("spotify",new NavFourSlotPolicy.Stream("spotify",
            "com.spotify.music",7,221,61,460,820,TOKEN));
        v.put("waze",new NavFourSlotPolicy.Stream("waze",
            "com.waze",8,222,62,1920,720,TOKEN));
        EnumMap<NavFourSlotPolicy.Slot,String> bind=
            new EnumMap<>(NavFourSlotPolicy.Slot.class);
        bind.put(NavFourSlotPolicy.Slot.CENTRAL_B,"spotify");
        bind.put(NavFourSlotPolicy.Slot.TABLERO_A,"waze");
        bind.put(NavFourSlotPolicy.Slot.TABLERO_B,"maps");
        return new NavFourSlotPolicy.State(TOKEN,0,v,bind);
    }
    private static NavFourSlotPolicy.State step(
            NavFourSlotPolicy.State s,Fake d,
            NavFourSlotPolicy.Slot src,NavFourSlotPolicy.Slot dest){
        NavFourSlotSwapController.Outcome next=
            NavFourSlotSwapController.transact(
                s,src,dest,TOKEN,s.revision,d);
        yes(next.accepted&&!next.requiresRecovery,"swap must confirm");
        d.verify(next.state);
        return next.state;
    }
    public static void main(String[] args){
        NavFourSlotPolicy.State s=original();
        Fake d=new Fake(s);d.verify(s);
        for(int i=0;i<5;i++){
            s=step(s,d,NavFourSlotPolicy.Slot.CENTRAL_B,
                NavFourSlotPolicy.Slot.CENTRAL_A);
            s=step(s,d,NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.CENTRAL_B);
        }
        yes("spotify".equals(s.idAt(NavFourSlotPolicy.Slot.CENTRAL_B)),
            "Spotify survives five back-and-forth cycles");
        s=step(s,d,NavFourSlotPolicy.Slot.CENTRAL_B,
            NavFourSlotPolicy.Slot.TABLERO_A);
        yes("waze".equals(s.idAt(NavFourSlotPolicy.Slot.CENTRAL_B)),
            "Waze is at Central B after swapping with Spotify");
        s=step(s,d,NavFourSlotPolicy.Slot.CENTRAL_B,
            NavFourSlotPolicy.Slot.CENTRAL_A);
        s=step(s,d,NavFourSlotPolicy.Slot.TABLERO_B,
            NavFourSlotPolicy.Slot.CENTRAL_B);
        yes("maps".equals(s.idAt(NavFourSlotPolicy.Slot.CENTRAL_B))&&
            "waze".equals(s.idAt(NavFourSlotPolicy.Slot.CENTRAL_A)),
            "both maps in central, Spotify cluster full");
        Fake stale=new Fake(s);stale.staleFrame=true;
        NavFourSlotSwapController.Outcome rejected=
            NavFourSlotSwapController.transact(
                s,NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_B,
                TOKEN,s.revision,stale);
        yes(!rejected.accepted&&rejected.requiresRecovery,
            "stale frame may NEVER confirm success");
        System.out.println("PASS LAB56: 5x Spotify A/B return, Waze/Spotify "+
            "and Maps central swaps preserve labels, stale frames fail closed");
    }
}
