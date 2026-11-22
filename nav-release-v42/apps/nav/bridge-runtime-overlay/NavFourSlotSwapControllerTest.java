package ar.com.rango5gps.drivelabauto.boot;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class NavFourSlotSwapControllerTest {
    private static final class Fake
            implements NavFourSlotSwapController.Driver {
        final Map<String,NavFourSlotPolicy.Slot> bindings=new HashMap<>();
        boolean badSlot,badTask,badFrame,badRollback;
        int bindCalls,ownershipChecks,taskReads;
        int failureOnBind=-1;
        Fake(NavFourSlotPolicy.State state){
            for(Map.Entry<NavFourSlotPolicy.Slot,String> e:
                    state.bindings.entrySet())
                if(e.getValue()!=null)
                    bindings.put(e.getValue(),e.getKey());
        }
        @Override public boolean unchanged(
                NavFourSlotPolicy.Stream stream){
            taskReads++;
            return !badTask&&stream.session==777&&
                   stream.taskId>10&&stream.displayId>1;
        }
        @Override public boolean ownsSlot(
                NavFourSlotPolicy.Slot slot,long token){
            ownershipChecks++;
            if(badSlot&&slot==NavFourSlotPolicy.Slot.TABLERO_B)
                return false;
            return token==777&&slot!=null;
        }
        @Override public void bind(NavFourSlotPolicy.Stream stream,
                                   NavFourSlotPolicy.Slot slot)throws Exception{
            bindCalls++;
            bindings.put(stream.id,slot);
            if(bindCalls==failureOnBind)throw new Exception(
                "Android setSurface falló después de modificar su enlace");
            if(badRollback&&bindCalls>=3)
                throw new Exception("Android rechazó restaurar Surface");
        }
        @Override public boolean awaitFrame(
            NavFourSlotPolicy.Stream stream,NavFourSlotPolicy.Slot slot){
            return !badFrame&&slot==bindings.get(stream.id);
        }
        String at(String id){return String.valueOf(bindings.get(id));}
    }
    private static NavFourSlotPolicy.State state(boolean fourth){
        Map<String,NavFourSlotPolicy.Stream> streams=new HashMap<>();
        streams.put("maps",new NavFourSlotPolicy.Stream(
            "maps","com.google.android.apps.maps",6,226,66,
            1250,980,777));
        streams.put("spotify",new NavFourSlotPolicy.Stream(
            "spotify","com.spotify.music",7,227,67,620,980,777));
        streams.put("waze",new NavFourSlotPolicy.Stream(
            "waze","com.waze",8,3319,74,1920,720,777));
        if(fourth)streams.put("maps2",new NavFourSlotPolicy.Stream(
            "maps2","com.google.android.apps.maps",9,4000,75,
            960,720,777));
        Map<NavFourSlotPolicy.Slot,String> positions=
            new EnumMap<>(NavFourSlotPolicy.Slot.class);
        positions.put(NavFourSlotPolicy.Slot.CENTRAL_A,"maps");
        positions.put(NavFourSlotPolicy.Slot.CENTRAL_B,"spotify");
        positions.put(NavFourSlotPolicy.Slot.TABLERO_A,"waze");
        if(fourth)positions.put(NavFourSlotPolicy.Slot.TABLERO_B,"maps2");
        return new NavFourSlotPolicy.State(777,0,streams,positions);
    }
    private static void yes(boolean assertion,String msg){
        if(!assertion)throw new AssertionError(msg);
    }
    private static void denied(Runnable op,String msg){
        try{op.run();throw new AssertionError("UNSAFE ACCEPTED: "+msg);}
        catch(SecurityException expected){}
    }
    public static void main(String[] args){
        NavFourSlotPolicy.State three=state(false);
        Fake empty=new Fake(three);
        NavFourSlotSwapController.Outcome toFree=
            NavFourSlotSwapController.transact(three,
                NavFourSlotPolicy.Slot.CENTRAL_B,
                NavFourSlotPolicy.Slot.TABLERO_B,777,0,empty);
        yes(toFree.accepted&&toFree.state.revision==1,
            "move Spotify into free cluster B");
        yes(empty.at("spotify").equals("TABLERO_B")&&
            toFree.state.idAt(NavFourSlotPolicy.Slot.CENTRAL_B)==null,
            "source central pane becomes free");
        yes(empty.taskReads>=6,"verified all tasks twice before moving");
        yes(NavFourSlotPolicy.clusterLayout(toFree.state)==
            NavFourSlotPolicy.ClusterLayout.SPLIT_AB,
            "cluster enters dual A/B when two apps visible");

        NavFourSlotPolicy.State four=state(true);
        Fake both=new Fake(four);
        NavFourSlotSwapController.Outcome crossed=
            NavFourSlotSwapController.transact(four,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_B,777,0,both);
        yes(crossed.accepted&&both.bindCalls==2,
            "swap existing Maps and second Maps by two Surface binds");
        yes(both.at("maps").equals("TABLERO_B")&&
            both.at("maps2").equals("CENTRAL_A"),
            "occupied slot has exact two-way exchange");
        yes(NavFourSlotPolicy.senderTarget(crossed.state,
            "com.google.android.apps.maps",
            NavFourSlotPolicy.Slot.TABLERO_B).taskId==226,
            "Sender can address original Maps task now visible on cluster");
        denied(()->NavFourSlotPolicy.senderTarget(crossed.state,
            "com.google.android.apps.maps",null),
            "never guess among duplicate Maps tasks");

        Fake failedSecond=new Fake(four);
        failedSecond.failureOnBind=2;
        NavFourSlotSwapController.Outcome undone=
            NavFourSlotSwapController.transact(four,
                NavFourSlotPolicy.Slot.CENTRAL_B,
                NavFourSlotPolicy.Slot.TABLERO_A,777,0,
                failedSecond);
        yes(!undone.accepted&&undone.rollbackVerified&&
            !undone.requiresRecovery&&undone.state==four,
            "partial Android error rolled back to original revision");
        yes(failedSecond.at("spotify").equals("CENTRAL_B")&&
            failedSecond.at("waze").equals("TABLERO_A"),
            "restore BOTH exact surfaces");
        yes(failedSecond.bindCalls==4,
            "one failed Android rebind plus two explicit restorations");

        Fake frameLost=new Fake(four);
        frameLost.badFrame=true;
        NavFourSlotSwapController.Outcome noFrames=
            NavFourSlotSwapController.transact(four,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_A,777,0,frameLost);
        yes(!noFrames.accepted&&noFrames.requiresRecovery&&
            !noFrames.rollbackVerified,
            "without frames, UI must NOT claim restoration");
        yes(noFrames.state==four,"not publish unverified after state");

        Fake rollbackFailure=new Fake(four);
        rollbackFailure.failureOnBind=2;
        rollbackFailure.badRollback=true;
        NavFourSlotSwapController.Outcome uncertain=
            NavFourSlotSwapController.transact(four,
                NavFourSlotPolicy.Slot.CENTRAL_B,
                NavFourSlotPolicy.Slot.TABLERO_A,777,0,
                rollbackFailure);
        yes(!uncertain.accepted&&uncertain.requiresRecovery,
            "failed rollback forces session recovery");

        Fake ownership=new Fake(four);
        ownership.badSlot=true;
        NavFourSlotSwapController.Outcome denied=
            NavFourSlotSwapController.transact(four,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_B,777,0,ownership);
        yes(!denied.accepted&&denied.rollbackVerified&&
            ownership.bindCalls==0,
            "never bind foreign/unavailable cluster slot");

        Fake staleTask=new Fake(four);
        staleTask.badTask=true;
        NavFourSlotSwapController.Outcome deniedTask=
            NavFourSlotSwapController.transact(four,
                NavFourSlotPolicy.Slot.CENTRAL_A,
                NavFourSlotPolicy.Slot.TABLERO_A,777,0,staleTask);
        yes(!deniedTask.accepted&&staleTask.bindCalls==0,
            "never mutate changed/outside NAV tasks");

        Fake noOp=new Fake(four);
        NavFourSlotSwapController.Outcome same=
            NavFourSlotSwapController.transact(four,
                NavFourSlotPolicy.Slot.CENTRAL_B,
                NavFourSlotPolicy.Slot.CENTRAL_B,777,0,noOp);
        yes(same.accepted&&same.state==four&&noOp.bindCalls==0,
            "same-slot action does nothing");

        denied(()->NavFourSlotSwapController.transact(four,
            NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.TABLERO_A,777,1,new Fake(four)),
            "revision must be exact");
        denied(()->NavFourSlotSwapController.transact(four,
            NavFourSlotPolicy.Slot.CENTRAL_A,
            NavFourSlotPolicy.Slot.TABLERO_A,1,0,new Fake(four)),
            "session ownership exact");
        System.out.println("PASS NavFourSlotSwapControllerTest: "+
            "atomic policy commit, partial rollback and fail-closed recovery");
    }
}
