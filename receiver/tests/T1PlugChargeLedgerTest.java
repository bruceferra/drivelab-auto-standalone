package ar.com.rango5gps.drivelabauto.receiver;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
public final class T1PlugChargeLedgerTest {
    private static void near(double a,double b){
        if(!Double.isFinite(a)||Math.abs(a-b)>0.0000001)
            throw new AssertionError(a+" expected "+b);
    }
    private static void assertUnknown(double x){
        if(!Double.isNaN(x))throw new AssertionError("Unlogged grid source MUST be unknown");
    }
    public static void main(String[] args)throws Exception{
        Map<String,T1PlugChargeLedger.Attribution> out=
            T1PlugChargeLedger.estimate(
                Arrays.asList(
                    new T1PlugChargeLedger.Charge(1000,8),
                    new T1PlugChargeLedger.Charge(4500,3)),
                Arrays.asList(
                    new T1PlugChargeLedger.Trip("unlogged",500,4),
                    new T1PlugChargeLedger.Trip("first",2000,5),
                    new T1PlugChargeLedger.Trip("second",3000,5),
                    new T1PlugChargeLedger.Trip("regen",3500,-2),
                    new T1PlugChargeLedger.Trip("later",5000,4)));
        assertUnknown(out.get("unlogged").plugKwh);
        near(out.get("first").plugKwh,5);
        near(out.get("second").plugKwh,3);
        near(out.get("second").remainingKwh,2);
        near(out.get("regen").plugKwh,0);
        near(out.get("later").plugKwh,3);
        // The recorded external input is never counted beyond 8+3=11kWh.
        near(out.get("first").plugKwh+
             out.get("second").plugKwh+out.get("later").plugKwh,11);
        File f=Files.createTempFile("t1-plug-charge",".csv").toFile();
        try{
            Files.writeString(f.toPath(),
                "fecha_epoch_ms;kwh_red_medidos;precio_kwh;importe_ars;nota\n"+
                "1000;8;250;2000;Casa\n4500;3;250;750;Casa\n");
            if(T1PlugChargeLedger.read(f).size()!=2)
                throw new AssertionError("not reading real registered plug charges");
        }finally{f.delete();}
        System.out.println("PASS only recorded plug inputs allocated FIFO, internal charge free");
    }
}
