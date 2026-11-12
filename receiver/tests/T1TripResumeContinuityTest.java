package ar.com.rango5gps.drivelabauto.receiver;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

/** Pure JVM acceptance test for CSV recovery and client checkpoint safety. */
public final class T1TripResumeContinuityTest {
    static void ok(boolean cond,String msg){
        if(!cond)throw new AssertionError(msg);
    }
    static String header(){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<42;i++){
            if(i>0)s.append(',');
            s.append(i==0?"timestamp_epoch_ms":i==1?"gear_1033":"column"+i);
        }
        return s.append('\n').toString();
    }
    static String row(long ts,double distance,int events){
        String[] v=new String[42];
        java.util.Arrays.fill(v,"0");
        v[0]=Long.toString(ts);
        v[16]=Double.toString(distance);
        v[17]="1.25";v[18]="0.52";
        v[36]=Integer.toString(events);
        v[37]=Integer.toString(events);
        return String.join(",",v)+"\n";
    }
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("t1-resume").toFile();
        String id="20261112_071153";
        File trip=new File(dir,"trip_"+id+".csv");
        try(FileWriter out=new FileWriter(trip)){
            out.write(header());
            out.write(row(1794478313183L,0.0,0));
            out.write(row(1794478403183L,3.125,2));
            out.write("1794478463183,1,0,PARTIAL_WITHOUT_DISTANCE");
        }
        T1TripRecoveryState.State restored=T1TripRecoveryState.read(trip);
        ok(restored.samples==2,"Only complete samples count after torn row");
        ok(restored.timestamp==1794478403183L,"Last valid timestamp");
        ok(restored.km==3.125,"Distance persisted, not reset to zero");
        ok(restored.hvOut==1.25&&restored.hvIn==0.52,
            "Existing charge and discharge accumulators survive restart");
        File evt=new File(dir,"trip_oem_"+id+".csv");
        try(FileWriter out=new FileWriter(evt)){
            out.write("timestamp_epoch_ms,channel,value_float_raw\n");
            out.write("1794478353183,NAFTA_500_m,5.0\n");
            out.write("1794478353184,ELÉCTRICO_500_m,-2.0\n");
            out.write("1794478393183,NAFTA_500_m,4.0\n");
            out.write("1794478393184,ELÉCTRICO_500_m,-1.0\n");
        }
        T1JourneyRecoveryFile.End old=T1JourneyRecoveryFile.read(dir,id);
        ok(old.fuelComplete&&old.electricComplete,"All OEM 500m events recovered");
        ok(old.fuelCount==2&&old.electricCount==2,
            "Original OEM callbacks and counters retained");
        ok(old.km==3.125&&old.rawFuel==9&&old.rawElectric==-3,
            "Original distance and event sums retained");
        T1JourneyMetrics.Checkpoint before=new T1JourneyMetrics.Checkpoint(
            id,1794478313183L,0,0,0,0,0);
        T1JourneyMetrics.Checkpoint after=new T1JourneyMetrics.Checkpoint(
            id,old.timestamp,old.km,old.rawFuel,old.rawElectric,
            old.fuelCount,old.electricCount);
        T1JourneyMetrics.Result original=T1JourneyMetrics.between(
            before,after,2129.0);
        ok(original.km==3.125&&original.fuelEvents==2,
            "Customer original checkpoint is preserved");
        T1JourneyMetrics.Checkpoint different=new T1JourneyMetrics.Checkpoint(
            "20261112_073616",1794478803183L,1,0,0,0,0);
        boolean rejected=false;
        try{T1JourneyMetrics.between(before,different,2129.0);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"Never secretly mix customer checkpoints across days");
        ok(!new File(dir,"client_trips.csv").exists(),
            "No automatic client finalization during recovery");
        System.out.println("OK: continuous OEM session restores original CSV "+
            "samples and 500m events without auto-finishing a customer");
    }
}
