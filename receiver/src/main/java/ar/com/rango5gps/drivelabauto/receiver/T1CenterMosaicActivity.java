package ar.com.rango5gps.drivelabauto.receiver;

import android.app.AlertDialog;
import android.app.ActivityOptions;
import android.graphics.Rect;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;
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
    private final String[] names={"DriveLab Auto","Spotify","DriveLab Uber Beta"};
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
    /** Opt-in best-effort real multiwindow launch on the CENTER display only.
     * Android may ignore launch bounds if OEM freeform is disabled.
     * No ADB, root, shell, cluster, HOME replacement or OEM writes.
     */
    private String windowCapabilities(){
        PackageManager pm=getPackageManager();
        boolean freeform=pm.hasSystemFeature("android.software.freeform_window_management");
        boolean pip=pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
        boolean secondary=pm.hasSystemFeature("android.software.activities_on_secondary_displays");
        boolean multi=isInMultiWindowMode();
        return "Freeform declarado: "+(freeform?"SÍ":"NO")+
            "\\nPicture-in-Picture: "+(pip?"SÍ":"NO")+
            "\\nActividades en displays secundarios: "+(secondary?"SÍ":"NO")+
            "\\nDriveLab está en multiwindow: "+(multi?"SÍ":"NO")+
            "\\nAndroid "+android.os.Build.VERSION.RELEASE+
            " · API "+android.os.Build.VERSION.SDK_INT+
            "\\nNota: estas capacidades no garantizan ventanas de apps externas.";
    }
    private void diagnoseWindows(){
        String result=windowCapabilities();
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString("window_capabilities",result).apply();
        new AlertDialog.Builder(this).setTitle("CAPACIDADES DE VENTANAS")
            .setMessage(result+"\\n\\nLa prueba anterior abrió tres apps a pantalla completa: "+
                "no hay evidencia de que ActivityOptions permita mosaico en esta T1.")
            .setPositiveButton("ENTENDIDO",null).show();
    }
    private void applyOnCenter(){
        if(!getPackageManager().hasSystemFeature("android.software.freeform_window_management")){
            new AlertDialog.Builder(this).setTitle("MOSAICO NATIVO NO DISPONIBLE")
                .setMessage("El sistema no declara soporte freeform. La prueba anterior abrió las tres aplicaciones a pantalla completa. No volveré a abrirlas como si fueran un mosaico. Revisá DIAGNOSTICAR VENTANAS para conocer las capacidades de Android.")
                .setPositiveButton("ENTENDIDO",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("PROBAR MOSAICO EN PANTALLA CENTRAL")
            .setMessage("Se intentará abrir las apps elegidas en ventanas "+
                "del Display 0 con los tamaños guardados. Android 11 puede "+
                "ignorar los límites si Jetour deshabilitó freeform. "+
                "No se toca el clúster ni el HOME original. "+
                "Para volver, abrí DriveLab desde su icono. "+
                "¿Abrir la distribución guardada? Si estás conduciendo, usá solo controles simples y evitá configurar ventanas.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("APLICAR",(d,w)->launchCenterTiles())
            .show();
    }
    private void launchCenterTiles(){
        try{
            // Everyday launch is not an OEM operation. No Vehicle Service
            // query: missing mirror/speed Binder must not block saved apps.
            // Keep editing and dragging as separate deliberate actions.
            android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            int w=dm.widthPixels,h=dm.heightPixels;
            T1CenterMosaicGeometry.Tile[] tiles=
                T1CenterMosaicGeometry.layout(w,h,mode,percent);
            int count=mode==3?2:3,launched=0;
            // Launch external apps first; never relaunch DriveLab itself
            // from the same activity or accidentally start OEM HOME.
            for(int i=count-1;i>=0;i--){
                String pkg=packages[i];
                if(pkg==null||pkg.isEmpty()||pkg.equals(getPackageName()))
                    continue;
                Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
                if(intent==null)continue;
                T1CenterMosaicGeometry.Tile tile=tiles[i];
                ActivityOptions options=ActivityOptions.makeBasic();
                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                options.setLaunchBounds(new Rect(tile.left,tile.top,
                    tile.right,tile.bottom));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(intent,options.toBundle());
                launched++;
            }
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString("last_apply",launched==0?
                    "SIN APPS EXTERNAS: elegí Spotify/Maps/Waze":
                    "SOLICITADO: "+launched+" apps. Confirmá visualmente "+
                    "si Android respetó las ventanas; no está verificado.")
                .apply();
            Toast.makeText(this,launched==0?
                "No hay aplicaciones externas seleccionadas":
                "Ventanas solicitadas. Comprobá si se dividió la pantalla.",
                Toast.LENGTH_LONG).show();
        }catch(Exception ex){
            new AlertDialog.Builder(this).setTitle("MOSAICO NO APLICADO")
                .setMessage(ex.getClass().getSimpleName()+": "+
                    String.valueOf(ex.getMessage()))
                .setPositiveButton("OK",null).show();
        }
    }
    private void applyAdbOnCenter(){
        int count=mode==3?2:3;
        for(int i=0;i<count;i++){
            if(!T1CenterAdbResize.allowed(packages[i])){
                new AlertDialog.Builder(this).setTitle("MOSAICO ADB")
                    .setMessage("La primera prueba ADB admite Waze, Google Maps "+
                        "y Spotify, sin repetir aplicaciones. El panel DriveLab "+
                        "y otras apps necesitan integración adicional.")
                    .setPositiveButton("ENTENDIDO",null).show();
                return;
            }
        }
        new AlertDialog.Builder(this).setTitle("REDIMENSIONAR DISPLAY 0")
            .setMessage("Usaremos el puente ADB local del clúster, pero en "+
                "la pantalla central (Display 0). Primero se abrirán las "+
                "aplicaciones y luego se buscarán sus tareas para aplicar "+
                "los tamaños guardados. Requiere velocidad 0 km/h confirmada "+
                "durante la configuración. ¿Probar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR",(d,w)->{
                launchCenterTiles();
                android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                T1CenterMosaicGeometry.Tile[] tiles=T1CenterMosaicGeometry.layout(
                    dm.widthPixels,dm.heightPixels,mode,percent);
                String[] selected=packages.clone();
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(()->T1CenterAdbResize.apply(this,selected,
                        tiles,count,result->{
                            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                                .putString("last_apply",result).apply();
                            new AlertDialog.Builder(this)
                                .setTitle("RESULTADO MOSAICO ADB")
                                .setMessage(result)
                                .setPositiveButton("OK",null).show();
                        }),1100L);
            }).show();
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
        text("PRUEBA EXPERIMENTAL: APLICAR solicita ventanas reales en "+
            "Display 0 mediante Android ActivityOptions. Jetour puede ignorar "+
            "sus posiciones si freeform está deshabilitado. No usa ADB ni "+
            "modifica el clúster. Verificá visualmente el resultado.",13);
        text("ÚLTIMA PRUEBA · "+getSharedPreferences(PREFS,MODE_PRIVATE)
            .getString("last_apply","Nunca aplicado"),13);
        button("DIAGNOSTICAR VENTANAS DE ANDROID",this::diagnoseWindows);
        button("PROBAR MOSAICO REAL · ADB DISPLAY 0",this::applyAdbOnCenter);
        button("PROBAR APLICAR EN PANTALLA CENTRAL",this::applyOnCenter);
        button("GUARDAR CONFIGURACIÓN",()->{
            save();new AlertDialog.Builder(this).setMessage(
                "Distribución guardada. No se modificaron las ventanas.")
                .setPositiveButton("OK",null).show();
        });
    }
}
