package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manual owner-approved local-ADB accessibility bootstrap for the NAV touch
 * adapter. Jetour Android 11 blocks the Settings accessibility activity.
 *
 * Uses the existing whitelisted BridgeLocalAdb shell; NEVER replaces,
 * removes or reorders OEM, T1Launcher or wheel entries. No change to vehicle
 * controls, wheel, task display IDs, foreground or boot state.
 */
final class NavMosaicTouchRecovery {
    interface Done { void complete(boolean connected,String status); }
    private static final String PACKAGE=
        "ar.com.rango5gps.drivelabauto.boot.lab";
    private static final String FULL=PACKAGE+
        "/ar.com.rango5gps.drivelabauto.boot.NavMosaicTouchService";
    private static final String SHORT=PACKAGE+
        "/.NavMosaicTouchService";
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private NavMosaicTouchRecovery(){}

    static void ask(Activity owner,Done done){
        if(RUNNING.get()){
            done.complete(false,"Recuperación ADB táctil ya en curso.");
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle("TACTO NAV · HABILITAR CON ADB LOCAL")
            .setMessage("La Jetour bloquea los Ajustes de accesibilidad. " +
                "NAV puede pedir al ADB LOCAL que agregue únicamente " +
                "su servicio de táctil mosaico. Conserva intactos " +
                "T1Launcher, volante y otros servicios. No se activará " +
                "ningún control de vehículo ni se ejecutará al encender. " +
                "Requiere autorización ADB previa de esta app. ¿Autorizar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("AUTORIZO ADB TÁCTIL",(d,w)->
                start(owner.getApplicationContext(),done))
            .show();
    }

    private static String normalize(String text){
        return text==null||"null".equals(text)?"" :text.trim();
    }
    private static String current(Context app){
        return normalize(Settings.Secure.getString(
            app.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
    }
    private static boolean containsOwn(String item){
        return FULL.equals(item)||SHORT.equals(item);
    }
    private static String onlyAddOwn(String existing){
        List<String> entries=BridgeAccessPolicy.parse(existing);
        if(entries==null)throw new SecurityException(
            "Lista de accesibilidad Android inválida: no se modificó");
        int found=0;
        for(String entry:entries)if(containsOwn(entry))found++;
        if(found>1)throw new SecurityException(
            "Servicio NAV duplicado: no se modificó la lista");
        if(found==1)return existing;
        if(entries.size()>=32)throw new SecurityException(
            "Demasiados servicios Android, sin cambios");
        String desired=existing.isEmpty()?FULL:existing+":"+FULL;
        if(desired.length()>1024||
           BridgeAccessPolicy.parse(desired)==null)
            throw new SecurityException("No se pudo conservar lista segura");
        return desired;
    }
    private static void start(Context app,Done done){
        if(!PACKAGE.equals(app.getPackageName())){
            done.complete(false,"ADB táctil exclusivo para DriveLab NAV LAB");
            return;
        }
        if(!RUNNING.compareAndSet(false,true)){
            done.complete(false,"ADB táctil ya en curso");
            return;
        }
        new Thread(()->{
            boolean ready=false;
            String report;
            try{
                report=run(app);
                for(int i=0;i<12&&!NavMosaicTouchService.connected();i++)
                    Thread.sleep(350);
                ready=NavMosaicTouchService.connected();
                if(!ready)report+=". Android aún NO enlazó el servicio táctil.";
            }catch(Exception error){
                report="ADB táctil no habilitado: "+
                    BridgeLocalAdb.hint(error)+
                    ". No se habilitó control del mosaico.";
            }finally{
                RUNNING.set(false);
            }
            final boolean ok=ready;
            final String status=report;
            MAIN.post(()->done.complete(ok,status));
        },"NAV-LAB-ADB-Owner-Touch").start();
    }
    private static String run(Context app)throws Exception{
        if(NavMosaicTouchService.connected())
            return "Servicio táctil NAV ya conectado, sin escrituras ADB";
        String androidBefore=current(app);
        if(BridgeAccessPolicy.parse(androidBefore)==null)
            throw new SecurityException("Android lista inaccesible");
        String adbBefore=normalize(BridgeLocalAdb.shell(app,
            "settings get secure enabled_accessibility_services").trim());
        if(!Objects.equals(androidBefore,adbBefore))
            throw new SecurityException("ADB y Android no coinciden: sin cambios");
        String updated=onlyAddOwn(androidBefore);
        int enabled=Settings.Secure.getInt(
            app.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);
        if(!androidBefore.equals(current(app)))
            throw new SecurityException("La lista cambió: no se sobrescribe");
        if(!updated.equals(androidBefore)){
            // Exact snapshot readback immediately before this single append.
            if(!androidBefore.equals(normalize(BridgeLocalAdb.shell(app,
                    "settings get secure enabled_accessibility_services").trim()))||
               !androidBefore.equals(current(app)))
                throw new SecurityException(
                    "Otro servicio cambió Accesibilidad: sin escritura");
            String output=BridgeLocalAdb.shell(app,
                BridgeAccessPolicy.command(updated));
            if(output.contains("Error")||output.contains("denied")||
               output.contains("Permission")||output.contains("Exception"))
                throw new SecurityException("Android rechazó escritura ADB");
            if(!updated.equals(current(app)))
                throw new SecurityException(
                    "Android no confirmó agregado exacto del servicio NAV");
        }
        // Global switch is enabled only after preserving/confirming service
        // registry; other active services are NEVER disabled.
        if(enabled==0){
            if(!updated.equals(current(app)))
                throw new SecurityException("Servicios cambiaron: abortado");
            BridgeLocalAdb.shell(app,
                "settings put secure accessibility_enabled 1");
            if(Settings.Secure.getInt(app.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)
                throw new SecurityException("Android no habilitó Accesibilidad");
        }
        if(!updated.equals(current(app)))
            throw new SecurityException(
                "Los servicios cambiaron antes de conectar táctil");
        return updated.equals(androidBefore)?
            "Servicio táctil NAV autorizado anteriormente con ADB":
            "ADB añadió sólo NavMosaicTouchService, sin borrar otros servicios";
    }
}
