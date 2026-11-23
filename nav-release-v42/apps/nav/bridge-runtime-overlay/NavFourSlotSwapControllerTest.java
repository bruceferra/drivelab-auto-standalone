package ar.com.rango5gps.drivelabauto.boot;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class NavFourSlotSwapControllerTest {
    private static final class Fake
            implements NavFourSlotSwapController.Driver {
        final Map<String,NavFourSlotPolicy.Slot> bindings=new HashMap<>();
        boolean badSlot,badTask,badFrame,badRollback;
        int bindCalls,ownershipChecks,taskReads,evictions;
        int failureOnBind=-1;
        int waitCalls,failWaitOnceAt=-1;
        Fake(NavFourSlotPolicy.State state){
            for(Map.Entry<NavFourSlotPolicy.Slot,String> e:
                    state.bindings.entrySet())
                if(e.getValue()!=null)bindings.put(e.getValue(),e.getKey());
        }
        @Override public boolean unchanged(NavFourSlotPolicy.Stream stream){
            taskReads++;
            return !badTask&&stream.session==777&&
                   stream.taskId>10&&stream.displayId>1;
        }
        @Override public boolean ownsSlot(NavFourSlotPolicy.Slot slot,long token){
            ownershipChecks++;
            return !(badSlot&&slot==NavFourSlotPolicy.Slot.TABLERO_B)&&
                token==777&&slot!=null;
        }
        @Override public void bind(NavFourSlotPolicy.Stream stream,
                                   NavFourSlotPolicy.Slot slot)throws Exception{
            bindCalls++;
            for(Map.Entry<String,NavFourSlotPolicy.Slot> entry:
                    bindings.entrySet())
                if(!entry.getKey().equals(stream.id)&&entry.getValue()==slot){
                    evictions++;
                    throw new SecurityException("Attempted occupant eviction");
                }
            bindings.put(stream.id,slot);
            if(bindCalls==failureOnBind)
                throw new Exception("setSurface changed then threw");
            if(badRollback&&bindCalls>=3)
                throw new Exception("restore Surface rejected");
        }
        @Override public boolean awaitFrame(
            NavFourSlotPolicy.Stream stream,NavFourSlotPolicy.Slot slot){
            waitCalls++;
            if(waitCalls==failWaitOnceAt)return false;
            return !badFrame&&slot==bindings.get(stream.id);
        }
        String at(String id){return String.valueOf(bindings.get(id));}
    }
    private static NavFourSlotPolicy.State state(boolean fourth){
        Map<String,NavFourSlotPolicy.Stream> streams=new HashMap<>();
        streams.put("maps",new NavFourSlotPolicy.Stream(
            "maps","com.google.android.apps.maps",6,226,66,1250,980,777));
        streams.put("spotify",new NavFourSlotPolicy.Stream(
            "spotify","com.spotify.music",7,227,67,620,980,777));
        streams.put("waze",new NavFourSlotPolicy.Stream(
            "waze","com.waze",8,3319,74,1920,720,777));
        if(fourth)streams.put("maps2",new NavFourSlotPolicy.Stream(
            "maps2","com.google.android.apps.maps",9,4000,75,960,720,777));
        Map<NavFourSlotPolicy.Slot,String> positions=
            new EnumMap<>(NavFourSlotPolicy.Slot.class);
        positions.put(NavFourSlotPolicy.Slot.CENTRAL_A,"maps");
        positions.put(NavFourSlotPolicy.Slot.CENTRAL_B,"spotify");
        positions.put(NavFourSlotPolicy.Slot.TABLERO_A,"waze");
        if(fourth)positions.put(NavFourSlotPolicy.Slot.TABLERO_B,"maps2");
        return new NavFourSlotPolicy.State(777,0,streams,positions);
    }
    private static void yes(boolean assertion,String why){
        if(!assertion)throw new AssertionError(why);
    }
    private static void denied(Runnable op,String why){
        try{op.run();throw new AssertionError("UNSAFE ACCEPTED: "+why);}
        catch(SecurityException expected){}
    }
    public static void main(String[] args){
        NavFourSlotPolicy.State three=state(false);
        NavFourSlotPolicy.State four=state(true);
        Fake empty=new Fake(three);
        NavFourSlotSwapController.Outcome moved=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_B,777,0,empty);
        yes(moved.accepted&&moved.state.revision==1&&
            empty.at("maps").equals("TABLERO_B")&&empty.bindCalls==1,
            "free-slot move directly rebinds without pausing VD");
        yes(empty.evictions==0&&empty.taskReads>=6,
            "never evict other app and verify original tasks");
        yes(NavFourSlotPolicy.clusterLayout(moved.state)==
            NavFourSlotPolicy.ClusterLayout.SPLIT_AB,
            "second TABLERO panel filled after verified commit");

        Fake swap=new Fake(three);
        NavFourSlotSwapController.Outcome result=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_A,777,0,swap);
        yes(result.accepted&&swap.bindCalls==3&&swap.evictions==0,
            "occupied central/cluster swap: park, move, restore");
        yes(swap.at("maps").equals("TABLERO_A")&&
            swap.at("waze").equals("CENTRAL_A")&&
            swap.at("spotify").equals("CENTRAL_B"),
            "same three existing tasks across the four physical slots");

        Fake dashboard=new Fake(three);
        NavFourSlotSwapController.Outcome ab=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.TABLERO_A,
                NavFourSlotPolicy.Slot.TABLERO_B,777,0,dashboard);
        yes(ab.accepted&&dashboard.bindCalls==1&&
            dashboard.at("waze").equals("TABLERO_B"),
            "Tablero A to B is a direct one-way unpaused move");
        denied(()->NavFourSlotSwapController.transact(four,
            NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.TABLERO_A,777,0,new Fake(four)),
            "full four-slot occupied swap must not pause a VD");

        // Regression from physical LAB56: a Spotify/Maps swap
        // briefly PARKS one app on vacant TABLERO_B. Do not move a
        // second app before verifying a fresh parking-slot frame.
        Fake parkingNoFrame=new Fake(three);
        parkingNoFrame.failWaitOnceAt=1;
        NavFourSlotSwapController.Outcome parkedRejected=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.CENTRAL_B,777,0,parkingNoFrame);
        yes(!parkedRejected.accepted&&parkedRejected.rollbackVerified&&
            !parkedRejected.requiresRecovery&&
            parkingNoFrame.bindCalls==2&&
            parkingNoFrame.at("maps").equals("CENTRAL_A")&&
            parkingNoFrame.at("spotify").equals("CENTRAL_B"),
            "missing PARKING frame blocks subsequent swap; restores A/B");

        Fake secondHopNoFrame=new Fake(three);
        secondHopNoFrame.failWaitOnceAt=2;
        NavFourSlotSwapController.Outcome secondRejected=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.CENTRAL_B,777,0,secondHopNoFrame);
        yes(!secondRejected.accepted&&secondRejected.rollbackVerified&&
            !secondRejected.requiresRecovery&&
            secondHopNoFrame.bindCalls==4&&
            secondHopNoFrame.at("maps").equals("CENTRAL_A")&&
            secondHopNoFrame.at("spotify").equals("CENTRAL_B"),
            "unrendered second hop does not bind third and safely restores A/B");

        Fake uncertain=new Fake(three);
        uncertain.failureOnBind=2;
        NavFourSlotSwapController.Outcome interrupted=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_A,777,0,uncertain);
        yes(!interrupted.accepted&&interrupted.requiresRecovery&&
            !interrupted.rollbackVerified&&interrupted.state==three,
            "Android throw after mutation must fail closed");
        yes(uncertain.evictions==0,"partial failure never evicts occupant");

        Fake noFrames=new Fake(three);
        noFrames.badFrame=true;
        NavFourSlotSwapController.Outcome timedOut=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_B,
                NavFourSlotPolicy.Slot.TABLERO_B,777,0,noFrames);
        yes(!timedOut.accepted&&timedOut.requiresRecovery&&
            timedOut.state==three,"no frames cannot confirm success or rollback");

        Fake deniedSlot=new Fake(three);
        deniedSlot.badSlot=true;
        NavFourSlotSwapController.Outcome rejected=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_B,777,0,deniedSlot);
        yes(!rejected.accepted&&deniedSlot.bindCalls==0&&
            rejected.rollbackVerified,"foreign panel blocked before mutation");
        Fake changed=new Fake(three);
        changed.badTask=true;
        NavFourSlotSwapController.Outcome deniedTask=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_A,777,0,changed);
        yes(!deniedTask.accepted&&changed.bindCalls==0,
            "changed tasks cannot be moved");

        Fake noOp=new Fake(three);
        NavFourSlotSwapController.Outcome noop=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.CENTRAL_A,777,0,noOp);
        yes(noop.accepted&&noop.state==three&&noOp.bindCalls==0,
            "same-slot action is harmless");
        denied(()->NavFourSlotSwapController.transact(three,
            NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.TABLERO_A,777,1,new Fake(three)),
            "stale revision forbidden");
        System.out.println("PASS NAV57: fresh frame on every parking/transfer hop, "+
            "occupied A/B swap without pausing, fail closed on uncertainty");
    }
}
