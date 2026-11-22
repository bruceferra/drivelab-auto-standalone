package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NAV52 SOURCE-ONLY wiring between compact four-slot buttons and real
 * Android VirtualDisplay.setSurface transactions. No commands reach OEM.
 *
 * LAB51 production remains unchanged. Only one transaction at a time;
 * central touch is explicitly disabled until the new Surface is verified
 * or a verified rollback restores the original mapping.
 */
final class NavFourSlotSessionCoordinator
        implements NavFourSlotControls.SwapRequest {
    interface TouchGate {
        /** Called on UI thread before scheduling any Android rebind. */
        boolean pauseAllCentralInput();
        /** Called after verified commit or verified rollback. */
        void resumeCentralInput();
    }

    private final Activity owner;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"NAV-FourSlot-OwnSurfaces");
        t.setDaemon(true);
        return t;
    });
    private final NavFourSlotAndroidSurfaceDriver driver;
    private final NavFourSlotAndroidSurfaceDriver.Ownership proof;
    private final Map<String,String> displayNames;
    private final TouchGate touches;
    private final NavFourSlotControls controls;
    private NavFourSlotPolicy.State current;
    private boolean busy,stopped,recoveryRequired;

    NavFourSlotSessionCoordinator(Activity activity,
                                 NavFourSlotPolicy.State initial,
                                 Map<String,String> names,
                                 NavFourSlotAndroidSurfaceDriver.Ownership guard,
                                 NavFourSlotAndroidSurfaceDriver androidDriver,
                                 TouchGate inputGate,
                                 NavFourSlotControls ui){
        if(activity==null||initial==null||names==null||guard==null||
           androidDriver==null||inputGate==null||ui==null)
            throw new SecurityException("NAV sin componentes 4-paneles");
        if(Looper.myLooper()!=Looper.getMainLooper())
            throw new SecurityException("Inicializar en el hilo de NAV");
        NavFourSlotPolicy.validate(initial);
        owner=activity;current=initial;driver=androidDriver;
        proof=guard;displayNames=names;touches=inputGate;controls=ui;
        NavFourSlotLiveRouting.publish(activity,initial,names,guard);
        controls.publish(initial);
    }
    @Override public void request(NavFourSlotPolicy.Slot from,
                                  NavFourSlotPolicy.Slot to,
                                  long session,long revision){
        if(Looper.myLooper()!=Looper.getMainLooper())
            throw new SecurityException("Botón NAV fuera del hilo UI");
        if(stopped||busy||recoveryRequired||
           current.session!=session||current.revision!=revision||
           !proof.liveSession(session)){
            controls.setBusy(false);
            return;
        }
        if(!touches.pauseAllCentralInput()){
            controls.setBusy(false);
            return;
        }
        busy=true;
        controls.setBusy(true);
        final NavFourSlotPolicy.State before=current;
        worker.execute(()->{
            NavFourSlotSwapController.Outcome result;
            try{
                result=NavFourSlotSwapController.transact(
                    before,from,to,session,revision,driver);
            }catch(Exception denied){
                // Domain policy rejected the command, without touching VDs.
                main.post(()->finishDenied(
                    "Movimiento NAV rechazado: "+
                    denied.getClass().getSimpleName()));
                return;
            }
            main.post(()->finish(result,before));
        });
    }
    private void finishDenied(String why){
        if(stopped||owner.isFinishing()||owner.isDestroyed())return;
        busy=false;
        controls.setBusy(false);
        touches.resumeCentralInput();
        new AlertDialog.Builder(owner)
            .setTitle("Intercambio no realizado")
            .setMessage(why)
            .setPositiveButton("ENTENDIDO",null).show();
    }
    private void finish(NavFourSlotSwapController.Outcome result,
                        NavFourSlotPolicy.State before){
        if(stopped||owner.isFinishing()||owner.isDestroyed())return;
        busy=false;
        if(!proof.liveSession(before.session)){
            requireRecovery("La sesión de NAV cambió durante el traslado");
            return;
        }
        if(result.accepted){
            try{
                NavFourSlotLiveRouting.publish(
                    owner,result.state,displayNames,proof);
                current=result.state;
                controls.setBusy(false);
                controls.publish(current);
                touches.resumeCentralInput();
            }catch(Exception notVerified){
                requireRecovery(
                    "El intercambio terminó pero no se pudo confirmar "+
                    "el mapa de tareas y superficies: "+
                    notVerified.getClass().getSimpleName());
            }
        }else if(result.rollbackVerified&&!result.requiresRecovery){
            controls.setBusy(false);
            controls.publish(before);
            touches.resumeCentralInput();
            new AlertDialog.Builder(owner)
                .setTitle("Se mantuvo la distribución anterior")
                .setMessage(result.reason)
                .setPositiveButton("ENTENDIDO",null).show();
        }else{
            requireRecovery(result.reason);
        }
    }
    private void requireRecovery(String message){
        recoveryRequired=true;
        controls.blockUntilRecovery(message);
        // Fail-closed: NO touch or further swap until owner session reset.
        // No force-stop/ADB cleanup and no ability to affect OEM displays.
    }
    boolean isSwapRunning(){return busy;}
    /** Only call when the owning NAV Activity has ended its session. */
    void closeAfterOwnerRelease(){
        if(Looper.myLooper()!=Looper.getMainLooper())
            throw new SecurityException("Cerrar paneles desde hilo UI");
        stopped=true;recoveryRequired=true;
        worker.shutdownNow();
        NavFourSlotLiveRouting.clear(current.session);
        driver.releaseAfterOwnerClose();
    }
}
