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
        // Any app bound into the live central A slot adapts to A's
        // MEASURED width, regardless of its original VD or package.
        NavFourSlotResponsiveGeometry.Target spotifyA=target(
            both,NavFourSlotPolicy.Slot.CENTRAL_A);
        ok(spotifyA.width==1250&&spotifyA.height==820,
            "Spotify uses actual wide Central A, not 620x980 original");
        NavFourSlotPolicy.State moved=NavFourSlotPolicy.swap(
            both,NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.CENTRAL_B,
            SESSION,both.revision).after;
        NavFourSlotResponsiveGeometry.Target spotifyB=
            NavFourSlotResponsiveGeometry.forSlot(moved,
                NavFourSlotPolicy.Slot.CENTRAL_B,
                1920,720,620,820);
        ok(spotifyB.width==620&&spotifyB.height==820,
            "same Spotify VD adapts back to measured narrow Central B");
        NavFourSlotPolicy.State mapsCenter=make("waze","spotify");
        NavFourSlotResponsiveGeometry.Target mapsA=target(
            mapsCenter,NavFourSlotPolicy.Slot.CENTRAL_A);
        ok(mapsA.width==1250&&mapsA.height==820,
            "Maps follows identical central geometry policy");
        NavFourSlotResponsiveGeometry.Target spotifyCluster=target(
            mapsCenter,NavFourSlotPolicy.Slot.TABLERO_B);
        ok(spotifyCluster.width==960&&spotifyCluster.height==720,
            "Spotify in cluster half uses full physical 720 height");

        // The policy must not care whether the stream ID/package is
        // Maps, Waze, Spotify or a future user-selected Android app.
        Map<String,NavFourSlotPolicy.Stream> arbitrary=new HashMap<>(
            singleA.streams);
        arbitrary.put("future",new NavFourSlotPolicy.Stream(
            "future","org.example.navigation",9,4015,88,
            540,960,SESSION));
        EnumMap<NavFourSlotPolicy.Slot,String> futureBindings=
            new EnumMap<>(singleA.bindings);
        futureBindings.put(NavFourSlotPolicy.Slot.TABLERO_B,"future");
        NavFourSlotPolicy.State future=new NavFourSlotPolicy.State(
            SESSION,0,arbitrary,futureBindings);
        NavFourSlotResponsiveGeometry.Target futureHalf=target(
            future,NavFourSlotPolicy.Slot.TABLERO_B);
        ok(futureHalf.width==960&&futureHalf.height==720,
            "arbitrary future app gets full cluster half with no package rule");
        NavMosaicGeometry.Fit oldFrame=NavMosaicGeometry.fit(
            540,960,960,720);
        ok(oldFrame.sx<1f&&oldFrame.sy==1f&&
            oldFrame.contains(480f,360f)&&
            !oldFrame.contains(0f,360f),
            "unresized old frame center-inside with no stretch/cropped touch");
        try{
            target(singleA,NavFourSlotPolicy.Slot.TABLERO_B);
            throw new AssertionError("empty panel should be rejected");
        }catch(SecurityException expected){}
        ok(singleA.streams.get("waze").displayId==
           both.streams.get("waze").displayId,
           "geometry never changes original app display ID");
        System.out.println("PASS NAV58: universal measured 4P geometry; "+
            "Spotify wide/narrow; Waze half/full; future app; no crop fallback");
    }
}
