package ar.com.rango5gps.drivelabauto.receiver;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

/** OPTIONAL user-enabled Android 11 global gesture.
 *
 * Three fingers DOWN on Display 0 -> one screenshot -> PRIVATE PNG ->
 * notification to review -> Android Sharesheet only after explicit user tap.
 * It never shares or uploads in background, never reads screen text,
 * records touches, or interacts with OEM/cluster Display 1.
 *
 * Accessibility touch-exploration can affect input on this ROM; the owner
 * must enable this service manually via system Settings, and may disable it.
 */
public final class T1ScreenshotGestureService extends AccessibilityService {
    private static final String TAG="DriveLabScreenshot";
    private static final String CHANNEL="t1_screenshot_user";
    private long lastGesture;
    private volatile boolean busy;
    private static volatile T1ScreenshotGestureService active;
    static boolean manualCapture(){
        T1ScreenshotGestureService service=active;
        if(service==null||service.busy)return false;
        service.capture();
        return true;
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        active=this;
    }
    @Override public void onDestroy(){
        if(active==this)active=null;
        super.onDestroy();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public boolean onGesture(AccessibilityGestureEvent gesture){
        if(gesture==null||
            gesture.getGestureId()!=GESTURE_3_FINGER_SWIPE_DOWN||
            gesture.getDisplayId()!=Display.DEFAULT_DISPLAY)return false;
        long now=SystemClock.elapsedRealtime();
        if(busy||now-lastGesture<3000)return true;
        lastGesture=now;
        capture();
        return true;
    }
    private void capture(){
        busy=true;
        try{
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),
                new TakeScreenshotCallback(){
                    @Override public void onSuccess(ScreenshotResult result){
                        HardwareBuffer source=result.getHardwareBuffer();
                        ColorSpace colors=result.getColorSpace();
                        new Thread(()->{
                            String filename=null;
                            String error=null;
                            try{
                                filename=T1ScreenshotShareStore.save(
                                    getApplicationContext(),source,colors);
                            }catch(Exception e){
                                error=e.getClass().getSimpleName()+": "+
                                    e.getMessage();
                                Log.w(TAG,"PNG local capture failed",e);
                            }finally{busy=false;}
                            if(filename!=null)notifyReady(filename);
                            else notifyProblem(error==null?"Error PNG":error);
                        },"T1-Screenshot-Encode").start();
                    }
                    @Override public void onFailure(int errorCode){
                        busy=false;
                        notifyProblem("Android rechazó la captura: "+
                            errorCode);
                    }
                });
        }catch(Exception ex){
            busy=false;
            notifyProblem("No se pudo capturar: "+
                ex.getClass().getSimpleName());
        }
    }
    private NotificationManager manager(){
        NotificationManager nm=(NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(
            CHANNEL,"Capturas manuales DriveLab",
            NotificationManager.IMPORTANCE_DEFAULT));
        return nm;
    }
    private void notifyReady(String filename){
        NotificationManager nm=manager();
        if(nm==null)return;
        Intent preview=new Intent(this,T1ScreenshotReviewActivity.class);
        preview.setAction("ar.com.rango5gps.drivelabauto.SHOW_CAPTURE");
        preview.putExtra("filename",filename);
        preview.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
            Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(this,73081,preview,
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Captura guardada · DriveLab")
            .setContentText("Tocá para verla y compartirla")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build();
        nm.notify(7308,n);
    }
    private void notifyProblem(String error){
        NotificationManager nm=manager();
        if(nm==null)return;
        nm.notify(7309,new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Captura no realizada")
            .setContentText(error)
            .setAutoCancel(true).build());
    }
}
