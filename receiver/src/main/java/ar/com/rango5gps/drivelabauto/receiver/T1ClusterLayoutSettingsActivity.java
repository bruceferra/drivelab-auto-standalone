package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.List;
import java.util.TreeMap;

/** Window geometry and physically verified OEM mic button controls.
 * These controls run on the center UI; no overlay is drawn over OEM dials.
 */
public final class T1ClusterLayoutSettingsActivity extends Activity {
    private LinearLayout column;
    private T1ClusterLayoutPreview preview;
    private TextView previewState;
    private boolean adbProbeRunning;
    private TextView adbPersistentStatus;
    private TextView adbBottomStatus;
    private void text(String value,int size){
        TextView view=new TextView(this);
        view.setText(value);view.setTextSize(size);
        view.setPadding(12,8,12,8);
        column.addView(view);
    }
    private void button(String name,Runnable action){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());column.addView(b);
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);render();
    }
    @Override protected void onResume(){super.onResume();render();}
    private SharedPreferences cfg(){return T1ClusterWazeRouter.prefs(this);}
    private void render(){
        ScrollView scroll=new ScrollView(this);
        column=new LinearLayout(this);
        column.setPadding(16,10,16,24);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);
        text("CLÚSTER · DIVISIÓN Y VENTANAS",22);
        text("Display seleccionado: "+cfg().getInt(
                T1ClusterWazeRouter.DISPLAY_ID,-1)+
            " · "+cfg().getString(T1ClusterWazeRouter.DISPLAY_NAME,"sin elegir")+
            "\nAplicación principal: "+
                T1ClusterWazeRouter.selectedAppLabel(this),15);
        text("T1Launcher T1N134 usa setLaunchBounds para abrir y "+
            "después ADB «am task resize» para ajustar la tarea REAL. "+
            "DriveLab ahora verifica los límites de Waze y Google Maps "+
            "en el Display elegido. Sin puente ADB autorizado se "+
            "conserva Waze pantalla completa.",13);
        boolean adb=T1ClusterAdbResize.enabled(this);
        adbPersistentStatus=new TextView(this);
        adbPersistentStatus.setTextSize(17);
        adbPersistentStatus.setPadding(12,12,12,12);
        column.addView(adbPersistentStatus);
        updateAdbStatus();
        button(adb?"DESACTIVAR PUENTE ADB LOCAL":
            "HABILITAR PUENTE ADB LOCAL (SIN ROOT)",
            this::toggleLocalAdb);
        button("PROBAR CONEXIÓN ADB LOCAL",this::probeLocalAdb);
        button("COPIAR RESULTADO ADB",this::copyAdbResult);
        text("Preparación una sola vez: conectá la T1 por USB a "+
            "una PC con ADB autorizado; ejecutá «adb tcpip 5555» "+
            "desde esa PC. Después aceptá en la T1 la clave de "+
            "depuración de DriveLab. Puede requerirse repetirlo "+
            "al reiniciar la T1. Usalo solo en una red de confianza "+
            "y apagá ADB TCP al terminar las pruebas.",12);
        String mode=T1ClusterLayoutPolicy.mode(this);
        boolean applied=T1ClusterLayoutPolicy.active(this);
        text("Modo elegido: "+("FULL".equals(mode)?"PANTALLA COMPLETA":
            "SPLIT".equals(mode)?"DOS APPS":"VENTANA AJUSTABLE")+
            "\nAplicar en navegación: "+(applied?"SÍ":"NO; modo seguro"),15);
        preview=new T1ClusterLayoutPreview(this);
        column.addView(preview,new LinearLayout.LayoutParams(-1,200));
        previewState=new TextView(this);
        previewState.setTextSize(13);
        previewState.setPadding(12,6,12,8);
        column.addView(previewState);
        updatePreview();
        button("PANTALLA COMPLETA · COMO AHORA",()->
            chooseMode(T1ClusterLayoutPolicy.MODE_FULL));
        button("UNA VENTANA · AJUSTAR TAMAÑO Y POSICIÓN",()->
            chooseMode(T1ClusterLayoutPolicy.MODE_WINDOW));
        button("DOS APPS · DIVIDIR ANCHO",()->
            chooseMode(T1ClusterLayoutPolicy.MODE_SPLIT));
        if(T1ClusterLayoutPolicy.MODE_WINDOW.equals(mode)){
            slider("Ancho ventana",T1ClusterLayoutPolicy.WIDTH,70,30,100,1);
            slider("Alto ventana",T1ClusterLayoutPolicy.HEIGHT,75,35,100,1);
            slider("Posición horizontal",T1ClusterLayoutPolicy.X,0,0,100,1);
            slider("Posición vertical",T1ClusterLayoutPolicy.Y,0,0,100,1);
        }
        if(T1ClusterLayoutPolicy.MODE_SPLIT.equals(mode)){
            boolean reversed=T1ClusterLayoutPolicy.inverted(this);
            button(reversed?"RESTAURAR PANELES · WAZE IZQ / GOOGLE DER":
                "INVERTIR PANELES · GOOGLE 40% IZQ / WAZE 60% DER",
                this::invertPanels);
            text("Orientación: "+(reversed?
                "Google Maps izquierda / Waze derecha":
                "Waze izquierda / Google Maps derecha"),15);
            slider("Porcentaje ventana izquierda · "+
                    (reversed?"GOOGLE MAPS":"WAZE"),
                T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70,1);
            button("−10 % IZQUIERDA / +10 % DERECHA",
                ()->adjustSplit(-10));
            button("+10 % IZQUIERDA / −10 % DERECHA",
                ()->adjustSplit(10));
            text("Google Maps queda a la izquierda y Waze a la derecha "+
                "al invertir. Ajuste real SOLO en P y 0 km/h; "+
                "verificá la imagen antes de confirmar el diseño.",14);
            button("ELEGIR SEGUNDA APP (GOOGLE MAPS)",this::chooseSecondApp);
        }
        button("PROBAR DISEÑO EN DISPLAY · SOLO EN P Y 0 KM/H",
            this::testLayout);
        if(!T1ClusterLayoutPolicy.MODE_FULL.equals(mode)){
            button("CONFIRMAR DISEÑO VISIBLE · ACTIVAR AJUSTE",()->
                new AlertDialog.Builder(this)
                    .setTitle("¿SE VEN LAS VENTANAS EN EL TABLERO?")
                    .setMessage("Activá solamente si acabás de verificar "+
                        "físicamente tamaño, posición y visibilidad de las "+
                        "apps en Display 1, con el auto detenido. "+
                        "Una solicitud Android sin error NO confirma imagen.")
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("LO COMPROBÉ",(d,w)->{
                        if(!T1ClusterLayoutPolicy.enableAfterTest(this))
                            T1ClusterWazeRouter.status(this,
                                "Primero probá el diseño actual, verificá "+
                                "Display 1 y activá destinos al clúster.");
                        else T1ClusterWazeRouter.status(this,
                            "Diseño ajustable habilitado por el conductor. "+
                            "Si falla, tocá RESTABLECER PANTALLA COMPLETA.");
                        render();
                    }).show());
            button("RESTABLECER PANTALLA COMPLETA",()->
                chooseMode(T1ClusterLayoutPolicy.MODE_FULL));
        }
        adbBottomStatus=new TextView(this);
        adbBottomStatus.setTextSize(15);
        adbBottomStatus.setPadding(12,8,12,8);
        column.addView(adbBottomStatus);
        updateAdbStatus();
        text("Estado general Waze (NO es prueba ADB): "+cfg().getString(T1ClusterWazeRouter.STATUS,
            "Ninguna prueba realizada"),13);
        text("MICRÓFONO FÍSICO · PROTOCOLO REAL DEL APK",19);
        SharedPreferences mic=T1OemMicrophoneService.prefs(this);
        boolean enabled=mic.getBoolean(T1OemMicrophoneService.ENABLED,false);
        text("T1Launcher T1N134: servicio hardkey OEM, evento 1010 "+
            "en estado 100. El botón inicia el reconocedor de DriveLab. "+
            "No es un KeyEvent Android ni exige mantener T1Launcher Cluster "+
            "activo. Se probará en la T1 real.",13);
        text("Puente micrófono: "+(enabled?"ACTIVADO":"APAGADO")+
            "\nBinder: "+mic.getString(T1OemMicrophoneService.STATUS,
                "sin diagnóstico")+
            "\nÚltima tecla: "+mic.getString(
                T1OemMicrophoneService.LAST_EVENT,"ninguna")+
            "\nReconocimiento / TTS: "+mic.getString(
                T1OemMicrophoneService.LAST_VOICE_STATUS,
                "todavía sin prueba interna")+
            "\nMostrar sobre otras apps: "+
                (Settings.canDrawOverlays(this)?"AUTORIZADO":"NO AUTORIZADO"),14);
        button(enabled?"DESACTIVAR BOTÓN OEM MICRÓFONO":
            "ACTIVAR BOTÓN OEM MICRÓFONO",this::toggleMicrophone);
        button("ACTUALIZAR DIAGNÓSTICO",this::render);
        text("Un único enlace OEM recibe ahora micrófono 1010 "+
            "y ruedita 1000–1003. Para que la rueda controle Waze también "+
            "debe estar habilitado el servicio Android de Accesibilidad "+
            "DriveLab · Waze y volante. No modifica Google Maps.",12);
        button("VOLVER",this::finish);
        setContentView(scroll);
    }
    private void chooseMode(String mode){
        T1ClusterLayoutPolicy.setMode(this,mode);render();
    }
    private void updatePreview(){
        if(preview==null)return;
        String mode=T1ClusterLayoutPolicy.mode(this);
        int ratio=T1ClusterLayoutPolicy.number(this,
            T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70);
        int w=T1ClusterLayoutPolicy.number(this,
            T1ClusterLayoutPolicy.WIDTH,70,30,100);
        int h=T1ClusterLayoutPolicy.number(this,
            T1ClusterLayoutPolicy.HEIGHT,75,35,100);
        int x=T1ClusterLayoutPolicy.number(this,
            T1ClusterLayoutPolicy.X,0,0,100);
        int y=T1ClusterLayoutPolicy.number(this,
            T1ClusterLayoutPolicy.Y,0,0,100);
        preview.update(mode,ratio,w,h,x,y,
            T1ClusterWazeRouter.selectedAppLabel(this),
            T1ClusterLayoutPolicy.secondLabel(this),
            T1ClusterLayoutPolicy.inverted(this));
        if(previewState!=null)
            previewState.setText("División: "+ratio+"% / "+(100-ratio)+
                "% · ventana "+w+"% × "+h+"% · "+
                "posición X="+x+"% Y="+y+"%");
    }
    private void slider(String heading,String key,int def,int min,
                        int max,int step){
        TextView name=new TextView(this);name.setTextSize(15);
        name.setPadding(10,10,10,2);column.addView(name);
        SeekBar seek=new SeekBar(this);
        seek.setMin(min);seek.setMax(max);
        seek.setProgress(T1ClusterLayoutPolicy.number(
            this,key,def,min,max));
        name.setText(heading+": "+seek.getProgress()+"%");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,
                                                    boolean fromUser){
                name.setText(heading+": "+value+"%");
                if(!fromUser)return;
                T1ClusterLayoutPolicy.setPercent(
                    T1ClusterLayoutSettingsActivity.this,key,
                    value,min,max);
                updatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        column.addView(seek);
    }
    private void chooseSecondApp(){
        PackageManager pm=getPackageManager();
        TreeMap<String,String> candidates=new TreeMap<>(
            String.CASE_INSENSITIVE_ORDER);
        Intent query=new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=pm.queryIntentActivities(query,0);
        for(ResolveInfo ri:apps){
            if(ri.activityInfo==null)continue;
            String pkg=ri.activityInfo.packageName;
            if(!T1ClusterWazeRouter.isSelectablePackage(this,pkg))continue;
            String name=String.valueOf(ri.loadLabel(pm));
            candidates.put(name+" · "+pkg,pkg);
        }
        for(String pkg:new String[]{"com.waze","com.google.android.apps.maps"}){
            if(!T1ClusterWazeRouter.isSelectablePackage(this,pkg))continue;
            if(!candidates.containsValue(pkg))
                candidates.put((pkg.equals("com.waze")?"Waze":"Google Maps")+
                    " · "+pkg,pkg);
        }
        if(candidates.isEmpty()){
            T1ClusterWazeRouter.status(this,"No hay segunda app iniciable");
            render();return;
        }
        String[] labels=candidates.keySet().toArray(new String[0]);
        String[] pkgs=candidates.values().toArray(new String[0]);
        // Jetour's AlertDialog hides setItems if setMessage is present.
        new AlertDialog.Builder(this)
            .setTitle("APP DE LA DERECHA")
            .setItems(labels,(d,which)->{
                T1ClusterLayoutPolicy.secondApp(
                    this,pkgs[which],labels[which]);
                render();
            }).setNegativeButton("CANCELAR",null).show();
    }
    private void toggleLocalAdb(){
        if(T1ClusterAdbResize.enabled(this)){
            T1ClusterAdbResize.enable(this,false);
            render();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("PUENTE ADB LOCAL · SIN ROOT")
            .setMessage("Solo conecta con adbd en 127.0.0.1:5555 "+
                "después de que vos lo habilites desde USB. "+
                "Android pedirá autorizar la clave de DriveLab. "+
                "La depuración TCP puede ser accesible desde "+
                "la misma red Wi-Fi: probalo solamente en una red "+
                "confiable y apagalo cuando no lo necesites. "+
                "Solo se permite listar tareas y cambiar sus "+
                "límites, nunca ejecutar comandos arbitrarios.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("HABILITAR", (dialog,which)->{
                T1ClusterAdbResize.enable(this,true);
                render();
            }).show();
    }
    private void updateAdbStatus(){
        String value=T1ClusterAdbResize.result(this);
        String label="PUENTE ADB: "+
            (T1ClusterAdbResize.enabled(this)?
                "HABILITADO (no confirma conexión)":"DESACTIVADO")+
            "\nRESULTADO DE PRUEBA ADB: "+value;
        if(adbPersistentStatus!=null)adbPersistentStatus.setText(label);
        if(adbBottomStatus!=null)
            adbBottomStatus.setText("ESTADO ADB TAMAÑO REAL: "+value+
                "\nÚltima verificación: "+
                (cfg().getLong(T1ClusterAdbResize.VERIFIED_AT,0L)>0?
                    "CONEXIÓN O TAREAS LEÍDAS":"NINGUNA"));
    }
    private void copyAdbResult(){
        String result=T1ClusterAdbResize.result(this);
        ClipboardManager clipboard=(ClipboardManager)
            getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null)clipboard.setPrimaryClip(
            ClipData.newPlainText("Resultado ADB DriveLab",result));
        Toast.makeText(this,"Resultado ADB copiado",Toast.LENGTH_SHORT).show();
    }
    private void probeLocalAdb(){
        if(!T1ClusterAdbResize.enabled(this)){
            cfg().edit().putString(T1ClusterAdbResize.RESULT,
                "PRUEBA BLOQUEADA: primero habilitá puente ADB.").commit();
            updateAdbStatus();
            return;
        }
        if(adbProbeRunning)return;
        adbProbeRunning=true;
        // The result stays in two TextViews. NEVER create a transient dialog
        // or replace the Activity view tree from an asynchronous callback.
        T1ClusterAdbResize.probe(this,(message,connected)->{
            adbProbeRunning=false;
            if(isFinishing()||isDestroyed())return;
            updateAdbStatus();
        });
        updateAdbStatus();
    }
    private void invertPanels(){
        if(!T1ClusterLayoutPolicy.MODE_SPLIT.equals(
            T1ClusterLayoutPolicy.mode(this)))return;
        T1ClusterLayoutPolicy.invertPanels(this);
        render();
        // The button tests both REAL task bounds only when P/0 is verified.
        // A changed layout is not trusted for boot/route until confirmed.
        testLayout();
    }
    private void adjustSplit(int delta){
        if(!T1ClusterLayoutPolicy.MODE_SPLIT.equals(
            T1ClusterLayoutPolicy.mode(this)))return;
        int old=T1ClusterLayoutPolicy.number(this,
            T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70);
        T1ClusterLayoutPolicy.setPercent(this,
            T1ClusterLayoutPolicy.SPLIT_PERCENT,
            Math.max(30,Math.min(70,old+delta)),30,70);
        render();
        // Exactly the same P/0 guard as PROBAR DISEÑO. No shell
        // commands or window repositioning while the car is moving.
        testLayout();
    }
    private void testLayout(){
        T1ClusterWazeRouter.status(this,
            "Verificando P y 0 km/h del OEM (solo lectura)…");
        new Thread(()->{
            MirrorAutomationOem oem=null;
            String failure=null;
            try{
                oem=new MirrorAutomationOem(this);
                int gear=oem.gear();
                double speed=oem.speedKmh();
                if(gear!=1||!Double.isFinite(speed)||
                   speed<0||speed>0.5)
                    failure="BLOQUEADO: la T1 no confirmó P a 0 km/h.";
            }catch(Exception ex){
                failure="BLOQUEADO: sin lectura OEM de P/0.";
            }finally{
                if(oem!=null)oem.close();
            }
            String result=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(result!=null){
                    T1ClusterWazeRouter.status(this,result);
                    render();return;
                }
                String launch=T1ClusterLayoutPolicy.test(this);
                T1ClusterWazeRouter.status(this,launch);
                render();
                boolean full=T1ClusterLayoutPolicy.MODE_FULL.equals(
                    T1ClusterLayoutPolicy.mode(this));
                if(!T1ClusterAdbResize.enabled(this)||(
                    !full&&!launch.startsWith("APP SOLICITADA")))return;
                // Real ADB measurements are asynchronous; never run an
                // ADB socket or shell command on the Jetour UI thread.
                String originalMode=T1ClusterLayoutPolicy.mode(this);
                int originalSplit=T1ClusterLayoutPolicy.number(this,
                    T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70);
                boolean originalInvert=T1ClusterLayoutPolicy.inverted(this);
                T1ClusterAdbResize.Done verified=(message,ok)->{
                    if(isFinishing()||isDestroyed())return;
                    boolean same=originalMode.equals(
                        T1ClusterLayoutPolicy.mode(this))&&
                        originalInvert==T1ClusterLayoutPolicy.inverted(this)&&
                        originalSplit==T1ClusterLayoutPolicy.number(this,
                            T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70);
                    if(ok&&same&&!full){
                        // Same timestamp as the measured ADB bounds.
                        // Setting TEST_AT later than VERIFIED_AT would make
                        // enableAfterTest() reject every successful 50/50.
                        long verifiedAt=cfg().getLong(
                            T1ClusterAdbResize.VERIFIED_AT,0L);
                        if(verifiedAt>0L)
                            cfg().edit().putLong(
                                T1ClusterLayoutPolicy.TEST_AT,verifiedAt)
                                .putString(T1ClusterLayoutPolicy.TEST_MODE,
                                    originalMode).apply();
                    }
                    T1ClusterWazeRouter.status(this,message);
                    render();
                };
                if(full)
                    T1ClusterAdbResize.restoreFull(this,verified);
                else T1ClusterAdbResize.apply(this,
                    T1ClusterLayoutPolicy.MODE_SPLIT.equals(originalMode),
                    1000L,verified);
            });
        },"T1-ClusterLayout-Park-ReadOnly").start();
    }
    private void toggleMicrophone(){
        SharedPreferences mic=T1OemMicrophoneService.prefs(this);
        if(mic.getBoolean(T1OemMicrophoneService.ENABLED,false)){
            mic.edit().putBoolean(T1OemMicrophoneService.ENABLED,false).apply();
            try{startService(new Intent(this,T1OemMicrophoneService.class)
                .setAction(T1OemMicrophoneService.ACTION_STOP));}
            catch(RuntimeException ignored){}
            render();return;
        }
        enableMic();
    }
    private void enableMic(){
        new AlertDialog.Builder(this)
            .setTitle("ACTIVAR VOLANTE OEM UNIFICADO")
            .setMessage("Escucha solo la pulsación física, sin grabar "+
                "audio en segundo plano. Al apretar, intenta abrir el "+
                "reconocedor INTERNO de DriveLab sin ventana externa. "+
                "El mismo Binder enviará rueda/OK/Atrás solo a los "+
                "controles accesibles de Waze, si Accesibilidad está activa. "+
                "T1Launcher y DriveLab pueden competir si ambos módulos "+
                "entrar en conflicto si reactivás su módulo Cluster. "+
                "Probalo detenido y verificá el diagnóstico.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR", (d,w)->{
                // One opt-in enables mic and Waze-only wheel routing.
                // No control is sent to the vehicle. The wheel listener
                // still requires explicitly installed Android Accessibility.
                getSharedPreferences(T1WazeWheelService.PREFS,MODE_PRIVATE)
                    .edit().putBoolean(T1WazeWheelService.ENABLED,true)
                    .putBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,true)
                    .apply();
                T1OemMicrophoneService.prefs(this).edit()
                    .putBoolean(T1OemMicrophoneService.UNIFIED_WHEEL_CONFIGURED,true)
                    .putBoolean(T1OemMicrophoneService.ENABLED,true)
                    .putString(T1OemMicrophoneService.STATUS,
                        "Activando enlace OEM del botón 1010…").apply();
                try{
                    startForegroundService(new Intent(this,
                        T1OemMicrophoneService.class)
                        .setAction(T1OemMicrophoneService.ACTION_START));
                }catch(RuntimeException e){
                    T1OemMicrophoneService.status(this,
                        "No arrancó servicio micrófono: "+
                        e.getClass().getSimpleName());
                }
                render();
            }).show();
    }
}