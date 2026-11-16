package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.Normalizer;
import java.util.Locale;

/** A narrowly scoped, expiring authorization for the Maps route-replacement
 * dialog following a NEW, explicit Sender/voice navigation request.
 * No routine popup, permissions dialog or stale request can be accepted.
 */
final class T1GoogleMapsReplacement {
    private static final String PREF="t1_google_maps_replace_v1";
    private static final String TIME="requested_at",ACTIVE="pending",RESULT="result",
        START_PENDING="start_pending";
    private static final long TTL=12000L;
    private T1GoogleMapsReplacement(){}
    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static void arm(Context c){
        prefs(c).edit().putBoolean(ACTIVE,true)
            .putBoolean(START_PENDING,true)
            .putLong(TIME,System.currentTimeMillis())
            .putString(RESULT,"Esperando confirmación de ruta nueva, si aparece")
            .apply();
    }
    private static boolean recent(Context c){
        if(!T1WheelTarget.mapsConfigured(c))return false;
        SharedPreferences p=prefs(c);
        long age=System.currentTimeMillis()-p.getLong(TIME,0L);
        if(age<0||age>TTL){
            if(p.getBoolean(ACTIVE,false)||
               p.getBoolean(START_PENDING,false))cancel(c);
            return false;
        }
        return true;
    }
    static boolean pending(Context c){
        return recent(c)&&prefs(c).getBoolean(ACTIVE,false);
    }
    static boolean startPending(Context c){
        return recent(c)&&prefs(c).getBoolean(START_PENDING,false);
    }
    static void consumeStart(Context c){
        prefs(c).edit().putBoolean(START_PENDING,false).apply();
    }
    static boolean startAction(String value){
        String n=norm(value);
        // Only explicit Maps navigation action following the signed
        // Sender request. Never generic OK/Yes or a system permission.
        return n.equals("iniciar")||n.equals("iniciar navegacion")||
            n.equals("comenzar navegacion")||
            n.equals("start")||n.equals("start navigation")||
            n.equals("start driving")||n.equals("comenzar");
    }
    static void cancel(Context c){
        prefs(c).edit().putBoolean(ACTIVE,false)
            .putBoolean(START_PENDING,false).apply();
    }
    static void consume(Context c){
        prefs(c).edit().putBoolean(ACTIVE,false).apply();
    }
    static void result(Context c,String status){
        prefs(c).edit().putString(RESULT,status).apply();
    }
    static String status(Context c){
        return prefs(c).getString(RESULT,"Sin solicitud reciente");
    }
    private static String norm(String input){
        if(input==null)return "";
        return Normalizer.normalize(input,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT)
            .replaceAll("\\s+"," ").trim();
    }
    static boolean stopQuestion(String value){
        String n=norm(value);
        // Matches Maps' prompt about STOPPING the CURRENT navigation.
        // Does not match generic Yes/No, permissions or location notices.
        return (n.contains("salir")&&n.contains("navegac"))||
            (n.contains("quit")&&n.contains("navigat"))||
            (n.contains("detener")&&n.contains("navegac"))||
            (n.contains("finalizar")&&n.contains("navegac"))||
            (n.contains("stop")&&n.contains("navigat"))||
            (n.contains("end")&&n.contains("navigat"))||
            (n.contains("stop")&&n.contains("this route"));
    }
    /** An exact field-observed Google Maps question (not generic quit). */
    static boolean exitQuestion(String value){
        String n=norm(value).replaceAll("[^a-z0-9 ]+"," ");
        return n.contains("quieres salir de la navegacion")||
            n.contains("quiere salir de la navegacion")||
            n.contains("want to exit navigation");
    }
    /** YES / OK is allowed ONLY with exact exitQuestion + explicit new
     * destination; never for a generic Maps popup or a system dialog.
     */
    static boolean genericExitAction(String value){
        String n=norm(value);
        return n.equals("si")||n.equals("sí")||
            n.equals("aceptar")||n.equals("ok")||
            n.equals("yes")||n.equals("confirmar");
    }
    static boolean stopAction(String value){
        String n=norm(value);
        return n.equals("salir")||n.equals("salir de la navegacion")||
            n.equals("si, salir")||n.equals("exit navigation")||
            n.equals("si, detener")||n.equals("si, detener navegacion")||
            n.equals("si, finalizar")||n.equals("detener navegacion")||
            n.equals("detener")||n.equals("yes, stop")||
            n.equals("yes, stop navigation")||n.equals("stop navigating")||
            n.equals("stop navigation");
    }
}
