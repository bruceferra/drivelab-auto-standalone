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
    private TextView title;
    private TextView warning;
    private LinearLayout rootLayout;
    private Button fullButton;
    private Button exitButton;
    private boolean fullMosaic;
    private Button launch;
    private Button clusterButton;
    private Button touchButton;
    private Button wheelButton;
    private boolean touchEnabled;
    private boolean wheelAdjustedByMosaic;
    private final float[] touchDownX=new float[2],touchDownY=new float[2];
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final int CLUSTER_SAME_MAP=1;
    private static final int CLUSTER_SEPARATE_WAZE=2;
    private ClusterLayer clusterLayer;
    private Surface clusterSink;
    private VirtualDisplay clusterWazeVirtual;
    private int clusterMode;
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
        rootLayout=root;
        title=label("DRIVELAB NAV · MOSAICO PROPIO",19,0xFF78D8FF);
        root.addView(title);
        warning=label(
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
        fullButton=new Button(this);
        fullButton.setText("MOSAICO · AMPLIAR A TODA LA PANTALLA");
        fullButton.setAllCaps(false);
        fullButton.setOnClickListener(v->setFullMosaic(!fullMosaic));
        root.addView(fullButton);
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
            // Preserve the actual display aspect ratio in each panel.
            // A stretched TextureView makes Maps unreadable and breaks the
            // normalized touch coordinates sent to its virtual display.
            FrameLayout viewport=new FrameLayout(this);
            viewport.setBackgroundColor(Color.BLACK);
            frame.addView(viewport,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
            TextureView texture=new TextureView(this);
            texture.setOpaque(true);
            panels[i].texture=texture;
            viewport.addView(texture,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,Gravity.CENTER));
            final int sourceW=i==0?1280:480;
            final int sourceH=i==0?720:800;
            viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
                int vw=r-l,vh=b-t;
                if(vw<10||vh<10)return;
                FrameLayout.LayoutParams fitted=fitAspect(
                    vw,vh,sourceW,sourceH,Gravity.CENTER);
                ViewGroup.LayoutParams old=texture.getLayoutParams();
                if(old.width!=fitted.width||old.height!=fitted.height)
                    texture.setLayoutParams(fitted);
            });
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
        clusterButton.setText("CLUSTER NAV · ELEGIR MAPS O WAZE (20 S)");
        clusterButton.setEnabled(false);
        clusterButton.setOnClickListener(v->{
            if(clusterLayer!=null){
                returnToCenter("Cluster devuelto al estado original.");return;
            }
            if(fullMosaic){
                show("Mostrá primero los controles del mosaico.");return;
            }
            new AlertDialog.Builder(this)
                .setTitle("CLUSTER NAV · ELEGIR QUÉ MOSTRAR")
                .setMessage("Prueba sólo con Jetour estacionada. " +
                    "Se conserva el mosaico central. El contenido se " +
                    "ajusta SIN estirar la imagen. Se retira en 20 s. " +
                    "No captura volante ni botones originales.")
                .setItems(new String[]{
                    "Mismo Maps/Waze central · mover sesión existente",
                    "Waze independiente · Maps + Spotify siguen centrales"
                },(d,which)->showOnCluster(which==0?
                    CLUSTER_SAME_MAP:CLUSTER_SEPARATE_WAZE))
                .setNegativeButton("CANCELAR",null).show();
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
                // Jetour OEM blocks ACTION_ACCESSIBILITY_SETTINGS. Do NOT
                // send the owner back to an unusable Settings screen.
                // Uses already existing ADB local with explicit consent,
                // adding only NAV touch service without touching T1Launcher.
                NavMosaicTouchRecovery.ask(this,(connected,report)->{
                    if(closed||isFinishing())return;
                    if(connected&&appsRequested&&
                       NavMosaicSessionRegistry.snapshot(this)!=null){
                        touchEnabled=true;
                        touchButton.setText("TACTO MOSAICO · ACTIVADO");
                        show(report+". Tacto NAV habilitado en esta sesión. "+
                            "Probá un toque en Maps; volante/OEM intactos.");
                    }else{
                        touchEnabled=false;
                        touchButton.setText(
                            "TACTO MOSAICO · APAGADO (OPCIONAL)");
                        show(report+
                            ". No se afirmó control táctil sin servicio conectado.");
                    }
                });
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
        // Physical Jetour testing showed OEM navigation and Maps both
        // responding to OK. No safe exclusive owner of the OEM key stream
        // has been established. Do NOT offer opt-in wheel in this LAB.
        // Disable only NAV's own wheel requests; never change T1Launcher/OEM.
        BridgeWheelAccessibilityService.setManuallyArmed(false);
        BridgeWheelAccessibilityService.prefs(this).edit()
            .putBoolean(BridgeWheelAccessibilityService.ENABLED,false).apply();
        NavMosaicSessionRegistry.setWheel(this,sessionToken,false);
        wheelButton=new Button(this);
        wheelButton.setAllCaps(false);
        wheelButton.setText("VOLANTE NAV · PAUSADO POR CONFLICTO OEM");
        wheelButton.setEnabled(false);
        root.addView(wheelButton);
        exitButton=new Button(this);
        exitButton.setText("SALIR · LIBERAR MOSAICO");
        exitButton.setAllCaps(false);
        exitButton.setOnClickListener(v->{
            stop("Mosaico liberado por el usuario.");finish();
        });
        root.addView(exitButton);
        setContentView(root);
    }

    /** Contain, never stretch or crop, a live virtual display surface. */
    private static FrameLayout.LayoutParams fitAspect(int areaW,int areaH,
            int contentW,int contentH,int gravity){
        int safeW=Math.max(1,areaW),safeH=Math.max(1,areaH);
        int w=Math.max(1,Math.min(safeW,
            (int)((long)safeH*contentW/contentH)));
        int h=Math.max(1,(int)((long)w*contentH/contentW));
        return new FrameLayout.LayoutParams(w,h,gravity);
    }

    private void setFullMosaic(boolean requested){
        if(clusterLayer!=null){
            show("Primero devolvé el mapa del Cluster.");
            return;
        }
        fullMosaic=requested;
        int visibility=requested?View.GONE:View.VISIBLE;
        title.setVisibility(visibility);
        warning.setVisibility(visibility);
        status.setVisibility(visibility);
        launch.setVisibility(visibility);
        clusterButton.setVisibility(visibility);
        touchButton.setVisibility(visibility);
        wheelButton.setVisibility(visibility);
        exitButton.setVisibility(visibility);
        rootLayout.setPadding(requested?0:14,requested?0:10,
                              requested?0:14,requested?0:10);
        fullButton.setText(requested?
            "MOSAICO · MOSTRAR CONTROLES":
            "MOSAICO · AMPLIAR A TODA LA PANTALLA");
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
        final String[] packages={chosenMap(),SPOTIFY};
        final int[] displayIds=new int[2];
        final String[] components=new String[2];
        try{
            // Validate BOTH exact app components before ANY ADB launch.
            for(int i=0;i<2;i++){
                Panel panel=panels[i];
                if(!panel.ready||panel.display==null||
                   panel.display.getDisplay()==null)
                    throw new SecurityException(
                        "Pantalla virtual NAV "+(i+1)+" desconectada");
                displayIds[i]=panel.display.getDisplay().getDisplayId();
                Intent intent=allowedIntent(packages[i]);
                components[i]=intent.getComponent().flattenToString();
            }
            // Unlike LAB27 (regular app UID + isActivityStartAllowedOnDisplay),
            // launch only through the already authorized LOCAL ADB shell.
            // This is the scoped T1N145-equivalent am start --display route.
            // No move-stack, task resize, fallback to Display 0/1 or root.
            NavMosaicSessionRegistry.publish(this,
                displayIds[0],displayIds[1],packages[0],sessionToken);
            launch.setEnabled(false);
            show("NAV: solicitando Maps/Spotify por ADB local autorizado "+
                "en las dos pantallas VIRTUALES propias. "+
                "No se abrirán en la pantalla OEM si Android rechaza.");
            new Thread(()->{
                int count=0;
                String error=null;
                try{
                    // Fail before ANY app launch if the bridge is not
                    // locally connected or the owner never authorized it.
                    BridgeLocalAdb.shell(this,"am stack list");
                    for(int index=0;index<2;index++){
                        NavMosaicSessionRegistry.Session active=
                            NavMosaicSessionRegistry.snapshot(this);
                        if(active==null||active.token!=sessionToken)
                            throw new SecurityException(
                                "Sesión virtual cerrada antes de abrir apps");
                        BridgeLocalAdb.startOwnedVirtualApp(
                            this,displayIds[index],packages[index],
                            components[index],sessionToken);
                        count++;
                    }
                }catch(Exception denied){
                    error=denied.getClass().getSimpleName()+": "+
                        String.valueOf(denied.getMessage());
                }
                final int requests=count;
                final String problem=error;
                runOnUiThread(()->{
                    if(closed)return;
                    if(problem!=null){
                        NavMosaicSessionRegistry.clear(sessionToken);
                        show("NAV ADB: "+requests+"/2 aperturas solicitadas. "+
                            problem+
                            ". Android/Jetour no confirmó el mosaico. "+
                            "Cerrá para liberar los displays. NO hubo "+
                            "redirección al sistema OEM.");
                        return;
                    }
                    appsRequested=true;
                    if(panels[0].frame&&clusterButton!=null)
                        clusterButton.setEnabled(true);
                    show("NAV ADB: Android respondió a DOS solicitudes "+
                        "am start --display en las pantallas VIRTUALES "+
                        "de NAV. Verificá visualmente Maps y Spotify: "+
                        "todavía NO se garantiza su visibilidad ni "+
                        "control táctil. No se usó T1Launcher.");
                });
            },"NAV-Mosaico-ADB-Local").start();
        }catch(RuntimeException denied){
            launch.setEnabled(false);
            NavMosaicSessionRegistry.clear(sessionToken);
            show("No se pudo preparar apertura en NAV: "+
                denied.getClass().getSimpleName()+": "+
                String.valueOf(denied.getMessage())+
                ". No se abrió ninguna app; cerrá el mosaico.");
        }
    }
    // CLUSTER_SAME_MAP never starts another task; it moves ONLY the
    // existing 1280x720 map surface, then restores it to the center.
    // CLUSTER_SEPARATE_WAZE creates a temporary THIRD owned VD: its Waze
    // session is independent of the central Maps + Spotify virtual tasks.
    private void showOnCluster(int mode){
        if(closed||!appsRequested||!panels[0].frame||
           clusterLayer!=null||panels[0].display==null)return;
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(this);
        if(live==null||live.token!=sessionToken)return;
        if(mode!=CLUSTER_SAME_MAP&&mode!=CLUSTER_SEPARATE_WAZE)return;
        if(mode==CLUSTER_SEPARATE_WAZE){
            if(!MAPS.equals(live.mapPackage)){
                show("Waze independiente requiere Maps central. "+
                    "No se duplicará ni moverá una tarea Waze existente.");
                return;
            }
            try{allowedIntent(WAZE);}
            catch(RuntimeException missing){
                show("Waze no está instalado o no ofrece una actividad "+
                    "de inicio comprobable. No se cambió el Cluster.");
                return;
            }
        }
        DisplayManager manager=(DisplayManager)
            getSystemService(Context.DISPLAY_SERVICE);
        Display physical=manager==null?null:manager.getDisplay(1);
        if(physical==null||physical.getState()==Display.STATE_OFF||
           (physical.getFlags()&Display.FLAG_PRIVATE)!=0||
           physical.getDisplayId()==panels[0].display.getDisplay().getDisplayId()||
           physical.getDisplayId()==panels[1].display.getDisplay().getDisplayId()){
            show("Cluster físico Display 1 no disponible; mosaico intacto.");
            return;
        }
        try{
            clusterMode=mode;
            clusterLayer=new ClusterLayer(this,physical);
            clusterLayer.show();
            clusterButton.setText("CLUSTER NAV · CERRAR PRESENTACIÓN");
            main.postDelayed(clusterTimeout,20000L);
            show("Cluster: se solicitó "+(mode==CLUSTER_SEPARATE_WAZE?
                "Waze independiente":"misma sesión de Maps")+
                " por 20 segundos. La visibilidad se verifica en Jetour.");
        }catch(RuntimeException denied){
            returnToCenter("Cluster rechazado: "+
                denied.getClass().getSimpleName()+
                ". Se conservó el mosaico central.");
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
        // Only the original live-map mode detaches central Maps. A separate
        // Cluster Waze virtual display NEVER changes central Maps/Spotify.
        if(clusterMode==CLUSTER_SAME_MAP&&
           !closed&&panels[0].display!=null&&panels[0].sink!=null){
            try{panels[0].display.setSurface(panels[0].sink);}
            catch(RuntimeException e){
                reason+=" · Recuperación de Surface denegada";
            }
        }
        clusterMode=0;
        if(clusterWazeVirtual!=null){
            Display waze=clusterWazeVirtual.getDisplay();
            int id=waze==null?-1:waze.getDisplayId();
            NavMosaicSessionRegistry.clearClusterWaze(sessionToken,id);
            try{clusterWazeVirtual.release();}
            catch(RuntimeException ignored){}
            clusterWazeVirtual=null;
        }
        if(clusterLayer!=null){
            ClusterLayer old=clusterLayer;
            clusterLayer=null;
            try{old.dismiss();}catch(RuntimeException ignored){}
        }
        if(clusterSink!=null){clusterSink.release();clusterSink=null;}
        if(clusterButton!=null)
            clusterButton.setText(
                "CLUSTER NAV · ELEGIR MAPS O WAZE (20 S)");
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
            // Do not hide OEM's upper 40% in a parked lab test. The
            // original map is 16:9, so a full-width bottom stripe would
            // distort it. Contain it centered and letterbox if necessary.
            FrameLayout transparent=new FrameLayout(
                NavVirtualMosaicActivity.this);
            transparent.setBackgroundColor(Color.TRANSPARENT);
            Display display=getDisplay();
            Point size=new Point();
            display.getRealSize(size);
            int bottomHeight=Math.max(160,size.y*60/100);
            view=new TextureView(NavVirtualMosaicActivity.this);
            view.setOpaque(true);
            FrameLayout.LayoutParams bottom=fitAspect(
                size.x,bottomHeight,1280,720,
                Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
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
                if(clusterMode==CLUSTER_SAME_MAP){
                    panels[0].display.setSurface(clusterSink);
                    NavVirtualMosaicActivity.this.show(
                        "Mapa CENTRAL conectado al Cluster sin escalar "+
                        "ni duplicar tarea. Volverá en 20 segundos.");
                }else if(clusterMode==CLUSTER_SEPARATE_WAZE){
                    DisplayManager manager=(DisplayManager)
                        getSystemService(Context.DISPLAY_SERVICE);
                    if(manager==null)
                        throw new IllegalStateException("DisplayManager ausente");
                    clusterWazeVirtual=manager.createVirtualDisplay(
                        "DriveLabNAVClusterWaze",1280,720,213,clusterSink,
                        DISPLAY_FLAGS);
                    if(clusterWazeVirtual==null||
                       clusterWazeVirtual.getDisplay()==null)
                        throw new IllegalStateException(
                            "Tercera pantalla virtual no creada");
                    int displayId=clusterWazeVirtual.getDisplay()
                        .getDisplayId();
                    NavMosaicSessionRegistry.publishClusterWaze(
                        NavVirtualMosaicActivity.this,sessionToken,displayId);
                    final String component=allowedIntent(WAZE)
                        .getComponent().flattenToString();
                    new Thread(()->{
                        String result;
                        try{
                            result=BridgeLocalAdb.startOwnedVirtualApp(
                                NavVirtualMosaicActivity.this,
                                displayId,WAZE,component,sessionToken);
                        }catch(Exception error){
                            result="Waze no confirmado por ADB: "+
                                BridgeLocalAdb.hint(error);
                        }
                        final String note=result;
                        runOnUiThread(()->{
                            if(!closed&&clusterLayer==ClusterLayer.this)
                                NavVirtualMosaicActivity.this.show(
                                    note+". Maps + Spotify siguen centrales.");
                        });
                    },"NAV-Cluster-Waze-ADB-Local").start();
                }
            }catch(RuntimeException denied){
                returnToCenter("No se pudo conectar el Cluster: "+
                    denied.getClass().getSimpleName()+
                    ". Mosaico central conservado.");
            }
        }
        @Override public void onSurfaceTextureSizeChanged(
                SurfaceTexture texture,int width,int height){
            // No resizing the running virtual tasks.
        }
        @Override public void onSurfaceTextureUpdated(
                SurfaceTexture surface){
            // A frame does not prove OEM Cluster visibility.
        }
        @Override public boolean onSurfaceTextureDestroyed(
                SurfaceTexture surface){
            if(clusterLayer==this&&!closed)
                returnToCenter("Se cerró la capa del Cluster; central intacto.");
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
