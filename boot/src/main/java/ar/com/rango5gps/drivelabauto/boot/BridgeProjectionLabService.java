package ar.com.rango5gps.drivelabauto.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Presentation;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Explicit user-consented and 20-second screen mirroring of DEFAULT_DISPLAY
 * to the proven, noninteractive lower band of PHYSICAL Display 1.
 * NOT a separate Waze task; NOT MediaRecorder; NO network or frame storage.
 */
public final class BridgeProjectionLabService extends Service
        implements TextureView.SurfaceTextureListener {
    static final String ACTION_START="bridge.lab.projection.START";
    static final String ACTION_STOP="bridge.lab.projection.STOP";
    static final String EXTRA_RESULT_CODE="bridge.lab.projection.resultCode";
    static final String EXTRA_RESULT_DATA="bridge.lab.projection.resultData";
    private static final String PREFS="bridge_projection_lab_result_v1";
    private static final String CHANNEL="bridge_lab_projection_20s";
    private static final int NOTICE=8219;
    private static final long LIMIT_MS=20000L;
    private static final int DENSITY=213;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->end("PRUEBA FINALIZADA: "+
        "copia de pantalla desactivada tras 20 segundos.");
    private final MediaProjection.Callback projectionCallback=
        new MediaProjection.Callback(){
            @Override public void onStop(){
                main.post(()->end("ANDROID DETUVO LA CAPTURA; recursos liberados."));
            }
        };
    private Presentation cluster;
    private TextureView image;
    private MediaProjection projection;
    private VirtualDisplay captureDisplay;
    private Surface output;
    private int sourceWidth,sourceHeight;
    private int viewWidth,viewHeight;
    private boolean closing,frameReceived;
    private boolean started;
    private Intent consentData;
    private int consentCode;

    static void report(Context c,String message){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("result",message)
            .putLong("time",System.currentTimeMillis()).apply();
    }
    static String lastResult(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString("result","Sin captura solicitada");
    }
    @Override public IBinder onBind(Intent ignored){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf(startId);return START_NOT_STICKY;}
        if(ACTION_STOP.equals(intent.getAction())){
            end("CAPTURA DETENIDA MANUALMENTE.");
            return START_NOT_STICKY;
        }
        if(!ACTION_START.equals(intent.getAction())||started){
            return START_NOT_STICKY;
        }
        started=true;
        try{
            // Android 10+ requires a foreground service explicitly of
            // type mediaProjection BEFORE obtaining a projection token.
            NotificationManager nm=(NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm==null)throw new IllegalStateException("Sin notificaciones");
            nm.createNotificationChannel(new NotificationChannel(
                CHANNEL,"Bridge LAB · captura temporal",NotificationManager.IMPORTANCE_LOW));
            PendingIntent stop=PendingIntent.getService(this,0,
                new Intent(this,BridgeProjectionLabService.class)
                    .setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notif=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_drivelab_boot)
                .setContentTitle("Bridge LAB · pantalla compartida")
                .setContentText("Copia local al tablero: 20 s · estacionado")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                    R.drawable.ic_drivelab_boot,"DETENER",stop).build())
                .build();
            startForeground(NOTICE,notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            consentCode=intent.getIntExtra(EXTRA_RESULT_CODE,0);
            consentData=intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if(consentCode!=android.app.Activity.RESULT_OK||consentData==null)
                throw new SecurityException("Permiso de captura no concedido");
            main.postDelayed(timeout,LIMIT_MS);
            startDisplay();
        }catch(RuntimeException denied){
            end("NO SE PUDO CAPTURAR: "+denied.getClass().getSimpleName()+
                " · "+shortMessage(denied));
        }
        return START_NOT_STICKY;
    }
    private static String shortMessage(Throwable e){
        String m=e.getMessage();
        if(m==null)return "sin detalle";
        // Avoid writing sensitive tokens or vendor internals to shared output.
        return m.length()>95?m.substring(0,95):m;
    }
    private void startDisplay(){
        DisplayManager dm=(DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        if(dm==null)throw new IllegalStateException("DisplayManager no disponible");
        Display physical=dm.getDisplay(1);
        Display source=dm.getDisplay(Display.DEFAULT_DISPLAY);
        if(physical==null||source==null||
            physical.getState()==Display.STATE_OFF||
            (physical.getFlags()&Display.FLAG_PRIVATE)!=0)
            throw new SecurityException("Display físico 1 no disponible");
        Point clusterSize=new Point(),src=new Point();
        physical.getRealSize(clusterSize);
        source.getRealSize(src);
        if(clusterSize.x<1200||clusterSize.y<480||
            src.x<400||src.y<300)
            throw new IllegalStateException("Geometría inesperada");
        sourceWidth=src.x;
        sourceHeight=src.y;
        viewWidth=clusterSize.x;
        viewHeight=Math.round(clusterSize.y*0.65f);
        cluster=new Presentation(this,physical);
        FrameLayout host=new FrameLayout(cluster.getContext());
        host.setBackgroundColor(Color.TRANSPARENT);
        image=new TextureView(cluster.getContext());
        image.setOpaque(true);
        image.setSurfaceTextureListener(this);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(
            viewWidth,viewHeight,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        host.addView(image,params);
        cluster.setContentView(host);
        configureWindow(cluster,clusterSize.x,clusterSize.y);
        cluster.setOnDismissListener(dialog->{
            if(!closing)main.post(()->end("Android retiró Presentation de Display 1"));
        });
        cluster.show();
        configureWindow(cluster,clusterSize.x,clusterSize.y);
        report(this,"ESPERANDO PERMISO DE PROYECCIÓN Y PRIMER CUADRO. "+
            "Captura consentida de Display 0 → zona inferior Display 1.");
    }
    private void configureWindow(Presentation p,int w,int h){
        Window window=p.getWindow();
        if(window==null)throw new IllegalStateException("Sin ventana");
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setGravity(Gravity.TOP|Gravity.LEFT);
        window.setLayout(w,h);
    }
    @Override public void onSurfaceTextureAvailable(
            SurfaceTexture t,int w,int h){
        if(closing||captureDisplay!=null)return;
        try{
            t.setDefaultBufferSize(sourceWidth,sourceHeight);
            output=new Surface(t);
            MediaProjectionManager mgr=(MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if(mgr==null)throw new SecurityException("MediaProjectionManager ausente");
            projection=mgr.getMediaProjection(consentCode,consentData);
            if(projection==null)throw new SecurityException("Token de captura inválido");
            projection.registerCallback(projectionCallback,main);
            // The output is SurfaceTexture displayed by Presentation on
            // PHYSICAL Display1. The new VirtualDisplay CAPTURES Display0;
            // no foreign Activity is launched on any virtual display.
            captureDisplay=projection.createVirtualDisplay("BridgeMirror20s",
                sourceWidth,sourceHeight,DENSITY,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                output,null,main);
            if(captureDisplay==null)
                throw new IllegalStateException("Sin VirtualDisplay capturador");
            // Fit rather than distort: this mirror is diagnostic, not
            // an independent Waze layout at dashboard resolution.
            Matrix fit=new Matrix();
            float scale=Math.min((float)viewWidth/sourceWidth,
                (float)viewHeight/sourceHeight);
            fit.setScale((sourceWidth*scale)/viewWidth,
                (sourceHeight*scale)/viewHeight,
                viewWidth/2f,viewHeight/2f);
            image.setTransform(fit);
            report(this,"CAPTURA INICIADA CON PERMISO. "+
                "Ahora abrí Waze en CENTRAL; comprobar copia en TABLERO.");
        }catch(RuntimeException error){
            end("ANDROID RECHAZÓ CAPTURA: "+
                error.getClass().getSimpleName()+" · "+shortMessage(error));
        }
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){
        if(closing||frameReceived)return;
        frameReceived=true;
        report(this,"CUADRO DE CAPTURA RECIBIDO EN DISPLAY 1. "+
            "La imagen visible de Waze debe confirmarse en el vehículo.");
    }
    @Override public void onSurfaceTextureSizeChanged(
        SurfaceTexture t,int width,int height){}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){
        end("SUPERFICIE CERRADA; captura y proyección liberadas.");
        return true;
    }
    private void end(String message){
        if(closing)return;
        closing=true;
        main.removeCallbacks(timeout);
        if(captureDisplay!=null){
            try{captureDisplay.release();}catch(RuntimeException ignored){}
            captureDisplay=null;
        }
        if(projection!=null){
            try{projection.unregisterCallback(projectionCallback);}
            catch(RuntimeException ignored){}
            try{projection.stop();}catch(RuntimeException ignored){}
            projection=null;
        }
        if(output!=null){try{output.release();}catch(RuntimeException ignored){}
            output=null;}
        if(cluster!=null){
            try{cluster.dismiss();}catch(RuntimeException ignored){}
            cluster=null;
        }
        consentData=null;
        report(this,message);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy(){
        end("SERVICIO CERRADO; captura liberada.");
        super.onDestroy();
    }
}
