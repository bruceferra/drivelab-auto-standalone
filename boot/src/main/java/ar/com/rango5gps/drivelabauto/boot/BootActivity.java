package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
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
    private void apply(){
        persist();
        try{
            if(getPackageManager().checkSignatures(getPackageName(),
                BootConfig.RECEIVER)!=PackageManager.SIGNATURE_MATCH)
                throw new SecurityException(
                    "Primero instalá Receiver compatible, firmado igual");
            java.util.HashSet<String> unique=new java.util.HashSet<>();
            for(int i=0;i<count();i++){
                if(!safe(selected[i])&&
                    !BootConfig.RECEIVER.equals(selected[i]))
                    throw new IllegalStateException(
                        "Panel "+(i+1)+": seleccioná una app válida");
                if(!unique.add(selected[i]))
                    throw new IllegalStateException(
                        "Elegí aplicaciones distintas en cada panel");
            }
            if(clusterEnabled&&clusterCount==2&&
                clusterApps[0].equals(clusterApps[1]))
                throw new IllegalStateException(
                    "Display 1: elegí Waze y Google Maps, no dos iguales");
            if(BootConfig.enabled(this)&&clusterEnabled)
                BootConfig.takeOver(this,true);
            BootConfig.send(this);
            Toast.makeText(this,
                "Mosaico solicitado · Receiver usa el ADB existente",
                Toast.LENGTH_LONG).show();
            // A fullscreen settings Activity can hide the actual
            // tiles. Move only Boot's own task behind them.
            moveTaskToBack(true);
        }catch(Exception e){
            new AlertDialog.Builder(this)
                .setTitle("NO SE ENVIÓ")
                .setMessage(e.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }
    private void reopen(){
        try{
            Intent target=new Intent(Intent.ACTION_VIEW);
            target.setComponent(new ComponentName(BootConfig.RECEIVER,
                BootConfig.RECEIVER+".T1MosaicShortcutActivity"));
            startActivity(target);
        }catch(Exception e){
            new AlertDialog.Builder(this)
                .setMessage("Primero instalá Receiver compatible. "+
                    "No se pudo recuperar el mosaico: "+
                    e.getClass().getSimpleName())
                .setPositiveButton("OK",null).show();
        }
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16,10,16,30);
        scroll.addView(content);
        text("DRIVELAB BOOT",26,WHITE);
        text("Administrador de pantallas: Display 0 y navegación opcional "+
            "en Display 1. Sin reemplazar HOME, clúster OEM ni avisos "+
            "del vehículo.",13,QUIET);
        text("DISPLAY 0 · PANTALLA CENTRAL",19,WHITE);
        button("DISTRIBUCIÓN · "+MODES[mode],this::chooseMode);
        for(int i=0;i<count();i++){
            final int index=i;
            button("PANEL "+(i+1)+" · "+labelFor(selected[i]),
                ()->chooseApp(index));
        }
        if(mode!=4){
            percentLabel=text("Panel principal: "+percent+" % / "+
                (100-percent)+" %",17,WHITE);
            SeekBar seek=new SeekBar(this);
            seek.setMin(1);seek.setMax(99);seek.setProgress(percent);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(SeekBar bar,int v,
                        boolean fromUser){
                    percent=v;
                    percentLabel.setText("Panel principal: "+percent+
                        " % / "+(100-percent)+" %");
                    if(fromUser)persist();
                }
                @Override public void onStartTrackingTouch(SeekBar bar){}
                @Override public void onStopTrackingTouch(SeekBar bar){
                    persist();
                }
            });
            content.addView(seek);
            button("VOLVER A 50 / 50",()->{
                percent=50;persist();draw();
            });
        }
        text("BARRA GRIS DE ANDROID · ÁREA SEGURA",18,WHITE);
        text("Freeform agrega un encabezado del sistema. Este ajuste "+
            "desplaza los controles propios de DriveLab para que no "+
            "queden debajo de maximizar; no elimina las barras OEM ni "+
            "las de aplicaciones externas.",12,QUIET);
        TextView captionLabel=text(
            "Separación superior de controles DriveLab: "+captionSafeDp+" dp",
            14,WHITE);
        SeekBar captionSeek=new SeekBar(this);
        captionSeek.setMax(88);captionSeek.setProgress(captionSafeDp);
        captionSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int v,
                    boolean fromUser){
                captionSafeDp=v;
                captionLabel.setText("Separación superior de controles DriveLab: "+
                    v+" dp");
                if(fromUser)persist();
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){persist();}
        });
        content.addView(captionSeek);
        text("DISPLAY 1 · CLÚSTER / NAVEGACIÓN",19,WHITE);
        button(clusterEnabled?
            "INCLUIR DISPLAY 1 EN AUTOBOOT: SÍ · DESACTIVAR":
            "INCLUIR DISPLAY 1 EN AUTOBOOT: NO · ACTIVAR",
            ()->{clusterEnabled=!clusterEnabled;clusterConfirmed=false;
                persist();draw();});
        if(clusterEnabled){
            text("Se reutiliza el Display 1 ya seleccionado y habilitado "+
                "en Receiver. Primero elegilo en CLÚSTER y probalo "+
                "con el vehículo detenido. No modifica agujas, "+
                "alertas ni servicios OEM.",12,QUIET);
            button(clusterCount==1?
                "CLÚSTER: UNA APP 100 % · CAMBIAR A DOS":
                "CLÚSTER: DOS APPS DIVIDIDAS · CAMBIAR A UNA",
                ()->{clusterCount=clusterCount==1?2:1;
                    clusterConfirmed=false;persist();draw();});
            for(int i=0;i<clusterCount;i++){
                final int slot=i;
                button("CLÚSTER PANEL "+(i+1)+" · "+
                    ("com.waze".equals(clusterApps[i])?"Waze":"Google Maps"),
                    ()->new AlertDialog.Builder(this)
                        .setTitle("NAVEGACIÓN DISPLAY 1 · PANEL "+(slot+1))
                        .setItems(new String[]{"Waze","Google Maps"},
                            (d,which)->{
                                clusterApps[slot]=which==0?
                                    "com.waze":"com.google.android.apps.maps";
                                clusterConfirmed=false;persist();draw();
                            })
                        .setNegativeButton("CANCELAR",null).show());
            }
            if(clusterCount==2){
                TextView clusterLabel=text(
                    "Clúster: "+clusterPercent+" % / "+
                    (100-clusterPercent)+" %",14,WHITE);
                SeekBar cs=new SeekBar(this);
                cs.setMin(30);cs.setMax(70);cs.setProgress(clusterPercent);
                cs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    @Override public void onProgressChanged(SeekBar bar,int v,
                            boolean fromUser){
                        clusterPercent=v;
                        if(fromUser)clusterConfirmed=false;
                        clusterLabel.setText("Clúster: "+v+" % / "+(100-v)+" %");
                        if(fromUser)persist();
                    }
                    @Override public void onStartTrackingTouch(SeekBar bar){}
                    @Override public void onStopTrackingTouch(SeekBar bar){persist();}
                });
                content.addView(cs);
            }
            button(clusterConfirmed?
                "DISPLAY 1 · VISUALIZACIÓN CONFIRMADA · VOLVER A PROBAR":
                "CONFIRMAR VISUALMENTE DISPLAY 1 · TRAS PRUEBA",
                ()->new AlertDialog.Builder(this)
                    .setTitle("CONFIRMACIÓN FÍSICA DEL CLÚSTER")
                    .setMessage("Solo confirmá si, estando estacionado, "+
                        "ya aplicaste este diseño y verificaste que Waze/Maps "+
                        "están visibles en el Display 1 correcto, sin tapar "+
                        "alertas ni instrumentos OEM. "+
                        "¿Habilitar este diseño para el próximo encendido?")
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("SÍ, SE VE BIEN",(d,w)->{
                        clusterConfirmed=true;persist();draw();
                    }).show());
            text(clusterConfirmed?
                "Diseño confirmado por el usuario. Queda pendiente "+
                "verificar cómo reaparece después de encender el Jetour.":
                "PENDIENTE: prueba física y confirmación para autoinicio "+
                "de Display 1.",12,QUIET);
        }
        button("▶ APLICAR PANTALLAS CONFIGURADAS",this::apply);
        button("▦ RECUPERAR VENTANAS ABIERTAS",this::reopen);
        boolean enabled=BootConfig.enabled(this);
        button(enabled?"AUTOINICIO AL ENCENDER: ACTIVADO · DESACTIVAR":
            "AUTOINICIO AL ENCENDER: DESACTIVADO · ACTIVAR",
            this::setAuto);
        text("Autoinicio: un intento por encendido, unos segundos "+
            "después de Android. Display 0 usa Receiver y ADB autorizado; "+
            "Display 1 usa el motor de navegación ya instalado. "+
            "Sin root, sin forzar detención ni reemplazar T1 Launcher.",12,QUIET);
        text("ÚLTIMO DIAGNÓSTICO",18,WHITE);
        String status=prefs().getString("last_status",
            "Sin lanzamientos realizados todavía");
        TextView diagnosis=text(status,13,QUIET);
        diagnosis.setBackgroundColor(PANEL);
        diagnosis.setTextIsSelectable(true);
        button("ACTUALIZAR DIAGNÓSTICO",this::draw);
        text("Spotify se ha probado a 33 %. YouTube puede rechazar "+
            "el modo Freeform (Illegal transform). "+
            "El modo inmersivo del lanzador no garantiza ocultar "+
            "las barras de Jetour dentro de otras apps.",12,QUIET);
        setContentView(scroll);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
