package ar.com.rango5gps.drivelabauto.receiver;
import java.io.File;
import java.nio.file.Files;
public final class T1TripEnergyAuditTest {
    private static void near(double a,double b){
        if(!Double.isFinite(a)||Math.abs(a-b)>0.00001)
            throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("t1-hv-audit").toFile();
        try{
            String file="trip_20261117_173400.csv";
            String data="timestamp_epoch_ms,hv_gross_discharge_kwh_est,hv_charge_kwh_est\n"+
                "1000,1.00,0.50\n2000,3.00,1.50\n3000,4.00,1.80\n"+
                "4000,9.00,5.00\n";
            Files.writeString(new File(root,file).toPath(),data);
            T1TripEnergyAudit.Result r=T1TripEnergyAudit.read(
                root,"20261117_173400",2000,3000);
            near(r.grossKwh,1);near(r.chargedKwh,.3);
            if(T1TripEnergyAudit.read(root,"20261117_173400",6000,7000)
                .available())throw new AssertionError("missing samples are not free");
            if(T1TripEnergyAudit.read(root,"../evil",1000,4000).available())
                throw new AssertionError("path traversal");
            System.out.println("PASS client-level HV net and energy in from master CSV");
        }finally{
            for(File f:root.listFiles())f.delete();root.delete();
        }
    }
}
