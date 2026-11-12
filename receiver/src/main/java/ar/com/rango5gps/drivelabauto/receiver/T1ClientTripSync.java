package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reliable private client-finish receipts over the already enrolled QR cloud.
 * Never sends passenger labels, location, VIN or a raw trip CSV. Existing
 * foreground telemetry batch sync remains active; this is its summary peer.
 *
 * One deterministic, locally fsynced code_diag CSV is saved per completed
 * client. Server-side file ID is content-derived/idempotent. Success only
 * follows the real server acknowledgment; offline/busy retries never delete
 * or mark the local record sent.
 */
final class T1ClientTripSync {
    private static final String PREFS="t1_client_trip_sync_v1";
    private static final String SENT="sent_",STAGED="staged_",LEGACY_DONE="legacy_imported_v1";
    private static final String STATUS="status";
    private static final AtomicBoolean ACTIVE=new AtomicBoolean(false);
    private static volatile long lastAttempt;
    private static final long PERIOD_MS=55000L;
    private static final String HEADER=
        "event_type;client_id;start_epoch_ms;end_epoch_ms;trip_ids;"+
        "trip_km_calibrated_provisional;electric_km_engine_off_1035;"+
        "thermal_km_engine_on_1035;unknown_engine_km_1035;"+
        "fuel_500m_events;fuel_500m_raw;fuel_l_experimental;"+
        "electric_500m_events;electric_500m_raw;net_kwh_experimental;"+
        "gas_cost_experimental;speed_scale\n";
    private T1ClientTripSync(){}

    private static SharedPreferences p(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static String status(Context c){
        return p(c).getString(STATUS,"Sin resumen de cliente sincronizado todavía");
    }
    private static String n(double value){
        return Double.isFinite(value)?
            String.format(Locale.ROOT,"%.6f",value):"";
    }
    static void persist(Context c,String clientId,long started,long ended,
                        String chain,T1JourneyMetrics.Result r,
                        double evKm,double iceKm,double unknownKm)throws IOException{
        if(clientId==null||!clientId.matches("[0-9]{12,16}")||
            ended<started||r==null)
            throw new IOException("Identificador o resumen de cliente inválido");
        File dir=T1TripRecorderService.tripDir(c);
        String safelyScoped=chain!=null&&chain.matches("[0-9_+]+")?chain:"";
        SimpleDateFormat fmt=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT);
        File dest;
        long tick=ended;
        do {
            dest=new File(dir,"code_diag_"+fmt.format(new Date(tick))+".csv");
            tick+=1000;
        }while(dest.exists()&&tick-ended<3600000L);
        if(dest.exists())throw new IOException("Sin nombre de informe disponible");
        String row="CLIENT_FINISHED_V1;"+clientId+";"+started+";"+ended+";"+
            safelyScoped+";"+n(r.km)+";"+n(evKm)+";"+n(iceKm)+";"+
            n(unknownKm)+";"+r.fuelEvents+";"+n(r.rawFuel)+";"+
            n(r.fuelLitersCandidate)+";"+r.electricEvents+";"+
            n(r.rawElectric)+";"+n(r.electricNetKwhCandidate)+";"+
            n(r.fuelCostCandidate)+";"+n(22.9/24.55)+"\n";
        File tmp=File.createTempFile("client_pending_",".tmp",dir);
        boolean moved=false;
        try{
            try(FileOutputStream out=new FileOutputStream(tmp)){
                out.write((HEADER+row).getBytes(StandardCharsets.UTF_8));
                out.flush();out.getFD().sync();
            }
            moved=tmp.renameTo(dest);
            if(!moved)throw new IOException("No se pudo confirmar el resumen local");
        }finally{if(!moved)tmp.delete();}
        p(c).edit().putBoolean(STAGED+clientId,true)
            .putString(STATUS,
                "Cliente guardado · resumen en cola para servidor QR").commit();
    }
    /** Stage older client_trips.csv into the same private QR queue once,
     * so rides completed before upgrading are analyzable too. Historical
     * engine-state split was not recorded: empty fields, never invented 0s.
     * Client labels are deliberately omitted from uploaded summaries. */
    private static void stageLegacyLocalHistory(Context c){
        if(p(c).getBoolean(LEGACY_DONE,false))return;
        File source=new File(T1TripRecorderService.tripDir(c),"client_trips.csv");
        if(!source.isFile()){
            p(c).edit().putBoolean(LEGACY_DONE,true).commit();return;
        }
        try(BufferedReader in=new BufferedReader(new FileReader(source))){
            String line=in.readLine();
            while((line=in.readLine())!=null){
                String[] f=line.split(";",-1);
                if(f.length<14||p(c).getBoolean(STAGED+f[0],false))continue;
                try{
                    String id=f[0];long start=Long.parseLong(f[1]);
                    long end=Long.parseLong(f[2]);
                    double km=Double.parseDouble(f[5]);
                    int nf=Integer.parseInt(f[6]),ne=Integer.parseInt(f[9]);
                    double fuelRaw=f[7].isEmpty()?0:Double.parseDouble(f[7]);
                    double electricRaw=f[10].isEmpty()?0:Double.parseDouble(f[10]);
                    double price=Double.parseDouble(f[13]);
                    if(!Double.isFinite(km)||km<0)continue;
                    T1JourneyMetrics.Result result=new T1JourneyMetrics.Result(
                        km,fuelRaw,electricRaw,nf,ne,price);
                    persist(c,id,start,end,f[4],result,
                        Double.NaN,Double.NaN,Double.NaN);
                }catch(IllegalArgumentException ignored){
                    // Malformed historical line remains unchanged on disk.
                }
            }
            p(c).edit().putBoolean(LEGACY_DONE,true).commit();
        }catch(IOException ignored){
            // Next trigger retries staging; never remove the historical CSV.
        }
    }
    private static boolean isClientSummary(File file){
        if(!file.isFile()||!file.getName().matches(
                "code_diag_[0-9]{8}_[0-9]{6}\\.csv"))return false;
        try(BufferedReader in=new BufferedReader(new FileReader(file))){
            return HEADER.trim().equals(in.readLine());
        }catch(IOException e){return false;}
    }
    static void trigger(Context c,boolean force){
        Context app=c.getApplicationContext();
        long now=SystemClock.elapsedRealtime();
        if(!force&&now-lastAttempt<PERIOD_MS)return;
        if(!ACTIVE.compareAndSet(false,true))return;
        lastAttempt=now;
        try{
            stageLegacyLocalHistory(app);
            File[] files=T1TripRecorderService.tripDir(app).listFiles(
                f->f.getName().startsWith("code_diag_")&&f.isFile());
            if(files==null||files.length==0){
                ACTIVE.set(false);return;
            }
            // Newest clients first; don't delay the just-completed ride
            // behind years of historical backfill.
            Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));
            File pending=null;
            for(File file:files){
                if(p(app).getBoolean(SENT+file.getName(),false))continue;
                if(isClientSummary(file)){pending=file;break;}
            }
            if(pending==null){ACTIVE.set(false);return;}
            if(!T1CloudDiagnostics.active(app)){
                p(app).edit().putString(STATUS,
                    "Cliente LOCAL · envío pendiente: activar QR cuando haya red")
                    .apply();
                ACTIVE.set(false);return;
            }
            File selected=pending;
            p(app).edit().putString(STATUS,
                "SINCRONIZANDO CLIENTE · "+selected.getName()).apply();
            T1CloudDiagnostics.upload(app,selected,(message,qr)->{
                boolean confirmed=message!=null&&
                    message.startsWith("SERVIDOR OK");
                if(confirmed){
                    p(app).edit().putBoolean(SENT+selected.getName(),true)
                        .putString(STATUS,
                            "SERVIDOR OK · cliente finalizado sincronizado")
                        .commit();
                }else{
                    p(app).edit().putString(STATUS,
                        "CLIENTE PENDIENTE · CSV local conservado · "+
                        (message==null?"sin respuesta":message)).apply();
                }
                ACTIVE.set(false);
                if(confirmed)trigger(app,true); // drain next local summary
                // Busy or offline: never tight-loop; next periodic pass retries.
            });
        }catch(Exception e){
            p(app).edit().putString(STATUS,
                "CLIENTE PENDIENTE · reintento automático · "+
                    e.getClass().getSimpleName()).apply();
            ACTIVE.set(false);
        }
    }
}
