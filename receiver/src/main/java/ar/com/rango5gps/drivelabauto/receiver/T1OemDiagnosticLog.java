package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent, PRIVATE diagnostic trace without VIN, GPS, identity or trip data.
 * Deliberately small and bounded; visible from cockpit even after Activity exit. */
final class T1OemDiagnosticLog {
    static final String TAG="DriveLabHevDiag";
    private static final String PREFS="t1_hev_command_diagnostics",KEY="last_report",
        PREVIOUS="previous_report";
    private static SharedPreferences p(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static synchronized void begin(Context c,String reason){
        String old=p(c).getString(KEY,"");
        p(c).edit().putString(PREVIOUS,old).putString(KEY,"").commit();
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
        String previous=p(c).getString(PREVIOUS,"");
        if(r.isEmpty()&&previous.isEmpty())
            return "Todavía no existe ningún diagnóstico EV/HEV.";
        return "DriveLab · informe EV/HEV (códigos OEM, sin GPS/VIN)\n\n"+
            "ÚLTIMO INTENTO:\n"+(r.isEmpty()?"Sin datos":r)+
            (previous.isEmpty()?"":"\nINTENTO ANTERIOR:\n"+previous);
    }
    /**
     * Build a private app-owned file already accepted by the existing
     * QR-enrolled HTTPS cloud channel. No ACTION_SEND or file chooser.
     * Snapshot is generated only on the explicit UPLOAD button press.
     */
    static synchronized File exportCloudCsv(Context context)throws Exception{
        Context app=context.getApplicationContext();
        long capturedAt=System.currentTimeMillis();
        String data=T1OemReportCsv.encode(report(app),capturedAt);
        File folder=T1TripRecorderService.tripDir(app).getCanonicalFile();
        if(!folder.isDirectory())
            throw new java.io.IOException("Carpeta privada de diagnóstico ausente");
        String timestamp=new SimpleDateFormat(
            "yyyyMMdd_HHmmss",Locale.ROOT).format(new Date(capturedAt));
        String fileName="code_diag_"+timestamp+".csv";
        if(!T1OemReportCsv.cloudFilename(fileName))
            throw new IllegalStateException("Nombre CSV inválido");
        File output=new File(folder,fileName);
        // Do not overwrite another previously captured diagnosis.
        if(!output.createNewFile())
            throw new IllegalStateException(
                "Ya existe un informe de este segundo; volvé a tocar SUBIR");
        boolean saved=false;
        try(FileOutputStream stream=new FileOutputStream(output,false)){
            stream.write(data.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            saved=true;
        }finally{
            if(!saved)output.delete();
        }
        return output;
    }
    private T1OemDiagnosticLog(){}
}
