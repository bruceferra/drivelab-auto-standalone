package ar.com.rango5gps.drivelabauto.receiver;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

public final class T1DailyTripSummaryTest {
    private static void check(boolean ok,String msg){
        if(!ok)throw new AssertionError(msg);
    }
    private static void csv(File dir,String id,double... kms)throws Exception{
        try(FileWriter w=new FileWriter(new File(dir,"trip_"+id+".csv"))){
            w.write("timestamp_epoch_ms,gear_1033,distance_km_est,notes\n");
            for(int i=0;i<kms.length;i++)
                w.write((1794478313183L+i*3000)+",4,"+kms[i]+",valid\n");
        }
    }
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("t1-day-rollup").toFile();
        csv(dir,"20261112_071153",0,8.03408);
        csv(dir,"20261112_073616",0.002,9,32.69145);
        csv(dir,"20261111_183400",0,99);
        File torn=new File(dir,"trip_20261112_090100.csv");
        try(FileWriter w=new FileWriter(torn)){
            w.write("timestamp_epoch_ms,gear_1033,distance_km_est,notes\n");
            w.write("1794478313183,4,0.25,valid\n");
            w.write("1794478323183,4,100.0\n"); // torn row ignored
        }
        T1DailyTripSummary.Result got=T1DailyTripSummary.read(dir,"20261112");
        check(got.sessions==3,"distinct same-day sessions only");
        check(Math.abs(got.km-40.97553)<0.000001,
            "sum last valid cumulative km, never sum every row");
        check(got.unreadable==0,"torn line cannot invalidate valid rows");
        check(T1DailyTripSummary.read(dir,"20261111").km==99,
            "do not merge days");
        check(new File(dir,"trip_20261112_071153.csv").isFile(),
            "source recordings must remain intact");
        System.out.println("OK: daily stop-split rollup, no data mutation");
    }
}
