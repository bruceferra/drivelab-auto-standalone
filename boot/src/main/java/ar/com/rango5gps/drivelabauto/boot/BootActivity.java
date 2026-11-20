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

/** Independent DriveLab Bridge: Xiaomi QR and updater are standalone.
 * Legacy Receiver mosaics remain ONLY in opt-in diagnostics.
 */
public final class BootActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String[] MODES={
        "Principal izquierda + 2 a la derecha",
        "Principal arriba + 2 abajo",
        "Tres columnas",
        "Dos columnas",
        "Una aplicación (100 % de la zona útil)"};
    private static volatile boolean bridgeUiVisible=false;
    static boolean isVisible(){return bridgeUiVisible;}
    private int mode,percent;
    private int activeDisplay=0;
    private boolean revealPending=false;
    private boolean legacyAdvanced=false;
    private ImageView qrImage;
    private TextView qrCaption;
    private TextView statusLabel;
    private TextView wheelRecoveryLabel;
    private final String[] selected=new String[3];
    private boolean clusterEnabled,clusterConfirmed;
    private int clusterCount,clusterPercent,captionSafeDp;
    private final String[] clusterApps=new String[2];
    private LinearLayout content;
    private TextView percentLabel;
    private static final int BG=0xFF101B25,PANEL=0xFF23313C,
        WHITE=Color.WHITE,QUIET=0xFFBDCED8;
    private SharedPreferences prefs(){return BootConfig.prefs(this);}
    private SharedPreferences linkPrefs(){return BootLinkService.prefs(this);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        BridgeImmersive.apply(this);
        SharedPreferences p=prefs();
        mode=Math.min(4,Math.max(0,p.getInt("mode",3)));
        percent=Math.min(99,Math.max(1,p.getInt("percent",65)));
        for(int i=0;i<3;i++)
            selected[i]=p.getString("pkg"+i,
                i==0?"com.google.android.apps.maps":i==1?"com.spotify.music":"");
        clusterEnabled=p.getBoolean("cluster_enabled",false);
        clusterConfirmed=p.getBoolean("cluster_confirmed",false);
        clusterCount=Math.min(2,Math.max(1,p.getInt("cluster_count",2)));
        clusterPercent=Math.min(70,Math.max(30,p.getInt("cluster_percent",50)));
        captionSafeDp=Math.min(88,Math.max(0,p.getInt("caption_safe_dp",44)));
        for(int i=0;i<2;i++)
            clusterApps[i]=p.getString("cluster_pkg"+i,
                i==0?"com.waze":"com.google.android.apps.maps");
        draw();
    }
    @Override protected void onResume(){
        super.onResume();
        bridgeUiVisible=true;
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
        if(!"last_status".equals(key))return;
        String current=p.getString("last_status","Sin diagnóstico");
        runOnUiThread(()->{
            if(statusLabel!=null)statusLabel.setText(current);
            if(!revealPending)return;
            if(current.startsWith("MOSAICO_RECUPERADO_SOLICITADO:")){
                revealPending=false;
                Toast.makeText(this,
                    "Ventanas existentes al frente · verificá el mosaico",
                    Toast.LENGTH_LONG).show();
                // Only after Receiver verifies and reorders the exact
                // managed tasks. No Activity launch or forced termination.
                moveTaskToBack(true);
            }else if(current.startsWith("MOSAICO NO RECUPERADO:")||
                     current.startsWith("MOSAICO OCUPADO:")||
                     current.startsWith("NO SE ABRIÓ MOSAICO:")){
                revealPending=false;
                if(!isFinishing()&&!isDestroyed())
                    new AlertDialog.Builder(this)
                        .setTitle("NO SE MOSTRÓ EL MOSAICO")
                        .setMessage(current)
                        .setPositiveButton("ENTENDIDO",null).show();
            }
        });
    }
    private void persist(){
        SharedPreferences.Editor e=prefs().edit().putInt("mode",mode)
            .putInt("percent",percent)
            .putBoolean("cluster_enabled",clusterEnabled)
            .putBoolean("cluster_confirmed",clusterConfirmed)
            .putInt("cluster_count",clusterCount)
            .putInt("cluster_percent",clusterPercent)
            .putInt("caption_safe_dp",captionSafeDp);
        for(int i=0;i<3;i++)e.putString("pkg"+i,selected[i]);
        for(int i=0;i<2;i++)e.putString("cluster_pkg"+i,clusterApps[i]);
        e.apply();
    }
    private int count(){return mode==4?1:mode==3?2:3;}
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
        if(pkg.equals(BootConfig.RECEIVER))return "LEGADO · DriveLab Auto";
        try{
            PackageManager pm=getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg,0))
                .toString()+" · "+pkg;
        }catch(Exception e){return pkg;}
    }
    private static boolean safe(String pkg){
        if(pkg==null||!pkg.matches(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
            return false;
        String p=pkg.toLowerCase(java.util.Locale.ROOT);
        return !p.startsWith("android.")&&
            !p.startsWith("com.android.")&&
            !p.startsWith("com.autolink.")&&
            !p.startsWith("com.jetour.")&&
            !p.startsWith("com.chery.")&&
            !p.startsWith("com.t1.")&&
            !p.contains("launcher")&&
            !p.contains("cluster")&&
            !p.contains("vehicle")&&
            !p.contains("systemui");
    }
    private void chooseApp(int slot){
        PackageManager pm=getPackageManager();
        TreeMap<String,String> apps=new TreeMap<>(
            String.CASE_INSENSITIVE_ORDER);
        // Never offer the large DriveLab Auto Cockpit as a new map tile.
        Intent query=new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo info:pm.queryIntentActivities(query,0)){
            if(info.activityInfo==null)continue;
            String pkg=info.activityInfo.packageName;
            if(!safe(pkg))continue;
            try{
                ApplicationInfo app=pm.getApplicationInfo(pkg,0);
                if((app.flags&(ApplicationInfo.FLAG_SYSTEM|
                    ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)
                    continue;
                apps.put(info.loadLabel(pm)+" · "+pkg,pkg);
            }catch(Exception ignored){}
        }
        String[] labels=apps.keySet().toArray(new String[0]);
        String[] ids=apps.values().toArray(new String[0]);
        if(ids.length==0){
            new AlertDialog.Builder(this)
                .setMessage("No encontré aplicaciones instaladas para "+
                    "el mosaico. Instalá Receiver primero.")
                .setPositiveButton("OK",null).show();return;
        }
        new AlertDialog.Builder(this)
            .setTitle("APLICACIÓN · PANEL "+(slot+1))
            .setItems(labels,(d,which)->{
                selected[slot]=ids[which];
                persist();draw();
            })
            .setNegativeButton("CANCELAR",null).show();
    }
    private void chooseMode(){
        new AlertDialog.Builder(this).setTitle("DISTRIBUCIÓN")
            .setSingleChoiceItems(MODES,mode,(d,which)->{
                mode=which;persist();d.dismiss();draw();
            })
            .setNegativeButton("CANCELAR",null).show();
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
            "\nVENTANAS DE MAPAS: sin arranque independiente verificado. "+
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
                    "Autoinicio Bridge APAGADO. DriveLab Auto sigue apagado.")
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
        try{
            startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:"+getPackageName())));
        }catch(Exception ex){
            Toast.makeText(this,"Android no abrió el permiso: "+
                ex.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
        }
    }
    /** Explicitly opt in to option navigation, never zoom. This can
     * compete with T1Launcher's OEM hard-key callback, so owner approval
     * is required and Bridge never enables it during auto-start.
     */
    private void toggleWheel(){
        SharedPreferences p=BridgeWheelAccessibilityService.prefs(this);
        if(p.getBoolean(BridgeWheelAccessibilityService.ENABLED,false)){
            p.edit().putBoolean(BridgeWheelAccessibilityService.ENABLED,false)
                .putString(BridgeWheelAccessibilityService.STATUS,
                    "Volante Bridge desactivado por el propietario").apply();
            draw();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("VOLANTE · RECORRER OPCIONES DEL MAPA")
            .setMessage("La rueda avanza o retrocede entre opciones "+
                "visibles de Waze/Google Maps en Display 1. "+
                "OK confirma y ATRÁS cancela. NO hace zoom.\n\n"+
                "Antes de activarlo, desactivá VOLANTE → MAPA "+
                "en T1Launcher: el Binder OEM puede permitir "+
                "un solo controlador. Bridge no se activará "+
                "automáticamente al encender ni tocará el sistema Jetour.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                p.edit().putBoolean(
                    BridgeWheelAccessibilityService.ENABLED,true).apply();
                draw();
            }).show();
    }
    private void chooseWheelMap(){
        SharedPreferences p=BridgeWheelAccessibilityService.prefs(this);
        String current=p.getString(BridgeWheelAccessibilityService.TARGET,
            BridgeWheelAccessibilityService.MAPS);
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
        new AlertDialog.Builder(this)
            .setTitle("PANTALLA DEL TABLERO · MAPA")
            .setSingleChoiceItems(new String[]{"Google Maps","Waze"},
                BridgeWindowsPolicy.WAZE.equals(current)?1:0,
                (d,choice)->{
                    BridgeStandaloneWindows.prefs(this).edit()
                        .putString(BridgeStandaloneWindows.TAB_MAP,
                            choice==0?BridgeWindowsPolicy.MAPS:
                            BridgeWindowsPolicy.WAZE)
                        .putBoolean(BridgeStandaloneWindows.TAB_CONFIRMED,false)
                        .putBoolean(BridgeStandaloneWindows.TAB_ROUTE,false)
                        .apply();
                    d.dismiss();draw();
                })
            .setNegativeButton("CANCELAR",null).show();
    }
    private void confirmTableroMap(){
        String prior=BridgeStandaloneWindows.prefs(this).getString(
            BridgeStandaloneWindows.LAST,"");
        if(!prior.startsWith("Display 1: 1 solicitudes")){
            new AlertDialog.Builder(this).setTitle("MAPA SIN PRUEBA RECIENTE")
                .setMessage("Primero solicitá el mapa desde Bridge. "+
                    "Una respuesta de Android no prueba que sea visible.")
                .setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("CONFIRMAR MAPA VISIBLE EN DISPLAY 1")
            .setMessage("Confirmá únicamente si ves Waze o Google Maps "+
                "sobre la pantalla del tablero sin que desaparezcan "+
                "las alertas importantes de Jetour. Esta confirmación "+
                "caduca en el próximo arranque y al cambiar el mapa.")
            .setNegativeButton("TODAVÍA NO",null)
            .setPositiveButton("SÍ, SE VE",(d,w)->{
                BridgeStandaloneWindows.prefs(this).edit()
                    .putBoolean(BridgeStandaloneWindows.TAB_CONFIRMED,true)
                    .apply();
                draw();
            }).show();
    }
    private void toggleSenderTablero(){
        SharedPreferences p=BridgeStandaloneWindows.prefs(this);
        boolean enabled=p.getBoolean(BridgeStandaloneWindows.TAB_ROUTE,false);
        if(enabled){
            p.edit().putBoolean(BridgeStandaloneWindows.TAB_ROUTE,false).apply();
            draw();return;
        }
        if(!p.getBoolean(BridgeStandaloneWindows.TAB_CONFIRMED,false)){
            new AlertDialog.Builder(this).setTitle("TABLERO SIN CONFIRMACIÓN")
                .setMessage("La ruta del Xiaomi seguirá yendo a la "+
                    "pantalla central hasta que confirmes que el mapa "+
                    "de Bridge está visible en Display 1.")
                .setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("XIAOMI SENDER → DISPLAY 1")
            .setMessage("Las rutas enviadas desde Xiaomi a "+
                labelFor(BridgeStandaloneWindows.tabMap(this))+
                " se solicitarán en Display 1. Las rutas al otro "+
                "navegador siguen en la pantalla central. "+
                "No se cambia tamaño ni se reinician mapas al "+
                "recibir destinos. ¿Activar esta preferencia?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                p.edit().putBoolean(BridgeStandaloneWindows.TAB_ROUTE,true)
                    .apply();draw();
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
    private void toggleLegacy(){
        if(legacyAdvanced){legacyAdvanced=false;draw();return;}
        new AlertDialog.Builder(this)
            .setTitle("EXPERIMENTOS ANTIGUOS · NO VERIFICADOS")
            .setMessage("Este módulo todavía utiliza el Receiver "+
                "de DriveLab Auto para ventanas. No debe usarse "+
                "al encender. Sólo para diagnóstico con vehículo "+
                "estacionado, sin tocar procesos OEM.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ABRIR",(d,w)->{
                legacyAdvanced=true;draw();
            }).show();
    }
    /** Opening Display 0 is a separate, explicit action. If Cockpit is a
     * chosen tile, seeing Receiver in that tile is expected, not a Boot menu.
     */
    private void applyCenter(){
        persist();
        try{
            BootConfig.assertReceiver(this);
            java.util.HashSet<String> unique=new java.util.HashSet<>();
            for(int i=0;i<count();i++){
                if((!safe(selected[i])&&
                    !BootConfig.RECEIVER.equals(selected[i]))||
                    !unique.add(selected[i]))
                    throw new IllegalStateException(
                        "Display 0 · Panel "+(i+1)+": elegí apps distintas y válidas");
            }
            BootConfig.sendCenterOnly(this);
            Toast.makeText(this,
                "Preparación solicitada; comprobá el estado abajo. "+
                "No se confirmó aún la visibilidad.",
                Toast.LENGTH_LONG).show();
        }catch(Exception error){
            new AlertDialog.Builder(this)
                .setTitle("DISPLAY 0 · NO ENVIADO")
                .setMessage(error.getMessage())
                .setPositiveButton("CERRAR",null).show();
        }
    }
    /** Reveal ONLY already-created Freeform tasks; never a force-stop. */
    private void showExistingMosaic(){
        try{
            revealPending=true;
            BootConfig.revealCenter(this);
        }catch(Exception failure){
            revealPending=false;
            new AlertDialog.Builder(this)
                .setTitle("NO SE PUDO SOLICITAR EL MOSAICO")
                .setMessage(failure.getMessage())
                .setPositiveButton("ENTENDIDO",null).show();
        }
    }
    /** This does not open the Receiver menus or alter Display 0 tasks. */
    private void applyCluster(){
        persist();
        try{
            if(clusterCount==2&&clusterApps[0].equals(clusterApps[1]))
                throw new IllegalStateException(
                    "Display 1 · Elegí Waze y Google Maps, no dos iguales");
            BootConfig.sendClusterOnly(this);
            new AlertDialog.Builder(this)
                .setTitle("DISPLAY 1 · PRUEBA ENVIADA")
                .setMessage("Sólo se solicitó el clúster, sin cambiar "+
                    "las ventanas de la pantalla central ni salir de "+
                    "DriveLab Boot. Comprobá visualmente Waze/Maps "+
                    "en el tablero con el vehículo detenido. "+
                    "Después tocá ACTUALIZAR DIAGNÓSTICO.")
                .setPositiveButton("ENTENDIDO",null).show();
        }catch(Exception error){
            new AlertDialog.Builder(this)
                .setTitle("DISPLAY 1 · NO ENVIADO")
                .setMessage(error.getMessage())
                .setPositiveButton("CERRAR",null).show();
        }
    }
    private String packageVersion(String pkg){
        try{
            return getPackageManager().getPackageInfo(pkg,0).versionName;
        }catch(Exception ignored){return "NO INSTALADO";}
    }
    private void navigation(){
        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<2;i++){
            final int tab=i;
            Button b=new Button(this);
            b.setAllCaps(false);
            b.setText(tab==0?"CENTRAL · 0":"CLÚSTER · 1");
            b.setTextSize(17);
            b.setTextColor(WHITE);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                activeDisplay==tab?0xFF096D82:0xFF293D4A));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT,1f);
            lp.setMargins(3,3,3,6);
            tabs.addView(b,lp);
            b.setOnClickListener(v->{activeDisplay=tab;draw();});
        }
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
        text("No abre la pantalla DriveLab Auto. No reemplaza "+
            "el firmware ni los avisos del Jetour.",13,QUIET);
        text("XIAOMI · CONEXIÓN DIRECTA",18,WHITE);
        button("INICIAR RECEPTOR XIAOMI",()->
            startLink(BootLinkService.START));
        button("VINCULAR POR QR · MOSTRAR CÓDIGO",this::showQr);
        button("CERRAR BRIDGE · APAGAR ENLACE Y SALIR",
            this::stopBridge);
        text("Para recibir destinos con Bridge oculto, Android "+
            "puede requerir autorización de ventanas superpuestas. "+
            "El permiso no garantiza que Jetour muestre mapas.",12,QUIET);
        button(Settings.canDrawOverlays(this)?
            "PERMISO VENTANAS CONCEDIDO · REVISAR":
            "AUTORIZAR VENTANAS EN SEGUNDO PLANO",this::requestOverlay);
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
        button("MAPA DEL TABLERO · "+
            labelFor(BridgeStandaloneWindows.tabMap(this))+
            " · CAMBIAR",this::chooseBridgeMap);
        button("PROBAR MAPA TABLERO · ESTACIONADO",
            ()->askBridgeWindows(true));
        SharedPreferences windows=BridgeStandaloneWindows.prefs(this);
        boolean visibleTab=windows.getBoolean(
            BridgeStandaloneWindows.TAB_CONFIRMED,false);
        boolean routesTab=windows.getBoolean(
            BridgeStandaloneWindows.TAB_ROUTE,false);
        button(visibleTab?"MAPA DISPLAY 1 · CONFIRMADO VISUALMENTE":
            "CONFIRMAR: MAPA SE VE EN DISPLAY 1",
            this::confirmTableroMap);
        button(routesTab?
            "SENDER → TABLERO: ACTIVADO · APAGAR":
            "SENDER → TABLERO: DESACTIVADO · ACTIVAR",
            this::toggleSenderTablero);
        text("El modo RELOJES, el desvanecido y la posición "+
            "permanente siguen en desarrollo. Todavía no se "+
            "garantiza cubrir la interfaz MODE ni preservar "+
            "dos mapas en Display 1.",12,QUIET);
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
            BridgeWheelAccessibilityService.MAPS);
        button("MAPA DEL VOLANTE: "+
            (BridgeWheelAccessibilityService.WAZE.equals(wheelTarget)?
                "WAZE":"GOOGLE MAPS")+" · CAMBIAR",this::chooseWheelMap);
        button(wheelOn?"VOLANTE OPCIONES: ACTIVADO · DESACTIVAR":
            "VOLANTE OPCIONES: DESACTIVADO · ACTIVAR",this::toggleWheel);
        button("RECONECTAR ACCESIBILIDAD · ADB LOCAL · SIN TERMUX",
            ()->BridgeWheelAccessRecovery.ask(this));
        wheelRecoveryLabel=text(
            BridgeWheelAccessRecovery.describe(this),13,WHITE);
        button("AJUSTES ACCESIBILIDAD ANDROID · OPCIONAL",
            this::openWheelPermission);
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
        text("ACTUALIZADOR DE BRIDGE",18,WHITE);
        button(getPackageName().endsWith(".lab")?
            "BUSCAR ACTUALIZACIÓN FIRMADA · LAB OTA":
            "BUSCAR ACTUALIZACIÓN FIRMADA · BOOT ESTABLE",()->
            BootUpdater.check(this,s->prefs().edit()
                .putString("last_status",s).apply()));
        text("VENTANAS DISPLAY 0 Y DISPLAY 1",18,WHITE);
        text("El motor Freeform de Bridge está separado de Receiver. "+
            "Su disponibilidad real depende de Android Jetour. "+
            "No abre DriveLab Auto, no fuerza cierres ni promete "+
            "visibilidad sin inspección física.",13,QUIET);
        button(legacyAdvanced?"CERRAR EXPERIMENTOS ANTIGUOS":
            "ABRIR EXPERIMENTOS ANTIGUOS · REQUIERE RECEIVER",
            this::toggleLegacy);
        if(legacyAdvanced){
            navigation();
        text("DIAGNÓSTICO MODE · FIRMWARE JETOUR",17,WHITE);
        button("INSPECCIONAR CAPAS EN EL TABLERO · SOLO LECTURA",
            ()->startActivity(
                new Intent(this,BridgeLayersActivity.class)));

        if(activeDisplay==0){
            text("DISPLAY 0 · PANTALLA CENTRAL",20,WHITE);
            text("Elegí las apps que aparecerán juntas. "+
                "No hace falta entrar al menú Mosaico del Receiver.",13,QUIET);
            button("DISEÑO · "+MODES[mode],this::chooseMode);
            for(int i=0;i<count();i++){
                final int slot=i;
                button("PANEL "+(slot+1)+" · "+labelFor(selected[slot]),
                    ()->chooseApp(slot));
            }
            if(mode!=4){
                TextView ratio=text("Reparto principal: "+percent+
                    " % / "+(100-percent)+" %",15,WHITE);
                SeekBar slider=new SeekBar(this);
                slider.setMin(1);slider.setMax(99);slider.setProgress(percent);
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    @Override public void onProgressChanged(SeekBar b,int v,boolean user){
                        percent=v;
                        ratio.setText("Reparto principal: "+v+
                            " % / "+(100-v)+" %");
                        if(user)persist();
                    }
                    @Override public void onStartTrackingTouch(SeekBar b){}
                    @Override public void onStopTrackingTouch(SeekBar b){persist();}
                });
                content.addView(slider);
            }
            TextView caption=text("BARRA GRIS · MARGEN INTERIOR: "+
                captionSafeDp+" dp",15,WHITE);
            SeekBar safeSeek=new SeekBar(this);
            safeSeek.setMax(88);safeSeek.setProgress(captionSafeDp);
            safeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(SeekBar b,int v,boolean user){
                    captionSafeDp=v;
                    caption.setText("BARRA GRIS · MARGEN INTERIOR: "+v+" dp");
                    if(user)persist();
                }
                @Override public void onStartTrackingTouch(SeekBar b){}
                @Override public void onStopTrackingTouch(SeekBar b){persist();}
            });
            content.addView(safeSeek);
            text("Este margen protege los botones propios de DriveLab. "+
                "No elimina las barras de Android o Jetour.",12,QUIET);
            button("▶ MOSTRAR MOSAICO · RECUPERAR VENTANAS",
                this::showExistingMosaic);
            button("PREPARAR / CAMBIAR DISTRIBUCIÓN",this::applyCenter);
            text("MOSTRAR: trae al frente las tareas existentes que "+
                "DriveLab pueda verificar. PREPARAR: crea/redimensiona "+
                "solamente cuando cambiás el diseño. Nunca hace "+
                "Forzar detención ni borra datos.",12,QUIET);
        }else{
            text("DISPLAY 1 · CLÚSTER / MAPAS",20,WHITE);
            text("Elegí Waze y Google Maps ACÁ. No hace falta abrir "+
                "la pantalla «PROBAR» del Receiver.",13,QUIET);
            button(clusterCount==1?
                "DISEÑO: 1 MAPA PANTALLA COMPLETA · CAMBIAR":
                "DISEÑO: 2 MAPAS DIVIDIDOS · CAMBIAR",
                ()->{clusterCount=clusterCount==1?2:1;
                    clusterConfirmed=false;persist();draw();});
            for(int i=0;i<clusterCount;i++){
                final int slot=i;
                String choice="com.waze".equals(clusterApps[i])?
                    "WAZE":"GOOGLE MAPS";
                button("ELEGIR MAPA "+(slot+1)+" · "+choice,
                    ()->new AlertDialog.Builder(this)
                        .setTitle("DISPLAY 1 · MAPA "+(slot+1))
                        .setItems(new String[]{"WAZE","GOOGLE MAPS"},
                            (d,which)->{
                                clusterApps[slot]=which==0?
                                    "com.waze":"com.google.android.apps.maps";
                                clusterConfirmed=false;persist();draw();
                            })
                        .setNegativeButton("CANCELAR",null).show());
            }
            if(clusterCount==2){
                TextView split=text("DIVISIÓN: "+clusterPercent+" % / "+
                    (100-clusterPercent)+" %",15,WHITE);
                SeekBar slider=new SeekBar(this);
                slider.setMin(30);slider.setMax(70);
                slider.setProgress(clusterPercent);
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    @Override public void onProgressChanged(SeekBar b,int v,boolean user){
                        clusterPercent=v;
                        if(user)clusterConfirmed=false;
                        split.setText("DIVISIÓN: "+v+" % / "+(100-v)+" %");
                        if(user)persist();
                    }
                    @Override public void onStartTrackingTouch(SeekBar b){}
                    @Override public void onStopTrackingTouch(SeekBar b){persist();}
                });
                content.addView(slider);
            }
            button("▶ PROBAR SOLO DISPLAY 1 · SIN SALIR DE BOOT",
                this::applyCluster);
            text("La prueba requiere que Receiver ya tenga identificado "+
                "el Display 1 y detecte 0 km/h. Si no, no moverá "+
                "ventanas y lo indicará el diagnóstico.",12,QUIET);
            button(clusterConfirmed?
                "CONFIGURACIÓN COMPROBADA · RECONFIRMAR":
                "CONFIRMAR DISEÑO DEL CLÚSTER TRAS VERLO",
                ()->new AlertDialog.Builder(this)
                    .setTitle("CONFIRMACIÓN VISUAL")
                    .setMessage("¿Viste físicamente Waze y/o Google Maps "+
                        "en Display 1, con el vehículo estacionado "+
                        "y sin tapar alertas ni instrumentos?")
                    .setNegativeButton("TODAVÍA NO",null)
                    .setPositiveButton("SÍ, CONFIRMADO",(d,w)->{
                        clusterConfirmed=true;persist();draw();
                    }).show());
            button(clusterEnabled?
                "GUARDADO DISPLAY 1: SÍ · CAMBIAR":
                "GUARDADO DISPLAY 1: NO · CAMBIAR",
                ()->{
                    clusterEnabled=!clusterEnabled;
                    if(!clusterEnabled)clusterConfirmed=false;
                    persist();draw();
                });
            text(clusterEnabled?
                "Preferencia guardada para pruebas avanzadas. "+
                    "BRIDGE TODAVÍA NO AUTOINICIA VENTANAS.":
                "Display 1 sólo se puede ensayar manualmente "+
                    "desde la sección LEGADA. Nunca al encender.",12,QUIET);
        }
        } // Las configuraciones antiguas sólo se abren por decisión explícita.
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
