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
        if(content!=null)draw();
        onSharedPreferenceChanged(prefs(),"last_status");
    }
    @Override protected void onPause(){
        bridgeUiVisible=false;
        prefs().unregisterOnSharedPreferenceChangeListener(this);
        linkPrefs().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p,
            String key){
        if(p==linkPrefs()){
            if("qr_ticket".equals(key)||"qr_until".equals(key)||
               "pair_status".equals(key)||"status".equals(key)||
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
            qrCaption.setText(linkPrefs().getString("pair_status",
                "Creando QR (Jetour y Xiaomi en la misma red)…"));
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
                draw();
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
        button("DETENER RECEPTOR Y SU AUTOINICIO",this::stopBridge);
        text("Para recibir destinos con Bridge oculto, Android "+
            "puede requerir autorización de ventanas superpuestas. "+
            "El permiso no garantiza que Jetour muestre mapas.",12,QUIET);
        button(Settings.canDrawOverlays(this)?
            "PERMISO VENTANAS CONCEDIDO · REVISAR":
            "AUTORIZAR VENTANAS EN SEGUNDO PLANO",this::requestOverlay);
        text("ACTUALIZADOR DE BRIDGE",18,WHITE);
        button("BUSCAR ACTUALIZACIÓN FIRMADA",()->
            BootUpdater.check(this,s->prefs().edit()
                .putString("last_status",s).apply()));
        text("VENTANAS DISPLAY 0 Y DISPLAY 1",18,WHITE);
        text("Mosaico independiente, modo reloj, transparencia "+
            "y rueda: todavía sin comprobación física. "+
            "No se lanza DriveLab Auto para simular éxito.",13,QUIET);
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
                "AUTOBOOT INCLUYE CLÚSTER: SÍ · CAMBIAR":
                "AUTOBOOT INCLUYE CLÚSTER: NO · CAMBIAR",
                ()->{
                    clusterEnabled=!clusterEnabled;
                    if(!clusterEnabled)clusterConfirmed=false;
                    persist();draw();
                });
            text(clusterEnabled?
                "Display 1 se incluirá en el próximo encendido SOLO "+
                    "si su diseño fue confirmado.":
                "Display 1 puede probarse manualmente, pero no "+
                    "se abrirá con Boot al encender.",12,QUIET);
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
