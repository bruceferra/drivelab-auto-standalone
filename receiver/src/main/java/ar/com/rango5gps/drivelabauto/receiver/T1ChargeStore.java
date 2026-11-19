package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Independent, private SQLite charge history. No reference to T1Launcher
 * files, shared UID, Android root, GPS trips or navigation services.
 * Grid-meter kWh and HV battery-in kWh are NEVER the same metric. */
final class T1ChargeStore extends SQLiteOpenHelper {
    static final String PREFS="t1_charge_auto",ENABLED="enabled",
        STATUS="status",LAST_CABLE="last_cable_raw",LAST_READ="last_read",
        CURRENT_SESSION="current_session_id",
        LAST_SOC="last_soc",LAST_VOLTS="last_volts",
        LAST_AMPS="last_amps",LAST_SAMPLE_OK="last_sample_ok",
        LAST_SAMPLE_AT="last_sample_at",
        LAST_VOLTAGE_PROBE="last_voltage_probe";
    static final String DB="t1_charge_sessions.db",CSV="grid_charges_auto.csv";
    static final class Record {
        long id,start,end,last,lastActive,chargeEnded;
        double socStart,socEnd,ah,kwh,meterKwh,price;
        int samples,cable,open;
        String source,note;
        String describe(){
            SimpleDateFormat fmt=new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault());
            double external=Double.isFinite(meterKwh)&&meterKwh>0?meterKwh:kwh;
            String basis=Double.isFinite(meterKwh)&&meterKwh>0?
                "RED (medidor)":"BATERÍA (est.)";
            return fmt.format(new Date(end>0?end:start))+
                " · "+(open==1?(kwh>0?"CARGANDO (HV estimado)":
                    "ENCHUFADO · flujo HV por confirmar"):
                    (kwh>0?"FINALIZADA":"FINALIZADA · sin HV verificado"))+
                " · "+fmt(socStart,0)+"→"+fmt(socEnd,0)+" %"+
                "\n"+fmt(kwh,2)+" kWh batería · "+
                fmt(ah,1)+" Ah · "+samples+" muestras"+
                "\n"+fmt(external,2)+" kWh "+basis+" · $ "+
                fmt(external*price,0)+" (tarifa $ "+fmt(price,2)+"/kWh)"+
                "\nInicio: "+fmt.format(new Date(start))+
                "\n"+(chargeEnded>0?
                    "Última corriente de carga observada: "+
                    fmt.format(new Date(chargeEnded))+
                    " (estimación, no confirmación del cargador)":
                    "Sin hora de corte de corriente confirmada")+
                (end>0?"\nFin de sesión / cable: "+
                    fmt.format(new Date(end)):"")+
                "\n"+source+" · "+(note==null?"":note);
        }
    }
    static String fmt(double n,int precision){
        return Double.isFinite(n)?
            String.format(Locale.getDefault(),"%."+precision+"f",n):"—";
    }
    T1ChargeStore(Context ctx){super(ctx,DB,null,2);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sessions("+
            "_id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL DEFAULT 0,"+
            "last_ms INTEGER NOT NULL DEFAULT 0,"+
            "soc_start REAL,soc_end REAL,ah REAL NOT NULL DEFAULT 0,"+
            "battery_kwh REAL NOT NULL DEFAULT 0,meter_kwh REAL,"+
            "price_kwh REAL NOT NULL DEFAULT 0,"+
            "samples INTEGER NOT NULL DEFAULT 0,"+
            "cable_raw INTEGER NOT NULL DEFAULT -1,"+
            "is_open INTEGER NOT NULL DEFAULT 1,"+
            "source TEXT NOT NULL,note TEXT DEFAULT '',"+
            "last_active_ms INTEGER NOT NULL DEFAULT 0,"+
            "charge_done_ms INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX ix_charge_end ON sessions(end_ms)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){
        if(from<2){
            db.execSQL("ALTER TABLE sessions ADD COLUMN "+
                "last_active_ms INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sessions ADD COLUMN "+
                "charge_done_ms INTEGER NOT NULL DEFAULT 0");
        }
    }
    private static double val(Cursor c,int col){
        return c.isNull(col)?Double.NaN:c.getDouble(col);
    }
    private static Record parse(Cursor c){
        Record r=new Record();
        r.id=c.getLong(0);r.start=c.getLong(1);r.end=c.getLong(2);
        r.last=c.getLong(3);r.socStart=val(c,4);r.socEnd=val(c,5);
        r.ah=c.getDouble(6);r.kwh=c.getDouble(7);r.meterKwh=val(c,8);
        r.price=c.getDouble(9);r.samples=c.getInt(10);r.cable=c.getInt(11);
        r.open=c.getInt(12);r.source=c.getString(13);r.note=c.getString(14);
        r.lastActive=c.getLong(15);r.chargeEnded=c.getLong(16);
        return r;
    }
    private static final String COLS="_id,start_ms,end_ms,last_ms,soc_start,"+
        "soc_end,ah,battery_kwh,meter_kwh,price_kwh,samples,cable_raw,"+
        "is_open,source,note,last_active_ms,charge_done_ms";
    synchronized Record openSession(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT "+COLS+" FROM sessions WHERE is_open=1 "+
                    "ORDER BY _id DESC LIMIT 1",null)){
            return c.moveToFirst()?parse(c):null;
        }
    }
    synchronized long begin(long at,double soc,double price,int cable){
        Record old=openSession();
        if(old!=null)return old.id;
        ContentValues v=new ContentValues();
        v.put("start_ms",at);v.put("last_ms",at);
        v.put("last_active_ms",at);
        if(T1ChargeMath.validSoc(soc))v.put("soc_start",soc);
        v.put("price_kwh",price);v.put("cable_raw",cable);
        v.put("source","OEM CABLE 1060");
        v.put("note","Cable 1060 confirmado. Solo contar energía HV "+
            "con 1063 V / 1064 A válidos; nunca es medidor de red");
        return getWritableDatabase().insertOrThrow("sessions",null,v);
    }
    synchronized void sample(long id,T1ChargeOemReader.Reading reading){
        if(id<=0)return;
        Record r=openSession();
        if(r==null||r.id!=id)return;
        long elapsed=reading.timeMs-r.last;
        double[] extra=T1ChargeMath.integrate(
            reading.cable,reading.volts,reading.amps,elapsed);
        ContentValues v=new ContentValues();
        v.put("last_ms",reading.timeMs);
        if(T1ChargeMath.validSoc(reading.soc))v.put("soc_end",reading.soc);
        v.put("cable_raw",reading.cable);
        if(extra[0]>0){
            v.put("ah",r.ah+extra[0]);
            v.put("battery_kwh",r.kwh+extra[1]);
            v.put("last_active_ms",reading.timeMs);
            // Charging resumed after a quiet period. Only trust the next
            // stable full SOC window, never freeze an unfinished session.
            v.put("charge_done_ms",0);
        }else if(r.chargeEnded<=0 && r.lastActive>0 &&
                 T1ChargeMath.plugged(reading.cable)&&
                 T1ChargeMath.validSoc(reading.soc)&&
                 reading.soc>=99.0 &&
                 reading.timeMs-r.lastActive>=300000L){
            // Vehicle stays plugged after reaching 100%. End of ENERGY
            // FLOW is an estimate: last valid sample, NOT unplug time.
            v.put("charge_done_ms",r.lastActive);
            v.put("note","Fin de flujo HV estimado tras 5 min "+
                "sin corriente a SOC>=99%, cable aún conectado");
        }
        v.put("samples",r.samples+1);
        getWritableDatabase().update("sessions",v,"_id=?",
            new String[]{Long.toString(id)});
    }
    synchronized boolean finish(long id,long at){
        Record old=openSession();
        if(old==null||old.id!=id)return false;
        ContentValues v=new ContentValues();
        v.put("is_open",0);
        v.put("end_ms",Math.max(at,old.last));
        if(old.chargeEnded==0)
            v.put("charge_done_ms",old.lastActive>0?
                old.lastActive:Math.max(at,old.last));
        int n=getWritableDatabase().update("sessions",v,"_id=?",
            new String[]{Long.toString(id)});
        return n==1;
    }
    synchronized long importLauncher(long finished,double soc0,double soc1,
                                     double ah,double batteryKwh,
                                     double meterKwh,double price)
        throws IllegalArgumentException {
        if(finished<=0||!T1ChargeMath.validSoc(soc0)||
           !T1ChargeMath.validSoc(soc1)||soc1<soc0||
           !Double.isFinite(ah)||ah<0||ah>300||
           !Double.isFinite(batteryKwh)||batteryKwh<=0||batteryKwh>150||
           !Double.isFinite(price)||price<0||price>100000||
           (Double.isFinite(meterKwh)&&
              (meterKwh<batteryKwh*0.5||meterKwh>150)))
            throw new IllegalArgumentException("Datos de carga inválidos");
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sessions WHERE "+
                    "ABS(CASE WHEN end_ms>0 THEN end_ms ELSE start_ms END - ?)<3600000",
                new String[]{Long.toString(finished)})){
            if(c.moveToFirst()&&c.getInt(0)>0)
                throw new IllegalArgumentException(
                    "Ya hay una sesión cercana a esa hora; evitar duplicados");
        }
        ContentValues v=new ContentValues();
        v.put("start_ms",finished);v.put("end_ms",finished);
        v.put("last_ms",finished);v.put("charge_done_ms",finished);
        v.put("soc_start",soc0);
        v.put("soc_end",soc1);v.put("ah",ah);
        v.put("battery_kwh",batteryKwh);
        if(Double.isFinite(meterKwh))v.put("meter_kwh",meterKwh);
        v.put("price_kwh",price);v.put("samples",0);
        v.put("cable_raw",1);v.put("is_open",0);
        v.put("source","T1LAUNCHER · IMPORTACIÓN MANUAL");
        v.put("note","Fecha final informada por usuario; "+
            "hora de corte NO verificada con vehículo");
        return getWritableDatabase().insertOrThrow("sessions",null,v);
    }
    /** Fix the already imported first charge without losing kWh/price/ID.
     * The manually supplied final timestamp remains owner-provided rather
     * than asserted as observed by Android or the OEM charging controller. */
    synchronized void correctManualHours(long id,long start,long end){
        if(start<=0||end<start||end>System.currentTimeMillis()+300000L ||
           end-start>172800000L)
            throw new IllegalArgumentException("Horas de carga inválidas");
        Record target=null;
        for(Record r:latest(500))
            if(r.id==id){target=r;break;}
        if(target==null||target.open!=0||
           !target.source.contains("IMPORTACIÓN MANUAL"))
            throw new IllegalArgumentException(
                "Sólo se corrigen cargas importadas manualmente");
        ContentValues update=new ContentValues();
        update.put("start_ms",start);
        update.put("end_ms",end);
        update.put("last_ms",end);
        update.put("charge_done_ms",end);
        update.put("note","Inicio y fin informados MANUALMENTE; "+
            "no corroborados por OEM");
        if(getWritableDatabase().update("sessions",update,"_id=?",
            new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("No se actualizó la sesión");
    }

    /** Owner-confirmed cleanup of a closed manually imported CHARGE only.
     * Never touches live OEM charge sessions or trip/journey samples.
     * Exact ID/source/status are rechecked atomically at delete time.
     */
    synchronized boolean deleteManualImport(long id){
        if(id<=0)throw new IllegalArgumentException("ID de carga inválido");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            long[] check=new long[1];
            try(Cursor c=db.rawQuery(
                    "SELECT COUNT(*) FROM sessions WHERE _id=? "+
                    "AND is_open=0 AND source LIKE '%IMPORTACIÓN MANUAL%'",
                    new String[]{Long.toString(id)})){
                if(c.moveToFirst())check[0]=c.getLong(0);
            }
            if(check[0]!=1)
                throw new IllegalStateException(
                    "No se borra: carga no manual, abierta o inexistente");
            int deleted=db.delete("sessions",
                "_id=? AND is_open=0 AND source LIKE ?",
                new String[]{Long.toString(id),"%IMPORTACIÓN MANUAL%"});
            if(deleted!=1)
                throw new IllegalStateException("La carga no pudo eliminarse");
            db.setTransactionSuccessful();
            return true;
        }finally{
            db.endTransaction();
        }
    }

    synchronized ArrayList<Record> latest(int max){
        ArrayList<Record> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT "+COLS+" FROM sessions ORDER BY "+
                    "COALESCE(NULLIF(end_ms,0),start_ms) DESC LIMIT "+
                    Math.max(1,Math.min(500,max)),null)){
            while(c.moveToNext())out.add(parse(c));
        }
        return out;
    }
    synchronized String csv(){
        StringBuilder b=new StringBuilder();
        b.append("id;inicio_epoch_ms;fin_epoch_ms;soc_inicial;soc_final;"+
            "ah_bateria;kwh_bateria_est;kwh_medidor_red;precio_kwh;"+
            "estado;origen;nota;ultima_corriente_ms;fin_flujo_estimado_ms\n");
        for(Record r:latest(500)){
            b.append(r.id).append(';').append(r.start).append(';')
                .append(r.end).append(';').append(fmtCsv(r.socStart)).append(';')
                .append(fmtCsv(r.socEnd)).append(';').append(fmtCsv(r.ah))
                .append(';').append(fmtCsv(r.kwh)).append(';')
                .append(fmtCsv(r.meterKwh)).append(';')
                .append(fmtCsv(r.price)).append(';')
                .append(r.open==1?"ABIERTA":"CERRADA").append(';')
                .append(clean(r.source)).append(';')
                .append(clean(r.note)).append(';')
                .append(r.lastActive).append(';')
                .append(r.chargeEnded).append('\n');
        }
        return b.toString();
    }
    private static String fmtCsv(double x){
        return Double.isFinite(x)?
            String.format(Locale.ROOT,"%.5f",x):"";
    }
    private static String clean(String x){
        return x==null?"":x.replace(';',',')
            .replace('\r',' ').replace('\n',' ');
    }
    /** Separate auto CSV: do not overwrite existing manual grid_charges.csv.
     * A battery-in estimate is a lower bound for grid draw (losses excluded),
     * so it is ALWAYS labeled ESTIMADO in the financial projection.
     */
    synchronized void exportForCosts(Context context)throws IOException{
        File dst=new File(T1TripRecorderService.tripDir(context),CSV);
        File temp=new File(dst.getParentFile(),CSV+".tmp");
        StringBuilder body=new StringBuilder(
            "fecha_epoch_ms;kwh_red_medidos;precio_kwh;importe_ars;nota\n");
        for(Record r:latest(500)){
            if(r.open!=0||r.end<=0)continue;
            boolean metered=Double.isFinite(r.meterKwh)&&r.meterKwh>0;
            double purchased=metered?r.meterKwh:r.kwh;
            if(!Double.isFinite(purchased)||purchased<=0)continue;
            body.append(r.chargeEnded>0?r.chargeEnded:r.end)
                .append(';').append(fmtCsv(purchased))
                .append(';').append(fmtCsv(r.price)).append(';')
                .append(fmtCsv(purchased*r.price)).append(';')
                .append("DRIVELAB_AUTO_ID=").append(r.id).append(' ')
                .append(metered?"RED MEDIDOR":"BATERIA_ESTIMADA_NO_MEDIDOR")
                .append(" · ").append(clean(r.source)).append('\n');
        }
        try(FileOutputStream out=new FileOutputStream(temp,false)){
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();out.getFD().sync();
        }
        if(!temp.renameTo(dst))
            throw new IOException("No se pudo publicar CSV automático");
    }
}
