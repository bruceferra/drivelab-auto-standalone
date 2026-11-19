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
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last_apply",
                    "NO APLICADO: aplicación no admitida en panel "+(i+1)+": "+packages[i]).apply();
                new AlertDialog.Builder(this).setTitle("MOSAICO ADB")
                    .setMessage("Elegí aplicaciones instaladas y distintas. No se permiten "+
                        "procesos del sistema o del vehículo. Revisá la selección.")
                    .setPositiveButton("ENTENDIDO",null).show();
                return;
            }
        }
        new AlertDialog.Builder(this).setTitle("REDIMENSIONAR DISPLAY 0")
            .setMessage("Usaremos el puente ADB local del clúster, pero en "+
                "la pantalla central (Display 0). Primero se abrirán las "+
                "aplicaciones y luego se buscarán sus tareas para aplicar "+
                "los tamaños guardados. No toca tareas del clúster "+
                "ni controles OEM. ¿Probar?")
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
    private void diagnoseAdbOnCenter(){
        int count=mode==3?2:3;
        String[] selected=packages.clone();
        T1CenterAdbResize.diagnose(this,selected,count,result->{
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString("last_diagnostic",result).apply();
            new AlertDialog.Builder(this)
                .setTitle("DIAGNÓSTICO ADB · DISPLAY 0")
                .setMessage(result)
                .setPositiveButton("OK",null).show();
        });
    }
    private void testWindowModeOnCenter(){
        String pkg=packages[0];
        if(pkg==null||!T1CenterAdbResize.allowed(pkg)||
            pkg.equals(getPackageName())){
            new AlertDialog.Builder(this)
                .setTitle("PRUEBA DE MODO · DISPLAY 0")
                .setMessage("Seleccioná en Panel 1 una aplicación externa "+
                    "instalada por vos, por ejemplo ProManager. "+
                    "No se permiten DriveLab ni aplicaciones OEM.")
                .setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("PRUEBA REVERSIBLE · UNA APLICACIÓN")
            .setMessage("Con el vehículo detenido, abrí antes "+names[0]+
                " y volvé a DriveLab. Se comprobará su tarea única en "+
                "Display 0. Sólo si Android informa su resizeMode original "+
                "se probará temporalmente 'am task resizeable <ID> 2' "+
                "y un 50% de ancho; inmediatamente intentaremos restaurar "+
                "modo y tamaño originales. No cambia ajustes globales, "+
                "HOME, Display 1 ni tareas OEM. ¿Ejecutar la prueba?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("PROBAR",(dialog,which)->
                T1CenterWindowModeProbe.test(this,pkg,result->{
                    getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                        .putString("last_window_mode_probe",result).apply();
                    new AlertDialog.Builder(this)
                        .setTitle("RESULTADO · MODO DISPLAY 0")
                        .setMessage(result)
                        .setPositiveButton("OK",null).show();
                }))
            .show();
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
        text("DISPLAY 0 · Pantalla central. La prueba utiliza ADB y no modifica las tareas del clúster (Display 1).",13);
        String last=getSharedPreferences(PREFS,MODE_PRIVATE)
            .getString("last_apply","Nunca aplicado");
        String brief=last.split("\n",2)[0];
        text("ÚLTIMA PRUEBA · "+brief,13);
        button("VER DETALLE DE LA ÚLTIMA PRUEBA ADB",()->
            new AlertDialog.Builder(this).setTitle("ÚLTIMA PRUEBA ADB")
                .setMessage(last).setPositiveButton("OK",null).show());
        button("DIAGNOSTICAR VENTANAS DE ANDROID",this::diagnoseWindows);
        button("DIAGNOSTICAR TAREAS ADB · DISPLAY 0",this::diagnoseAdbOnCenter);
        button("PROBAR MODO DE VENTANA · DISPLAY 0",
            this::testWindowModeOnCenter);
        button("VER RESULTADO DE ÚLTIMA PRUEBA DE MODO",()->
            new AlertDialog.Builder(this).setTitle("ÚLTIMA PRUEBA · MODO")
                .setMessage(getSharedPreferences(PREFS,MODE_PRIVATE)
                    .getString("last_window_mode_probe",
                        "Aún no probaste el modo de ventana"))
                .setPositiveButton("OK",null).show());
        button("PROBAR MOSAICO REAL · ADB DISPLAY 0",this::applyAdbOnCenter);
        // Old ActivityOptions-only trial intentionally hidden: OEM ignored bounds.
        button("GUARDAR CONFIGURACIÓN",()->{
            save();new AlertDialog.Builder(this).setMessage(
                "Distribución guardada. No se modificaron las ventanas.")
                .setPositiveButton("OK",null).show();
        });
    }
}
