package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Temporary read-only OEM hard-key log for owner's physical MODE test.
 * This does not simulate a MODE press or write audio sources. The exact
 * MODE key is NOT known until the owner's real event log confirms it.
 */
final class T1ModeKeyProbe {
    static final String PREFS="t1_mode_key_probe",UNTIL="until_uptime",
        HISTORY="events",COUNT="event_count";
    private static final int LIMIT=25;
    private T1ModeKeyProbe(){}
    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean armed(Context c){
        long expiry=prefs(c).getLong(UNTIL,0);
        long now=SystemClock.elapsedRealtime();
        return expiry>now&&expiry-now<=30000L;
    }
    static void arm(Context c){
        long until=SystemClock.elapsedRealtime()+30000L;
        prefs(c).edit().putLong(UNTIL,until)
            .putString(HISTORY,"CAPTURA SOLO LECTURA · sin código MODE supuesto")
            .putInt(COUNT,0).commit();
    }
    static void observe(Context c,int code,int action,int extra){
        if(!armed(c))return;
        SharedPreferences p=prefs(c);
        int n=p.getInt(COUNT,0);
        if(n>=LIMIT)return;
        String line="key="+code+" action="+action+" extra="+extra;
        String old=p.getString(HISTORY,"");
        p.edit().putInt(COUNT,n+1)
            .putString(HISTORY,old+"\\n"+line).apply();
    }
    static String status(Context c){
        SharedPreferences p=prefs(c);
        return (armed(c)?"CAPTURANDO MODE (hasta 30 segundos)":
            "Captura terminada")+" · "+p.getInt(COUNT,0)+
            " eventos\\n"+p.getString(HISTORY,
                "Sin prueba; no hay código MODE confirmado");
    }
}
