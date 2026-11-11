package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Owner-opt-in telemetria sync. Reads only locally-recorded TRIP CSV files,
 * uploads a restricted whitelist (never GPS, VIN, phone, or full CSV), and
 * advances a durable per-file cursor ONLY after a server acknowledgement.
 *
 * Endpoint stores private batches with RLS; it reuses the independent T1
 * QR-provisioned per-device credential held in Android Keystore. No PIN.
 */
final class T1TripSync {
    static final String PREFS="t1_trip_private_sync";
    static final String ENABLED="enabled",STATUS="status",SENT="rows_sent",
        LAST="last_success_ms",SECRET="encrypted_owner_pin";
    private static final String CURSOR="cursor_";
    private static final AtomicBoolean ACTIVE=new AtomicBoolean(false);
    private static volatile long lastAttempt=0;
    private static final int BATCH=40,MAX_PER_PASS=12,MAX_BATCHES_PER_FILE=4;

    private T1TripSync(){}
    static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static void configure(Context c){
        if(!T1CloudDiagnostics.active(c))
            throw new IllegalStateException(
                "Activá la nube con QR una sola vez en VEHÍCULO → DIAGNÓSTICO");
        prefs(c).edit().remove(SECRET).putBoolean(ENABLED,true)
            .putString(STATUS,"TRIP sin PIN · sincronización privada por QR activa")
            .apply();
    }
    static void disable(Context c){
        prefs(c).edit().putBoolean(ENABLED,false)
            .putString(STATUS,"Sincronización pausada; CSV locales conservados").apply();
    }
    static void trigger(Context c,boolean force){
        Context app=c.getApplicationContext();
        if(!prefs(app).getBoolean(ENABLED,false))return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(!force && now-lastAttempt<55000L)return;
        if(!ACTIVE.compareAndSet(false,true))return;
        lastAttempt=now;
        new Thread(()->{
            try{sendPending(app);}
            catch(Exception ex){
                String reason=ex.getMessage()==null?"":ex.getMessage();
                String status;
                if(reason.contains("HTTP 401"))
                    status="Dispositivo QR no autorizado (HTTP 401). "+
                        "Revisá vinculación de nube; CSV locales conservados.";
                else if(reason.contains("HTTP 429"))
                    status="Demasiados intentos (HTTP 429). "+
                        "Sincronización en pausa temporal; CSV conservados.";
                else
                    status="Sin conexión o envío fallido: "+
                        ex.getClass().getSimpleName()+
                        " (se conserva todo para reintentar)";
                prefs(app).edit().putString(STATUS,status).apply();
            }finally{ACTIVE.set(false);}
        },"T1-Private-Trip-Sync").start();
    }
    private static void putNumber(JSONObject obj,String key,String[] fields,int at)
        throws Exception{
        if(at>=fields.length||fields[at].isEmpty()){
            obj.put(key,JSONObject.NULL);return;
        }
        try{
            double number=Double.parseDouble(fields[at]);
            obj.put(key,Double.isFinite(number)?number:JSONObject.NULL);
        }catch(NumberFormatException ignored){obj.put(key,JSONObject.NULL);}
    }
    private static JSONObject parseRow(String csv)throws Exception{
        String[] f=csv.split(",",-1);
        if(f.length<23||!f[0].matches("[0-9]{12,15}"))
            throw new IllegalArgumentException("Fila de TRIP incompleta");
        JSONObject s=new JSONObject();
        s.put("ts",Long.parseLong(f[0]));
        putNumber(s,"gear",f,1);
        putNumber(s,"speed",f,2);
        putNumber(s,"fuel",f,3);
        putNumber(s,"socInternal",f,4);
        putNumber(s,"socDisplay",f,5);
        putNumber(s,"voltage",f,6);
        putNumber(s,"current",f,7);
        putNumber(s,"tripKm",f,16);
        putNumber(s,"hvOut",f,17);
        putNumber(s,"hvIn",f,18);
        putNumber(s,"gasProxy",f,19);
        // Added after the original 23 columns. Older TRIP logs are valid too.
        putNumber(s,"rpm",f,23);
        putNumber(s,"motorOn",f,24);
        putNumber(s,"oemElectricRolling50",f,25);
        putNumber(s,"oemFuelRolling50",f,26);
        putNumber(s,"oemElectricAverage500m",f,27);
        putNumber(s,"oemFuelAverage500m",f,28);
        putNumber(s,"rpmValid1034",f,29);
        putNumber(s,"oemElectric500mInt",f,30);
        putNumber(s,"oemFuel500mInt",f,31);
        putNumber(s,"voltage1063Int",f,32);
        putNumber(s,"current1064Int",f,33);
        putNumber(s,"raw1090Float",f,34);
        putNumber(s,"raw1090Int",f,35);
        // Real OEM callback counts and most recent raw segment values.
        // These are NOT measured liters nor kWh (sign still unverified).
        putNumber(s,"oem500FuelCount",f,36);
        putNumber(s,"oem500ElectricCount",f,37);
        putNumber(s,"oem500FuelRaw",f,38);
        putNumber(s,"oem500ElectricRaw",f,39);
        putNumber(s,"oem50FuelCallbackRaw",f,40);
        putNumber(s,"oem50ElectricCallbackRaw",f,41);
        return s;
    }
    private static JSONArray readBatch(File file,int offset)throws Exception{
        JSONArray arr=new JSONArray();
        try(BufferedReader r=new BufferedReader(new FileReader(file))){
            String line=r.readLine(); // CSV header
            if(line==null)return arr;
            for(int i=0;i<offset;i++)if(r.readLine()==null)return arr;
            while(arr.length()<BATCH&&(line=r.readLine())!=null){
                if(line.isEmpty())continue;
                arr.put(parseRow(line));
            }
        }
        return arr;
    }
    private static void post(Context c,String id,int cursor,JSONArray batch)
        throws Exception{
        T1CloudDiagnostics.uploadTripBatch(c,id,cursor,batch);
    }
    private static void sendPending(Context c)throws Exception{
        if(!T1CloudDiagnostics.active(c))
            throw new IllegalStateException(
                "Nube no activada por QR; TRIP y CSV locales conservados");
        File[] files=T1TripRecorderService.tripDir(c).listFiles(
            f->f.isFile()&&f.getName().matches("trip_[0-9]{8}_[0-9]{6}\\.csv"));
        if(files==null||files.length==0){
            prefs(c).edit().putString(STATUS,"No hay viajes CSV para subir").apply();return;
        }
        // Most recent TRIP first: a multi-thousand-row old trip must not hide
        // a newly completed journey. Keep per-trip cursors so no history is lost.
        Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));
        int batches=0,uploaded=0;
        for(File file:files){
            String tripId=file.getName().substring(5,20);
            String cursorKey=CURSOR+tripId;
            int cursor=prefs(c).getInt(cursorKey,0);
            int thisFileBatches=0;
            // Fair-share each pass across up to three pending trips; older
            // trips continue to drain on subsequent automatic/manual passes.
            while(batches<MAX_PER_PASS&&thisFileBatches<MAX_BATCHES_PER_FILE){
                JSONArray batch=readBatch(file,cursor);
                if(batch.length()==0)break;
                post(c,tripId,cursor,batch);
                cursor+=batch.length();uploaded+=batch.length();batches++;thisFileBatches++;
                prefs(c).edit().putInt(cursorKey,cursor)
                    .putInt(SENT,prefs(c).getInt(SENT,0)+batch.length())
                    .putLong(LAST,System.currentTimeMillis())
                    .putString(STATUS,"Sincronizado "+tripId+" · "+cursor+
                        " muestras · último envío OK").commit();
            }
            if(batches>=MAX_PER_PASS)break;
        }
        if(uploaded==0)
            prefs(c).edit().putString(STATUS,"Al día: no hay nuevas muestras").apply();
    }
}
