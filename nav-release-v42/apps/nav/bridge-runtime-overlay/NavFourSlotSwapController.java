package ar.com.rango5gps.drivelabauto.boot;

import java.util.HashSet;
import java.util.Set;

/**
 * LAB51 SOURCE-ONLY transaction coordinator; NO Android APK or OTA wiring.
 *
 * Actual View/VirtualDisplay backend must implement Driver on a background
 * executor, retaining each existing app Task and VirtualDisplay. Moving an
 * app changes ONLY the Surface bound to its already-running VirtualDisplay.
 *
 * No task launch/move/stop, OEM Binder or steering-wheel interception.
 * A swap between occupied slots consists of two non-atomic Surface changes;
 * an error triggers restoration of BOTH original bindings.
 */
final class NavFourSlotSwapController {
    interface Driver {
        /** Independent ADB/live inventory reads for an EXACT NAV task. */
        boolean unchanged(NavFourSlotPolicy.Stream expected);
        /** Check destination host is a NAV-owned surface (not Jetour OEM). */
        boolean ownsSlot(NavFourSlotPolicy.Slot slot,long ownerSession);
        /** Equivalent to existing owned VirtualDisplay.setSurface(...). */
        void bind(NavFourSlotPolicy.Stream stream,
                  NavFourSlotPolicy.Slot target) throws Exception;
        /** Wait a bounded interval for a new frame from EXACT stream. */
        boolean awaitFrame(NavFourSlotPolicy.Stream stream,
                           NavFourSlotPolicy.Slot target) throws Exception;
    }

    static final class Outcome {
        final NavFourSlotPolicy.State state;
        final boolean accepted,rollbackVerified,requiresRecovery;
        final String reason;
        private Outcome(NavFourSlotPolicy.State current,
                        boolean ok,boolean rolledBack,
                        boolean recover,String details){
            state=current;accepted=ok;rollbackVerified=rolledBack;
            requiresRecovery=recover;reason=details;
        }
    }
    private NavFourSlotSwapController(){}
    private static String shortCause(Exception error){
        String message=error.getMessage();
        if(message==null||message.isEmpty())
            return error.getClass().getSimpleName();
        // Surface and SecurityException messages are useful to isolate
        // driver/host failures, without dumping long OEM diagnostic logs.
        return error.getClass().getSimpleName()+": "+
            message.substring(0,Math.min(180,message.length()));
    }

    private static boolean verifyAll(NavFourSlotPolicy.State state,Driver d){
        Set<Integer> verified=new HashSet<>();
        for(NavFourSlotPolicy.Stream stream:state.streams.values()){
            if(!verified.add(stream.displayId)||
               !d.unchanged(stream)||!d.unchanged(stream))
                return false;
        }
        return true;
    }

    static Outcome transact(NavFourSlotPolicy.State current,
                            NavFourSlotPolicy.Slot from,
                            NavFourSlotPolicy.Slot to,
                            long token,long revision,
                            Driver d){
        if(d==null)throw new SecurityException("Falta driver NAV aislado");
        NavFourSlotPolicy.Swap plan=
            NavFourSlotPolicy.swap(current,from,to,token,revision);
        if(from==to)return new Outcome(
            current,true,true,false,"Sin movimiento");
        if(!d.ownsSlot(from,token)||!d.ownsSlot(to,token)||
           !verifyAll(current,d))
            return new Outcome(current,false,true,false,
                "Preflight NAV rechazado; no se movió ningún display");

        // Journal only completed Surface transfers. Android surfaces are
        // NOT atomically swappable; a spare empty NAV slot lets us transfer
        // each VD directly without ever calling setSurface(null).
        java.util.ArrayList<NavFourSlotPolicy.Bind> undo=
            new java.util.ArrayList<>();
        java.util.EnumMap<NavFourSlotPolicy.Slot,String> locations=
            new java.util.EnumMap<>(current.bindings);
        boolean uncertainBind=false;
        try{
            if(!d.ownsSlot(from,token)||!d.ownsSlot(to,token)||
               !verifyAll(current,d))
                return new Outcome(current,false,true,false,
                    "Sesión/tareas cambiaron antes del intercambio");
            for(NavFourSlotPolicy.Bind next:plan.apply){
                NavFourSlotPolicy.Stream stream=
                    current.streams.get(next.stream);
                NavFourSlotPolicy.Slot old=null;
                for(NavFourSlotPolicy.Slot slot:NavFourSlotPolicy.Slot.values())
                    if(next.stream.equals(locations.get(slot))){
                        old=slot;break;
                    }
                if(stream==null||old==null||old==next.slot||
                   locations.get(next.slot)!=null||
                   !d.ownsSlot(old,token)||
                   !d.ownsSlot(next.slot,token))
                    throw new SecurityException(
                        "Transferencia NAV sin espacio propio libre");
                // bind() must report success only AFTER setSurface returned.
                // If Android throws during a setSurface call we cannot
                // safely prove whether it changed the underlying binding.
                try{
                    d.bind(stream,next.slot);
                }catch(Exception mutation){
                    uncertainBind=true;
                    throw mutation;
                }
                locations.put(old,null);
                locations.put(next.slot,next.stream);
                undo.add(new NavFourSlotPolicy.Bind(next.stream,old));
            }
            // The temporary parking surface is NOT a final destination.
            // Wait for frames only at the exact final slots.
            java.util.HashSet<String> checked=new java.util.HashSet<>();
            for(NavFourSlotPolicy.Bind step:plan.apply){
                if(!checked.add(step.stream))continue;
                NavFourSlotPolicy.Stream stream=
                    current.streams.get(step.stream);
                NavFourSlotPolicy.Slot finalSlot=null;
                for(NavFourSlotPolicy.Slot slot:NavFourSlotPolicy.Slot.values())
                    if(step.stream.equals(plan.after.idAt(slot))){
                        finalSlot=slot;break;
                    }
                if(finalSlot==null||!d.awaitFrame(stream,finalSlot))
                    throw new IllegalStateException(
                        "No apareció cuadro nuevo en panel final "+finalSlot);
            }
            if(!verifyAll(current,d))
                throw new SecurityException(
                    "Una tarea cambió durante el intercambio");
            return new Outcome(plan.after,true,true,false,
                "Intercambio de superficies NAV verificado");
        }catch(Exception failure){
            boolean rollback=!uncertainBind;
            String diagnostic=shortCause(failure);
            // Reverse precisely the transfers that completed, without
            // evicting a live VD or using a guessed Android display.
            for(int i=undo.size()-1;i>=0;i--){
                NavFourSlotPolicy.Bind previous=undo.get(i);
                try{
                    NavFourSlotPolicy.Stream stream=
                        current.streams.get(previous.stream);
                    if(stream==null||
                       !d.ownsSlot(previous.slot,token))
                        throw new SecurityException(
                            "Panel original perdió propietario");
                    d.bind(stream,previous.slot);
                }catch(Exception reverted){
                    rollback=false;
                    diagnostic+=";rollback="+shortCause(reverted);
                }
            }
            if(rollback){
                java.util.HashSet<String> restored=new java.util.HashSet<>();
                for(NavFourSlotPolicy.Bind previous:undo){
                    if(!restored.add(previous.stream))continue;
                    NavFourSlotPolicy.Stream stream=
                        current.streams.get(previous.stream);
                    NavFourSlotPolicy.Slot oldSlot=null;
                    for(NavFourSlotPolicy.Slot slot:NavFourSlotPolicy.Slot.values())
                        if(previous.stream.equals(current.idAt(slot))){
                            oldSlot=slot;break;
                        }
                    try{
                        if(oldSlot==null||!d.awaitFrame(stream,oldSlot)){
                            rollback=false;
                            diagnostic+=";sin cuadro tras restaurar";
                        }
                    }catch(Exception verifyFailure){
                        rollback=false;
                        diagnostic+=";sin verificación="+
                            shortCause(verifyFailure);
                    }
                }
            }
            if(rollback&&!verifyAll(current,d)){
                rollback=false;
                diagnostic+=";identidad de tarea no confirmada";
            }
            return new Outcome(current,false,rollback,!rollback,
                rollback?"Intercambio revertido: "+diagnostic:
                    "BLOQUEADO: estado de superficies incierto, "+
                    "requiere recuperar sesión NAV; "+diagnostic);
        }
    }
}
