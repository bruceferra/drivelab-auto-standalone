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
        ok(central.width==1250&&central.height==820,
            "central app native to its real host, not Waze 1920x720");
        try{
            target(singleA,NavFourSlotPolicy.Slot.TABLERO_B);
            throw new AssertionError("empty panel should be rejected");
        }catch(SecurityException expected){}
        ok(singleA.streams.get("waze").displayId==
           both.streams.get("waze").displayId,
           "geometry never changes original app display ID");
        System.out.println("PASS NAV56: 1920x720 single, 960x720 dual, "+
            "native central host; task/VD identities unmodified");
    }
}
