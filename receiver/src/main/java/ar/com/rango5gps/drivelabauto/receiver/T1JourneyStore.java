package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The whole-day TRIP keeps recording OEM samples. A passenger trip is a LOCAL
 * checkpoint pair inside that one TRIP; it does not restart callbacks, maps
 * or independent trips. No location or identifying passenger data is uploaded.
 */
final class T1JourneyStore {
    private static final String PREFS="t1_passenger_journeys";
    private static final String ACTIVE="active",LABEL="label",TIME="start_time",
        TRIP="trip_id",KM="km",FUEL="fuel",ELEC="electric",N_FUEL="nf",N_ELEC="ne";
    private static final String CSV_HEADER=
        "id;inicio;fin;etiqueta;trip_id;km_est_oem_velocidad;eventos_nafta;"+
        "nafta_raw;litros_experimentales;eventos_electricidad;electricidad_raw;"+
        "kwh_neto_experimental;costo_nafta_experimental;precio_nafta\n";
    private static final String CHARGES_HEADER=
        "fecha_epoch_ms;kwh_red_medidos;precio_kwh;importe_ars;nota\n";

    static final class Entry {
        final String id,label,tripId;
        final long started,ended;
        final double km,liters,electricNet,fuelCost;
        final int fuelEvents,electricEvents;
        Entry(String id,long started,long ended,String label,String tripId,
              double km,int fuelEvents,double liters,int electricEvents,
              double electricNet,double fuelCost){
            this.id=id;this.started=started;this.ended=ended;
            this.label=label;this.tripId=tripId;this.km=km;
            this.fuelEvents=fuelEvents;this.electricEvents=electricEvents;
            this.liters=liters;this.electricNet=electricNet;this.fuelCost=fuelCost;
        }
    }
    static final class Totals {
        int trips,charges;
        double km,liters,electricNet,fuelCost,gridKwh,gridCost;
        void add(Entry e){
            trips++;km+=e.km;
            if(Double.isFinite(e.liters))liters+=e.liters;
            if(Double.isFinite(e.electricNet))electricNet+=e.electricNet;
            if(Double.isFinite(e.fuelCost))fuelCost+=e.fuelCost;
        }
    }
    private static SharedPreferences p(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static SharedPreferences master(Context c){
        return c.getApplicationContext().getSharedPreferences(
            T1TripRecorderService.PREFS,Context.MODE_PRIVATE);
    }
    static String cleanLabel(String value) {
        String v=value==null?"":value.trim().replaceAll("[\\r\\n;\\t]"," ");
        if(v.isEmpty())v="Trayecto";
        if(v.length()>45)v=v.substring(0,45);
        if("=+-@".indexOf(v.charAt(0))>=0)v="'"+v; // spreadsheet formula neutralization
        return v;
    }
    static T1JourneyMetrics.Checkpoint now(Context c) {
        SharedPreferences m=master(c);
        String trip=m.getString(T1TripRecorderService.ID,"");
        if(!trip.matches("[0-9]{8}_[0-9]{6}")||
                m.getInt(T1TripRecorderService.SAMPLES,0)<1)
            throw new IllegalStateException("Primero iniciá JORNADA y esperá una muestra");
        return new T1JourneyMetrics.Checkpoint(trip,
            System.currentTimeMillis(),m.getFloat(T1TripRecorderService.DISTANCE,0f),
            m.getFloat(T1TripRecorderService.SEG_FUEL_RAW_SUM,0f),
            m.getFloat(T1TripRecorderService.SEG_ELEC_RAW_SUM,0f),
            m.getInt(T1TripRecorderService.SEG_FUEL_COUNT,0),
            m.getInt(T1TripRecorderService.SEG_ELEC_COUNT,0));
    }
    static boolean active(Context c){return p(c).getBoolean(ACTIVE,false);}
    static String activeLabel(Context c){return p(c).getString(LABEL,"");}
    static synchronized String start(Context c,String label){
        if(active(c))return "Ya hay un trayecto activo; finalizalo primero.";
        if(!master(c).getBoolean(T1TripRecorderService.RUNNING,false))
            return "Primero iniciá JORNADA para grabar el TRIP OEM.";
        T1JourneyMetrics.Checkpoint mark=now(c);
        boolean ok=p(c).edit().putBoolean(ACTIVE,true)
            .putString(LABEL,cleanLabel(label)).putString(TRIP,mark.tripId)
            .putLong(TIME,mark.time).putFloat(KM,(float)mark.km)
            .putFloat(FUEL,(float)mark.rawFuel)
            .putFloat(ELEC,(float)mark.rawElectric)
            .putInt(N_FUEL,mark.fuelCount).putInt(N_ELEC,mark.electricCount)
            .commit();
        return ok?"Trayecto iniciado: "+cleanLabel(label)+
                " · sin reiniciar la jornada":"No se guardó el inicio";
    }
    private static T1JourneyMetrics.Checkpoint startCheckpoint(Context c){
        SharedPreferences z=p(c);
        return new T1JourneyMetrics.Checkpoint(z.getString(TRIP,""),
            z.getLong(TIME,0),z.getFloat(KM,0),z.getFloat(FUEL,0),
            z.getFloat(ELEC,0),z.getInt(N_FUEL,0),z.getInt(N_ELEC,0));
    }
    static synchronized T1JourneyMetrics.Result running(Context c) {
        if(!active(c))return null;
        try{return T1JourneyMetrics.between(startCheckpoint(c),now(c),
            master(c).getFloat(T1TripRecorderService.FUEL_PRICE,2129f));}
        catch(IllegalArgumentException|IllegalStateException ex){return null;}
    }
    static synchronized String finish(Context c){
        if(!active(c))return "No hay trayecto activo.";
        T1JourneyMetrics.Checkpoint start=startCheckpoint(c),end=now(c);
        if(!start.tripId.equals(end.tripId))
            return "TRIP cambió. No se juntan viajes distintos: revisá la jornada.";
        T1JourneyMetrics.Result result;
        try{result=T1JourneyMetrics.between(start,end,
            master(c).getFloat(T1TripRecorderService.FUEL_PRICE,2129f));}
        catch(IllegalArgumentException ex){return "Lecturas incoherentes: no se guardó";}
        String id=Long.toString(start.time);
        String label=cleanLabel(activeLabel(c));
        // Idempotent even when Android crashes between CSV flush and clearing prefs.
        for(Entry e:history(c))if(id.equals(e.id)){
            p(c).edit().clear().commit();
            return "Trayecto anterior ya estaba guardado.";
        }
        String row=id+";"+start.time+";"+end.time+";"+label+";"+start.tripId+
            ";"+db(result.km)+";"+result.fuelEvents+";"+db(result.rawFuel)+
            ";"+db(result.fuelLitersCandidate)+";"+result.electricEvents+
            ";"+db(result.rawElectric)+";"+db(result.electricNetKwhCandidate)+
            ";"+db(result.fuelCostCandidate)+";"+
            db(master(c).getFloat(T1TripRecorderService.FUEL_PRICE,2129f))+"\n";
        try{
            append(new File(T1TripRecorderService.tripDir(c),"client_trips.csv"),CSV_HEADER,row);
        }catch(IOException ex){return "No se guardó: "+ex.getClass().getSimpleName();}
        p(c).edit().clear().commit();
        return "GUARDADO: "+label+" · "+T1JourneyMetrics.fmt(result.km,2)+
            " km (OEM velocidad, sin calibrar) · "+
            (result.fuelAvailable?T1JourneyMetrics.fmt(result.fuelLitersCandidate,3)+
            " L experimentales":"sin tramo OEM completo")+
            " · costo de nafta experimental, NO costo total";
    }
    static synchronized void recordCharge(Context c,double kwh,double price,String note)
        throws IOException {
        if(!Double.isFinite(kwh)||kwh<=0||kwh>150||
                !Double.isFinite(price)||price<0||price>100000)
            throw new IllegalArgumentException("Revisá kWh medidos y tarifa");
        String row=System.currentTimeMillis()+";"+db(kwh)+";"+db(price)+
            ";"+db(kwh*price)+";"+cleanLabel(note)+"\n";
        append(new File(T1TripRecorderService.tripDir(c),"grid_charges.csv"),
            CHARGES_HEADER,row);
    }
    private static void append(File file,String header,String row)throws IOException {
        boolean first=!file.exists()||file.length()==0;
        try(FileOutputStream f=new FileOutputStream(file,true)){
            if(first)f.write(header.getBytes(StandardCharsets.UTF_8));
            f.write(row.getBytes(StandardCharsets.UTF_8));
            f.flush();
            f.getFD().sync();
        }
    }
    private static String db(double v){
        return Double.isFinite(v)?String.format(Locale.ROOT,"%.6f",v):"";
    }
    private static double number(String raw){
        try{return raw.isEmpty()?Double.NaN:Double.parseDouble(raw);}
        catch(Exception e){return Double.NaN;}
    }
    private static long time(String raw){
        try{return Long.parseLong(raw);}catch(Exception e){return 0L;}
    }
    static ArrayList<Entry> history(Context c) {
        ArrayList<Entry> out=new ArrayList<>();
        File file=new File(T1TripRecorderService.tripDir(c),"client_trips.csv");
        if(!file.isFile())return out;
        LinkedHashMap<String,Entry> dedup=new LinkedHashMap<>();
        try(BufferedReader reader=new BufferedReader(new FileReader(file))){
            String line=reader.readLine(); // CSV header
            while((line=reader.readLine())!=null){
                String[] f=line.split(";",-1);
                if(f.length<14)continue;
                long a=time(f[1]),b=time(f[2]);
                if(a<=0||b<a||!Double.isFinite(number(f[5])))continue;
                int nf=Integer.parseInt(f[6]),ne=Integer.parseInt(f[9]);
                Entry e=new Entry(f[0],a,b,f[3],f[4],number(f[5]),
                    nf,number(f[8]),ne,number(f[11]),number(f[12]));
                dedup.put(f[0],e);
            }
        }catch(IOException|NumberFormatException ignored){}
        out.addAll(dedup.values());
        return out;
    }
    static Totals totals(Context c,String datePrefix){
        Totals t=new Totals();
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
        for(Entry e:history(c)){
            if(fmt.format(new Date(e.started)).startsWith(datePrefix))t.add(e);
        }
        File file=new File(T1TripRecorderService.tripDir(c),"grid_charges.csv");
        if(file.isFile())try(BufferedReader reader=new BufferedReader(new FileReader(file))){
            reader.readLine();String line;
            while((line=reader.readLine())!=null){
                String[] f=line.split(";",-1);
                if(f.length<4)continue;
                if(!fmt.format(new Date(time(f[0]))).startsWith(datePrefix))continue;
                double k=number(f[1]),cost=number(f[3]);
                if(Double.isFinite(k)&&Double.isFinite(cost)){
                    t.charges++;t.gridKwh+=k;t.gridCost+=cost;
                }
            }
        }catch(IOException ignored){}
        return t;
    }
    static String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT)
        .format(new Date());}
    static String month(){return today().substring(0,7);}
    private T1JourneyStore(){}
}
