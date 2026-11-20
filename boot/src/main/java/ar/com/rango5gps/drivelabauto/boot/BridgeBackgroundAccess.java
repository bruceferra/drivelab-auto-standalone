package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner-approved Bridge LAB background navigation exemption for Jetour 11.
 *
 * Android 11 restricts an app in the background from opening Waze or Maps.
 * SYSTEM_ALERT_WINDOW is the documented exception; the OEM hides the normal
 * overlay Settings page. A LOCAL, previously authorized ADB host may grant
 * only Bridge LAB's own appop after an explicit owner confirmation.
 *
 * Never runs at boot, never modifies OEM/other apps, never silently grants.
 * Always verify the REAL Settings.canDrawOverlays result before declaring
 * background map launches authorized.
 */
final class BridgeBackgroundAccess {
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    static final String LAB="ar.com.rango5gps.drivelabauto.boot.lab";
    static final String STATUS="background_permission_status";
    private BridgeBackgroundAccess(){}
    static boolean authorized(Context c){
        return Settings.canDrawOverlays(c);
    }
    static String describe(Context c){
        String perm=authorized(c)?
            "PERMISO ANDROID: AUTORIZADO":
            "PERMISO ANDROID: BLOQUEADO";
        String link=BootLinkService.prefs(c).getString("status",
            "Receptor todavía no iniciado");
        return perm+" · AUTOINICIO: "+
            (BootConfig.enabled(c)?"ACTIVO":"APAGADO")+
            "\nRECEPTOR: "+link+"\n"+
            BootConfig.prefs(c).getString(STATUS,"Sin verificación ADB local");
    }
    private static void note(Context c,String value){
        BootConfig.prefs(c).edit().putString(STATUS,value)
            .putString("last_status",value).apply();
    }
    /** Always report a final outcome in the FOREGROUND where owner tapped,
     * not only in a preference line far below the scrolling Bridge screen.
     * The actual Android permission is checked again for the report.
     */
    private static void finish(Activity owner,Context app,String outcome){
        note(app,outcome);
        owner.runOnUiThread(()->{
            if(owner.isFinishing()||owner.isDestroyed())return;
            boolean verified=authorized(owner);
            new AlertDialog.Builder(owner)
                .setTitle(verified?
                    "SEGUNDO PLANO · PERMISO ANDROID ACTIVO":
                    "SEGUNDO PLANO · PERMISO ANDROID BLOQUEADO")
                .setMessage(outcome+"\n\n"+
                    (verified?"Android confirma ventanas superpuestas.":
                        "Bridge NO tiene permiso para abrir Waze oculto. "+
                        "No se modificó el QR ni ninguna otra aplicación."))
                .setPositiveButton("ENTENDIDO",null).show();
        });
    }
    static void ask(Activity owner){
        if(!LAB.equals(owner.getPackageName())){
            note(owner,"Este permiso sólo se gestiona en Bridge LAB");
            return;
        }
        if(BUSY.get()){
            new AlertDialog.Builder(owner)
                .setTitle("ADB LOCAL · OPERACIÓN EN CURSO")
                .setMessage("Bridge todavía está verificando Android. "+
                    "El resultado aparecerá en esta pantalla.")
                .setPositiveButton("ENTENDIDO",null).show();
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle("BRIDGE · RECEPCIÓN PERMANENTE EN SEGUNDO PLANO")
            .setMessage(
                "Ya comprobamos que Uber, Compartir y Sender funcionan "+
                "mientras Bridge está visible. Android Jetour bloquea "+
                "abrir Waze si falta el permiso de ventanas superpuestas "+
                "y Bridge está oculto.\n\n"+
                "AUTORIZO exclusivamente a Bridge LAB a habilitar "+
                "SYSTEM_ALERT_WINDOW mediante ADB LOCAL ya autorizado. "+
                "También activar el autoinicio de su receptor Xiaomi "+
                "para que siga escuchando con la pantalla de Bridge cerrada. "+
                "NO inicia mapas al arrancar, NO toca el tablero, OEM "+
                "ni aplicaciones ajenas.\n\n"+
                "Si Android no autoriza realmente el permiso o ADB local "+
                "no está disponible, Bridge informará el error; "+
                "no simulará que quedó habilitado.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("AUTORIZAR SEGUNDO PLANO",
                (dialog,which)->grant(owner))
            .show();
    }
    private static void grant(Activity owner){
        Context app=owner.getApplicationContext();
        if(!BUSY.compareAndSet(false,true))return;
        note(app,"ADB LOCAL: verificando conexión y permiso de Bridge…");
        new Thread(()->{
            try{
                if(!LAB.equals(app.getPackageName()))
                    throw new SecurityException("No es el paquete LAB esperado");
                if(!authorized(app)){
                    String command="appops set "+LAB+" SYSTEM_ALERT_WINDOW allow";
                    String output=BridgeLocalAdb.shell(app,command);
                    if(error(output))
                        throw new SecurityException("Android rechazó appops: "+
                            safe(output));
                    String read=BridgeLocalAdb.shell(app,
                        "appops get "+LAB+" SYSTEM_ALERT_WINDOW");
                    if(error(read)||!read.contains("allow"))
                        throw new SecurityException(
                            "ADB no confirmó el appop: "+safe(read));
                    Thread.sleep(400L);
                }
                if(!authorized(app)){
                    finish(owner,app,
                        "ADB respondió, pero Android Jetour todavía "+
                        "NO concede ventanas superpuestas. Verificación "+
                        "Settings.canDrawOverlays() = false.");
                    return;
                }
                boolean saved=BootConfig.prefs(app).edit()
                    .putBoolean("enabled",true).commit();
                if(!saved)throw new IllegalStateException(
                    "No se pudo conservar autoinicio");
                // The actual encrypted LAN foreground service is what
                // listens for phone navigation when Bridge UI is closed.
                app.startForegroundService(new Intent(app,BootLinkService.class)
                    .setAction(BootLinkService.START));
                finish(owner,app,
                    "SEGUNDO PLANO AUTORIZADO POR ANDROID · "+
                    "autoinicio del receptor Xiaomi activado. "+
                    "No abre ningún mapa hasta que Xiaomi envíe una ruta.");
            }catch(Exception problem){
                finish(owner,app,"SEGUNDO PLANO NO HABILITADO: "+
                    BridgeLocalAdb.hint(problem)+
                    ". Conservé el QR; no toqué OEM ni otros permisos.");
            }finally{BUSY.set(false);}
        },"Bridge-Owner-Approved-Local-ADB-Overlay").start();
    }
    private static boolean error(String s){
        if(s==null)return true;
        String normalized=s.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("error")||
            normalized.contains("denied")||
            normalized.contains("exception")||
            normalized.contains("unknown")||
            normalized.contains("not allowed");
    }
    private static String safe(String output){
        if(output==null)return "Sin respuesta";
        String s=output.replace('\n',' ').replace('\r',' ');
        return s.length()>160?s.substring(0,160):s;
    }
}
