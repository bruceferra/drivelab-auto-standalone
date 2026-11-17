package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.concurrent.atomic.AtomicBoolean;

/** Recover an ALREADY enabled accessibility service when Jetour's Android 11
 * leaves the OEM microphone Binder alive but the accessibility binding dead.
 *
 * Uses only the owner's previously authorized LOCAL ADB (if available).
 * Never enables a service for the first time, modifies unrelated entries,
 * touches the cluster, injects any key, or writes vehicle/OEM properties.
 * A persisted restore journal repairs interrupted two-step rebinds.
 * Back off after failed attempts; Android remains the final binder owner.
 */
final class T1WheelAccessibilityRecovery {
    static final String STATUS="accessibility_recovery_status";
    private static final String PENDING="accessibility_restore_pending";
    private static final String EXPECTED="accessibility_restore_without";
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private static final long RETRY_MS=90000L;
    private static volatile long lastAttempt=0L;
    private T1WheelAccessibilityRecovery(){}

    static void request(Context context){
        Context app=context.getApplicationContext();
        SharedPreferences prefs=app.getSharedPreferences(
            T1WazeWheelService.PREFS,Context.MODE_PRIVATE);
        if(T1WazeWheelService.isServiceConnected()||
           !prefs.getBoolean(T1WazeWheelService.ENABLED,false)||
           !prefs.getBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,false)||
           !T1OemMicrophoneService.prefs(app).getBoolean(
                T1OemMicrophoneService.ENABLED,false))return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastAttempt<RETRY_MS&&now>=lastAttempt)return;
        if(!RUNNING.compareAndSet(false,true))return;
        lastAttempt=now;
        new Thread(()->{
            try{
                attempt(app,prefs);
            }finally{
                RUNNING.set(false);
            }
        },"T1-Accessibility-Authorized-Rebind").start();
    }

    static String describe(Context c){
        if(T1WazeWheelService.isServiceConnected())
            return "ACCESIBILIDAD: SERVICIO CONECTADO";
        SharedPreferences p=c.getSharedPreferences(
            T1WazeWheelService.PREFS,Context.MODE_PRIVATE);
        return p.getString(STATUS,
            "ACCESIBILIDAD: configurada, sin instancia conectada");
    }

    private static String current(Context app){
        return Settings.Secure.getString(app.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    }

    private static void note(SharedPreferences p,String text){
        p.edit().putString(STATUS,text).apply();
    }

    private static boolean restoreIfPending(Context app,SharedPreferences p)
            throws Exception {
        String original=p.getString(PENDING,"");
        if(original.isEmpty())return true;
        String without=p.getString(EXPECTED,"");
        if(!without.equals(T1WheelAccessListPolicy.withoutOurService(original))){
            note(p,"RECUPERACIÓN SUSPENDIDA: registro previo inválido");
            return false;
        }
        String actual=current(app);
        if(original.equals(actual)){
            p.edit().remove(PENDING).remove(EXPECTED).commit();
            return true;
        }
        if(!without.equals(actual)){
            // Someone changed Android settings since we started; preserve it.
            p.edit().remove(PENDING).remove(EXPECTED).commit();
            note(p,"ACCESIBILIDAD CAMBIÓ EN ANDROID; no se sobrescribió");
            return false;
        }
        // This is the exact temporary value WE wrote on the previous run.
        // Restore it before any new attempt; never write another snapshot.
        T1LocalAdbClient.shell(app,
            T1WheelAccessListPolicy.command(original));
        if(!original.equals(current(app)))
            throw new IllegalStateException("Android no confirmó restauración");
        p.edit().remove(PENDING).remove(EXPECTED).commit();
        note(p,"ACCESIBILIDAD: listado anterior restaurado");
        return true;
    }

    private static void attempt(Context app,SharedPreferences p){
        try{
            if(!restoreIfPending(app,p))return;
            if(T1WazeWheelService.isServiceConnected())return;
            String old=current(app);
            String without=T1WheelAccessListPolicy.withoutOurService(old);
            if(without==null){
                note(p,"ACCESIBILIDAD: no figura habilitada por Android "+
                    "o la lista no es segura; no se cambió nada");
                return;
            }
            int enabled=Settings.Secure.getInt(app.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,0);
            if(enabled!=1){
                note(p,"ACCESIBILIDAD: Android la tiene desactivada; "+
                    "solo el usuario puede habilitarla");
                return;
            }
            // Fail closed: don't overwrite a value read before another
            // component or the user changed the secure accessibility list.
            if(!old.equals(current(app)))return;
            if(!p.edit().putString(PENDING,old).putString(EXPECTED,without)
                .commit()){
                note(p,"ACCESIBILIDAD: no se pudo guardar restauración");
                return;
            }
            note(p,"ACCESIBILIDAD: recuperando enlace autorizado…");
            try{
                // Unbind ONLY DriveLab while preserving OEM/T1Launcher.
                T1LocalAdbClient.shell(app,
                    T1WheelAccessListPolicy.command(without));
                if(!without.equals(current(app)))
                    throw new IllegalStateException(
                        "Android rechazó desconectar solo DriveLab");
                Thread.sleep(950L);
            }finally{
                // ADB may have failed after removing our entry.
                // Best-effort repair is mandatory before returning.
                restoreIfPending(app,p);
            }
            Thread.sleep(600L);
            if(T1WazeWheelService.isServiceConnected())
                note(p,"ACCESIBILIDAD: servicio recuperado automáticamente");
            else note(p,"ACCESIBILIDAD: Android conserva el servicio "+
                "desconectado; listado restaurado, sin cambiar otros servicios");
        }catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            note(p,"ACCESIBILIDAD: reintento interrumpido");
        }catch(Exception ex){
            note(p,"ACCESIBILIDAD: recuperación no confirmada · "+
                ex.getClass().getSimpleName()+
                (ex.getMessage()==null?"":" · "+ex.getMessage()));
        }
    }
}
