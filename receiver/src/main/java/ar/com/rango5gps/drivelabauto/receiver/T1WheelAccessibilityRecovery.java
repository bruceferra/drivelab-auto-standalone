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
        if(lastAttempt>0L&&now>=lastAttempt&&
           now-lastAttempt<RETRY_MS)return;
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

    /** Android is the authority: a green DriveLab preference is not a
     * running AccessibilityService and must not be presented as connected.
     * The OS accessibility list is READ ONLY here, not guessed or altered. */
    static String permissionState(Context context){
        if(T1WazeWheelService.isServiceConnected())
            return "CONECTADA · Android entregó el servicio";
        try{
            int enabled=Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,0);
            String current=current(context);
            boolean approved=false;
            if(current!=null){
                for(String component:current.split(":")){
                    if(T1WheelAccessListPolicy.target(component)){
                        approved=true;break;
                    }
                }
            }
            if(!approved||enabled!=1)
                return "DESHABILITADA EN ANDROID · requiere autorización "+
                    "manual en Ajustes de accesibilidad";
            return "HABILITADA PERO DESCONECTADA · "+
                "Android no entregó el servicio a DriveLab";
        }catch(Exception error){
            return "ESTADO ANDROID NO VERIFICABLE · "+
                error.getClass().getSimpleName();
        }
    }

    /** One-tap manual action. Never grants Accessibility. When the owner
     * already enabled this exact service AND ADB was authorized, a safe
     * journalled rebind can be attempted without Termux, using loopback only.
     * Manual requests bypass the 90s automatic backoff, not the safety gates. */
    static String requestManual(Context context){
        Context app=context.getApplicationContext();
        SharedPreferences p=app.getSharedPreferences(
            T1WazeWheelService.PREFS,Context.MODE_PRIVATE);
        if(T1WazeWheelService.isServiceConnected()){
            T1WazeWheelService.requestScan(app);
            return "Accesibilidad ya conectada. Actualizando controles Waze/Maps.";
        }
        String pending=p.getString(PENDING,"");
        String state=permissionState(app);
        if(pending.isEmpty()&&
            !state.startsWith("HABILITADA PERO DESCONECTADA")){
            note(p,state);
            return state;
        }
        if(!pending.isEmpty()){
            // Repair the exact prior ADB snapshot even if the temporary
            // removal left our component absent from Android's list.
            if(!RUNNING.compareAndSet(false,true))
                return "Recuperación de accesibilidad en curso";
            lastAttempt=SystemClock.elapsedRealtime();
            note(p,"RECONECTAR · restaurando lista anterior de Android…");
            new Thread(()->{
                try{attempt(app,p);}
                finally{RUNNING.set(false);}
            },"T1-Wheel-Repair-Pending-Manual").start();
            return "Reparando un reinicio interrumpido; mirá el estado abajo.";
        }
        if(T1WheelAccessListPolicy.withoutOurService(current(app))==null){
            note(p,"El servicio está habilitado, pero Android no permite "+
                "un rebind seguro con la lista actual. Usá Ajustes.");
            return "Servicio habilitado; la lista de accesibilidad no "+
                "permite reengancharlo automáticamente. Abrí Ajustes.";
        }
        if(!RUNNING.compareAndSet(false,true))
            return "Recuperación de accesibilidad ya en curso";
        lastAttempt=SystemClock.elapsedRealtime();
        note(p,"RECONECTAR · intentando ADB local autorizado "+
            "sin modificar otros servicios…");
        new Thread(()->{
            try{attempt(app,p);}
            finally{RUNNING.set(false);}
        },"T1-Wheel-Manual-Authorized-Rebind").start();
        return "Reconexión solicitada: sólo el enlace previamente autorizado; "+
            "no hace falta Termux ni internet.";
    }

    static String describe(Context c){
        String status=permissionState(c);
        if(T1WazeWheelService.isServiceConnected())return status;
        SharedPreferences p=c.getSharedPreferences(
            T1WazeWheelService.PREFS,Context.MODE_PRIVATE);
        return status+"\\n"+p.getString(STATUS,
            "Recuperación no intentada");
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
