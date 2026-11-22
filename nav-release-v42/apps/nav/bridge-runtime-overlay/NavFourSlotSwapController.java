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

        try{
            // Revalidate immediately before mutation. Surfaces and tasks
            // must still belong to the SAME exact LAB50 owner session.
            if(!d.ownsSlot(from,token)||!d.ownsSlot(to,token)||
               !verifyAll(current,d))
                return new Outcome(current,false,true,false,
                    "Sesión/tareas cambiaron antes del intercambio");
            for(NavFourSlotPolicy.Bind next:plan.apply){
                NavFourSlotPolicy.Stream stream=
                    current.streams.get(next.stream);
                if(stream==null||!d.ownsSlot(next.slot,token))
                    throw new SecurityException(
                        "Destino o flujo NAV desapareció");
                d.bind(stream,next.slot);
            }
            for(NavFourSlotPolicy.Bind next:plan.apply){
                NavFourSlotPolicy.Stream stream=
                    current.streams.get(next.stream);
                if(!d.awaitFrame(stream,next.slot))
                    throw new IllegalStateException(
                        "No apareció un cuadro nuevo en el destino");
            }
            // Do not publish the swapped UI state based on a write ACK.
            // Double-confirm every app TASK remains the SAME.
            if(!verifyAll(current,d))
                throw new SecurityException(
                    "Una tarea cambió durante el intercambio");
            return new Outcome(plan.after,true,true,false,
                "Intercambio de superficies NAV verificado");
        }catch(Exception failure){
            boolean rollback=true;
            String diagnostic=failure.getClass().getSimpleName();
            // Restore ALL old slots (not just ones we believe changed).
            // A backend may mutate a surface before reporting an error.
            for(NavFourSlotPolicy.Bind before:plan.rollback){
                try{
                    NavFourSlotPolicy.Stream stream=
                        current.streams.get(before.stream);
                    if(stream==null||
                       !d.ownsSlot(before.slot,token))
                        throw new SecurityException(
                            "Panel original perdió propietario");
                    d.bind(stream,before.slot);
                }catch(Exception reverted){
                    rollback=false;
                    diagnostic+=";rollback="+
                        reverted.getClass().getSimpleName();
                }
            }
            if(rollback){
                for(NavFourSlotPolicy.Bind before:plan.rollback){
                    try{
                        NavFourSlotPolicy.Stream stream=
                            current.streams.get(before.stream);
                        if(!d.awaitFrame(stream,before.slot)){
                            rollback=false;
                            diagnostic+=";sin cuadro tras restaurar";
                        }
                    }catch(Exception verifyFailure){
                        rollback=false;
                        diagnostic+=";sin verificación de restauración";
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
