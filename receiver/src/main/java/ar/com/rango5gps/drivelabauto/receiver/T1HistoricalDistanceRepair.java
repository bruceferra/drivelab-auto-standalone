package ar.com.rango5gps.drivelabauto.receiver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Repairs historical, CLOSED client trip distance using original recorder
 * samples (OEM speed + timestamps). Never overwrites client_trips.csv nor
 * trip_*.csv. All proposed corrections live in a separate, audit-friendly
 * sidecar keyed by slot and original client ID.
 *
 * Independent OEM 500m event count must agree with speed-integrated distance.
 * Newest Viaje 2 is deliberately protected at owner's explicit request.
 */
final class T1HistoricalDistanceRepair {
    static final String FILE="client_distance_corrections_v1.csv";
    static final String HEADER="slot;client_id;start_ms;end_ms;original_km;"+
        "oem_km;ev_km;thermal_km;unknown_km;oem_500m_events;"+
        "oem_500m_km;valid_intervals;speed_samples;gps_samples;"+
        "status;algorithm\n";
    static final String METHOD="OEM_SPEED_CAL_22.9_24.55_V1";
    static final class Entry {
        final int slot;
        final String id,status;
        final long started,ended;
        final double originalKm,km,evKm,iceKm,unknownKm,eventsKm;
        final int events,validIntervals,speedSamples,gpsSamples;
        Entry(int slot,String id,long start,long end,double old,double reconstructed,
              double ev,double ice,double unknown,int count,int valid,
              int speed,int gps,String status){
            this.slot=slot;this.id=id;started=start;ended=end;
            originalKm=old;km=reconstructed;evKm=ev;iceKm=ice;
            unknownKm=unknown;events=count;eventsKm=count*.5;
            validIntervals=valid;speedSamples=speed;gpsSamples=gps;
            this.status=status;
        }
        boolean applied(){return "CORREGIDO".equals(status);}
    }
    private static final class Original {
        final int slot;final String id,tripChain;
        final long from,to;final double km;final int events;
        Original(int slot,String id,long a,long b,String chain,double km,int events){
            this.slot=slot;this.id=id;from=a;to=b;tripChain=chain;
            this.km=km;this.events=events;
        }
    }
    private static double dbl(String x){
        try{double n=Double.parseDouble(x);return Double.isFinite(n)?n:Double.NaN;}
        catch(Exception e){return Double.NaN;}
    }
    private static long lng(String x){
        try{return Long.parseLong(x);}catch(Exception e){return 0L;}
    }
    static ArrayList<Original> originals(File folder,int slot)throws IOException{
        ArrayList<Original> rows=new ArrayList<>();
        File file=new File(folder,slot==2?"client_trips_2.csv":"client_trips.csv");
        if(!file.isFile())return rows;
        try(BufferedReader b=new BufferedReader(new FileReader(file))){
            b.readLine();String line;
            LinkedHashMap<String,Original> dedup=new LinkedHashMap<>();
            while((line=b.readLine())!=null){
                String[] x=line.split(";",-1);
                if(x.length<14)continue;
                long start=lng(x[1]),end=lng(x[2]);
                double km=dbl(x[5]);
                int nf=(int)lng(x[6]);
                if(start<=0||end<start||!Double.isFinite(km)||km<0||
                    nf<0||nf>1000000||!x[0].matches("[0-9]{10,16}"))continue;
                dedup.put(x[0],new Original(slot,x[0],start,end,x[4],km,nf));
            }
            rows.addAll(dedup.values());
        }
        return rows;
    }
    private static final class Aggregate {
        int valid,speed,gps;
        double km,ev,ice,unknown;
    }
    private static Aggregate scan(File folder,Original trip)throws IOException{
        Aggregate sum=new Aggregate();
        String[] chain=trip.tripChain.split("[+]",-1);
        for(String raw:chain){
            if(!raw.matches("[0-9]{8}_[0-9]{6}"))continue;
            File csv=new File(folder,"trip_"+raw+".csv");
            if(!csv.isFile())continue;
            try(BufferedReader b=new BufferedReader(new FileReader(csv))){
                String header=b.readLine();
                if(header==null||!header.startsWith("timestamp_epoch_ms,"))continue;
                int expected=header.split(",",-1).length;
                long prior=0;String line;
                while((line=b.readLine())!=null){
                    String[] x=line.split(",",-1);
                    if(x.length!=expected||x.length<25)continue;
                    long now=lng(x[0]);
                    double speed=dbl(x[2]);
                    long elapsed=now-prior;
                    prior=now;
                    if(now<trip.from||now>trip.to)continue;
                    if(elapsed<500||elapsed>10000)continue;
                    if(!Double.isFinite(speed)||speed<0||speed>220)continue;
                    String source=x[15];
                    if(source.startsWith("GPS"))sum.gps++;
                    if(source.startsWith("OEM_SPEED")||source.startsWith("OEM_SPEED_ZERO"))
                        sum.speed++;
                    double step=speed*elapsed/3600000d*
                        T1TripDistancePolicy.OEM_KM_FACTOR;
                    sum.km+=step;sum.valid++;
                    double on=dbl(x[24]);
                    if(on==0)sum.ev+=step;
                    else if(on==1)sum.ice+=step;
                    else sum.unknown+=step;
                }
            }
        }
        return sum;
    }
    private static Entry inspect(File folder,Original trip,String protectedNewestId){
        Aggregate a=new Aggregate();
        String status="REVISAR";
        try{
            a=scan(folder,trip);
            double eventsKm=trip.events*.5;
            boolean independentOk=trip.events>=2&&a.valid>=10&&
                Math.abs(a.km-eventsKm)<=Math.max(1.5,eventsKm*.12);
            boolean needsCorrection=Math.abs(a.km-trip.km)>
                Math.max(.4,trip.km*.03);
            // On v0.2.56 OEM became authoritative; don't apply a second
            // adjustment to trips already recorded by the fixed recorder.
            boolean alreadyOem=a.speed>=5&&a.speed>=a.gps*4;
            boolean protectedTrip=trip.slot==2&&trip.id.equals(protectedNewestId);
            if(protectedTrip)status="PROTEGIDO_ULTIMO_VIAJE_2";
            else if(alreadyOem)status="YA_OEM";
            else if(!needsCorrection)status="SIN_CAMBIO";
            else if(independentOk)status="CORREGIDO";
            else status="REVISAR_MUESTRAS_OEM";
        }catch(IOException ex){status="SIN_CSV"; }
        return new Entry(trip.slot,trip.id,trip.from,trip.to,trip.km,
            a.km,a.ev,a.ice,a.unknown,trip.events,a.valid,
            a.speed,a.gps,status);
    }
    static ArrayList<Entry> calculate(File folder)throws IOException{
        ArrayList<Original> all=originals(folder,1);
        ArrayList<Original> second=originals(folder,2);
        String latestSecond="";
        long latestEnd=0;
        for(Original o:second)if(o.to>=latestEnd){
            latestEnd=o.to;latestSecond=o.id;
        }
        all.addAll(second);
        ArrayList<Entry> result=new ArrayList<>();
        for(Original o:all)result.add(inspect(folder,o,latestSecond));
        return result;
    }
    static void save(File dir,List<Entry> corrections)throws IOException{
        if(dir==null||!dir.isDirectory())throw new IOException("Sin carpeta de viajes");
        File tmp=new File(dir,FILE+".part"),dest=new File(dir,FILE);
        try(FileOutputStream out=new FileOutputStream(tmp)){
            out.write(HEADER.getBytes(StandardCharsets.UTF_8));
            for(Entry x:corrections){
                String line=x.slot+";"+x.id+";"+x.started+";"+x.ended+";"+
                    Double.toString(x.originalKm)+";"+Double.toString(x.km)+";"+
                    Double.toString(x.evKm)+";"+Double.toString(x.iceKm)+";"+
                    Double.toString(x.unknownKm)+";"+x.events+";"+
                    Double.toString(x.eventsKm)+";"+x.validIntervals+";"+
                    x.speedSamples+";"+x.gpsSamples+";"+x.status+";"+METHOD+"\n";
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
            out.getFD().sync();
        }
        if(!tmp.renameTo(dest)){
            // Backward-compatible Android filesystem fallback. Preserve the
            // existing corrections if replacement fails.
            if(dest.exists()&&!dest.delete()||!tmp.renameTo(dest))
                throw new IOException("No se guardó informe de correcciones");
        }
    }
    static Map<String,Entry> load(File folder){
        Map<String,Entry> out=new HashMap<>();
        File file=new File(folder,FILE);
        if(!file.isFile())return out;
        try(BufferedReader b=new BufferedReader(new FileReader(file))){
            String first=b.readLine();
            if(first==null||!first.equals(HEADER.trim()))return out;
            String line;
            while((line=b.readLine())!=null){
                String[] x=line.split(";",-1);
                if(x.length!=16||!x[1].matches("[0-9]{10,16}")||
                    !METHOD.equals(x[15]))continue;
                int slot=(int)lng(x[0]);
                if(slot!=1&&slot!=2)continue;
                Entry e=new Entry(slot,x[1],lng(x[2]),lng(x[3]),
                    dbl(x[4]),dbl(x[5]),dbl(x[6]),dbl(x[7]),dbl(x[8]),
                    (int)lng(x[9]),(int)lng(x[11]),(int)lng(x[12]),
                    (int)lng(x[13]),x[14]);
                out.put(slot+":"+x[1],e);
            }
        }catch(Exception ignored){}
        return out;
    }
    static boolean matches(Entry correction,long started,long ended,double old){
        return correction!=null&&correction.applied()&&
            correction.started==started&&correction.ended==ended&&
            Double.isFinite(correction.km)&&correction.km>0&&
            Math.abs(correction.originalKm-old)<.001;
    }
    private T1HistoricalDistanceRepair(){}
}
