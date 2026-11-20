package ar.com.rango5gps.drivelabauto.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Small non-focusable settings gear over the CENTRAL Android display.
 * Android TYPE_APPLICATION_OVERLAY is always BELOW critical SYSTEM windows.
 * It never overlays Display 1 or intercepts gestures outside the 52dp gear.
 * Requires the owner-approved SYSTEM_ALERT_WINDOW appop, no ADB here.
 */
public final class BridgeCockpitOverlayService extends Service {
    static final String SHOW="bridge.cockpit.overlay.SHOW";
    static final String HIDE="bridge.cockpit.overlay.HIDE";
    private static final String CHANNEL="bridge_cockpit_settings";
    private static final int NOTICE=7752;
    private WindowManager windows;
    private View gear;
    private boolean displayed;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(HIDE.equals(action)){remove();stopSelf();return START_NOT_STICKY;}
        if(!SHOW.equals(action)){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){
            BridgeCockpitConfig.status(this,"Mosaico solicitado, pero la "+
                "ruedita requiere permiso Android Mostrar sobre otras apps. "+
                "Entrá a Bridge para revisar permisos.");
            stopSelf();return START_NOT_STICKY;
        }
        NotificationManager nm=(NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(
            CHANNEL,"Bridge · opciones del mosaico",NotificationManager.IMPORTANCE_LOW));
        Intent settings=new Intent(this,BootActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent actionSettings=PendingIntent.getActivity(this,7752,settings,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(NOTICE,new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setContentTitle("DriveLab Bridge · mosaico")
            .setContentText("Tocá para abrir la configuración del mosaico")
            .setContentIntent(actionSettings)
            .setOngoing(true).build());
        if(!displayed)showGear();
        return START_STICKY;
    }
    private int dp(float n){
        return Math.round(n*getResources().getDisplayMetrics().density);
    }
    private void showGear(){
        if(displayed)return;
        try{
            windows=(WindowManager)getSystemService(WINDOW_SERVICE);
            if(windows==null)throw new IllegalStateException("Sin WindowManager");
            TextView circle=new TextView(this);
            circle.setText("\u2699");
            circle.setTextSize(27);
            circle.setTextColor(Color.WHITE);
            circle.setGravity(Gravity.CENTER);
            circle.setContentDescription("Configuración DriveLab Bridge");
            GradientDrawable bg=new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xEE153F55);
            bg.setStroke(dp(1.5f),0xFF57DCE5);
            circle.setBackground(bg);
            circle.setElevation(dp(4));
            circle.setOnClickListener(v->{
                try{
                    startActivity(new Intent(this,BootActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                            Intent.FLAG_ACTIVITY_SINGLE_TOP));
                }catch(RuntimeException blocked){
                    BridgeCockpitConfig.status(this,"Android bloqueó abrir "+
                        "configuración: "+blocked.getClass().getSimpleName());
                }
            });
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                dp(52),dp(52),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            lp.gravity=Gravity.TOP|Gravity.RIGHT;
            lp.x=dp(10);
            lp.y=dp(8);
            windows.addView(circle,lp);
            gear=circle;displayed=true;
        }catch(Exception ex){
            remove();
            BridgeCockpitConfig.status(this,
                "No se pudo mostrar ruedita: "+ex.getClass().getSimpleName()+
                ". Abrí Bridge para acceder a las opciones.");
        }
    }
    private void remove(){
        if(gear!=null&&windows!=null){
            try{windows.removeView(gear);}catch(RuntimeException ignored){}
        }
        gear=null;windows=null;displayed=false;
    }
    @Override public void onTaskRemoved(Intent rootIntent){
        remove();stopSelf();super.onTaskRemoved(rootIntent);
    }
    @Override public void onDestroy(){
        remove();super.onDestroy();
    }
}
