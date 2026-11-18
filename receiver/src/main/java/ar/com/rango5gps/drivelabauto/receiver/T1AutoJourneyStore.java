package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Independent, crash-surviving log for ALL verified ignition sessions.
 * Does not rewrite Manual TRIP1/TRIP2/TOTAL and never joins GPS samples from
 * separate ignitions. SQLite is private to DriveLab, not shared with Launcher.
 */
final class T1AutoJourneyStore extends SQLiteOpenHelper {
    static final String DB="t1_ignition_journeys.db",PREFS="t1_ignition_auto",
        ENABLED="enabled",STATUS="status",LAST_OEM="last_oem_ms",
        LAST_READY="last_ready",SESSION="last_session_id";
    static final long GAP_MS=90000L;
    static final String SESSION_COLS="_id,start_ms,end_ms,last_ms,last_elapsed,"+
        "start_soc,end_soc,km_oem,km_gps,hv_out,hv_in,n_samples,"+
        "is_open,closed_reason,start_lat,start_lon,end_lat,end_lon";
    static final class Journey {
        long id,start,end,last,lastElapsed;
        double soc0,soc1,km,kmGps,hvOut,hvIn,lat0,lon0,lat1,lon1;
        int samples,open;
        String reason;
        double net(){return hvOut-hvIn;}
        String describe(){
            SimpleDateFormat f=new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault());
            return f.format(new Date(start))+" — "+
                (open==1?"EN CURSO":f.format(new Date(end)))+
                " · "+(open==1?"AUTO ACTIVA":"FINALIZADA")+
                "\n"+fmt(km,2)+" km OEM · GPS "+fmt(kmGps,2)+
                " km · "+samples+" muestras"+
                "\nSOC "+fmt(soc0,1)+" % → "+fmt(soc1,1)+" % ("+
                (Double.isFinite(soc0)&&Double.isFinite(soc1)?
                  fmt(soc1-soc0,1)+" pp":"sin lectura")+")"+
                "\nBatería SALIDA "+fmt(hvOut,3)+" kWh · ENTRADA "+
                fmt(hvIn,3)+" kWh (regeneración + motor) · NETA "+
                fmt(net(),3)+" kWh"+
                "\n"+(reason==null?"":reason);
        }
    }
    static final class Summary {
        int trips;
        double km,kmGps,out,in;
        double net(){return out-in;}
        String describe(String title){
            return title+" · "+trips+" sesiones"+
                "\n"+fmt(km,1)+" km OEM (GPS "+fmt(kmGps,1)+")"+
                "\nHV salido "+fmt(out,3)+" kWh · HV entrado "+
                fmt(in,3)+" kWh · NETO "+fmt(net(),3)+" kWh";
        }
    }
    static String fmt(double n,int d){
        return Double.isFinite(n)?
            String.format(Locale.getDefault(),"%."+d+"f",n):"—";
    }
    T1AutoJourneyStore(Context c){super(c,DB,null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sessions ("+
            "_id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL DEFAULT 0,"+
            "last_ms INTEGER NOT NULL,last_elapsed INTEGER NOT NULL,"+
            "start_soc REAL,end_soc REAL,"+
            "km_oem REAL NOT NULL DEFAULT 0,km_gps REAL NOT NULL DEFAULT 0,"+
            "hv_out REAL NOT NULL DEFAULT 0,hv_in REAL NOT NULL DEFAULT 0,"+
            "n_samples INTEGER NOT NULL DEFAULT 0,"+
            "is_open INTEGER NOT NULL DEFAULT 1,closed_reason TEXT DEFAULT '',"+
            "start_lat REAL,start_lon REAL,end_lat REAL,end_lon REAL)");
        db.execSQL("CREATE TABLE samples ("+
            "_id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "session_id INTEGER NOT NULL,t_epoch_ms INTEGER NOT NULL,"+
            "raw_ready INTEGER,raw_cable INTEGER,raw_gear INTEGER,"+
            "speed_kmh REAL,soc REAL,volt REAL,amps REAL,rpm REAL,"+
            "gps_lat REAL,gps_lon REAL,gps_accuracy_m REAL,"+
            "km_oem_cum REAL,km_gps_cum REAL,hv_out_cum REAL,hv_in_cum REAL)");
        db.execSQL("CREATE INDEX ix_auto_time ON sessions(start_ms)");
        db.execSQL("CREATE INDEX ix_auto_sample ON samples(session_id,t_epoch_ms)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){}
    private static double nullable(Cursor c,int index){
        return c.isNull(index)?Double.NaN:c.getDouble(index);
    }
    private static Journey journey(Cursor c){
        Journey r=new Journey();
        r.id=c.getLong(0);r.start=c.getLong(1);r.end=c.getLong(2);
        r.last=c.getLong(3);r.lastElapsed=c.getLong(4);
        r.soc0=nullable(c,5);r.soc1=nullable(c,6);
        r.km=c.getDouble(7);r.kmGps=c.getDouble(8);
        r.hvOut=c.getDouble(9);r.hvIn=c.getDouble(10);
        r.samples=c.getInt(11);r.open=c.getInt(12);r.reason=c.getString(13);
        r.lat0=nullable(c,14);r.lon0=nullable(c,15);
        r.lat1=nullable(c,16);r.lon1=nullable(c,17);
        return r;
    }
    synchronized Journey openJourney(){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT "+SESSION_COLS+" FROM sessions WHERE is_open=1 "+
                "ORDER BY _id DESC LIMIT 1",null)){
            return c.moveToFirst()?journey(c):null;
        }
    }
    private static void geo(ContentValues v,Location fix,String lat,String lon){
        if(fix==null)return;
        v.put(lat,fix.getLatitude());v.put(lon,fix.getLongitude());
    }
    private static void real(ContentValues v,String col,double value){
        if(Double.isFinite(value))v.put(col,value);
    }
    synchronized long begin(T1AutoDriveOem.Point p,Location fix){
        Journey existing=openJourney();
        if(existing!=null)return existing.id;
        ContentValues row=new ContentValues();
        row.put("start_ms",p.wallMs);row.put("last_ms",p.wallMs);
        row.put("last_elapsed",p.elapsedMs);
        real(row,"start_soc",p.soc);real(row,"end_soc",p.soc);
        geo(row,fix,"start_lat","start_lon");
        geo(row,fix,"end_lat","end_lon");
        long id=getWritableDatabase().insertOrThrow("sessions",null,row);
        append(id,p,fix);
        return id;
    }
    /**
     * Atomic sample+totals: power loss never advances a counter without its
     * raw point. Gaps >10s are marked as missing (never backfilled).
     */
    synchronized void append(long id,T1AutoDriveOem.Point p,Location gps){
        Journey old=openJourney();
        if(old==null||old.id!=id)return;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            double dt=p.elapsedMs-old.lastElapsed;
            // Power cut and process restart can reset elapsedRealtime.
            if(p.wallMs<=old.last||dt<0)dt=0;
            double[] incr=T1AutoDrivePolicy.integrate(p.volt,p.amps,p.speed,(long)dt);
            double gpsKm=0;
            if(gps!=null && Double.isFinite(old.lat1)&&
               Double.isFinite(old.lon1)&&
               dt>0&&dt<=15000L&&gps.hasAccuracy()&&
               gps.getAccuracy()<=40){
                float[] dist=new float[1];
                Location.distanceBetween(old.lat1,old.lon1,
                    gps.getLatitude(),gps.getLongitude(),dist);
                if(dist[0]>=0&&dist[0]<=60*(dt/1000.0))
                    gpsKm=dist[0]/1000.0;
            }
            ContentValues v=new ContentValues();
            v.put("last_ms",p.wallMs);v.put("last_elapsed",p.elapsedMs);
            real(v,"end_soc",p.soc);
            v.put("km_oem",old.km+incr[2]);
            v.put("km_gps",old.kmGps+gpsKm);
            v.put("hv_out",old.hvOut+incr[0]);
            v.put("hv_in",old.hvIn+incr[1]);
            v.put("n_samples",old.samples+1);
            geo(v,gps,"end_lat","end_lon");
            db.update("sessions",v,"_id=?",new String[]{Long.toString(id)});
            ContentValues raw=new ContentValues();
            raw.put("session_id",id);raw.put("t_epoch_ms",p.wallMs);
            raw.put("raw_ready",p.ready);raw.put("raw_cable",p.cable);
            raw.put("raw_gear",p.gear);
            real(raw,"speed_kmh",p.speed);real(raw,"soc",p.soc);
            real(raw,"volt",p.volt);real(raw,"amps",p.amps);
            real(raw,"rpm",p.rpm);
            if(gps!=null){
                geo(raw,gps,"gps_lat","gps_lon");
                if(gps.hasAccuracy())raw.put("gps_accuracy_m",gps.getAccuracy());
            }
            raw.put("km_oem_cum",old.km+incr[2]);
            raw.put("km_gps_cum",old.kmGps+gpsKm);
            raw.put("hv_out_cum",old.hvOut+incr[0]);
            raw.put("hv_in_cum",old.hvIn+incr[1]);
            db.insertOrThrow("samples",null,raw);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized void end(long id,String reason){
        Journey old=openJourney();
        if(old==null||old.id!=id)return;
        ContentValues v=new ContentValues();
        v.put("end_ms",old.last);v.put("is_open",0);
        v.put("closed_reason",reason);
        getWritableDatabase().update("sessions",v,
            "_id=?",new String[]{Long.toString(id)});
    }
    synchronized ArrayList<Journey> latest(int max){
        ArrayList<Journey> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT "+SESSION_COLS+" FROM sessions "+
            "ORDER BY start_ms DESC LIMIT "+
            Math.max(1,Math.min(500,max)),null)){
            while(c.moveToNext())all.add(journey(c));
        }
        return all;
    }
    synchronized Summary since(long epoch){
        Summary out=new Summary();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT COUNT(*),COALESCE(SUM(km_oem),0),"+
                "COALESCE(SUM(km_gps),0),COALESCE(SUM(hv_out),0),"+
                "COALESCE(SUM(hv_in),0) FROM sessions WHERE start_ms>=?",
            new String[]{Long.toString(epoch)})){
            if(c.moveToFirst()){
                out.trips=c.getInt(0);out.km=c.getDouble(1);
                out.kmGps=c.getDouble(2);out.out=c.getDouble(3);
                out.in=c.getDouble(4);
            }
        }
        return out;
    }

    /** A virtual journey generated from existing automatic raw samples.
     * No rewriting source sessions; exact [from,to) user-local window, even
     * when individual ignition sessions straddle its boundaries.
     */
    static final class Slice {
        final String title;
        final long from,until;
        int sessions;
        double km,gps,out,in;
        long sampledMs;
        private final java.util.HashSet<Long> ids=new java.util.HashSet<>();
        Slice(String title,long from,long until){
            this.title=title;this.from=from;this.until=until;
        }
        void add(long sessionId,T1AutoRangeMath.Delta delta){
            if(delta.observedMs<=0)return;
            ids.add(sessionId);sessions=ids.size();
            km+=delta.distanceKm;gps+=delta.gpsKm;
            out+=delta.hvOutKwh;in+=delta.hvInKwh;
            sampledMs+=delta.observedMs;
        }
        double net(){return out-in;}
        String describe(){
            return title+" · "+sessions+" encendidos"+
                "\n"+fmt(km,2)+" km OEM · "+fmt(gps,2)+" km GPS"+
                "\nBatería: SALIDA "+fmt(out,3)+" kWh; ENTRADA "+
                fmt(in,3)+" kWh; NETA "+fmt(net(),3)+" kWh"+
                "\nTiempo con telemetría: "+
                fmt(sampledMs/60000.0,1)+" minutos"+
                "\nENTRADA = regeneración + generador térmico, "+
                "no solo frenado. Nafta: SIN LITROS OEM VALIDADOS.";
        }
    }
    static final class RangeReport {
        final long from,until;
        final Slice total;
        final java.util.TreeMap<String,Slice> days=new java.util.TreeMap<>();
        final java.util.LinkedHashMap<Long,Slice> byIgnition=
            new java.util.LinkedHashMap<>();
        RangeReport(long start,long end){
            from=start;until=end;total=new Slice("TOTAL FILTRADO",start,end);
        }
        void add(long id,long sessionStart,T1AutoRangeMath.Cumulative previous,
                 T1AutoRangeMath.Cumulative current){
            long fromMs=Math.max(from,previous.at),toMs=Math.min(until,current.at);
            if(toMs<=fromMs)return;
            // Split a short observed segment at local midnights. Both
            // total and day rows aggregate the SAME measured increments.
            long part=fromMs;
            while(part<toMs){
                java.util.Calendar cal=java.util.Calendar.getInstance();
                cal.setTimeInMillis(part);
                java.text.SimpleDateFormat day=new java.text.SimpleDateFormat(
                    "yyyy-MM-dd",Locale.getDefault());
                String key=day.format(new Date(part));
                cal.set(java.util.Calendar.HOUR_OF_DAY,0);
                cal.set(java.util.Calendar.MINUTE,0);
                cal.set(java.util.Calendar.SECOND,0);
                cal.set(java.util.Calendar.MILLISECOND,0);
                cal.add(java.util.Calendar.DAY_OF_MONTH,1);
                long end=Math.min(toMs,cal.getTimeInMillis());
                T1AutoRangeMath.Delta delta=T1AutoRangeMath.clip(
                    previous,current,part,end);
                total.add(id,delta);
                days.computeIfAbsent(key,x->new Slice(x,from,until))
                    .add(id,delta);
                String label="ENCENDIDO #"+id+" · "+
                    new java.text.SimpleDateFormat("dd/MM HH:mm",
                        Locale.getDefault()).format(new Date(sessionStart));
                byIgnition.computeIfAbsent(id,
                    x->new Slice(label,from,until)).add(id,delta);
                part=end;
            }
        }
        String csv(){
            StringBuilder s=new StringBuilder(
                "nivel;nombre;desde_epoch_ms;hasta_epoch_ms;encendidos;"+
                "km_oem;km_gps;hv_salida_kwh;hv_entrada_kwh;hv_neto_kwh;"+
                "minutos_con_telemetria\n");
            csvRow(s,"TOTAL",total);
            for(Slice slice:days.values())csvRow(s,"DIA",slice);
            for(Slice slice:byIgnition.values())csvRow(s,"ENCENDIDO",slice);
            return s.toString();
        }
        private void csvRow(StringBuilder out,String type,Slice row){
            out.append(type).append(';')
                .append(row.title.replace(';',',')).append(';')
                .append(from).append(';').append(until).append(';')
                .append(row.sessions).append(';')
                .append(fmtCsv(row.km)).append(';')
                .append(fmtCsv(row.gps)).append(';')
                .append(fmtCsv(row.out)).append(';')
                .append(fmtCsv(row.in)).append(';')
                .append(fmtCsv(row.net())).append(';')
                .append(fmtCsv(row.sampledMs/60000.0))
                .append('\n');
        }
    }
    private static T1AutoRangeMath.Cumulative point(Cursor c){
        return new T1AutoRangeMath.Cumulative(
            c.getLong(0),c.getDouble(1),c.getDouble(2),
            c.getDouble(3),c.getDouble(4));
    }
    /**
     * Per-session cumulative baselines: fetch last sample at/before FROM,
     * subsequent samples up to and INCLUDING the first after UNTIL.
     * Fractional overlap avoids taking an entire ignition for a 19:00-23:50
     * request. No gaps or fabricated earlier recordings.
     */
    synchronized RangeReport between(long from,long until){
        if(from<=0||until<=from||until-from>370L*86400000L)
            throw new IllegalArgumentException(
                "Elegí un intervalo de hasta 370 días");
        RangeReport result=new RangeReport(from,until);
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor sessions=db.rawQuery(
            "SELECT _id,start_ms FROM sessions WHERE start_ms<? "+
            "AND last_ms>? ORDER BY start_ms",
            new String[]{Long.toString(until),Long.toString(from)})){
            while(sessions.moveToNext()){
                long sessionId=sessions.getLong(0),started=sessions.getLong(1);
                String id=Long.toString(sessionId);
                T1AutoRangeMath.Cumulative previous=null;
                try(Cursor base=db.rawQuery(
                    "SELECT t_epoch_ms,km_oem_cum,km_gps_cum,"+
                    "hv_out_cum,hv_in_cum FROM samples "+
                    "WHERE session_id=? AND t_epoch_ms<=? "+
                    "ORDER BY t_epoch_ms DESC LIMIT 1",
                    new String[]{id,Long.toString(from)})){
                    if(base.moveToFirst())previous=point(base);
                }
                long firstAfter=previous==null?from-1:previous.at;
                try(Cursor rows=db.rawQuery(
                    "SELECT t_epoch_ms,km_oem_cum,km_gps_cum,"+
                    "hv_out_cum,hv_in_cum FROM samples "+
                    "WHERE session_id=? AND t_epoch_ms>? "+
                    "ORDER BY t_epoch_ms",
                    new String[]{id,Long.toString(firstAfter)})){
                    while(rows.moveToNext()){
                        T1AutoRangeMath.Cumulative current=point(rows);
                        if(previous!=null)
                            result.add(sessionId,started,previous,current);
                        previous=current;
                        if(current.at>=until)break;
                    }
                }
            }
        }
        return result;
    }
    synchronized void exportWindowPoints(OutputStream out,long from,long until)
        throws IOException{
        if(until<=from)throw new IllegalArgumentException("Intervalo");
        writeLine(out,"sesion_id;fecha_epoch_ms;ready_1031;cable_1060;"+
            "marcha_1033;velocidad_kmh;soc;volt;amps;rpm;gps_lat;gps_lon;"+
            "precision_m;km_oem_acum;km_gps_acum;hv_salida_acum;"+
            "hv_entrada_acum\n");
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT session_id,t_epoch_ms,raw_ready,raw_cable,raw_gear,"+
                "speed_kmh,soc,volt,amps,rpm,gps_lat,gps_lon,"+
                "gps_accuracy_m,km_oem_cum,km_gps_cum,hv_out_cum,hv_in_cum "+
                "FROM samples WHERE t_epoch_ms>=? AND t_epoch_ms<? "+
                "ORDER BY t_epoch_ms",
            new String[]{Long.toString(from),Long.toString(until)})){
            while(c.moveToNext()){
                StringBuilder row=new StringBuilder();
                for(int i=0;i<c.getColumnCount();i++){
                    if(i>0)row.append(';');
                    if(!c.isNull(i))row.append(i<=4?
                        Long.toString(c.getLong(i)):fmtCsv(c.getDouble(i)));
                }
                writeLine(out,row.append('\n').toString());
            }
        }
        out.flush();
    }

    private static String fmtCsv(double x){
        return Double.isFinite(x)?String.format(Locale.ROOT,"%.6f",x):"";
    }
    static void writeLine(OutputStream out,String line)throws IOException{
        out.write(line.getBytes(StandardCharsets.UTF_8));
    }
    synchronized void export(OutputStream out,boolean allSamples)throws IOException{
        if(allSamples){
            writeLine(out,"sesion_id;fecha_epoch_ms;ready_1031;cable_1060;"+
                "marcha_1033;velocidad_kmh;soc_1062;volt_1063;amps_1064;"+
                "rpm_1035;gps_lat;gps_lon;gps_accuracy_m;km_oem_acum;"+
                "km_gps_acum;hv_salida_kwh_acum;hv_entrada_kwh_acum\n");
            try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT session_id,t_epoch_ms,raw_ready,raw_cable,"+
                "raw_gear,speed_kmh,soc,volt,amps,rpm,gps_lat,gps_lon,"+
                "gps_accuracy_m,km_oem_cum,km_gps_cum,hv_out_cum,hv_in_cum "+
                "FROM samples ORDER BY t_epoch_ms",null)){
                while(c.moveToNext()){
                    StringBuilder line=new StringBuilder();
                    for(int i=0;i<c.getColumnCount();i++){
                        if(i>0)line.append(';');
                        if(!c.isNull(i))
                            line.append(i<=4?Long.toString(c.getLong(i)):
                                fmtCsv(c.getDouble(i)));
                    }
                    writeLine(out,line.append('\n').toString());
                }
            }
        }else{
            writeLine(out,"sesion_id;inicio_epoch_ms;fin_epoch_ms;soc_inicial;"+
                "soc_final;km_oem;km_gps;kwh_salida;kwh_entrada_interna;"+
                "kwh_neto_bateria;muestras;estado;motivo_fin\n");
            for(Journey s:latest(500)){
                writeLine(out,s.id+";"+s.start+";"+s.end+";"+
                    fmtCsv(s.soc0)+";"+fmtCsv(s.soc1)+";"+
                    fmtCsv(s.km)+";"+fmtCsv(s.kmGps)+";"+
                    fmtCsv(s.hvOut)+";"+fmtCsv(s.hvIn)+";"+
                    fmtCsv(s.net())+";"+s.samples+";"+
                    (s.open==1?"ABIERTA":"CERRADA")+";"+
                    (s.reason==null?"":s.reason.replace(';',','))+"\n");
            }
        }
        out.flush();
    }
}
