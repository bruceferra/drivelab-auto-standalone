package ar.com.rango5gps.drivelabauto.receiver;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Pure-JVM: never overwrite historical CSV; keep last good Viaje 2. */
public final class T1HistoricalMasterRebuildTest {
    static void ok(boolean v,String message){if(!v)throw new AssertionError(message);}
    static void near(double value,double target,double tolerance,String name){
        ok(Math.abs(value-target)<=tolerance,
            name+": "+value+" vs "+target);
    }
    static void save(File file,String text)throws Exception{
        try(FileWriter out=new FileWriter(file)){out.write(text);}
    }
    static File makeMaster(File folder,String id,int events,boolean recGps)
            throws Exception{
        File out=new File(folder,"trip_"+id+".csv");
        StringBuilder data=new StringBuilder("timestamp_epoch_ms,gear_1033,"+
            "speed_oem_raw,distance_km_est,motor_on_rpm_candidate,"+
            "oem_500m_fuel_event_count,hv_gross_discharge_kwh_est\n");
        long time=1794499937000L;
        double km=0;
        for(int i=0;i<=400;i++){
            // 60 km/h for 1,200 seconds: calibrated ~18.65 km.
            if(i>0)km+=60*3/3600d*T1TripDistancePolicy.OEM_KM_FACTOR;
            double stored=recGps?km*.54:km;
            data.append(String.format(Locale.ROOT,
                "%d,4,60,%.6f,%d,%d,%.5f\n",
                time+i*3000,stored,i<250?0:1,
                (int)(i*events/400d),i*.003));
        }
        save(out,data.toString());
        return out;
    }
    static void masterCheck(File dir)throws Exception{
        File wrong=makeMaster(dir,"20261112_111217",37,true);
        StringBuilder events=new StringBuilder("timestamp_epoch_ms,channel,value_float_raw\n");
        for(int i=0;i<37;i++)events.append(
            "1794499937000,NAFTA_500_m,5.0\n");
        save(new File(dir,"trip_oem_20261112_111217.csv"),events.toString());
        T1MasterTripHistory.Entry e=T1MasterTripHistory.read(wrong);
        ok(e.corrected&&e.verified,"correct master if 500m witness agrees");
        near(e.originalKm,10.06,.4,"original GPS undercount");
        near(e.km,18.65,.3,"rebuilt OEM distance");
        near(e.liters,0.925,1e-8,"independent provisional 500m litres");
        ok(e.evKm>0&&e.iceKm>0,"motor split reconstructed");
        ok(new java.util.Scanner(wrong).hasNextLine(),"original remains");
        File good=makeMaster(dir,"20261112_130000",37,false);
        T1MasterTripHistory.Entry recent=T1MasterTripHistory.read(good);
        ok(!recent.corrected,"never fix already correct OEM master");
        File unknown=makeMaster(dir,"20261112_145000",0,true);
        T1MasterTripHistory.Entry unconfirmed=T1MasterTripHistory.read(unknown);
        ok(!unconfirmed.corrected&&!unconfirmed.verified,
            "no independent witness: preserve original");
        List<T1MasterTripHistory.Entry> closed=T1MasterTripHistory.closed(
            dir,"20261112_130000",true);
        ok(closed.size()==2,"active master excluded from history");
    }
    static void clientCheck(File folder)throws Exception{
        long start=1794499937000L,end=start+1200000L;
        String id=String.valueOf(start);
        File trip=new File(folder,"trip_20261112_111217.csv");
        String[] header=new String[42];
        for(int j=0;j<header.length;j++)header[j]="column"+j;
        header[0]="timestamp_epoch_ms";header[1]="gear_1033";
        header[2]="speed_oem_raw";header[16]="distance_km_est";
        header[22]="notes";header[24]="motor_on_rpm_candidate";
        StringBuilder contents=new StringBuilder(String.join(",",header)+"\n");
        String[] row=new String[42];
        Arrays.fill(row,"");
        for(int i=0;i<=400;i++){
            row[0]=Long.toString(start+i*3000);
            row[2]="60";row[16]=String.format(Locale.ROOT,"%.3f",i*.025);
            row[22]="GPS";row[24]="0";
            contents.append(String.join(",",row)).append("\n");
        }
        save(trip,contents.toString());
        String common=id+";"+start+";"+end+";test;20261112_111217;"+
            "10.0;37;1000;0.5;37;500;0.5;1000;2000\n";
        save(new File(folder,"client_trips.csv"),
            "id;start;end;name;trip;km;nf;raw;litres;ne;rawE;kwh;cost;price\n"+
                common);
        List<T1HistoricalDistanceRepair.Entry> corrections=
            T1HistoricalDistanceRepair.calculate(folder);
        ok(corrections.size()==1,"one client");
        T1HistoricalDistanceRepair.Entry c=corrections.get(0);
        ok(c.independentlyVerified(),"500m witness corroborates");
        near(c.km,18.65,.3,"client OEM reconstruction");
        T1HistoricalDistanceRepair.save(folder,corrections);
        T1HistoricalDistanceRepair.Entry recovered=
            T1HistoricalDistanceRepair.load(folder).get("1:"+id);
        ok(T1HistoricalDistanceRepair.matches(recovered,start,end,10.0),
            "sidecar matches only matching original record");
        ok(!T1HistoricalDistanceRepair.matches(recovered,start,end,10.2),
            "never overwrite changed original record");
        // Remove callback witness for a high-coverage GPS-led trip:
        // it must be marked ESTIMATED rather than OEM independently verified.
        save(new File(folder,"client_trips.csv"),
            "id;start;end;name;trip;km;nf;raw;litres;ne;rawE;kwh;cost;price\n"+
            id+";"+start+";"+end+";test;20261112_111217;"+
            "10.0;0;1000;0.5;0;500;0.5;1000;2000\n");
        T1HistoricalDistanceRepair.Entry estimated=
            T1HistoricalDistanceRepair.calculate(folder).get(0);
        ok(estimated.applied()&&!estimated.independentlyVerified(),
            "high coverage without witness is ESTIMATED not verified");
        String slot2=id+";"+start+";"+end+";test;20261112_111217;"+
            "10.0;37;1000;0.5;37;500;0.5;1000;2000\n";
        save(new File(folder,"client_trips_2.csv"),
            "id;start;end;name;trip;km;nf;raw;litres;ne;rawE;kwh;cost;price\n"+
                slot2);
        T1HistoricalDistanceRepair.Entry protectedTrip=null;
        for(T1HistoricalDistanceRepair.Entry x:
                T1HistoricalDistanceRepair.calculate(folder))
            if(x.slot==2)protectedTrip=x;
        ok(protectedTrip!=null&&!protectedTrip.applied()&&
            "PROTEGIDO_ULTIMO_VIAJE_2".equals(protectedTrip.status),
            "latest good Viaje2 is protected");
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(System.getProperty("java.io.tmpdir"),
            "test_master_"+System.nanoTime());
        ok(dir.mkdirs(),"tmp folder");
        try{
            masterCheck(dir);
            for(File f:dir.listFiles())ok(f.delete(),"clear master "+f.getName());
            clientCheck(dir);
            System.out.println("OK: master historical validation and client all-trips audit, non-destructive");
        }finally{
            for(File f:dir.listFiles())f.delete();
            dir.delete();
        }
    }
}
