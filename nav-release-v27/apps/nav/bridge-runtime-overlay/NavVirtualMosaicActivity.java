package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Presentation;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.security.SecureRandom;
import android.view.Display;
import android.view.MotionEvent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;

/**
 * DriveLab NAV native CENTRAL compositor, completely independent of
 * T1Launcher/Receiver/OEM: two PUBLIC, own-content-only virtual displays
 * rendered side-by-side into two local TextureViews.
 *
 * This is a gated LAB implementation. Neither third-party app is started
 * until a parked owner taps LAUNCH. Android may deny launches to secondary
 * displays, a user may already have a task running elsewhere, and no input
 * forwarding or Cluster migration is claimed in this stage. Never report
 * that app content is confirmed just because startActivity returned.
 */
public final class NavVirtualMosaicActivity extends Activity {
    private static final int DISPLAY_FLAGS =
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC |
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    private static final String MAPS="com.google.android.apps.maps";
    private static final String WAZE="com.waze";
    private static final String SPOTIFY="com.spotify.music";
    private static final String PREFS="drivelab_nav_mosaic_lab_v1";
    private final Panel[] panels={new Panel(0),new Panel(1)};
    private TextView status;
    private Button launch;
    private Button clusterButton;
    private Button touchButton;
    private Button wheelButton;
    private boolean touchEnabled;
    private boolean wheelAdjustedByMosaic;
    private final float[] touchDownX=new float[2],touchDownY=new float[2];
    private final Handler main=new Handler(Looper.getMainLooper());
    private ClusterLayer clusterLayer;
    private Surface clusterSink;
    private boolean appsRequested;
    private final long sessionToken=new SecureRandom().nextLong() & Long.MAX_VALUE;
    private boolean closed=false;
    private int ready;
    private int firstFrames;
    private static final class Panel {
        final int index;
        TextureView texture;
        VirtualDisplay display;
        Surface sink;
        boolean ready;
        boolean frame;
        Panel(int i){index=i;}
        void release(){
            if(display!=null){display.release();display=null;}
            if(sink!=null){sink.release();sink=null;}
            ready=false;frame=false;
        }
    }

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        BridgeImmersive.apply(this);
        // Unlike the original Bridge floating windows, these are native
        // frames within NAV's own central Activity, no system task resize.
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(14,10,14,10);
        root.setBackgroundColor(0xFF091B2D);
        TextView title=label("DRIVELAB NAV · MOSAICO PROPIO",19,0xFF78D8FF);
        root.addView(title);
        TextView warning=label(
            "LAB · Sólo estacionado. Pantallas virtuales independientes " +
            "para Maps y Spotify. No usa T1Launcher, ni toca Cluster, " +
            "HOME o volante. El tacto necesita autorización manual " +
            "de accesibilidad y sólo opera en los displays NAV.",12,Color.WHITE);
        warning.setPadding(0,3,0,8);
        root.addView(warning);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setBackgroundColor(0xFF152B40);
        root.addView(body,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        int ratio=BridgeStandaloneWindows.ratio(this);
        if(ratio<60||ratio>75)ratio=67;
        for(int i=0;i<2;i++){
            final int index=i;
            LinearLayout frame=new LinearLayout(this);
            frame.setOrientation(LinearLayout.VERTICAL);
            frame.setPadding(3,3,3,3);
            frame.setBackgroundColor(i==0?0xFF10354D:0xFF13334A);
            body.addView(frame,new LinearLayout.LayoutParams(
                0,ViewGroup.LayoutParams.MATCH_PARENT,i==0?ratio:100-ratio));
            TextView cap=label(i==0?"MAPS / WAZE":"SPOTIFY · TELÉFONO",12,
                0xFF75D9FA);
            cap.setGravity(Gravity.CENTER);
            frame.addView(cap);
            TextureView texture=new TextureView(this);
            texture.setOpaque(true);
            panels[i].texture=texture;
            frame.addView(texture,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
            texture.setSurfaceTextureListener(
                new TextureView.SurfaceTextureListener(){
                    @Override public void onSurfaceTextureAvailable(
                        SurfaceTexture surface,int width,int height){
                        attach(index,surface,width,height);
                    }
                    @Override public void onSurfaceTextureSizeChanged(
                        SurfaceTexture surface,int width,int height){
                        // Keep the session/resolution intact: the host
                        // panel size changes, not the virtual task display.
                    }
                    @Override public void onSurfaceTextureUpdated(
                        SurfaceTexture surface){
                        Panel p=panels[index];
                        if(!p.frame&&p.display!=null){
                            p.frame=true;firstFrames++;
                            if(index==0&&appsRequested&&clusterButton!=null)
                                clusterButton.setEnabled(true);
                            show("Superficie "+(index==0?"MAPA":"SPOTIFY")+
                                " recibió un cuadro. No prueba aún "+
                                "la identidad ni interactividad de la app.");
                        }
                    }
                    @Override public boolean onSurfaceTextureDestroyed(
                        SurfaceTexture surface){
                        stop("El panel dejó de estar disponible. "+
                            "Se liberaron ambas pantallas virtuales.");
                        return true;
                    }
                });
            // Touch only inside the owner's NAV central view. Never
            // forward to physical display 0/1, T1Launcher or wheel.
            texture.setOnTouchListener((view,event)->{
                if(closed||!touchEnabled||!appsRequested||
                   (clusterLayer!=null&&index==0))return false;
                if(event.getPointerCount()!=1)return false;
                int type=event.getActionMasked();
                if(type==MotionEvent.ACTION_DOWN){
                    touchDownX[index]=event.getX();
                    touchDownY[index]=event.getY();
                    return true;
                }
                if(type==MotionEvent.ACTION_UP){
                    if(view.getWidth()<50||view.getHeight()<50)return true;
                    float x1=touchDownX[index]/view.getWidth();
                    float y1=touchDownY[index]/view.getHeight();
                    float x2=event.getX()/view.getWidth();
                    float y2=event.getY()/view.getHeight();
                    float dx=x2-x1,dy=y2-y1;
                    boolean swipe=dx*dx+dy*dy>0.0025f;
                    boolean sent=NavMosaicTouchService.forward(
                        this,sessionToken,index,x1,y1,x2,y2,swipe);
                    show(sent?
                        "NAV táctil: gesto SOLICITADO sobre "+
                        (index==0?"Maps/Waze":"Spotify")+
                        ". Android todavía no confirmó interacción.":
                        "NAV táctil: rechazado, app no verificable o "+
                        "display inactivo. Ningún gesto ejecutado.");
                    return true;
                }
                return type==MotionEvent.ACTION_MOVE||
                       type==MotionEvent.ACTION_CANCEL;
            });
        }
        status=label("Preparando dos pantallas virtuales vacías…",
            13,Color.WHITE);
        status.setPadding(0,8,0,6);
        root.addView(status);
        launch=new Button(this);
        launch.setText("ABRIR MAPS + SPOTIFY EN EL MOSAICO · SOLO ESTACIONADO");
        launch.setAllCaps(false);
        launch.setEnabled(false);
        launch.setOnClickListener(v->startThirdPartyApps());
        root.addView(launch);
        clusterButton=new Button(this);
        clusterButton.setAllCaps(false);
        clusterButton.setText("CLUSTER NAV · PRESENTAR MISMA SESIÓN 20 S (LAB)");
        clusterButton.setEnabled(false);
        clusterButton.setOnClickListener(v->{
            if(clusterLayer!=null){returnToCenter("Mapa devuelto al centro.");return;}
            new AlertDialog.Builder(this)
                .setTitle("CLUSTER NAV · MOVER SUPERFICIE, NO LA TAREA")
                .setMessage("Sólo ESTACIONADO. Durante 20 segundos NAV " +
                    "intentará mostrar en la franja inferior del tablero " +
                    "la misma sesión virtual de Maps. No inicia Maps " +
                    "otra vez, no captura volante, no toca T1Launcher " +
                    "ni modifica SystemUI. Android puede negar la capa. " +
                    "El mapa regresa automáticamente a la central.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("PRESENTAR 20 S",(d,w)->showOnCluster())
                .show();
        });
        root.addView(clusterButton);
        touchButton=new Button(this);
        touchButton.setAllCaps(false);
        touchButton.setText("TACTO MOSAICO · APAGADO (OPCIONAL)");
        touchButton.setOnClickListener(v->{
            if(touchEnabled){
                touchEnabled=false;
                touchButton.setText("TACTO MOSAICO · APAGADO (OPCIONAL)");
                show("Control táctil virtual desactivado por usuario.");
                return;
            }
            if(!NavMosaicTouchService.connected()){
                new AlertDialog.Builder(this)
                    .setTitle("NAV · PERMISO TÁCTIL OPCIONAL")
                    .setMessage("Android todavía no tiene habilitado " +
                        "DriveLab NAV · táctil mosaico. Podés autorizarlo " +
                        "manualmente en Accesibilidad. Es distinto al " +
                        "servicio del volante y no escucha teclas OEM. " +
                        "Al abrir Ajustes, esta prueba se cerrará por " +
                        "seguridad. No cambies permisos de T1Launcher.")
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("ABRIR ACCESIBILIDAD",(d,w)->
                        startActivity(new Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .show();
                return;
            }
            new AlertDialog.Builder(this)
                .setTitle("NAV · HABILITAR TACTO DE PANTALLA VIRTUAL")
                .setMessage("Sólo para este mosaico y esta sesión, " +
                    "con la T1 estacionada. Reenvía toques dentro de los " +
                    "paneles a la app visible EXACTA en los displays NAV. " +
                    "No captura volante ni opera sobre Cluster u OEM. " +
                    "Se desactiva al salir. ¿Activar?")
                .setNegativeButton("NO",null)
                .setPositiveButton("ACTIVAR",(d,w)->{
                    touchEnabled=true;
                    touchButton.setText("TACTO MOSAICO · ACTIVADO");
                    show("Toques permitidos sólo en pantallas NAV verificadas.");
                })
                .show();
        });
        root.addView(touchButton);
        wheelButton=new Button(this);
        wheelButton.setAllCaps(false);
        wheelButton.setText("VOLANTE NAV EN CLUSTER · APAGADO");
        wheelButton.setOnClickListener(v->{
            if(NavMosaicSessionRegistry.manuallyApprovedWheelMapDisplay(
                    this,MAPS)>=2){
                disableMosaicWheel("Volante NAV desactivado por el usuario.");
                return;
            }
            if(clusterLayer==null||!MAPS.equals(chosenMap())){
                show("Primero mostrà Google Maps en el Cluster NAV. "+
                    "Waze todavía no tiene control de volante NAV.");
                return;
            }
            if(!BridgeWheelAccessibilityService.isConnected()){
                new AlertDialog.Builder(this)
                    .setTitle("NAV · ACCESIBILIDAD DEL VOLANTE")
                    .setMessage("El servicio existente del volante de NAV "+
                        "todavía no está conectado. Podés habilitarlo en "+
                        "Accesibilidad Android. Es distinto al tacto. "+
                        "Al abrir Ajustes se cerrará este mosaico.")
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("ABRIR ACCESIBILIDAD",(d,w)->
                        startActivity(new Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .show();
                return;
            }
            new AlertDialog.Builder(this)
                .setTitle("VOLANTE NAV · USAR EL MAPA VIRTUAL")
                .setMessage("Sólo con la Jetour ESTACIONADA. El receptor "+
                    "de teclas ya existente intentará navegar opciones "+
                    "ACCESIBLES de Google Maps en el display virtual de NAV. "+
                    "La rueda selecciona, OK confirma y ATRÁS cancela, "+
                    "sin zoom ni controles OEM. Antes de activar, apagá "+
                    "VOLANTE → MAPA en T1Launcher porque puede competir "+
                    "por el mismo Binder. No está validado en el auto "+
                    "y se desactiva al cerrar este Cluster.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("HABILITAR EN ESTE CLUSTER",(d,w)->{
                    if(!NavMosaicSessionRegistry.setWheel(
                            this,sessionToken,true)){
                        show("NAV no pudo verificar la sesión de Maps.");
                        return;
                    }
                    android.content.SharedPreferences preferences=
                        BridgeWheelAccessibilityService.prefs(this);
                    boolean already=BridgeWheelAccessibilityService.isManuallyArmed()&&
                        preferences.getBoolean(
                            BridgeWheelAccessibilityService.ENABLED,false);
                    wheelAdjustedByMosaic=!already;
                    if(!already){
                        BridgeWheelAccessibilityService.setManuallyArmed(true);
                        preferences.edit().putBoolean(
                            BridgeWheelAccessibilityService.ENABLED,true).apply();
                    }
                    wheelButton.setText("VOLANTE NAV EN CLUSTER · ACTIVADO");
                    show("Volante NAV armado sólo para la ventana Maps de "+
                        "nuestro display virtual. Sin prueba física.");
                })
                .show();
        });
        root.addView(wheelButton);
        Button exit=new Button(this);
        exit.setText("SALIR · LIBERAR MOSAICO");
        exit.setAllCaps(false);
        exit.setOnClickListener(v->{stop("Mosaico liberado por el usuario.");finish();});
        root.addView(exit);
        setContentView(root);
    }

    private TextView label(String text,int sp,int color){
        TextView v=new TextView(this);
        v.setText(text);v.setTextColor(color);v.setTextSize(sp);
        return v;
    }
    private void show(String message){
        if(status!=null)status.setText(message);
        getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString("last_status",message).apply();
    }
    private boolean newDisplay(DisplayManager mgr,Display target,
                               Set<Integer> oldIds){
        return mgr!=null&&target!=null&&
            target.getDisplayId()!=Display.DEFAULT_DISPLAY&&
            !oldIds.contains(target.getDisplayId())&&
            (target.getFlags()&Display.FLAG_PRIVATE)==0;
    }
    private void attach(int index,SurfaceTexture texture,int width,int height){
        if(closed)return;
        Panel p=panels[index];
        if(p.ready||p.display!=null)return;
        try{
            // Spotify is deliberately portrait to request its phone
            // responsive layout. Maps remains landscape. These sizes
            // never resize after creation (same proven host principle).
            int dw=index==0?1280:480;
            int dh=index==0?720:800;
            texture.setDefaultBufferSize(dw,dh);
            p.sink=new Surface(texture);
            DisplayManager manager=(DisplayManager)
                getSystemService(Context.DISPLAY_SERVICE);
            if(manager==null)throw new IllegalStateException(
                "Android sin DisplayManager");
            PackageManager pm=getPackageManager();
            if(!pm.hasSystemFeature(
                    PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                throw new IllegalStateException(
                    "Android no permite aplicaciones en pantallas secundarias");
            Set<Integer> old=new HashSet<>();
            for(Display d:manager.getDisplays())old.add(d.getDisplayId());
            p.display=manager.createVirtualDisplay(
                index==0?"DriveLabNAVMapa":"DriveLabNAVSpotify",
                dw,dh,213,p.sink,DISPLAY_FLAGS);
            if(p.display==null||!newDisplay(manager,
                    p.display.getDisplay(),old))
                throw new SecurityException(
                    "Android no creó un display PÚBLICO aislado");
            p.ready=true;
            ready++;
            show("Pantalla NAV "+(index+1)+
                " disponible (ID "+p.display.getDisplay().getDisplayId()+
                "). Aún NO se abrió ninguna app.");
            if(ready==2&&launch!=null){
                launch.setEnabled(true);
                show("Mosaico propio preparado: dos superficies independientes. "+
                    "Abrir apps requiere confirmación manual; Cluster intacto.");
            }
        }catch(RuntimeException denied){
            stop("No se pudo preparar mosaico: "+
                denied.getClass().getSimpleName()+". "+
                "No se abrió ninguna app ni se modificó Cluster.");
        }
    }
    private Intent allowedIntent(String pkg){
        if(!MAPS.equals(pkg)&&!WAZE.equals(pkg)&&!SPOTIFY.equals(pkg))
            throw new SecurityException("Destino no autorizado");
        Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null||intent.getComponent()==null||
            !pkg.equals(intent.getComponent().getPackageName()))
            throw new IllegalStateException("App no instalada: "+pkg);
        // No CLEAR_TASK, CLEAR_TOP, MULTIPLE_TASK, force-stop or task move.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
            Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }
    private String chosenMap(){
        String pkg=BridgeStandaloneWindows.first(this);
        return WAZE.equals(pkg)?WAZE:MAPS;
    }
    private void startThirdPartyApps(){
        if(closed||ready!=2||launch==null||!launch.isEnabled())return;
        if(!SPOTIFY.equals(BridgeStandaloneWindows.second(this))){
            show("Elegí Spotify como panel derecho en NAV antes de abrir.");
            return;
        }
        String[] packages={chosenMap(),SPOTIFY};
        Intent[] prepared=new Intent[2];
        ActivityOptions[] options=new ActivityOptions[2];
        try{
            ActivityManager am=(ActivityManager)
                getSystemService(Context.ACTIVITY_SERVICE);
            if(am==null)throw new IllegalStateException(
                "ActivityManager no disponible");
            // Preflight BOTH activities: never fall back to Display 0
            // or attempt to launch an app on physical Cluster Display 1.
            for(int i=0;i<2;i++){
                Panel p=panels[i];
                if(!p.ready||p.display==null)
                    throw new IllegalStateException(
                        "Superficie "+i+" no disponible");
                int id=p.display.getDisplay().getDisplayId();
                prepared[i]=allowedIntent(packages[i]);
                if(!am.isActivityStartAllowedOnDisplay(
                        this,id,prepared[i]))
                    throw new SecurityException(
                        "Android rechazó "+packages[i]+" en display "+id);
                options[i]=ActivityOptions.makeBasic();
                options[i].setLaunchDisplayId(id);
            }
            // Manual request only. A startActivity call returning is NOT
            // proof of a task appearing on the virtual display. The first
            // frame could be an Android/system placeholder.
            launch.setEnabled(false);
            for(int i=0;i<2;i++)
                startActivity(prepared[i],options[i].toBundle());
            appsRequested=true;
            NavMosaicSessionRegistry.publish(this,
                panels[0].display.getDisplay().getDisplayId(),
                panels[1].display.getDisplay().getDisplayId(),
                chosenMap(),sessionToken);
            if(panels[0].frame&&clusterButton!=null)
                clusterButton.setEnabled(true);
            show("Android aceptó dos solicitudes de apertura en displays "+
                "NAV propios. Visibilidad, reutilización de tarea y tacto "+
                "aún SIN confirmar; ninguna acción en Cluster.");
        }catch(RuntimeException denied){
            launch.setEnabled(false);
            show("Mosaico no verificado: "+
                denied.getClass().getSimpleName()+
                ". No se forzó una apertura en otra pantalla. "+
                "Cerrá para liberar los displays.");
        }
    }
    // Only the frame source changes. The virtual DISPLAY, launched task,
    // app process, route and Android display ID stay unchanged.
    // No freeform move-stack, ADB task resizing or startActivity is used.
    private void showOnCluster(){
        if(closed||!appsRequested||!panels[0].frame||
           clusterLayer!=null||panels[0].display==null)return;
        DisplayManager manager=(DisplayManager)
            getSystemService(Context.DISPLAY_SERVICE);
        Display physical=manager==null?null:manager.getDisplay(1);
        if(physical==null||physical.getState()==Display.STATE_OFF||
           (physical.getFlags()&Display.FLAG_PRIVATE)!=0||
           physical.getDisplayId()==panels[0].display.getDisplay().getDisplayId()||
           physical.getDisplayId()==panels[1].display.getDisplay().getDisplayId()){
            show("Cluster físico Display 1 no disponible; Maps sigue central.");
            return;
        }
        try{
            clusterLayer=new ClusterLayer(this,physical);
            clusterLayer.show();
            if(clusterButton!=null)clusterButton.setText(
                "DEVOLVER MAPA AL MOSAICO CENTRAL");
            // Prevent unbounded overlay. Timeout runs regardless of wheel/OEM.
            main.postDelayed(clusterTimeout,20000L);
            show("Capa NAV solicitada en Display 1; visibilidad real "+
                "NO confirmada. La ruta NO se reinicia.");
        }catch(RuntimeException denied){
            returnToCenter("Cluster rechazado: "+
                denied.getClass().getSimpleName()+
                ". Se conserva Maps central.");
        }
    }
    private void disableMosaicWheel(String message){
        NavMosaicSessionRegistry.setWheel(this,sessionToken,false);
        if(wheelAdjustedByMosaic){
            BridgeWheelAccessibilityService.setManuallyArmed(false);
            BridgeWheelAccessibilityService.prefs(this).edit()
                .putBoolean(BridgeWheelAccessibilityService.ENABLED,false)
                .apply();
        }
        wheelAdjustedByMosaic=false;
        if(wheelButton!=null)
            wheelButton.setText("VOLANTE NAV EN CLUSTER · APAGADO");
        if(message!=null)show(message);
    }
    private final Runnable clusterTimeout=()->
        returnToCenter("Cluster LAB finalizó a los 20 s. "+
            "Misma sesión devuelta al centro.");
    private void returnToCenter(String reason){
        main.removeCallbacks(clusterTimeout);
        disableMosaicWheel(null);
        // Return the live virtual display to the original central surface,
        // without launching any new activity or changing its task identity.
        if(!closed&&panels[0].display!=null&&panels[0].sink!=null){
            try{panels[0].display.setSurface(panels[0].sink);}
            catch(RuntimeException e){reason+=" · Recuperación de Surface denegada"; }
        }
        if(clusterLayer!=null){
            ClusterLayer old=clusterLayer;
            clusterLayer=null;
            try{old.dismiss();}catch(RuntimeException ignored){}
        }
        if(clusterSink!=null){clusterSink.release();clusterSink=null;}
        if(clusterButton!=null)
            clusterButton.setText(
                "CLUSTER NAV · PRESENTAR MISMA SESIÓN 20 S (LAB)");
        show(reason);
    }
    private final class ClusterLayer extends Presentation
        implements TextureView.SurfaceTextureListener {
        private TextureView view;
        ClusterLayer(Context ctx,Display destination){
            super(ctx,destination);
            Window window=getWindow();
            if(window!=null){
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0f);
                window.setFormat(PixelFormat.TRANSLUCENT);
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                window.clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        }
        @Override protected void onCreate(Bundle state){
            super.onCreate(state);
            // Safety: window background fully transparent. Only the bottom
            // 60% contains NAV. The top 40% is not drawn by NAV.
            FrameLayout transparent=new FrameLayout(NavVirtualMosaicActivity.this);
            transparent.setBackgroundColor(Color.TRANSPARENT);
            Display display=getDisplay();
            Point size=new Point();
            display.getRealSize(size);
            view=new TextureView(NavVirtualMosaicActivity.this);
            view.setOpaque(true);
            FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(160,size.y*60/100),Gravity.BOTTOM);
            transparent.addView(view,bottom);
            view.setSurfaceTextureListener(this);
            setContentView(transparent);
        }
        @Override public void onSurfaceTextureAvailable(
                SurfaceTexture surface,int width,int height){
            if(closed||clusterLayer!=this||panels[0].display==null)return;
            try{
                surface.setDefaultBufferSize(1280,720);
                clusterSink=new Surface(surface);
                panels[0].display.setSurface(clusterSink);
                NavVirtualMosaicActivity.this.show("El display de MAPS conserva su ID "+
                    panels[0].display.getDisplay().getDisplayId()+
                    ". Superficie conectada al tablero; capa "+
                    "OEM/visibilidad aún pendientes de confirmación.");
            }catch(RuntimeException denied){
                returnToCenter("No se pudo pasar la misma Surface al tablero: "+
                    denied.getClass().getSimpleName());
            }
        }
        @Override public void onSurfaceTextureSizeChanged(
                SurfaceTexture texture,int width,int height){
            // Never resize the running virtual display or its map task.
        }
        @Override public void onSurfaceTextureUpdated(
                SurfaceTexture surface){
            // A frame does not prove T1 OEM Z-order or safety visibility.
        }
        @Override public boolean onSurfaceTextureDestroyed(
                SurfaceTexture surface){
            if(clusterLayer==this&&!closed)
                returnToCenter("Se cerró la capa del Cluster; mapa central.");
            return true;
        }
    }
    private void stop(String reason){
        if(closed)return;
        disableMosaicWheel(null);
        closed=true;
        touchEnabled=false;
        NavMosaicSessionRegistry.clear(sessionToken);
        if(launch!=null)launch.setEnabled(false);
        if(clusterButton!=null)clusterButton.setEnabled(false);
        returnToCenter(reason);
        for(Panel p:panels)p.release();
        ready=0;
        show(reason);
    }
    @Override protected void onStop(){
        // No display/service persists after leaving the foreground UI.
        stop("Mosaico detenido al salir; no quedan displays en NAV.");
        super.onStop();
    }
    @Override protected void onDestroy(){
        stop("Mosaico destruido y superficies liberadas.");
        super.onDestroy();
    }
}
