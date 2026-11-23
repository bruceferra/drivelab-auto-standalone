package ar.com.rango5gps.drivelabauto.boot;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class NavFourSlotPolicyTest {
    private static void ok(boolean valid,String msg){
        if(!valid)throw new AssertionError(msg);
    }
    private static void blocked(Runnable call,String msg){
        try{call.run();throw new AssertionError("UNSAFE ACCEPTED: "+msg);}
        catch(SecurityException expected){}
    }
    private static NavFourSlotPolicy.State state(boolean full){
        Map<String,NavFourSlotPolicy.Stream> map=new HashMap<>();
        map.put("maps",new NavFourSlotPolicy.Stream(
            "maps","com.google.android.apps.maps",6,226,66,1280,720,777));
        map.put("spotify",new NavFourSlotPolicy.Stream(
            "spotify","com.spotify.music",7,227,67,480,800,777));
        map.put("waze",new NavFourSlotPolicy.Stream(
            "waze","com.waze",8,3319,74,1920,720,777));
        if(full)map.put("maps2",new NavFourSlotPolicy.Stream(
            "maps2","com.google.android.apps.maps",9,3300,75,960,720,777));
        Map<NavFourSlotPolicy.Slot,String> binding=
            new EnumMap<>(NavFourSlotPolicy.Slot.class);
        binding.put(NavFourSlotPolicy.Slot.CENTRAL_A,"maps");
        binding.put(NavFourSlotPolicy.Slot.CENTRAL_B,"spotify");
        binding.put(NavFourSlotPolicy.Slot.TABLERO_A,"waze");
        if(full)binding.put(NavFourSlotPolicy.Slot.TABLERO_B,"maps2");
        return new NavFourSlotPolicy.State(777L,0L,map,binding);
    }
    public static void main(String[] args){
        NavFourSlotPolicy.State normal=state(false);
        ok(NavFourSlotPolicy.clusterLayout(normal)==
            NavFourSlotPolicy.ClusterLayout.SINGLE_FULL,
            "3 apps => Waze full-screen tablero");
        NavFourSlotPolicy.Swap move=NavFourSlotPolicy.swap(normal,
            NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.TABLERO_B,777,0);
        ok(move.apply.length==1&&
            "maps".equals(move.after.idAt(NavFourSlotPolicy.Slot.TABLERO_B)),
            "Central A -> free Tablero B");
        ok(NavFourSlotPolicy.clusterLayout(move.after)==
            NavFourSlotPolicy.ClusterLayout.SPLIT_AB,
            "2 apps visible in Tablero AB");
        NavFourSlotPolicy.Swap reverse=NavFourSlotPolicy.swap(move.after,
            NavFourSlotPolicy.Slot.TABLERO_B,
            NavFourSlotPolicy.Slot.CENTRAL_A,777,1);
        ok("maps".equals(reverse.after.idAt(NavFourSlotPolicy.Slot.CENTRAL_A)),
            "return without task relaunch");
        NavFourSlotPolicy.State all=state(true);
        NavFourSlotPolicy.Swap cross=NavFourSlotPolicy.swap(normal,
            NavFourSlotPolicy.Slot.CENTRAL_B,
            NavFourSlotPolicy.Slot.TABLERO_A,777,0);
        ok(cross.apply.length==3&&cross.rollback.length==3,
            "occupied swap parks the existing app in free fourth slot");
        ok(cross.apply[0].slot==NavFourSlotPolicy.Slot.TABLERO_B&&
            cross.apply[1].slot==NavFourSlotPolicy.Slot.TABLERO_A&&
            cross.apply[2].slot==NavFourSlotPolicy.Slot.CENTRAL_B,
            "parking then direct occupied A/B interchange");
        ok("spotify".equals(cross.after.idAt(NavFourSlotPolicy.Slot.TABLERO_A))&&
            "waze".equals(cross.after.idAt(NavFourSlotPolicy.Slot.CENTRAL_B)),
            "bidirectional cross");
        blocked(()->NavFourSlotPolicy.swap(all,
            NavFourSlotPolicy.Slot.CENTRAL_B,
            NavFourSlotPolicy.Slot.TABLERO_A,777,0),
            "do not swap four live VDs with no empty parking Surface");
        ok(NavFourSlotPolicy.touchDisplay(cross.after,
            NavFourSlotPolicy.Slot.CENTRAL_B)==8,
            "touch follows actual VD after swap");
        blocked(()->NavFourSlotPolicy.touchDisplay(all,
            NavFourSlotPolicy.Slot.TABLERO_A),
            "physical dashboard is not touch");
        blocked(()->NavFourSlotPolicy.swap(all,
            NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.TABLERO_B,777,1),
            "stale revision");
        blocked(()->NavFourSlotPolicy.swap(all,
            NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.TABLERO_B,123,0),
            "foreign session");
        ok(NavFourSlotPolicy.senderTarget(all,
            "com.google.android.apps.maps",
            NavFourSlotPolicy.Slot.TABLERO_B).taskId==3300,
            "explicit route to right dashboard map instance");
        blocked(()->NavFourSlotPolicy.senderTarget(all,
            "com.google.android.apps.maps",null),
            "ambiguous duplicate map");
        ok(NavFourSlotPolicy.senderTarget(normal,"com.waze",null)
            .taskId==3319,"default Waze target preserved");
        blocked(()->new NavFourSlotPolicy.Stream("sys",
            "com.jetour.home",1,32,34,1280,720,777),
            "physical display");
        blocked(()->new NavFourSlotPolicy.Stream("sys",
            "com.jetour.home",8,32,34,1280,720,777),
            "Jetour OEM package");
        Map<String,NavFourSlotPolicy.Stream> duplicated=new HashMap<>(
            all.streams);
        duplicated.put("duplicate",new NavFourSlotPolicy.Stream(
            "duplicate","com.google.android.apps.maps",6,4000,90,
            1280,720,777));
        blocked(()->new NavFourSlotPolicy.State(777,0,duplicated,
            all.bindings),"duplicate display id");
        ok(normal.streams.get("maps").taskId==
            reverse.after.streams.get("maps").taskId,
            "stable existing Maps task id");
        ok(normal.streams.get("spotify").taskId==
            reverse.after.streams.get("spotify").taskId,
            "stable Spotify playback task id");
        System.out.println("PASS NavFourSlotPolicyTest (source-only)");
    }
}
