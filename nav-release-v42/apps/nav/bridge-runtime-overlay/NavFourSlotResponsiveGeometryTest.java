package ar.com.rango5gps.drivelabauto.boot;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class NavFourSlotResponsiveGeometryTest {
    private static final long SESSION=777;
    private static void ok(boolean v,String m){
        if(!v)throw new AssertionError(m);
    }
    private static NavFourSlotPolicy.State make(String a,String b){
        Map<String,NavFourSlotPolicy.Stream> streams=new HashMap<>();
        streams.put("maps",new NavFourSlotPolicy.Stream("maps",
            "com.google.android.apps.maps",6,226,66,1280,980,SESSION));
        streams.put("spotify",new NavFourSlotPolicy.Stream("spotify",
            "com.spotify.music",7,227,67,620,980,SESSION));
        streams.put("waze",new NavFourSlotPolicy.Stream("waze",
            "com.waze",8,3319,74,1920,720,SESSION));
        EnumMap<NavFourSlotPolicy.Slot,String> bindings=
            new EnumMap<>(NavFourSlotPolicy.Slot.class);
        if(a!=null)bindings.put(NavFourSlotPolicy.Slot.TABLERO_A,a);
        if(b!=null)bindings.put(NavFourSlotPolicy.Slot.TABLERO_B,b);
        for(String id:new String[]{"maps","spotify","waze"}){
            if(id.equals(a)||id.equals(b))continue;
            if(!bindings.containsKey(NavFourSlotPolicy.Slot.CENTRAL_A))
                bindings.put(NavFourSlotPolicy.Slot.CENTRAL_A,id);
            else bindings.put(NavFourSlotPolicy.Slot.CENTRAL_B,id);
        }
        return new NavFourSlotPolicy.State(SESSION,0,streams,bindings);
    }
    private static NavFourSlotResponsiveGeometry.Target target(
            NavFourSlotPolicy.State s,NavFourSlotPolicy.Slot slot){
        return NavFourSlotResponsiveGeometry.forSlot(
            s,slot,1920,720,1250,820);
    }
    public static void main(String[] args){
        NavFourSlotPolicy.State singleA=make("waze",null);
        NavFourSlotResponsiveGeometry.Target full=target(
            singleA,NavFourSlotPolicy.Slot.TABLERO_A);
        ok(full.width==1920&&full.height==720,
            "single live cluster slot remains 1920x720");
        NavFourSlotPolicy.State singleB=make(null,"waze");
        ok(target(singleB,NavFourSlotPolicy.Slot.TABLERO_B).same(full),
            "Waze A to empty B preserves full cluster width");
        NavFourSlotPolicy.State both=make("maps","waze");
        NavFourSlotResponsiveGeometry.Target left=target(
            both,NavFourSlotPolicy.Slot.TABLERO_A);
        NavFourSlotResponsiveGeometry.Target right=target(
            both,NavFourSlotPolicy.Slot.TABLERO_B);
        ok(left.width==960&&left.height==720&&right.same(left),
            "native 960x720 per half, no vertical letterboxing");
        NavFourSlotResponsiveGeometry.Target central=target(
            both,NavFourSlotPolicy.Slot.CENTRAL_A);
        ok(central.width==620&&central.height==980,
            "Spotify remains at original native VD in wide central A");
        NavMosaicGeometry.Fit fit=NavMosaicGeometry.fit(
            central.width,central.height,1250,820);
        ok(fit.sx>0f&&fit.sx<1f&&fit.sy==1f,
            "Spotify must be fitted center-inside, not horizontally cropped");
        ok(fit.contains(625f,410f)&&!fit.contains(0f,410f),
            "central taps outside Spotify letterbox must be rejected");
        NavFourSlotPolicy.State moved=NavFourSlotPolicy.swap(
            both,NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.CENTRAL_B,
            SESSION,both.revision).after;
        NavFourSlotResponsiveGeometry.Target spotifyB=target(
            moved,NavFourSlotPolicy.Slot.CENTRAL_B);
        ok(spotifyB.same(central),
            "Spotify keeps exactly same original VD across A/B and back");
        // Maps in any other central host should still reflow natively.
        NavFourSlotPolicy.State mapsCenter=make("waze","spotify");
        NavFourSlotResponsiveGeometry.Target mapsA=target(
            mapsCenter,NavFourSlotPolicy.Slot.CENTRAL_A);
        ok(mapsA.width==1250&&mapsA.height==820,
            "Maps remains responsive when placed in Central A");
        NavFourSlotResponsiveGeometry.Target spotifyCluster=target(
            mapsCenter,NavFourSlotPolicy.Slot.TABLERO_B);
        ok(spotifyCluster.width==960&&spotifyCluster.height==720,
            "Spotify can still use native resized cluster half when needed");
        try{
            target(singleA,NavFourSlotPolicy.Slot.TABLERO_B);
            throw new AssertionError("empty panel should be rejected");
        }catch(SecurityException expected){}
        ok(singleA.streams.get("waze").displayId==
           both.streams.get("waze").displayId,
           "geometry never changes original app display ID");
        System.out.println("PASS NAV57: Spotify native Central A/B with "+
            "center-inside safe touch, Maps responsive, cluster geometry preserved");
    }
}
