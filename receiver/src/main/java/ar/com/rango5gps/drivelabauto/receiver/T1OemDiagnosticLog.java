package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent, PRIVATE diagnostic trace without VIN, GPS, identity or trip data.
 * Deliberately small and bounded; visible from cockpit even after Activity exit. */
final class T1OemDiagnosticLog {
    static final String TAG="DriveLabHevDiag";
    private static final String PREFS="t1_hev_command_diagnostics",KEY="last_report";
    private static SharedPreferences p(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static synchronized void begin(Context c,String reason){
        p(c).edit().putString(KEY,"").commit();
        add(c,"INICIO",reason);
    }
    static synchronized void add(Context c,String step,String detail){
        String time=new SimpleDateFormat("HH:mm:ss.SSS",Locale.ROOT).format(new Date());
        String safe=(detail==null?"":detail)
            .replace('\n',' ').replace('\r',' ');
        if(safe.length()>420)safe=safe.substring(0,420);
        String row=time+" | "+step+" | "+safe;
        Log.i(TAG,row);
        String old=p(c).getString(KEY,"");
        String next=old+row+"\n";
        if(next.length()>9000)next=next.substring(next.length()-9000);
        p(c).edit().putString(KEY,next).commit();
    }
    static String report(Context c){
        String r=p(c).getString(KEY,"");
        return r.isEmpty()?"Todavía no existe ningún diagnóstico EV/HEV.":
            "DriveLab · último ensayo EV/HEV\n"+
            "Registro local, solo códigos y valores OEM:\n\n"+r;
    }
    private T1OemDiagnosticLog(){}
}
