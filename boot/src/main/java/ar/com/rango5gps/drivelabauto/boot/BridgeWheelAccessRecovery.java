package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner-confirmed manual LAB accessibility authorization and safe rebind.
 * Jetour Android 11 Settings UI may not show/service-enable LAB normally.
 * An independently authorized LOCAL ADB host can set ONLY the exact Bridge
 * component while retaining all other OEM/T1Launcher service entries.
 *
 * Never enables steering-wheel OEM Binder automatically and NEVER operates
 * Android accessibility on ignition. New LAB key may need a one-time ADB
 * trust confirmation: ADB cannot silently bypass that security boundary.
 */
final class BridgeWheelAccessRecovery {
    static final String STATUS="bridge_lab_access_recovery_status";
    private static final String RESTORE="bridge_lab_access_restore_original";
    private static final String REMOVED="bridge_lab_access_restore_without";
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private BridgeWheelAccessRecovery(){}
    static SharedPreferences prefs(Context c){
        return BridgeWheelAccessibilityService.prefs(c);
    }
    static void note(Context c,String value){
        prefs(c).edit().putString(STATUS,value).apply();
    }
    private static String current(Context c){
        return Settings.Secure.getString(c.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    }
    private static String normalize(String s){
        return s==null||"null".equals(s)?"":s.trim();
    }
    private static boolean trulyConnected(){
        return BridgeWheelAccessibilityService.isConnected();
    }
    static String state(Context c){
        if(trulyConnected())return "ACCESIBILIDAD BRIDGE: CONECTADA";
        try{
            int flag=Settings.Secure.getInt(c.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,0);
            if(BridgeAccessPolicy.enabled(normalize(current(c)))&&flag==1)
                return "AUTORIZADA, PERO ANDROID NO ENLAZÓ EL SERVICIO";
            return "BRIDGE NO ESTÁ AUTORIZADO EN ACCESIBILIDAD ANDROID";
        }catch(Exception ex){
            return "ESTADO DE ACCESIBILIDAD NO VERIFICABLE: "+
                ex.getClass().getSimpleName();
        }
    }
    static String describe(Context c){
        if(trulyConnected())
            return "ACCESIBILIDAD BRIDGE: CONECTADA\n"+
                (prefs(c).getBoolean(BridgeWheelAccessibilityService.ENABLED,false)?
                    "Volante habilitado por el propietario; "+
                    "la visibilidad del mapa y OEM aún se verifican por separado.":
                    "Volante desactivado; no hace falta ADB ni Termux.");
        return state(c)+"\n"+prefs(c).getString(STATUS,
            "Sin intento ADB local desde esta LAB");
    }
    static void ask(Activity owner){
        if(RUNNING.get()){
            note(owner,"ADB local: operación anterior en curso");
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle("RECONECTAR ACCESIBILIDAD · BRIDGE LAB")
            .setMessage("Sólo con el vehículo estacionado. Usará ADB LOCAL "+
                "127.0.0.1:5556/5555, si está escuchando y Android autorizó "+
                "la clave de esta aplicación. NO usa Termux ni cable para "+
                "conectarse a una sesión ADB ya disponible.\n\n"+
                "AUTORIZO que Bridge LAB agregue únicamente SU servicio "+
                "a accesibilidad (o lo reconecte si ya figuraba), "+
                "conservando T1Launcher, Receiver y todos los demás. "+
                "No activa el volante ni pulsa botones.\n\n"+
                "Si la clave ADB de Bridge LAB todavía no está aprobada, "+
                "Android puede pedir autorización inicial por separado. "+
                "No se saltean los permisos del sistema.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("AUTORIZO ADB LOCAL",(d,w)->start(owner))
            .show();
    }
    private static void start(Context context){
        Context app=context.getApplicationContext();
        if(!BridgeAccessPolicy.PACKAGE.equals(app.getPackageName())){
            note(app,"Este botón está limitado a la LAB aislada");
            return;
        }
        if(trulyConnected()){
            note(app,"El servicio ya está conectado a Android; "+
                "no se modificaron permisos");
            return;
        }
        if(!RUNNING.compareAndSet(false,true)){
            note(app,"Recuperación ADB ya en curso");
            return;
        }
        note(app,"ADB LOCAL: verificando sesión y lista exacta de Android…");
        new Thread(()->{
            try{run(app);}
            catch(Exception ex){
                note(app,"ADB LOCAL NO COMPLETADO: "+BridgeLocalAdb.hint(ex)+
                    ". La clave de Bridge LAB puede requerir aprobación.");
            }finally{RUNNING.set(false);}
        },"Bridge-LAB-Owner-Approved-Accessibility").start();
    }
    private static void run(Context app)throws Exception{
        SharedPreferences p=prefs(app);
        // Repair interrupted own rebind BEFORE taking a new Android snapshot.
        String pending=p.getString(RESTORE,"");
        if(!pending.isEmpty()){
            String without=p.getString(REMOVED,"");
            if(!without.equals(BridgeAccessPolicy.removeOwn(pending)))
                throw new SecurityException("Journal LAB inválido; sin cambios");
            String live=normalize(current(app));
            if(live.equals(without)){
                checkedPut(app,without,pending);
                p.edit().remove(RESTORE).remove(REMOVED).commit();
            }else if(live.equals(pending)){
                p.edit().remove(RESTORE).remove(REMOVED).commit();
            }else throw new SecurityException(
                "Otro servicio cambió accesibilidad: no sobrescribir");
        }
        String old=normalize(current(app));
        if(BridgeAccessPolicy.parse(old)==null)
            throw new SecurityException("Listado Android ambiguo");
        String adbCurrent=normalize(BridgeLocalAdb.shell(app,
            "settings get secure enabled_accessibility_services").trim());
        if(!Objects.equals(old,adbCurrent))
            throw new SecurityException(
                "ADB y Android no coinciden. Sin cambios");
        int general=Settings.Secure.getInt(app.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_ENABLED,0);
        String updated=BridgeAccessPolicy.addOwn(old);
        if(updated==null)
            throw new SecurityException("Listado incompatible, sin cambios");
        if(!old.equals(normalize(current(app))))
            throw new SecurityException("Cambió la lista; cancelado");
        if(!updated.equals(old)){
            // Deliberate FIRST-TIME owner grant, not automatic at ignition.
            checkedPut(app,old,updated);
            note(app,"SERVICIO BRIDGE LAB AÑADIDO con ADB autorizado; "+
                "se conservaron los otros servicios");
        }else if(general==1){
            // Existing previously-approved service stuck disconnected:
            // save exact restoration journal and temporarily remove own entry.
            String without=BridgeAccessPolicy.removeOwn(old);
            if(without==null){
                note(app,"Servicio ya autorizado; no se puede reconectar "+
                    "sin deshabilitar el único servicio Android. No se tocó.");
            }else{
                if(!p.edit().putString(RESTORE,old)
                    .putString(REMOVED,without).commit())
                    throw new SecurityException("No se guardó restauración");
                try{
                    checkedPut(app,old,without);
                    Thread.sleep(850);
                }finally{
                    // Restore only if the list is STILL the exact state we
                    // wrote, otherwise leave untouched and keep journal.
                    if(without.equals(normalize(current(app)))){
                        checkedPut(app,without,old);
                        p.edit().remove(RESTORE).remove(REMOVED).commit();
                    }
                }
            }
        }
        if(general!=1){
            int before=Settings.Secure.getInt(app.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,0);
            if(before!=general)
                throw new SecurityException("Accesibilidad general cambió");
            String now=normalize(current(app));
            if(!BridgeAccessPolicy.enabled(now))
                throw new SecurityException("Servicio LAB no figura");
            BridgeLocalAdb.shell(app,
                "settings put secure accessibility_enabled 1");
            if(Settings.Secure.getInt(app.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)
                throw new SecurityException(
                    "Android rechazó habilitar accesibilidad general");
        }
        String verified=normalize(current(app));
        if(!BridgeAccessPolicy.enabled(verified))
            throw new SecurityException("Android no conserva el componente LAB");
        if(trulyConnected())
            note(app,"ACCESIBILIDAD BRIDGE LAB CONECTADA · Android confirmó");
        else note(app,"PERMISO LAB EN ANDROID: OK; esperando que Android "+
            "llame onServiceConnected. Volante permanece desactivado.");
    }
    /** Compare immediately before writing and read back exact result.
     * Not a CAS primitive: reject any new list observed between operations.
     */
    private static void checkedPut(Context app,String expected,String desired)
            throws Exception{
        String current=normalize(current(app));
        if(!expected.equals(current))
            throw new SecurityException("Cambió lista de accesibilidad; cancelado");
        if(!expected.equals(normalize(BridgeLocalAdb.shell(app,
            "settings get secure enabled_accessibility_services").trim())))
            throw new SecurityException("ADB/Android cambió durante la operación");
        if(!expected.equals(normalize(current(app))))
            throw new SecurityException("Lista Android cambió; cancelado");
        String output=BridgeLocalAdb.shell(app,
            BridgeAccessPolicy.command(desired));
        if(output.contains("Error")||output.contains("denied")||
           output.contains("Permission"))
            throw new SecurityException("Android rechazó el comando ADB");
        if(!desired.equals(normalize(current(app))))
            throw new SecurityException("Android no confirmó lista exacta");
    }
}
