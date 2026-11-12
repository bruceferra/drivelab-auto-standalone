package ar.com.rango5gps.drivelabauto.receiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/** Historical, closed JOURNADA CSV reader.
 * Never modifies original samples. Validates reconstructed speed-distance
 * against independent OEM 500m callback counts before applying a correction.
 * All fuel/kWh metrics remain experimental, never treated as metered fuel.
 */
final class T1MasterTripHistory {
    static final class Entry {
        final String id;
        final long started,ended;
        final double originalKm,km,evKm,iceKm,unknownKm,liters,electricityKwh;
        final int samples,fuelEvents;
        final boolean corrected,verified;
        final String status;
        Entry(String id,long start,long end,double original,double shown,
              double ev,double ice,double unknown,double fuel,double kwh,
              int n,int events,boolean applied,boolean verified,String status){
            this.id=id;started=start;ended=end;originalKm=original;km=shown;
            evKm=ev;iceKm=ice;unknownKm=unknown;liters=fuel;
            electricityKwh=kwh;samples=n;fuelEvents=events;
            corrected=applied;this.verified=verified;this.status=status;
        }
    }
    private static int index(String[] h,String column){
        for(int i=0;i<h.length;i++)if(column.equals(h[i]))return i;
        return -1;
    }
    private static double number(String[] row,int idx){
        if(idx<0||idx>=row.length||row[idx].trim().isEmpty())
            return Double.NaN;
        try{double n=Double.parseDouble(row[idx]);return Double.isFinite(n)?n:Double.NaN;}
        catch(NumberFormatException ex){return Double.NaN;}
    }
    private static final class EventTotals {
        int fuelCount,electricCount;
        double fuelRaw,electricRaw;
        boolean validFuel=true,validElectric=true;
    }
    private static EventTotals readOemEvents(File csv,String id)throws IOException{
        EventTotals sums=new EventTotals();
        File events=new File(csv.getParentFile(),"trip_oem_"+id+".csv");
        if(!events.isFile())return sums;
        try(BufferedReader reader=new BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(events),
                java.nio.charset.StandardCharsets.UTF_8))){
            String header=reader.readLine();
            if(header==null||!header.startsWith("timestamp_epoch_ms,channel,"))
                return sums;
            String line;
            while((line=reader.readLine())!=null){
                String[] f=line.split(",",-1);
                if(f.length!=3)continue;
                double value=number(f,2);
                if("NAFTA_500_m".equals(f[1])){
                    sums.fuelCount++;
                    if(Double.isFinite(value)&&value>=0&&value<=100)
                        sums.fuelRaw+=value;
                    else sums.validFuel=false;
                }else if("ELÉCTRICO_500_m".equals(f[1])){
                    sums.electricCount++;
                    if(Double.isFinite(value)&&Math.abs(value)<=150)
                        sums.electricRaw+=value;
                    else sums.validElectric=false;
                }
            }
        }
        return sums;
    }
    static Entry read(File source)throws IOException{
        if(source==null||!source.isFile()||
            !source.getName().matches("trip_[0-9]{8}_[0-9]{6}\\.csv"))
            throw new IOException("No es un TRIP OEM local");
        final String id=source.getName().substring(5,source.getName().length()-4);
        long start=0,end=0,previous=0;
        double original=Double.NaN,rebuilt=0,ev=0,ice=0,unknown=0,
            kwh=Double.NaN;
        int n=0,valid=0,events=0;
        try(BufferedReader reader=new BufferedReader(new FileReader(source))){
            String header=reader.readLine();
            if(header==null||!header.startsWith("timestamp_epoch_ms,gear_1033"))
                throw new IOException("TRIP sin cabecera OEM conocida");
            String[] h=header.split(",",-1);
            int it=index(h,"timestamp_epoch_ms"),
                iv=index(h,"speed_oem_raw"),
                ik=index(h,"distance_km_est"),
                im=index(h,"motor_on_rpm_candidate"),
                ie=index(h,"oem_500m_fuel_event_count"),
                ikh=index(h,"hv_gross_discharge_kwh_est");
            if(it<0||iv<0||ik<0)throw new IOException("Faltan km o velocidad");
            String line;
            while((line=reader.readLine())!=null){
                String[] row=line.split(",",-1);
                if(row.length!=h.length)continue;
                double t=number(row,it),d=number(row,ik);
                if(!Double.isFinite(t)||t<1600000000000d||
                    !Double.isFinite(d)||d<0)continue;
                long ms=(long)t;
                if(start==0)start=ms;
                end=ms;n++;
                original=d;
                double e=number(row,ie);
                if(Double.isFinite(e)&&e>=0&&e<1000000)
                    events=Math.max(events,(int)e);
                double electricity=number(row,ikh);
                if(Double.isFinite(electricity)&&electricity>=0)
                    kwh=electricity;
                long dt=ms-previous;
                previous=ms;
                double speed=number(row,iv);
                if(dt<500||dt>10000||!Double.isFinite(speed)||
                    speed<0||speed>220)continue;
                double delta=speed*dt/3600000.0*
                    T1TripDistancePolicy.OEM_KM_FACTOR;
                rebuilt+=delta;valid++;
                double motor=number(row,im);
                if(motor==0)ev+=delta;
                else if(motor==1)ice+=delta;
                else unknown+=delta;
            }
        }
        if(n<2)throw new IOException("Jornada sin datos completos");
        // The count 500m is an independent travel witness, not a fuel meter.
        double witness=events*.5;
        boolean verified=valid>=10&&events>=4&&
            Math.abs(rebuilt-witness)<=Math.max(1.5,witness*.08);
        boolean changed=verified&&
            Math.abs(rebuilt-original)>Math.max(.5,original*.03);
        // The CSV's gas_l_proxy is computed from the RECORDED distance and
        // is not measured fuel: NEVER reuse it after correcting distance.
        // Only use the independent raw 500m OEM callback file (same source
        // as the client's experimental L calculation). Missing/mismatched
        // callbacks produce an explicit unknown value, never zero litres.
        EventTotals totals=readOemEvents(source,id);
        boolean fuelCountMatches=events>0&&totals.fuelCount>0&&
            Math.abs(events-totals.fuelCount)<=2;
        double experimentalLiters=fuelCountMatches&&totals.validFuel?
            totals.fuelRaw*.5/100.0:Double.NaN;
        String status=changed?"CORREGIDA OEM "+events+
            " eventos de 500 m":verified?
            "DISTANCIA CONFIRMADA POR OEM":
            "DISTANCIA SIN VERIFICAR; original preservado";
        return new Entry(id,start,end,original,changed?rebuilt:original,
            changed?ev:Double.NaN,
            changed?ice:Double.NaN,
            changed?unknown:Double.NaN,
            experimentalLiters,kwh,n,events,changed,verified,status+
                (Double.isFinite(experimentalLiters)?
                    " · combustible OEM experimental":" · litros sin confirmar"));
    }
    static ArrayList<Entry> closed(File dir,String currentId,
                                   boolean currentOpen){
        ArrayList<Entry> out=new ArrayList<>();
        if(dir==null)return out;
        File[] files=dir.listFiles((folder,name)->
            name.matches("trip_[0-9]{8}_[0-9]{6}\\.csv"));
        if(files==null)return out;
        Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));
        for(File file:files){
            String id=file.getName().substring(5,file.getName().length()-4);
            if(currentOpen&&id.equals(currentId))continue;
            try{out.add(read(file));}
            catch(IOException ignored){}
            if(out.size()>=40)break;
        }
        return out;
    }
    private T1MasterTripHistory(){}
}
