package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.provider.Settings;
import android.widget.ImageView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/** Independent DriveLab Bridge: two-display requests, QR, encrypted
 * Xiaomi navigation, wheel Accessibility and OTA are self-owned.
 * Receiver may be uninstalled: no runtime IPC or signature dependency.
 */
public final class BootActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile boolean bridgeUiVisible=false;
    static boolean isVisible(){return bridgeUiVisible;}
    private ImageView qrImage;
    private TextView qrCaption;
    private TextView statusLabel;
    private TextView wheelRecoveryLabel;
    private TextView backgroundStatusLabel;
    private LinearLayout content;
    private TextView percentLabel;
    private static final int BG=0xFF101B25,PANEL=0xFF23313C,
        WHITE=Color.WHITE,QUIET=0xFFBDCED8;
    private SharedPreferences prefs(){return BootConfig.prefs(this);}
    private SharedPreferences linkPrefs(){return BootLinkService.prefs(this);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        BridgeImmersive.apply(this);
        // Never auto-launch a map on ignition and NEVER capture OEM wheel.
        // Do not erase an owner's confirmed Display1 routing merely because
        // Bridge's configuration Activity is opened/resumed.
        if(!BridgeWheelAccessibilityService.isManuallyArmed()){
            BridgeWheelAccessibilityService.prefs(this).edit()
                .putBoolean(BridgeWheelAccessibilityService.ENABLED,false).apply();
        }
        draw();
    }
    @Override protected void onResume(){
        super.onResume();
        bridgeUiVisible=true;
        // Jetour may suspend Android without BOOT_COMPLETED on ignition.
        // When the owner opens Bridge after wake, restore ONLY the paired
        // LAN listener; never re-pair or change cluster/OEM state.
        if(BootConfig.enabled(this)){
            try{
                startForegroundService(new Intent(this,BootLinkService.class)
                    .setAction(BootLinkService.START));
            }catch(Exception failed){
                prefs().edit().putString("last_status",
                    "No se pudo reanudar receptor Xiaomi: "+
                    failed.getClass().getSimpleName()).apply();
            }
        }
        prefs().registerOnSharedPreferenceChangeListener(this);
        linkPrefs().registerOnSharedPreferenceChangeListener(this);
        BridgeWheelAccessRecovery.prefs(this).registerOnSharedPreferenceChangeListener(this);
        if(content!=null)draw();
        onSharedPreferenceChanged(prefs(),"last_status");
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)BridgeImmersive.apply(this);
    }
    @Override protected void onPause(){
        bridgeUiVisible=false;
        prefs().unregisterOnSharedPreferenceChangeListener(this);
        linkPrefs().unregisterOnSharedPreferenceChangeListener(this);
        BridgeWheelAccessRecovery.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p,
            String key){
        if(p==BridgeWheelAccessRecovery.prefs(this)){
            runOnUiThread(()->{
                if(wheelRecoveryLabel!=null)
                    wheelRecoveryLabel.setText(
                        BridgeWheelAccessRecovery.describe(this));
            });
            return;
        }
        if(p==linkPrefs()){
            if("qr_ticket".equals(key)||"qr_until".equals(key)||
               "pair_status".equals(key)||"pair_port_status".equals(key)||
               "status".equals(key)||
               "received".equals(key)||"last_route".equals(key))
                runOnUiThread(()->{
                    if(qrImage!=null)renderQr();
                    if(statusLabel!=null)statusLabel.setText(bridgeStatus());
                });
            return;
        }
        if(!"last_status".equals(key)&&
           !BridgeBackgroundAccess.STATUS.equals(key))return;
        runOnUiThread(()->{
            if(backgroundStatusLabel!=null)
                backgroundStatusLabel.setText(
                    BridgeBackgroundAccess.describe(this));
            if(statusLabel!=null)statusLabel.setText(bridgeStatus()+
                "\\n\\nÚLTIMA ACCIÓN:\\n"+prefs().getString("last_status",""));
        });
    }
    private TextView text(String value,int sp,int color){
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(sp);v.setTextColor(color);
        v.setPadding(14,11,14,12);
        content.addView(v);
        return v;
    }
    private void button(String title,Runnable action){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(title);b.setTextSize(15);
        boolean primary=title.contains("QR")||
            title.contains("INICIAR RECEPTOR");
        b.setBackgroundTintList(ColorStateList.valueOf(
            primary?0xFF18C7D6:0xFF16465B));
        b.setTextColor(primary?0xFF08242C:Color.WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(5,3,5,3);
        content.addView(b,lp);
        b.setOnClickListener(v->action.run());
    }
    private String labelFor(String pkg){
        if(pkg==null||pkg.isEmpty())return "SIN ELEGIR";
        try{
            PackageManager pm=getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg,0))
                .toString()+" · "+pkg;
        }catch(Exception e){return pkg;}
    }
    private void startLink(String action){
        try{
            Intent request=new Intent(this,BootLinkService.class)
                .setAction(action);
            if(BootLinkService.STOP.equals(action))
                startService(request);
            else startForegroundService(request);
            prefs().edit().putString("last_status",
                "Solicitud local: "+action+" · esperando confirmación").apply();
        }catch(Exception ex){
            new AlertDialog.Builder(this).setTitle("BRIDGE NO INICIADO")
                .setMessage("Android rechazó el servicio: "+
                    ex.getClass().getSimpleName()+": "+ex.getMessage())
                .setPositiveButton("ENTENDIDO",null).show();
        }
    }
    private String bridgeStatus(){
        SharedPreferences p=linkPrefs();
        return "RECEPTOR XIAOMI INDEPENDIENTE\n"+
            p.getString("status","Receptor no iniciado")+
            "\nQR: "+p.getString("pair_status","Sin vínculo verificado")+
            "\n"+p.getString("last_route","Todavía no recibió destinos")+
            "\nSolicitudes confirmadas por protocolo: "+p.getInt("received",0)+
            "\nAUTOARRANQUE BRIDGE: "+(BootConfig.enabled(this)?
                "ACTIVO, solo enlace Xiaomi":"APAGADO")+
            "\nSEGUNDO PLANO: "+(BridgeBackgroundAccess.authorized(this)?
                "AUTORIZADO POR ANDROID":"SIN PERMISO ANDROID")+
            "\nVENTANAS MANUALES: Bridge propio; visibilidad requiere prueba. "+
            "No se abre DriveLab Auto.";
    }
    private void renderQr(){
        if(qrImage==null||qrCaption==null)return;
        String payload=BootLinkService.qr(this);
        if(payload.isEmpty()){
            qrImage.setVisibility(View.GONE);
            qrCaption.setText(
                "El QR aparecerá cuando ambos puertos estén LISTOS. "+
                "Navegación: "+linkPrefs().getString("status",
                    "iniciando")+
                " · QR: "+linkPrefs().getString("pair_port_status",
                    "iniciando")+
                " · "+linkPrefs().getString("pair_status",
                    "Conectá Jetour y Xiaomi a la misma red."));
            return;
        }
        try{
            qrImage.setImageBitmap(BootQr.draw(payload));
            qrImage.setVisibility(View.VISIBLE);
            qrCaption.setText("Leelo desde Sender Xiaomi. "+
                "Expira en 2 minutos. La clave no se muestra en texto.");
        }catch(Exception ex){
            qrImage.setVisibility(View.GONE);
            qrCaption.setText("QR inválido: "+ex.getClass().getSimpleName());
        }
    }
    private void showQr(){
        startLink(BootLinkService.PAIR_QR);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16,12,16,10);
        qrCaption=new TextView(this);
        qrCaption.setTextColor(Color.WHITE);
        qrCaption.setTextSize(16);
        layout.addView(qrCaption,new LinearLayout.LayoutParams(-1,-2));
        qrImage=new ImageView(this);
        qrImage.setBackgroundColor(Color.WHITE);
        qrImage.setAdjustViewBounds(true);
        layout.addView(qrImage,new LinearLayout.LayoutParams(-1,420));
        renderQr();
        new AlertDialog.Builder(this)
            .setTitle("VINCULAR XIAOMI · DRIVE LAB BRIDGE")
            .setView(layout)
            .setPositiveButton("CERRAR",null)
            .setOnDismissListener(d->{qrImage=null;qrCaption=null;})
            .show();
    }
    private void setAuto(){
        if(BootConfig.enabled(this)){
            prefs().edit().putBoolean("enabled",false)
                .putString("last_status",
                    "Autoinicio Bridge APAGADO sin tocar otras aplicaciones.")
                .apply();
            draw();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("AUTOINICIO INDEPENDIENTE")
            .setMessage("Activará SOLAMENTE el receptor Xiaomi cifrado de "+
                "DriveLab Bridge al encender. No arrancará la pantalla "+
                "de DriveLab Auto, ni mapas, ni el firmware del tablero. "+
                "La multiventana automática todavía está pendiente de "+
                "validación. ¿Activar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                prefs().edit().putBoolean("enabled",true)
                    .putString("last_status",
                        "Autoinicio Bridge: enlace Xiaomi opt-in; "+
                        "no se abrirán mapas ni Cockpit").apply();
                draw();
            }).show();
    }
    private void stopBridge(){
        new AlertDialog.Builder(this)
            .setTitle("DETENER BRIDGE")
            .setMessage("Detiene el receptor Xiaomi de Bridge y su "+
                "próximo autoinicio. No fuerza detener mapas ni borra "+
                "datos; no toca DriveLab Auto ni el firmware Jetour.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("DETENER",(d,w)->{
                prefs().edit().putBoolean("enabled",false).apply();
                startLink(BootLinkService.STOP);
                // User requested complete shutdown of the standalone
                // Bridge interface and listener; only this app's own task
                // is removed. No force-stop or third-party task changes.
                finishAndRemoveTask();
            }).show();
    }
    private void requestOverlay(){
        // Jetour does NOT expose the normal Android overlay settings page.
        // Bridge's own local ADB path requires explicit owner confirmation,
        // is restricted to its own package, and verifies the actual appop.
        BridgeBackgroundAccess.ask(this);
    }
    /** Explicitly opt in to option navigation, never zoom. This can
     * compete with T1Launcher's OEM hard-key callback, so owner approval
     * is required and Bridge never enables it during auto-start.
     */
    private void toggleWheel(){
        SharedPreferences p=BridgeWheelAccessibilityService.prefs(this);
        if(p.getBoolean(BridgeWheelAccessibilityService.ENABLED,false)&&
                BridgeWheelAccessibilityService.isManuallyArmed()){
            BridgeWheelAccessibilityService.setManuallyArmed(false);
            p.edit().putBoolean(BridgeWheelAccessibilityService.ENABLED,false)
                .putString(BridgeWheelAccessibilityService.STATUS,
                    "Volante Bridge desactivado por el propietario").apply();
            draw();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("VOLANTE · RECIBIR TECLAS Y OPCIONES")
            .setMessage("La rueda avanza o retrocede entre opciones "+
                "visibles de Waze/Google Maps en el Display seleccionado. "+
                "OK confirma y ATRÁS cancela. NO hace zoom.\n\n"+
                "Antes de activarlo, desactivá VOLANTE → MAPA "+
                "en T1Launcher: el Binder OEM puede permitir "+
                "un solo controlador. Bridge no se activará "+
                "automáticamente al encender ni tocará el sistema Jetour.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                BridgeWheelAccessibilityService.setManuallyArmed(true);
                p.edit().putString(BridgeWheelAccessibilityService.TARGET,
                    p.getString(BridgeWheelAccessibilityService.TARGET,
                        BridgeWheelAccessibilityService.WAZE))
                    .putBoolean(BridgeWheelAccessibilityService.ENABLED,true).apply();
                draw();
            }).show();
    }
    private void askRecaptureKeys(){
        new AlertDialog.Builder(this)
            .setTitle("RECAPTURAR VOLANTE · 20 SEGUNDOS")
            .setMessage("Con la Jetour ESTACIONADA y el control de volante "+
                "de T1Launcher DESACTIVADO para evitar dos receptores OEM. "+
                "Bridge conectará temporalmente el Binder de la rueda "+
                "y registrará códigos 1000–1003 de rueda, OK y Atrás. "+
                "Durante estos 20 segundos NO ejecuta opciones, zoom, "+
                "gestos ni acciones sobre Google Maps, Waze o Jetour. "+
                "Se desconecta al terminar si el volante no estaba "+
                "habilitado manualmente. No necesita ADB si Accesibilidad "+
                "Bridge ya está conectada. Si está desconectada, "+
                "usá el botón ADB local 5556/5555.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CAPTURAR 20 S",(dialog,which)->{
                BridgeWheelAccessibilityService.beginKeyCapture(this);
                draw();
            }).show();
    }
    private void chooseWheelDisplay(){
        SharedPreferences p=BridgeWheelAccessibilityService.prefs(this);
        int selected=p.getInt(BridgeWheelAccessibilityService.WINDOW_DISPLAY,1);
        new AlertDialog.Builder(this)
            .setTitle("VOLANTE · PANTALLA ANDROID REAL")
            .setMessage("Elegí dónde está la ventana real y accesible de "+
                "Google Maps/Waze, no dónde está una copia de imagen. "+
                "La selección NO mueve tareas. Si el mapa queda detrás "+
                "de una capa OEM, las teclas se registran pero NO se "+
                "envían acciones.")
            .setSingleChoiceItems(new String[]{
                "DISPLAY 1 · tablero físico",
                "DISPLAY 0 · pantalla central"
            },selected==0?1:0,(dialog,which)->{
                p.edit().putInt(BridgeWheelAccessibilityService.WINDOW_DISPLAY,
                    which==1?0:1).apply();
                dialog.dismiss();draw();
            })
            .setNegativeButton("CANCELAR",null).show();
    }
    private void chooseWheelMap(){
        SharedPreferences p=BridgeWheelAccessibilityService.prefs(this);
        String current=p.getString(BridgeWheelAccessibilityService.TARGET,
            BridgeWheelAccessibilityService.WAZE);
        new AlertDialog.Builder(this)
            .setTitle("VOLANTE · APP EN DISPLAY 1")
            .setSingleChoiceItems(new String[]{"Google Maps","Waze"},
                BridgeWheelAccessibilityService.WAZE.equals(current)?1:0,
                (d,which)->{
                    p.edit().putString(BridgeWheelAccessibilityService.TARGET,
                        which==0?BridgeWheelAccessibilityService.MAPS:
                            BridgeWheelAccessibilityService.WAZE).apply();
                    d.dismiss();draw();
                })
            .setNegativeButton("CANCELAR",null).show();
    }
    private void openWheelPermission(){
        try{
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }catch(Exception ex){
            new AlertDialog.Builder(this)
                .setTitle("ACCESIBILIDAD NO DISPONIBLE")
                .setMessage("No pude abrir permisos de Android: "+
                    ex.getClass().getSimpleName())
                .setPositiveButton("ENTENDIDO",null).show();
        }
    }
    private void chooseBridgeApp(boolean first){
        TreeMap<String,String> choices=new TreeMap<>(
            String.CASE_INSENSITIVE_ORDER);
        PackageManager pm=getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo found:pm.queryIntentActivities(query,0)){
            if(found.activityInfo==null)continue;
            String pkg=found.activityInfo.packageName;
            if(!BridgeWindowsPolicy.allowed(pkg))continue;
            choices.put(found.loadLabel(pm)+" · "+pkg,pkg);
        }
        // Maps can be a preinstalled Android application. Keep named
        // eligible choices even if Jetour filters system packages.
        for(String pkg:new String[]{BridgeWindowsPolicy.MAPS,
                BridgeWindowsPolicy.WAZE,BridgeWindowsPolicy.SPOTIFY}){
            try{
                if(pm.getLaunchIntentForPackage(pkg)==null)continue;
                choices.put(pm.getApplicationLabel(
                    pm.getApplicationInfo(pkg,0))+" · "+pkg,pkg);
            }catch(Exception ignored){}
        }
        if(choices.isEmpty()){
            new AlertDialog.Builder(this)
                .setTitle("APLICACIONES DE BRIDGE")
                .setMessage("No encontré aplicaciones instaladas para "+
                    "estas ventanas. No se abrirá DriveLab Auto.")
                .setPositiveButton("OK",null).show();
            return;
        }
        String[] labels=choices.keySet().toArray(new String[0]);
        String[] pkgs=choices.values().toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle(first?"PANTALLA CENTRAL · APP IZQUIERDA":
                "PANTALLA CENTRAL · APP DERECHA")
            .setItems(labels,(d,which)->{
                BridgeStandaloneWindows.prefs(this).edit()
                    .putString(first?BridgeStandaloneWindows.FIRST:
                        BridgeStandaloneWindows.SECOND,pkgs[which]).apply();
                draw();
            })
            .setNegativeButton("CANCELAR",null).show();
    }
    private void chooseBridgeMap(){
        String current=BridgeStandaloneWindows.tabMap(this);
        final String[] labels={"Waze","Google Maps","Waze + Google Maps"};
        final String[] pkgs={BridgeWindowsPolicy.WAZE,
            BridgeWindowsPolicy.MAPS,BridgeDualRoutePolicy.BOTH};
        int checked=BridgeWindowsPolicy.MAPS.equals(current)?1:
            BridgeDualRoutePolicy.BOTH.equals(current)?2:0;
        new AlertDialog.Builder(this)
            .setTitle("DISPLAY 1 · QUÉ MAPAS QUERÉS ABRIR")
            .setSingleChoiceItems(labels,checked,(d,choice)->{
                BridgeStandaloneWindows.prefs(this).edit()
                    .putString(BridgeStandaloneWindows.TAB_MAP,pkgs[choice])
                    .putBoolean(BridgeStandaloneWindows.TAB_CONFIRMED,false)
                    .putBoolean(BridgeStandaloneWindows.TAB_ROUTE,false)
                    .apply();
                d.dismiss();draw();
            })
            .setNegativeButton("CANCELAR",null).show();
    }
    private void askProjectionLab(){
        new AlertDialog.Builder(this)
            .setTitle("LAB · COPIAR PANTALLA CENTRAL 20 SEGUNDOS")
            .setMessage("SÓLO CON LA JETOUR ESTACIONADA. "+
                "Esta prueba NO pone Waze en otra tarea. "+
                "Le pedirá a Android permiso para CAPTURAR TODO lo "+
                "que se ve en la pantalla central, incluyendo "+
                "notificaciones y otros datos que aparezcan. "+
                "La copia se muestra únicamente sobre el 65 % "+
                "inferior del tablero durante un máximo de 20 segundos. "+
                "No se graba, no se transmite por red ni se almacenan "+
                "imágenes. Android puede rechazar la solicitud. "+
                "Después de autorizar, abrí Waze en la central. "+
                "No uses la prueba conduciendo.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CONTINUAR A PERMISOS",(d,w)->
                startActivity(new Intent(this,BridgeProjectionLabActivity.class)))
            .show();
    }
    private void askPublicVirtualActivityProbe(){
        new AlertDialog.Builder(this)
            .setTitle("LAB · DISPLAY VIRTUAL PÚBLICO")
            .setMessage("Sólo con la Jetour ESTACIONADA. Esta prueba de "+
                "20 segundos crea un display virtual PÚBLICO y limitado "+
                "a contenido propio, por lo que otras aplicaciones "+
                "podrían verlo durante la sesión. Intentará abrir "+
                "ÚNICAMENTE una actividad propia de Bridge; jamás "+
                "Waze ni Maps. No duplica pantalla central, "+
                "no modifica tareas ni funciones Jetour. "+
                "Si Android deniega la actividad, lo informará "+
                "y mostrará la animación de respaldo.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR PÚBLICO 20 S",(d,w)->
                startActivity(new Intent(this,
                    BridgeClusterSurfaceProbeActivity.class)
                    .putExtra(BridgeClusterSurfaceProbeActivity.EXTRA_ACTIVITY_PROBE,true)
                    .putExtra(BridgeClusterSurfaceProbeActivity.EXTRA_PUBLIC_PROBE,true)))
            .show();
    }
    private void askRealVirtualActivityProbe(){
        new AlertDialog.Builder(this)
            .setTitle("LAB · APP REAL EN DISPLAY VIRTUAL")
            .setMessage("SÓLO CON EL VEHÍCULO ESTACIONADO. Durante 20 segundos "+
                "Bridge intentará abrir una actividad Android PROPIA "+
                "en la pantalla virtual y mostrar un contador animado "+
                "sobre Radio/Bluetooth. No es Waze. No toca tareas "+
                "ajenas, mapas, volante ni controles Jetour. "+
                "El 35 % superior del tablero no dibuja Bridge.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR APP 20 S",(d,w)->
                startActivity(new Intent(this,
                    BridgeClusterSurfaceProbeActivity.class)
                    .putExtra(BridgeClusterSurfaceProbeActivity.EXTRA_ACTIVITY_PROBE,
                        true)))
            .show();
    }
    private void askRealClusterSurfaceProbe(){
        new AlertDialog.Builder(this)
            .setTitle("PRUEBA REAL DE SUPERFICIE · DISPLAY 1")
            .setMessage("Sólo con la Jetour COMPLETAMENTE ESTACIONADA. "+
                "Durante 12 segundos Bridge dibujará una banda de prueba "+
                "en TODO EL ANCHO de la zona inferior del tablero, "+
                "incluido Radio/Bluetooth. La zona superior de avisos "+
                "quedará transparente. No es un mapa. "+
                "Usará Presentation y VirtualDisplay "+
                "propios sin ADB, ni tocar el firmware, ni modificar "+
                "el orden de las superficies OEM. "+
                "Se borrará al salir o automáticamente. "+
                "NO uses esta prueba conduciendo. "+
                "Si la banda cubre Radio/Bluetooth, la capa quedó delante; "+
                "si Bluetooth sigue visible encima, el OEM domina. Android "+
                "Jetour no deja superponer esta Presentation.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("COMPROBAR 12 SEGUNDOS",(d,w)->
                startActivity(new Intent(this,
                    BridgeClusterSurfaceProbeActivity.class)))
            .show();
    }
    private void toggleDisplay1(){
        if(BridgeStandaloneWindows.tableroEnabled(this)){
            BridgeStandaloneWindows.disableTablero(this);
            draw();
            return;
        }
        String selection=BridgeStandaloneWindows.tabMap(this);
        String name=BridgeDualRoutePolicy.BOTH.equals(selection)?
            "Waze y Google Maps":BridgeWindowsPolicy.WAZE.equals(selection)?
            "Waze":"Google Maps";
        new AlertDialog.Builder(this)
            .setTitle("ACTIVAR DISPLAY 1 · 100%")
            .setMessage("Sólo con la Jetour ESTACIONADA. Bridge solicitará "+
                name+" en Display 1, a pantalla completa, sin Freeform. "+
                "La pantalla puede cubrir indicadores o avisos del auto: "+
                "si ocurre, NO uses el mapa en marcha.\n\n"+
                "Un pedido aceptado por Android no garantiza que el mapa "+
                "sea visible ni que permanezca en Display 1. "+
                "Para activar el envío de rutas tendrás que confirmar "+
                "visualmente el resultado.\n\n"+
                "El volante queda desactivado y el receptor Xiaomi "+
                "no se modifica.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ABRIR MAPA(S)",(d,w)->{
                BridgeStandaloneWindows.Result result=
                    BridgeStandaloneWindows.tablero(this);
                if(!result.submitted){
                    new AlertDialog.Builder(this)
                        .setTitle("NO SE ABRIÓ DISPLAY 1")
                        .setMessage(result.message)
                        .setPositiveButton("ENTENDIDO",(dialog,x)->draw())
                        .show();
                    return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("CONFIRMAR DISPLAY 1")
                    .setMessage(result.message+"\n\n"+
                        "¿Comprobaste que el mapa aparece en el tablero "+
                        "sin tapar indicadores o alertas esenciales? "+
                        "Si pediste ambos, Android puede dejar sólo "+
                        "uno visible encima del otro; los dos pueden "+
                        "recibir destinos de forma independiente.")
                    .setNegativeButton("NO ACTIVAR RUTAS",(dialog,x)->draw())
                    .setPositiveButton("SÍ, ACTIVAR ENVÍOS",(dialog,x)->{
                        BridgeStandaloneWindows.prefs(this).edit()
                            .putBoolean(BridgeStandaloneWindows.TAB_CONFIRMED,true)
                            .putBoolean(BridgeStandaloneWindows.TAB_ROUTE,true)
                            .apply();
                        draw();
                    }).show();
            }).show();
    }
    private void askBridgeWindows(boolean tablero){
        new AlertDialog.Builder(this)
            .setTitle(tablero?
                "PRUEBA DISPLAY 1 · TABLERO JETOUR":
                "PRUEBA DISPLAY 0 · DOS VENTANAS")
            .setMessage("Realizar SOLAMENTE con la camioneta estacionada. "+
                "Bridge solicitará ventanas Freeform por API Android. "+
                "No detendrá apps ni borrará rutas o datos. "+
                "Si Android bloquea el modo, no abrirá nada en "+
                "pantalla completa. Los avisos Jetour tienen prioridad "+
                "y la visibilidad final debe comprobarse físicamente. "+
                "¿Enviar solicitud?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR",(d,w)->{
                BridgeStandaloneWindows.Result result=tablero?
                    BridgeStandaloneWindows.tablero(this):
                    BridgeStandaloneWindows.center(this);
                new AlertDialog.Builder(this)
                    .setTitle(result.submitted?
                        "SOLICITUD ENVIADA · NO VERIFICADA":
                        "NO SE ABRIERON VENTANAS")
                    .setMessage(result.message)
                    .setPositiveButton("ENTENDIDO",(dialog,which)->draw())
                    .show();
            }).show();
    }
    private void askBridgeVirtualProbe(){
        new AlertDialog.Builder(this)
            .setTitle("PRUEBA AISLADA · PANTALLA VIRTUAL")
            .setMessage("Sólo con la Jetour detenida. Se creará una pantalla "+
                "virtual PRIVADA y se dibujará un patrón propio dentro de "+
                "Bridge, en la pantalla central. NO se abrirá Waze, Spotify "+
                "ni Display 1. La prueba se libera al salir o tras 2 minutos. "+
                "¿Ejecutar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR",(dialog,which)->
                startActivity(new Intent(this,BridgeVirtualProbeActivity.class)))
            .show();
    }
    private String packageVersion(String pkg){
        try{
            return getPackageManager().getPackageInfo(pkg,0).versionName;
        }catch(Exception ignored){return "NO INSTALADO";}
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(14,8,14,24);
        scroll.addView(content);

        text("DRIVELAB BRIDGE",24,0xFF32DBD5);
        text("Versión "+packageVersion(getPackageName())+
            " · Enlace Xiaomi + actualizador independientes",14,QUIET);
        // Principal y único acceso a OTA, siempre arriba, sin desplazamiento.
        button("BUSCAR ACTUALIZACIÓN · LAB OTA",()->
            BootUpdater.check(this,s->prefs().edit()
                .putString("last_status",s).apply()));
        text("No abre la pantalla DriveLab Auto. No reemplaza "+
            "el firmware ni los avisos del Jetour.",13,QUIET);
        text("XIAOMI · CONEXIÓN DIRECTA",18,WHITE);
        button("INICIAR RECEPTOR XIAOMI",()->
            startLink(BootLinkService.START));
        button("VINCULAR POR QR · MOSTRAR CÓDIGO",this::showQr);
        button("CERRAR BRIDGE · APAGAR ENLACE Y SALIR",
            this::stopBridge);
        button(BridgeBackgroundAccess.authorized(this)?
            "SEGUNDO PLANO: AUTORIZADO · REVISAR":
            "HABILITAR SEGUNDO PLANO · ADB LOCAL",this::requestOverlay);
        backgroundStatusLabel=text(
            BridgeBackgroundAccess.describe(this),12,QUIET);
        text("PANTALLA CENTRAL · DISPLAY 0 · BRIDGE",18,WHITE);
        text("Panel izquierdo ancho + panel derecho vertical "+
            "(Spotify tipo celular). Para pruebas también podés "+
            "elegir Spotify y ProManager; DriveLab Auto está excluido.",12,QUIET);
        button("APP IZQUIERDA · "+
            labelFor(BridgeStandaloneWindows.first(this)),
            ()->chooseBridgeApp(true));
        button("APP DERECHA · "+
            labelFor(BridgeStandaloneWindows.second(this)),
            ()->chooseBridgeApp(false));
        int centerRatio=BridgeStandaloneWindows.ratio(this);
        TextView ratioInfo=text("REPARTO CENTRAL: "+
            centerRatio+"% / "+(100-centerRatio)+"%",14,WHITE);
        SeekBar bridgeRatio=new SeekBar(this);
        bridgeRatio.setMin(40);bridgeRatio.setMax(80);
        bridgeRatio.setProgress(centerRatio);
        bridgeRatio.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(
                    SeekBar bar,int value,boolean user){
                    ratioInfo.setText("REPARTO CENTRAL: "+
                        value+"% / "+(100-value)+"%");
                    if(user)BridgeStandaloneWindows.prefs(BootActivity.this)
                        .edit().putInt(BridgeStandaloneWindows.RATIO,value)
                        .apply();
                }
                @Override public void onStartTrackingTouch(SeekBar bar){}
                @Override public void onStopTrackingTouch(SeekBar bar){}
            });
        content.addView(bridgeRatio);
        button("PROBAR DOS VENTANAS · SÓLO BRIDGE · ESTACIONADO",
            ()->askBridgeWindows(false));
        button("LAB · PROBAR PANTALLA VIRTUAL · SOLO CENTRAL",
            this::askBridgeVirtualProbe);
        text("Prueba de arquitectura T1N145: pantalla virtual privada, "+
            "SurfaceTexture y Presentation propias. Sólo patrón de "+
            "diagnóstico; todavía NO migra ni muestra aplicaciones "+
            "en el tablero.",12,QUIET);
        text("PANTALLA DEL TABLERO · DISPLAY 1",18,WHITE);
        String tabChoice=BridgeStandaloneWindows.tabMap(this);
        String tabName=BridgeDualRoutePolicy.BOTH.equals(tabChoice)?
            "WAZE + GOOGLE MAPS":BridgeWindowsPolicy.WAZE.equals(tabChoice)?
            "WAZE":"GOOGLE MAPS";
        button("MAPA(S) DEL TABLERO: "+tabName+" · CAMBIAR",
            this::chooseBridgeMap);
        boolean tabActive=BridgeStandaloneWindows.tableroEnabled(this);
        button(tabActive?
            "DESACTIVAR DISPLAY 1 · ENVIAR A CENTRAL":
            "ACTIVAR DISPLAY 1 · PANTALLA COMPLETA 100%",
            this::toggleDisplay1);
        text(tabActive?
            "DISPLAY 1 ACTIVADO PARA RUTAS: "+tabName+
            ". El destino Waze/Google Maps/Ambos se selecciona "+
            "desde Sender y no depende de la app visible.":
            "DISPLAY 1 SIN RUTAS. Los destinos se envían a Display 0. "+
            "Desactivar no cierra ni borra aplicaciones ya abiertas "+
            "por Android.",12,QUIET);
        button("COMPROBAR CAPA INFERIOR COMPLETA · DISPLAY 1 · 12 S",
            this::askRealClusterSurfaceProbe);
        button("LAB · PROBAR APP REAL EN CAPA VIRTUAL · 20 S",
            this::askRealVirtualActivityProbe);
        button("LAB · DISPLAY VIRTUAL PÚBLICO · APP PROPIA · 20 S",
            this::askPublicVirtualActivityProbe);
        button("LAB · COPIAR CENTRAL A TABLERO · PERMISO · 20 S",
            this::askProjectionLab);
        text("ÚLTIMA CAPTURA: "+BridgeProjectionLabService.lastResult(this),
            12,QUIET);
        text("RESULTADO CAPA / APP: "+
            BridgeClusterSurfaceProbeActivity.lastResult(this),12,QUIET);
        text("DIAGNÓSTICO PERSISTENTE: "+
            BridgeClusterSurfaceProbeActivity.lastDiagnostic(this),12,QUIET);
        button("LIBERAR CLÚSTER · SÓLO MAPAS VERIFICADOS",
            ()->BridgeClusterRecovery.ask(this));
        text("RECUPERACIÓN: "+BridgeClusterRecovery.status(this),12,QUIET);
        text("ESTADO DE VENTANAS: "+
            BridgeStandaloneWindows.prefs(this).getString(
                BridgeStandaloneWindows.LAST,"SIN SOLICITUD DESDE BRIDGE"),
            12,QUIET);
        text("VOLANTE · SELECCIÓN DE OPCIONES · SIN ZOOM",18,WHITE);
        SharedPreferences wheel=BridgeWheelAccessibilityService.prefs(this);
        boolean wheelOn=wheel.getBoolean(
            BridgeWheelAccessibilityService.ENABLED,false);
        String wheelTarget=wheel.getString(
            BridgeWheelAccessibilityService.TARGET,
            BridgeWheelAccessibilityService.WAZE);
        button("MAPA DEL VOLANTE: "+
            (BridgeWheelAccessibilityService.WAZE.equals(wheelTarget)?
                "WAZE":"GOOGLE MAPS")+" · CAMBIAR",this::chooseWheelMap);
        int actualDisplay=wheel.getInt(
            BridgeWheelAccessibilityService.WINDOW_DISPLAY,1);
        button("PANTALLA DE CONTROLES: DISPLAY "+actualDisplay+" · CAMBIAR",
            this::chooseWheelDisplay);
        button("RECAPTURAR TECLAS OEM · 20 S · SIN ZOOM",
            this::askRecaptureKeys);
        text("GOOGLE MAPS · NAVEGACIÓN AUTOMÁTICA: "+
            BridgeGoogleMapsReplacement.status(this),12,QUIET);
        text("DriveLab Auto: destino recibido de Sender → google.navigation "+
            "→ completar automáticamente Iniciar / reemplazo si Android "+
            "lo muestra durante 12 segundos. Sin botón manual, sin zoom.",
            12,QUIET);
        text("CAPTURA VOLANTE: "+
            BridgeWheelAccessibilityService.captureState(this),12,QUIET);
        text("ACCESIBILIDAD REAL: "+
            (BridgeWheelAccessibilityService.isConnected()?
                "CONECTADA":"DESCONECTADA · revisar ADB LOCAL 5556/5555"),
            12,QUIET);
        button(wheelOn&&BridgeWheelAccessibilityService.isManuallyArmed()?
            "VOLANTE BRIDGE: ACTIVADO · DESACTIVAR":
            "VOLANTE BRIDGE: DESACTIVADO · ACTIVAR",this::toggleWheel);
        text(wheelOn&&BridgeWheelAccessibilityService.isManuallyArmed()?
            "VOLANTE HABILITADO MANUALMENTE. Binder OEM puede recibir "+
            "teclas sin ventana de mapa visible, igual que DriveLab Auto. "+
            "Sólo ejecuta opciones, OK y Atrás si verifica el mapa en "+
            "el Display seleccionado. Sin zoom.":
            "VOLANTE APAGADO: no se registra la rueda OEM. "+
            "Para probarlo, desactivá VOLANTE → MAPA en T1Launcher, "+
            "elegí Waze y pulsá ACTIVAR. No se inicia solo.",13,WHITE);
        button("RECONECTAR ACCESIBILIDAD · ADB LOCAL · SIN TERMUX",
            ()->BridgeWheelAccessRecovery.ask(this));
        wheelRecoveryLabel=text(
            BridgeWheelAccessRecovery.describe(this),13,WHITE);
        text("La ROM Jetour puede bloquear Ajustes de accesibilidad. "+
            "No necesitás abrirlos otra vez: el permiso ya se obtuvo "+
            "por ADB local.",12,QUIET);
        text("ADB es local 127.0.0.1:5555/5556, no requiere Termux "+
            "si Android autorizó la clave de Bridge LAB. El volante "+
            "NO se activa con este botón; se controla por separado.",12,QUIET);
        text("RUEDA: siguiente/anterior opción. OK: confirmar. "+
            "ATRÁS: cancelar dentro del mapa. Nunca zoom. "+
            "Android debe autorizar accesibilidad. Desactivá primero "+
            "el volante de T1Launcher: ambos no deben competir.",12,QUIET);
        text("ÚLTIMO ESTADO VOLANTE: "+
            wheel.getString(BridgeWheelAccessibilityService.STATUS,
                "Sin prueba física"),12,QUIET);
        text("VENTANAS DISPLAY 0 Y DISPLAY 1",18,WHITE);
        text("El motor Freeform de Bridge está separado de Receiver. "+
            "Su disponibilidad real depende de Android Jetour. "+
            "No abre DriveLab Auto, no fuerza cierres ni promete "+
            "visibilidad sin inspección física.",13,QUIET);
        text("DIAGNÓSTICO DE CAPAS · SÓLO LECTURA",17,WHITE);
        button("INSPECCIONAR TABLERO · NO MODIFICAR CAPAS",
            ()->startActivity(new Intent(this,BridgeLayersActivity.class)));
        text("INICIO AUTOMÁTICO DE BRIDGE · SÓLO XIAOMI",17,WHITE);
        boolean enabled=BootConfig.enabled(this);
        button(enabled?"AUTOINICIO: ACTIVADO · DESACTIVAR":
            "AUTOINICIO: DESACTIVADO · ACTIVAR",this::setAuto);
        text("ESTADO / ERRORES DEL ÚLTIMO PEDIDO",17,WHITE);
        statusLabel=text(bridgeStatus()+"\n\nÚLTIMA ACCIÓN:\n"+
            prefs().getString("last_status",
                "Sin solicitudes enviadas"),13,QUIET);
        statusLabel.setBackgroundColor(PANEL);
        statusLabel.setTextIsSelectable(true);
        text("Los estados de Xiaomi corresponden exclusivamente "+
            "a Bridge. Un QR emitido NO es conexión confirmada.",12,QUIET);
        button("ACTUALIZAR ESTADO",this::draw);
        setContentView(scroll);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
            View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
