package ar.com.rango5gps.drivelabauto.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Intentional system-assistant shortcut when the physical Jetour 1010 key arrives.
 * The vendor Binder is registered by the *same* BridgeWheelAccessibilityService
 * that receives 1000-1003, never another overlapping callback.
 * This does NOT impersonate DriveLab Auto's own SpeechRecognizer or Gemini.
 */
final class BridgeOemVoiceLauncher {
    private static final String PREF="bridge_oem_voice_v1";
    private static final String CHANNEL="bridge_lab_oem_voice";
    private static final int NOTIFICATION=8127;
    private static long lastKey;
    private BridgeOemVoiceLauncher(){}
    static String state(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getString("last","1010 no recibido por Bridge");
    }
    private static void report(Context c,String message){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString("last",message).apply();
    }
    static void trigger(Context c,int action){
        if(action!=100)return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastKey<1500L)return;
        lastKey=now;
        Intent voice=new Intent(Intent.ACTION_VOICE_COMMAND)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(!BootActivity.isVisible()&&!BridgeBackgroundAccess.authorized(c)){
            report(c,"MICRÓFONO 1010/100 RECIBIDO. Android no autorizó "+
                "el inicio en segundo plano; se ofreció notificación.");
            notifyUser(c,voice);
            return;
        }
        try{
            c.startActivity(voice);
            report(c,"MICRÓFONO 1010/100 RECIBIDO · solicitud enviada "+
                "al asistente de voz predeterminado de Android. "+
                "Confirmar reconocimiento físico en la Jetour.");
        }catch(RuntimeException refused){
            report(c,"MICRÓFONO 1010/100 RECIBIDO; Android rechazó "+
                "VOICE_COMMAND ("+refused.getClass().getSimpleName()+")");
            notifyUser(c,voice);
        }
    }
    private static void notifyUser(Context c,Intent voice){
        try{
            NotificationManager nm=(NotificationManager)
                c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm==null)return;
            nm.createNotificationChannel(new NotificationChannel(
                CHANNEL,"Bridge · voz del volante",NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent tap=PendingIntent.getActivity(c,8127,voice,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            nm.notify(NOTIFICATION,new Notification.Builder(c,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Bridge · botón de micrófono recibido")
                .setContentText("Tocá para abrir el asistente de voz Android")
                .setContentIntent(tap).setAutoCancel(true).build());
        }catch(RuntimeException ignored){}
    }
}
