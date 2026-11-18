package ar.com.rango5gps.drivelabauto.receiver;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Map;
import java.util.TreeMap;

/** Separate touch-screen layout draft. Does not alter cluster or OEM HOME. */
public final class T1CenterMosaicActivity extends T1ImmersiveActivity {
    private static final String PREFS="drivelab_center_mosaic_v1";
    private static final String[] MODES={"Principal izquierda + dos a derecha",
        "Principal arriba + dos abajo","Tres columnas","Dos columnas"};
    private static final String[] DEFAULTS={
        "ar.com.rango5gps.drivelabauto.receiver","com.spotify.music",""};
    private int mode=0,percent=50;
    private final String[] packages=new String[3];
    private final String[] names={"DriveLab","Spotify","Elegir aplicación"};
    private LinearLayout root;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        mode=Math.max(0,Math.min(3,p.getInt("mode",0)));
        percent=Math.max(30,Math.min(70,p.getInt("percent",50)));
        for(int i=0;i<3;i++){
            packages[i]=p.getString("pkg"+i,DEFAULTS[i]);
            names[i]=p.getString("name"+i,names[i]);
        }
        draw();
    }
    private void text(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setPadding(12,10,12,10);root.addView(t);
    }
    private void button(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());root.addView(b);
    }
    private void save(){
        SharedPreferences.Editor e=getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putInt("mode",mode).putInt("percent",percent);
        for(int i=0;i<3;i++)e.putString("pkg"+i,packages[i])
            .putString("name"+i,names[i]);
        e.apply();
    }
    private void choose(int slot){
        PackageManager pm=getPackageManager();
        Map<String,String> apps=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Intent query=new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo info:pm.queryIntentActivities(query,0)){
            if(info.activityInfo==null)continue;
            String pkg=info.activityInfo.packageName;
            apps.put(info.loadLabel(pm)+" · "+pkg,pkg);
        }
        if(apps.isEmpty()){text("No se encontraron aplicaciones visibles",14);return;}
        String[] labels=apps.keySet().toArray(new String[0]);
        String[] ids=apps.values().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Aplicación del panel "+(slot+1))
            .setItems(labels,(dialog,index)->{
                packages[slot]=ids[index];
                names[slot]=labels[index].split(" · ")[0];
                save();draw();
            }).setNegativeButton("CANCELAR",null).show();
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xFF101A23);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,14,18,28);scroll.addView(root);setContentView(scroll);
        text("MOSAICO · PANTALLA CENTRAL",23);
        text("Configuración independiente de Display 1. El táctil sigue disponible. "+
            "No modifica el HOME original ni reinicia los mapas del clúster.",14);
        for(int i=0;i<3;i++){
            final int slot=i;
            button("PANEL "+(i+1)+" · "+names[i],()->choose(slot));
        }
        button("DISTRIBUCIÓN · "+MODES[mode],()->
            new AlertDialog.Builder(this).setTitle("Elegir mosaico")
                .setSingleChoiceItems(MODES,mode,(d,choice)->{
                    mode=choice;save();d.dismiss();draw();
                }).setNegativeButton("CANCELAR",null).show());
        text("Tamaño del panel principal · "+percent+"%",16);
        SeekBar seek=new SeekBar(this);seek.setMin(30);seek.setMax(70);
        seek.setProgress(percent);root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                if(fromUser)percent=value;
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){save();draw();}
        });
        int w=600,h=300;
        T1CenterMosaicGeometry.Tile[] tiles=
            T1CenterMosaicGeometry.layout(w,h,mode,percent);
        StringBuilder preview=new StringBuilder("VISTA PREVIA · 600 × 300\n");
        for(int i=0;i<(mode==3?2:3);i++)
            preview.append("Panel ").append(i+1).append(" · ")
                .append(names[i]).append(" · ").append(tiles[i].bounds())
                .append("\n");
        text(preview.toString(),14);
        text("PREPARADO PARA PRUEBA EN VEHÍCULO DETENIDO. "+
            "Este configurador guarda aplicaciones y distribución, pero todavía "+
            "NO aplica ventanas reales: falta validar el modo freeform táctil "+
            "de Android 11 en Display 0. No se ejecutan comandos ADB a ciegas.",13);
        button("GUARDAR CONFIGURACIÓN",()->{
            save();new AlertDialog.Builder(this).setMessage(
                "Distribución guardada. No se modificaron las ventanas.")
                .setPositiveButton("OK",null).show();
        });
    }
}
