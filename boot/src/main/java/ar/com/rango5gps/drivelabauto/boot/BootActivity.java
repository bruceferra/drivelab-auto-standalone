package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
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

/** Standalone DriveLab Boot: one touchscreen config page, not an OEM HOME.
 * The receiver app stays installed, owns ADB and its OEM/control services.
 */
public final class BootActivity extends Activity {
    private static final String[] MODES={
        "Principal izquierda + 2 a la derecha",
        "Principal arriba + 2 abajo",
        "Tres columnas",
        "Dos columnas",
        "Una aplicación (100 % de la zona útil)"};
    private int mode,percent;
    private int activeDisplay=0;
    private final String[] selected=new String[3];
    private boolean clusterEnabled,clusterConfirmed;
    private int clusterCount,clusterPercent,captionSafeDp;
    private final String[] clusterApps=new String[2];
    private LinearLayout content;
    private TextView percentLabel;
    private static final int BG=0xFF101B25,PANEL=0xFF23313C,
        WHITE=Color.WHITE,QUIET=0xFFBDCED8;
    private SharedPreferences prefs(){return BootConfig.prefs(this);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        SharedPreferences p=prefs();
        mode=Math.min(4,Math.max(0,p.getInt("mode",3)));
        percent=Math.min(99,Math.max(1,p.getInt("percent",50)));
        for(int i=0;i<3;i++)
            selected[i]=p.getString("pkg"+i,
                i==0?BootConfig.RECEIVER:i==1?"com.spotify.music":"");
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
        if(content!=null)draw();
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
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(5,3,5,3);
        content.addView(b,lp);
        b.setOnClickListener(v->action.run());
    }
    private String labelFor(String pkg){
        if(pkg==null||pkg.isEmpty())return "SIN ELEGIR";
        if(pkg.equals(BootConfig.RECEIVER))return "DriveLab T1 · Cockpit";
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
        try{pm.getPackageInfo(BootConfig.RECEIVER,0);
            apps.put("DriveLab T1 · Cockpit",BootConfig.RECEIVER);
        }catch(Exception ignored){}
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
    private void setAuto(){
        boolean current=BootConfig.enabled(this);
        if(current){
            prefs().edit().putBoolean("enabled",false).apply();
            try{BootConfig.takeOver(this,false);}catch(Exception ignored){}
            draw();return;
        }
        if(clusterEnabled&&!clusterConfirmed){
            new AlertDialog.Builder(this)
                .setTitle("PRIMERO PROBAR DISPLAY 1")
                .setMessage("Aplicá el diseño con el vehículo detenido y "+
                    "confirmá visualmente Waze/Maps en Display 1 antes de "+
                    "habilitar el autoinicio de ambas pantallas.")
                .setPositiveButton("ENTENDIDO",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("ACTIVAR AL ENCENDER EL T1")
            .setMessage("DriveLab Boot intentará abrir Display 0 y, si "+
                "lo habilitaste, el diseño de navegación de Display 1 "+
                "después de que Android termine de arrancar. "+
                "El autoinicio independiente anterior del clúster se "+
                "desactivará para evitar tareas duplicadas. No reemplaza "+
                "T1 Launcher ni los indicadores OEM. "+
                "Receiver debe estar instalado con ADB autorizado. "+
                "Si Jetour bloquea el autoinicio, podrás aplicarlo "+
                "manualmente. ¿Activar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                try{
                    persist();
                    BootConfig.takeOver(this);
                    prefs().edit().putBoolean("enabled",true).apply();
                    draw();
                    Toast.makeText(this,"DriveLab Boot configurado. "+
                        "El arranque anterior de Cockpit queda desactivado; "+
                        "los servicios Receiver siguen encendidos.",
                        Toast.LENGTH_LONG).show();
                }catch(Exception ex){
                    new AlertDialog.Builder(this)
                        .setTitle("NO SE ACTIVÓ AUTOINICIO")
                        .setMessage(ex.getMessage())
                        .setPositiveButton("OK",null).show();
                }
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
            new AlertDialog.Builder(this)
                .setTitle("DISPLAY 0 · MOSAICO SOLICITADO")
                .setMessage("Se configuró solamente la pantalla central. "+
                    "Si elegiste DriveLab Cockpit en un panel, esa ventana "+
                    "ES Receiver, pero Boot sigue siendo tu configuración. "+
                    "¿Querés ocultar este menú y ver el mosaico ahora?")
                .setNegativeButton("SEGUIR CONFIGURANDO",null)
                .setPositiveButton("MOSTRAR MOSAICO",
                    (d,w)->moveTaskToBack(true)).show();
        }catch(Exception error){
            new AlertDialog.Builder(this)
                .setTitle("DISPLAY 0 · NO ENVIADO")
                .setMessage(error.getMessage())
                .setPositiveButton("CERRAR",null).show();
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

        text("DRIVELAB BOOT · CONFIGURACIÓN",24,WHITE);
        text("BOOT "+packageVersion(getPackageName())+
            "   |   RECEIVER "+packageVersion(BootConfig.RECEIVER),14,QUIET);
        text("Son DOS apps distintas. Esta pantalla es Boot; Receiver "+
            "se usa sólo como motor de las ventanas.",13,QUIET);
        navigation();

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
            button("▶ ABRIR / PROBAR MOSAICO CENTRAL",this::applyCenter);
            button("VER VENTANAS YA ABIERTAS · OCULTAR BOOT",
                ()->moveTaskToBack(true));
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
        text("INICIO AUTOMÁTICO DE BOOT",17,WHITE);
        boolean enabled=BootConfig.enabled(this);
        button(enabled?"AUTOINICIO: ACTIVADO · DESACTIVAR":
            "AUTOINICIO: DESACTIVADO · ACTIVAR",this::setAuto);
        text("ESTADO / ERRORES DEL ÚLTIMO PEDIDO",17,WHITE);
        TextView status=text(prefs().getString("last_status",
            "Todavía no hay un diagnóstico"),13,QUIET);
        status.setBackgroundColor(PANEL);
        status.setTextIsSelectable(true);
        button("ACTUALIZAR DIAGNÓSTICO (SIN SALIR)",this::draw);
        setContentView(scroll);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
            View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
