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
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
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
    private static final String AUTO_APPS="start_apps_automatically";
    private static final String AUTO_CLUSTER="start_waze_cluster_automatically";
    private static final String OPEN_IN_MOSAIC="open_in_mosaic";
    private static final String AUTO_TOUCH="touch_mosaic_automatically";
    private static final String COVER_PANELS="fill_panels_without_bars";
    private static final String CONFIG_INTENT="nav_open_config_only";
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
    private Button clusterWazeButton;
    private Button wazeTraceButton;
    private Button touchButton;
    private Button wheelButton;
    private Button settingsButton,quickClusterButton,quickExitButton;
    private Button autoAppsButton,autoClusterButton,openInMosaicButton,advancedButton;
    private boolean scheduledAutoApps;
    private Button fillModeButton;
    private final Runnable touchAutoRetry=new Runnable(){
        @Override public void run(){
            if(closed||!appsRequested)return;
            tryEnableSavedTouch();
        }
    };
    private boolean explicitSpotifyStop;
    private boolean navigatingToConfig;
    private final Runnable clusterButtonRefresh=new Runnable(){
        @Override public void run(){
            if(closed)return;
            updateQuickCluster();
            main.postDelayed(this,1800L);
        }
    };
    private boolean touchEnabled;
    private boolean wheelAdjustedByMosaic;
    private final float[] touchDownX=new float[2],touchDownY=new float[2];
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean stoppedDuringOwnClusterWaze;
    private final Runnable clusterFocusWatchdog=new Runnable(){
        @Override public void run(){
            if(closed||!stoppedDuringOwnClusterWaze||isFinishing()||
               isDestroyed())return;
            // Settings and a live cluster are intentional NAV sessions:
            // never silently stop Waze after an arbitrary 52 seconds.
            if(navigatingToConfig||NavOwnClusterProbe.hasActiveWazeTrial()){
                main.postDelayed(this,20000L);
                return;
            }
            if(NavOwnClusterProbe.hasActiveWazeTrial()){
                NavOwnClusterProbe.releaseActive();
                main.postDelayed(this,4000L);
                return;
            }
            stoppedDuringOwnClusterWaze=false;
            // Close ONLY confirmed central tasks before releasing either VD.
            exitAndCloseTasks();
        }
    };
    private boolean appsRequested;
    private boolean launchAttempted;
    private boolean openingApps;
    private boolean exitPending;
    private boolean exitInProgress;
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
        SurfaceTexture retainedTexture;
        Panel(int i){index=i;}
        void release(){
            if(display!=null){display.release();display=null;}
            if(sink!=null){sink.release();sink=null;}
            if(retainedTexture!=null){
                retainedTexture.release();retainedTexture=null;
            }
            ready=false;frame=false;
        }
    }

    private android.content.SharedPreferences mosaicPrefs(){
        return getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static int dp(Context c,int value){
        return Math.round(value*c.getResources().getDisplayMetrics().density);
    }
    /** Fill both panes by cropping uniformly, never stretch source apps.
     * Keep virtual display/task dimensions unchanged to preserve Sender.
     */
    private void applyPanelCrop(TextureView texture,int sw,int sh,int vw,int vh){
        if(vw<10||vh<10)return;
        Matrix transform=new Matrix();
        float original=(float)sw/sh,screen=(float)vw/vh;
        boolean cover=mosaicPrefs().getBoolean(COVER_PANELS,true);
        float sx=cover?Math.max(1f,original/screen):
            Math.min(1f,original/screen);
        float sy=cover?Math.max(1f,screen/original):
            Math.min(1f,screen/original);
        transform.setScale(sx,sy,vw/2f,vh/2f);
        texture.setTransform(transform);
    }
    private float normalizedCoverPoint(int panel,View view,
                                       float pos,boolean horizontal){
        float w=view.getWidth(),h=view.getHeight();
        if(w<=0||h<=0)return -1f;
        float original=panel==0?1280f/720f:480f/800f;
        float screen=w/h;
        float value=horizontal?pos/w:pos/h;
        boolean cover=mosaicPrefs().getBoolean(COVER_PANELS,true);
        float scale=horizontal?
            (cover?Math.max(1f,original/screen):
                   Math.min(1f,original/screen)):
            (cover?Math.max(1f,screen/original):
                   Math.min(1f,screen/original));
        return (value-0.5f)/scale+0.5f;
    }
    private void updateTouchLabel(){
        if(touchButton==null)return;
        touchButton.setText(touchEnabled?
            "TACTO MOSAICO · ON (AUTOMÁTICO)":
            mosaicPrefs().getBoolean(AUTO_TOUCH,true)?
                "TACTO MOSAICO · ESPERANDO PERMISO":
                "TACTO MOSAICO · OFF");
    }
    private void tryEnableSavedTouch(){
        if(closed||!appsRequested||
           !mosaicPrefs().getBoolean(AUTO_TOUCH,true)){
            touchEnabled=false;
            updateTouchLabel();
            return;
        }
        if(NavMosaicTouchService.connected()){
            touchEnabled=true;
            updateTouchLabel();
        }else{
            touchEnabled=false;
            updateTouchLabel();
            main.removeCallbacks(touchAutoRetry);
            main.postDelayed(touchAutoRetry,2000L);
        }
    }
    private Button quickButton(String name,LinearLayout bar,float weight){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(name);b.setTextSize(12f);
        b.setPadding(0,0,0,0);
        b.setMinimumHeight(0);b.setMinimumWidth(0);
        b.setTextColor(0xFFE0F3F7);
        b.setBackgroundTintList(ColorStateList.valueOf(0xFF142C38));
        b.setElevation(0f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            0,dp(this,42),weight);
        lp.setMargins(dp(this,1),0,dp(this,1),0);
        bar.addView(b,lp);
        return b;
    }
    private void updateQuickCluster(){
        if(quickClusterButton!=null)
            quickClusterButton.setText(
                NavOwnClusterProbe.hasActiveWazeTrial()?
                "CLÚSTER · ON":"CLÚSTER · OFF");
    }
    private void toggleCluster(){
        if(closed||!appsRequested||openingApps||exitInProgress||
           !panels[0].ready||!panels[1].ready||
           !MAPS.equals(chosenMap())||
           NavMosaicSessionRegistry.snapshot(this)==null){
            android.widget.Toast.makeText(this,
                "Abrí primero Maps y Spotify en el mosaico NAV",
                android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if(!NavOwnClusterProbe.toggleWazeDirect(this,sessionToken))
            android.widget.Toast.makeText(this,
                "Clúster NAV ocupado; no se tocaron otras tareas",
                android.widget.Toast.LENGTH_SHORT).show();
        updateQuickCluster();
    }
    private void openAdvancedConfig(){
        navigatingToConfig=true;
        Intent intent=new Intent(this,BootActivity.class);
        intent.putExtra(CONFIG_INTENT,true);
        startActivity(intent);
    }
    private void confirmExit(){
        new AlertDialog.Builder(this)
            .setTitle("NAV · SALIR")
            .setMessage("Cerrar las tareas del mosaico no siempre detiene "+
                "el servicio de música de Spotify. Elegí cómo salir.")
            .setNegativeButton("VOLVER",null)
            .setNeutralButton("CERRAR MOSAICO",(d,w)->
                exitAndCloseTasks())
            .setPositiveButton("SALIR Y DETENER SPOTIFY",(d,w)->
                new AlertDialog.Builder(this)
                    .setTitle("DETENER SPOTIFY COMPLETAMENTE")
                    .setMessage("Se detendrá com.spotify.music para todo "+
                        "el sistema, incluida la música en segundo plano "+
                        "y cualquier otra sesión. Puede requerir abrir "+
                        "Spotify nuevamente para reproducir. ¿Continuar?")
                    .setNegativeButton("NO",null)
                    .setPositiveButton("DETENER Y SALIR",(x,y)->{
                        explicitSpotifyStop=true;
                        exitAndCloseTasks();
                    }).show())
            .show();
    }
    private void updateAutoButtons(){
        if(autoAppsButton!=null)autoAppsButton.setText(
            "AUTOABRIR MAPS + SPOTIFY · "+
            (mosaicPrefs().getBoolean(AUTO_APPS,true)?"ON":"OFF"));
        if(autoClusterButton!=null)autoClusterButton.setText(
            "AUTOABRIR WAZE CLÚSTER · "+
            (mosaicPrefs().getBoolean(AUTO_CLUSTER,false)?"ON":"OFF"));
        if(openInMosaicButton!=null)openInMosaicButton.setText(
            "NAV ABRE DIRECTO EN MOSAICO · "+
            (mosaicPrefs().getBoolean(OPEN_IN_MOSAIC,true)?"ON":"OFF"));
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
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(2,0,2,0);
        bar.setBackgroundColor(0xFF0E202B);
        root.addView(bar,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(this,44)));
        settingsButton=quickButton("⚙",bar,1f);
        settingsButton.setTextSize(21f);
        settingsButton.setTextColor(0xFF75D9FA);
        settingsButton.setContentDescription("Configuración NAV");
        settingsButton.setOnClickListener(v->setFullMosaic(!fullMosaic));
        quickClusterButton=quickButton("CLÚSTER · OFF",bar,3f);
        quickClusterButton.setTextColor(0xFF75D9FA);
        quickClusterButton.setOnClickListener(v->toggleCluster());
        quickExitButton=quickButton("⏻",bar,1f);
        quickExitButton.setTextSize(20f);
        quickExitButton.setContentDescription("Salir del mosaico NAV");
        quickExitButton.setOnClickListener(v->confirmExit());
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
        body.setBackgroundColor(0xFF0A1924);
        body.setPadding(dp(this,3),dp(this,2),
            dp(this,3),dp(this,3));
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
            frame.setPadding(dp(this,1),0,dp(this,1),0);
            frame.setBackgroundColor(Color.TRANSPARENT);
            body.addView(frame,new LinearLayout.LayoutParams(
                0,ViewGroup.LayoutParams.MATCH_PARENT,i==0?ratio:100-ratio));
            // No duplicate labels: apps themselves are the content.
            // Preserve the actual display aspect ratio in each panel.
            // A stretched TextureView makes Maps unreadable and breaks the
            // normalized touch coordinates sent to its virtual display.
            FrameLayout viewport=new FrameLayout(this);
            GradientDrawable rounded=new GradientDrawable();
            rounded.setColor(Color.BLACK);
            rounded.setCornerRadius(dp(this,13));
            viewport.setBackground(rounded);
            viewport.setClipToOutline(true);
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
                applyPanelCrop(texture,sourceW,sourceH,vw,vh);
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
                        Panel p=panels[index];
                        if(NavMosaicLifecyclePolicy.retainSurfaceOnDestroy(
                                NavOwnClusterProbe.hasOwnWazeFocusGrace(
                                    sessionToken)||navigatingToConfig,
                                closed,isFinishing(),p.display!=null)){
                            // Return false: Android must NOT destroy the
                            // SurfaceTexture while Waze holds cluster focus.
                            // The original VD/task stays alive for rebind.
                            p.retainedTexture=surface;
                            if(p.ready&&ready>0)ready--;
                            p.ready=false;p.frame=false;
                            show("NAV: textura central "+index+
                                " retenida durante cambio de foco a Waze.");
                            return false;
                        }
                        stop("El panel dejó de estar disponible. "+
                            "Se liberaron ambas pantallas virtuales.");
                        return true;
                    }
                });
            // Touch only inside the owner's NAV central view. Never
            // forward to physical display 0/1, T1Launcher or wheel.
            texture.setOnTouchListener((view,event)->{
                // No Cluster transfer exists in the read-only probe.
                // Keep working Maps and Spotify input on NAV's own displays.
                if(closed||!touchEnabled||!appsRequested)return false;
                if(event.getPointerCount()!=1)return false;
                int type=event.getActionMasked();
                if(type==MotionEvent.ACTION_DOWN){
                    touchDownX[index]=event.getX();
                    touchDownY[index]=event.getY();
                    return true;
                }
                if(type==MotionEvent.ACTION_UP){
                    if(view.getWidth()<50||view.getHeight()<50)return true;
                    float x1=normalizedCoverPoint(index,view,
                        touchDownX[index],true);
                    float y1=normalizedCoverPoint(index,view,
                        touchDownY[index],false);
                    float x2=normalizedCoverPoint(index,view,
                        event.getX(),true);
                    float y2=normalizedCoverPoint(index,view,
                        event.getY(),false);
                    float dx=x2-x1,dy=y2-y1;
                    boolean swipe=dx*dx+dy*dy>0.0025f;
                    boolean sent=NavMosaicTouchService.forward(
                        this,sessionToken,index,x1,y1,x2,y2,swipe);
                    // Avoid SharedPreferences writes and TextView layout on
                    // every successful gesture; these caused UI stutter.
                    if(!sent)show("NAV táctil: gesto rechazado; "+
                        "display o app no verificables.");
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
        // The cluster's renderer MUST belong entirely to NAV. LAB31's
        // third-party Waze launch changed central tasks. First isolate the
        // owned display creation itself (no Waze yet, no foreign renderer).
        clusterButton.setText(
            "CLÚSTER NAV PROPIO · PROBAR SUPERFICIE · 12 S");
        clusterButton.setEnabled(false);
        clusterButton.setOnClickListener(v->{
            if(closed||!appsRequested||!panels[0].ready||
               !panels[1].ready||panels[0].display==null||
               panels[1].display==null){
                show("Abrí primero Maps y Spotify en NAV.");
                return;
            }
            if(fullMosaic){
                show("Mostrá primero los controles del mosaico.");
                return;
            }
            NavOwnClusterProbe.ask(this,sessionToken);
        });
        root.addView(clusterButton);
        clusterWazeButton=new Button(this);
        clusterWazeButton.setAllCaps(false);
        clusterWazeButton.setText(
            "CLÚSTER NAV · WAZE (ABRIR / CERRAR MANUAL)");
        clusterWazeButton.setEnabled(false);
        clusterWazeButton.setOnClickListener(v->{
            if(closed||!appsRequested||openingApps||exitInProgress||
               !panels[0].ready||!panels[1].ready||
               !MAPS.equals(chosenMap())||
               NavMosaicSessionRegistry.snapshot(this)==null){
                show("Primero abrí Maps + Spotify en NAV; Waze tendrá "+
                    "su tercera pantalla PROPIA. No muevas tareas OEM.");
                return;
            }
            if(fullMosaic){
                show("Mostrá los controles NAV antes de abrir Waze.");
                return;
            }
            NavOwnClusterProbe.askWaze(this,sessionToken);
        });
        root.addView(clusterWazeButton);
        wazeTraceButton=new Button(this);
        wazeTraceButton.setAllCaps(false);
        wazeTraceButton.setText("VER / COPIAR DIAGNÓSTICO WAZE · DISPLAY Y TAREA");
        wazeTraceButton.setOnClickListener(v->
            NavOwnClusterProbe.showLastWazeTrace(this));
        root.addView(wazeTraceButton);
        touchButton=new Button(this);
        touchButton.setAllCaps(false);
        updateTouchLabel();
        touchButton.setOnClickListener(v->{
            if(touchEnabled){
                mosaicPrefs().edit().putBoolean(AUTO_TOUCH,false).apply();
                touchEnabled=false;
                main.removeCallbacks(touchAutoRetry);
                updateTouchLabel();
                show("Tacto NAV desactivado. Podés reactivarlo desde ajustes.");
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
                        mosaicPrefs().edit().putBoolean(AUTO_TOUCH,true).apply();
                        touchEnabled=true;
                        updateTouchLabel();
                        show(report+". Tacto NAV habilitado en esta sesión. "+
                            "Probá un toque en Maps; volante/OEM intactos.");
                    }else{
                        touchEnabled=false;
                        updateTouchLabel();
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
                    mosaicPrefs().edit().putBoolean(AUTO_TOUCH,true).apply();
                    touchEnabled=true;
                    updateTouchLabel();
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
        exitButton.setText("SALIR · CERRAR APPS Y LIBERAR MOSAICO");
        exitButton.setAllCaps(false);
        exitButton.setOnClickListener(v->exitAndCloseTasks());
        root.addView(exitButton);
        autoAppsButton=new Button(this);
        autoAppsButton.setAllCaps(false);
        autoAppsButton.setOnClickListener(v->{
            boolean on=!mosaicPrefs().getBoolean(AUTO_APPS,true);
            mosaicPrefs().edit().putBoolean(AUTO_APPS,on).apply();
            updateAutoButtons();
        });
        root.addView(autoAppsButton);
        autoClusterButton=new Button(this);
        autoClusterButton.setAllCaps(false);
        autoClusterButton.setOnClickListener(v->{
            boolean on=!mosaicPrefs().getBoolean(AUTO_CLUSTER,false);
            mosaicPrefs().edit().putBoolean(AUTO_CLUSTER,on).apply();
            updateAutoButtons();
        });
        root.addView(autoClusterButton);
        openInMosaicButton=new Button(this);
        openInMosaicButton.setAllCaps(false);
        openInMosaicButton.setOnClickListener(v->{
            boolean on=!mosaicPrefs().getBoolean(OPEN_IN_MOSAIC,true);
            mosaicPrefs().edit().putBoolean(OPEN_IN_MOSAIC,on).apply();
            updateAutoButtons();
        });
        root.addView(openInMosaicButton);
        fillModeButton=new Button(this);
        fillModeButton.setAllCaps(false);
        fillModeButton.setOnClickListener(v->{
            boolean crop=!mosaicPrefs().getBoolean(COVER_PANELS,true);
            mosaicPrefs().edit().putBoolean(COVER_PANELS,crop).apply();
            fillModeButton.setText(crop?
                "PANELES: LLENAR SIN DEFORMAR":"PANELES: AJUSTAR SIN RECORTAR");
            for(int i=0;i<2;i++){
                TextureView tv=panels[i].texture;
                if(tv!=null&&tv.getWidth()>10&&tv.getHeight()>10)
                    applyPanelCrop(tv,i==0?1280:480,i==0?720:800,
                        tv.getWidth(),tv.getHeight());
            }
        });
        fillModeButton.setText(mosaicPrefs().getBoolean(COVER_PANELS,true)?
            "PANELES: LLENAR SIN DEFORMAR":"PANELES: AJUSTAR SIN RECORTAR");
        root.addView(fillModeButton);
        advancedButton=new Button(this);
        advancedButton.setAllCaps(false);
        advancedButton.setText("CONFIGURACIÓN AVANZADA DE NAV");
        advancedButton.setOnClickListener(v->openAdvancedConfig());
        root.addView(advancedButton);
        updateAutoButtons();
        setContentView(root);
        setFullMosaic(true);
        updateQuickCluster();
        main.postDelayed(clusterButtonRefresh,1500L);
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
        fullMosaic=requested;
        int visibility=requested?View.GONE:View.VISIBLE;
        title.setVisibility(visibility);
        warning.setVisibility(visibility);
        status.setVisibility(visibility);
        launch.setVisibility(visibility);
        clusterButton.setVisibility(visibility);
        clusterWazeButton.setVisibility(visibility);
        touchButton.setVisibility(visibility);
        wheelButton.setVisibility(visibility);
        exitButton.setVisibility(visibility);
        wazeTraceButton.setVisibility(visibility);
        fullButton.setVisibility(visibility);
        autoAppsButton.setVisibility(visibility);
        autoClusterButton.setVisibility(visibility);
        openInMosaicButton.setVisibility(visibility);
        advancedButton.setVisibility(visibility);
        fillModeButton.setVisibility(visibility);
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
        if(p.ready)return;
        try{
            if(p.display!=null){
                // Never launch new central displays or move apps after
                // temporary Waze focus changed the central TextureView.
                if(!NavMosaicLifecyclePolicy.reattachSameDisplay(
                        p.retainedTexture!=null,true,closed))
                    throw new SecurityException(
                        "No existe textura NAV retenida para reconectar");
                SurfaceTexture oldTexture=p.retainedTexture;
                if(texture!=oldTexture){
                    int dw=index==0?1280:480,dh=index==0?720:800;
                    texture.setDefaultBufferSize(dw,dh);
                    Surface fresh=new Surface(texture);
                    try{p.display.setSurface(fresh);}
                    catch(RuntimeException fail){
                        fresh.release();throw fail;
                    }
                    if(p.sink!=null)p.sink.release();
                    p.sink=fresh;
                    oldTexture.release();
                }
                p.retainedTexture=null;
                p.ready=true;ready++;
                show("NAV: display central "+index+
                    " reconectado conservando la tarea original.");
                return;
            }
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
                show("Mosaico NAV listo: dos displays independientes.");
                if(!scheduledAutoApps&&mosaicPrefs().getBoolean(AUTO_APPS,true)){
                    scheduledAutoApps=true;
                    main.postDelayed(()->{
                        if(!closed&&!launchAttempted&&!openingApps&&ready==2)
                            startThirdPartyApps();
                    },500L);
                }
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
            launchAttempted=true; // Includes partial 1/2 ADB launch failure.
            openingApps=true; // Never race task removal against ADB app launch.
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
                    openingApps=false;
                    if(exitPending){
                        exitPending=false;
                        exitAndCloseTasks();
                        return;
                    }
                    if(problem!=null){
                        // Keep the session owner for cleanup of a partial 1/2 launch.
                        show("NAV ADB: "+requests+"/2 aperturas solicitadas. "+
                            problem+
                            ". Android/Jetour no confirmó el mosaico. "+
                            "Cerrá para liberar los displays. NO hubo "+
                            "redirección al sistema OEM.");
                        return;
                    }
                    appsRequested=true;
                    tryEnableSavedTouch();
                    if(panels[0].frame&&clusterButton!=null)
                        clusterButton.setEnabled(true);
                    if(clusterWazeButton!=null)
                        clusterWazeButton.setEnabled(
                            MAPS.equals(packages[0]));
                    if(MAPS.equals(packages[0])&&
                       mosaicPrefs().getBoolean(AUTO_CLUSTER,false))
                        main.postDelayed(()->{
                            if(!closed&&appsRequested&&!exitInProgress&&
                               !NavOwnClusterProbe.hasActiveWazeTrial())
                                toggleCluster();
                        },2100L);
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
    /**
     * Previously Exit released VDs without closing third-party app tasks.
     * Never force-stop whole package processes: those may serve T1Launcher.
     */
    private void exitAndCloseTasks(){
        if(closed||exitInProgress)return;
        if(NavOwnClusterProbe.hasActiveWazeTrial()){
            if(exitPending)return;
            exitPending=true;
            show("SALIR: cerrando la capa Waze del clúster y luego "+
                "las tareas Maps y Spotify automáticamente, SIN otra pulsación.");
            NavOwnClusterProbe.releaseActiveThen(()->{
                if(closed||isFinishing()||isDestroyed())return;
                exitPending=false;
                exitAndCloseTasks();
            });
            return;
        }
        if(openingApps){
            exitPending=true;
            touchEnabled=false;
            exitButton.setEnabled(false);
            show("Terminando la solicitud ADB de apertura antes de "+
                "cerrar las tareas y liberar el mosaico…");
            return;
        }
        if(!launchAttempted){
            stop("Mosaico vacío liberado sin tareas iniciadas.");
            finish();
            return;
        }
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(this);
        if(live==null||live.token!=sessionToken||
           panels[0].display==null||panels[1].display==null){
            showExitFailure("El mosaico cambió o perdió sus pantallas. "+
                "No se intentó cerrar ninguna aplicación.");
            return;
        }
        final int mapId=live.displayId,mediaId=live.mediaDisplayId;
        final String mapPkg=live.mapPackage;
        exitInProgress=true;
        touchEnabled=false;
        exitButton.setEnabled(false);
        if(clusterButton!=null)clusterButton.setEnabled(false);
        if(clusterWazeButton!=null)clusterWazeButton.setEnabled(false);
        show("Cerrando las tareas Maps/Waze y Spotify del mosaico "+
            "mediante ADB local, sin tocar otras sesiones…");
        new Thread(()->{
            String problem=null;
            try{
                NavMosaicExitPolicy.Snapshot first=
                    NavMosaicExitPolicy.parse(
                        BridgeLocalAdb.shell(this,"am stack list"),
                        mapId,mapPkg,mediaId);
                NavMosaicExitPolicy.Snapshot second=
                    NavMosaicExitPolicy.parse(
                        BridgeLocalAdb.shell(this,"am stack list"),
                        mapId,mapPkg,mediaId);
                if(!first.same(second))
                    throw new SecurityException("Las tareas del mosaico "+
                        "no están identificadas de forma estable: "+
                        first.reason+" / "+second.reason+
                        ". Verificá que Maps y Spotify sigan en NAV.");
                for(NavMosaicExitPolicy.Task wanted:second.targets()){
                    NavMosaicSessionRegistry.Session still=
                        NavMosaicSessionRegistry.snapshot(this);
                    if(still==null||still.token!=sessionToken||
                       still.displayId!=mapId||still.mediaDisplayId!=mediaId||
                       !mapPkg.equals(still.mapPackage))
                        throw new SecurityException(
                            "La sesión NAV cambió; cierre detenido");
                    NavMosaicExitPolicy.Snapshot now=
                        NavMosaicExitPolicy.parse(
                            BridgeLocalAdb.shell(this,"am stack list"),
                            mapId,mapPkg,mediaId);
                    NavMosaicExitPolicy.Task current=
                        wanted.displayId==mapId?now.map:now.spotify;
                    if(!now.valid||
                       !NavMosaicExitPolicy.sameTask(wanted,current))
                        throw new SecurityException(
                            "La tarea cambió antes de retirarla");
                    String response=BridgeLocalAdb.shell(this,
                        NavMosaicExitPolicy.removeCommand(
                            current,mapId,mapPkg,mediaId));
                    if(NavMosaicExitPolicy.rejected(response))
                        throw new SecurityException(
                            "Android rechazó cerrar "+wanted.pkg+
                            " (d"+wanted.displayId+" stack"+
                            wanted.stackId+" tarea"+wanted.taskId+"). "+
                            NavWazeDisplayTrace.launchReply(response));
                    boolean gone=false;
                    for(int i=0;i<3;i++){
                        String afterRaw=BridgeLocalAdb.shell(this,"am stack list");
                        NavMosaicExitPolicy.Snapshot after=
                            NavMosaicExitPolicy.parse(
                                afterRaw,mapId,mapPkg,mediaId);
                        if(!after.valid)
                            throw new SecurityException(
                                "No se pudo comprobar el cierre");
                        NavMosaicExitPolicy.Task remaining=
                            wanted.displayId==mapId?after.map:after.spotify;
                        if(remaining==null){
                            if(NavMosaicExitPolicy.taskStillExists(
                                    afterRaw,wanted))
                                throw new SecurityException(
                                    wanted.pkg+" salió de su panel NAV "+
                                    "pero su tarea "+wanted.taskId+
                                    " sigue abierta en Android. "+
                                    "NO se liberaron los displays.");
                            gone=true;break;
                        }
                        if(!NavMosaicExitPolicy.sameTask(wanted,remaining))
                            throw new SecurityException(
                                "Android cambió la tarea durante el cierre");
                        Thread.sleep(180L);
                    }
                    if(!gone)throw new SecurityException(
                        "Android no confirmó el cierre de "+wanted.pkg);
                }
                String finalRaw=BridgeLocalAdb.shell(this,"am stack list");
                NavMosaicExitPolicy.Snapshot finalState=
                    NavMosaicExitPolicy.parse(
                        finalRaw,mapId,mapPkg,mediaId);
                for(NavMosaicExitPolicy.Task original:second.targets()){
                    if(NavMosaicExitPolicy.taskStillExists(finalRaw,original))
                        throw new SecurityException(
                            "La tarea "+original.taskId+" de "+original.pkg+
                            " sigue abierta en Android. "+
                            "No se libera un mosaico con tareas activas.");
                }
                if(!finalState.valid||!finalState.targets().isEmpty())
                    throw new SecurityException(
                        "Todavía hay una tarea en el mosaico. "+
                        "La pantalla y sus tareas siguen sin liberarse.");
                if(explicitSpotifyStop){
                    // Separate, explicit owner-confirmed choice. Removing
                    // Spotify's NAV task does not necessarily stop its audio
                    // service; only this opt-in action stops the whole app.
                    String stopReply=BridgeLocalAdb.stopSpotifyAfterOwnedExit(
                        this,mapId,mediaId,sessionToken);
                    if(NavMosaicExitPolicy.rejected(stopReply))
                        throw new SecurityException(
                            "Android rechazó la detención de Spotify");
                }
            }catch(Exception failed){
                problem=failed.getClass().getSimpleName()+": "+
                    String.valueOf(failed.getMessage());
            }
            final String failure=problem;
            runOnUiThread(()->{
                if(closed)return;
                exitInProgress=false;
                if(failure==null){
                    stop(explicitSpotifyStop?
                        "Mosaico liberado; detención completa de Spotify solicitada.":
                        "Apps del mosaico cerradas y pantallas NAV liberadas.");
                    finish();
                }else{
                    exitButton.setEnabled(true);
                    showExitFailure("No se confirmó el cierre de todas "+
                        "las tareas NAV: "+failure+
                        ". No se tocó ningún proceso del sistema.");
                }
            });
        },"NAV-Mosaico-Cerrar-Tareas-Propias").start();
    }
    private void showExitFailure(String detail){
        if(closed||isFinishing())return;
        new AlertDialog.Builder(this)
            .setTitle("NAV · CIERRE DE APPS NO CONFIRMADO")
            .setMessage(detail+"\n\nPodés reintentar, seguir en el "+
                "mosaico o liberar las pantallas sin cerrar las tareas. "+
                "Esta última opción puede dejar apps abiertas en Android.")
            .setNegativeButton("VOLVER AL MOSAICO",null)
            .setNeutralButton("SOLO LIBERAR (DEJA APPS)",(d,w)->
                new AlertDialog.Builder(this)
                    .setTitle("ATENCIÓN · APPS QUEDARÁN ABIERTAS")
                    .setMessage("Android no confirmó el cierre. "+
                        "Si liberás las pantallas ahora, Maps y Spotify "+
                        "pueden quedar flotando. ¿Querés abandonar el "+
                        "mosaico SIN cerrar las apps?")
                    .setNegativeButton("VOLVER",null)
                    .setPositiveButton("LIBERAR SIN CERRAR",(x,y)->{
                        stop("Se liberaron pantallas SIN cerrar apps; "+
                            "fue una decisión manual.");
                        finish();
                    }).show())
            .setPositiveButton("REINTENTAR",(d,w)->exitAndCloseTasks())
            .show();
    }
    @Override public void onBackPressed(){
        exitAndCloseTasks();
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
    private void stop(String reason){
        if(closed)return;
        disableMosaicWheel(null);
        closed=true;
        touchEnabled=false;
        NavMosaicSessionRegistry.clear(sessionToken);
        if(launch!=null)launch.setEnabled(false);
        if(clusterButton!=null)clusterButton.setEnabled(false);
        if(clusterWazeButton!=null)clusterWazeButton.setEnabled(false);
        NavOwnClusterProbe.releaseActive();
        for(Panel p:panels)p.release();
        ready=0;
        show(reason);
    }
    @Override protected void onStart(){
        super.onStart();
        navigatingToConfig=false;
        if(appsRequested)tryEnableSavedTouch();
        // Android can preserve the same TextureView/SurfaceTexture across
        // a temporary stop without firing a new onSurfaceTextureAvailable.
        // Reclaim readiness without changing any VirtualDisplay identity.
        for(Panel p:panels){
            if(p.display!=null&&p.retainedTexture!=null&&
               p.texture!=null&&p.texture.isAvailable()&&
               p.texture.getSurfaceTexture()==p.retainedTexture){
                p.retainedTexture=null;
                if(!p.ready){p.ready=true;ready++;}
            }
        }
        if(stoppedDuringOwnClusterWaze){
            stoppedDuringOwnClusterWaze=false;
            main.removeCallbacks(clusterFocusWatchdog);
            if(!closed)show("NAV: recuperó primer plano conservando "+
                "Maps y Spotify en sus displays propios.");
        }
    }
    @Override protected void onStop(){
        // LAB37: launching Waze on a third NAV VirtualDisplay can stop
        // this central Activity. The OLD unconditional stop() destroyed
        // both central displays and rehomed Apps as floating fullscreen.
        if(NavMosaicLifecyclePolicy.retainOnStop(
                NavOwnClusterProbe.hasOwnWazeFocusGrace(sessionToken)||
                    navigatingToConfig||exitInProgress||exitPending||openingApps,
                closed,isFinishing())){
            stoppedDuringOwnClusterWaze=true;
            main.removeCallbacks(clusterFocusWatchdog);
            main.postDelayed(clusterFocusWatchdog,52000L);
            show("NAV: Waze tomó foco temporal. Mosaico conservado.");
        }else{
            stop("Mosaico detenido al salir; no quedan displays en NAV.");
        }
        super.onStop();
    }
    @Override protected void onDestroy(){
        main.removeCallbacks(clusterButtonRefresh);
        main.removeCallbacks(touchAutoRetry);
        main.removeCallbacks(clusterFocusWatchdog);
        stoppedDuringOwnClusterWaze=false;
        stop("Mosaico destruido y superficies liberadas.");
        super.onDestroy();
    }
}
