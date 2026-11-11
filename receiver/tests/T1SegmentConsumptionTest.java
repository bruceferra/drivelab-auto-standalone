package ar.com.rango5gps.drivelabauto.receiver;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/** Synthetic regression based on real 11/11/2026 paired OEM numbers. */
public final class T1SegmentConsumptionTest {
    private static void near(double got,double want,double eps,String label) {
        if(!Double.isFinite(got)||Math.abs(got-want)>eps)
            throw new AssertionError(label+": "+got+" != "+want);
    }
    private static void check(boolean good,String label) {
        if(!good)throw new AssertionError(label);
    }
    private static String sample(long ts,double km,String source) {
        String[] cells=new String[23];
        java.util.Arrays.fill(cells,"");
        cells[0]=Long.toString(ts);
        cells[16]=String.format(Locale.US,"%.5f",km);
        cells[22]=source;
        return String.join(",",cells)+"\n";
    }
    public static void main(String[] args) throws Exception {
        File events=File.createTempFile("drivelab-oem-",".csv");
        File trip=File.createTempFile("drivelab-trip-",".csv");
        try {
            int[] fuel={4,7,3,0,20,7,7,10,10,8,7,5,8,1,17,9,14,0,13};
            int[] electric={14,1,11,31,-12,-3,9,-3,-1,-2,-1,9,-10,21,-12,1,-12,31,-12};
            StringBuilder e=new StringBuilder("timestamp_epoch_ms,channel,value_float_raw\n");
            StringBuilder t=new StringBuilder("header\n");
            long base=1794426076969L;
            for(int i=0;i<fuel.length;i++) {
                long ts=base+i*60000L;
                // Reverse arrival order on some callbacks; do not lose a pair.
                if(i%2==0) {
                    e.append(ts).append(",NAFTA_500_m,").append(fuel[i]).append(".0\n");
                    e.append(ts+1).append(",ELÉCTRICO_500_m,").append(electric[i]).append(".0\n");
                }else{
                    e.append(ts).append(",ELÉCTRICO_500_m,").append(electric[i]).append(".0\n");
                    e.append(ts+1).append(",NAFTA_500_m,").append(fuel[i]).append(".0\n");
                }
                e.append(ts+2).append(",NAFTA_50_km,5.9\n");
                e.append(ts+3).append(",ELÉCTRICO_50_km,-0.9\n");
                t.append(sample(ts,0.05+(9.95*i/18),"OEM_SPEED_UNIT_CANDIDATE"));
            }
            Files.write(events.toPath(),e.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(trip.toPath(),t.toString().getBytes(StandardCharsets.UTF_8));
            T1SegmentConsumption.Summary s=T1SegmentConsumption.analyze(events,trip);
            check(s.segments==19,"all 19 actual pairs");
            check(s.unmatchedFuel==0&&s.unmatchedElectric==0,"no unmatched callbacks");
            near(s.fuelRawSum,150,0.00001,"fuel sum");
            near(s.electricRawSum,60,0.00001,"electric sum");
            near(s.hypotheticalFuelLiters(),0.75,0.00001,"candidate fuel L");
            near(s.hypotheticalElectricKwh(),0.30,0.00001,"candidate electric kWh");
            near(s.nominalDistanceKm(),9.5,0.00001,"nominal 19 intervals");
            near(s.observedKmPerEventInterval(),9.95/18,0.0001,"observed per OEM interval");
            near(s.distanceRatio(),9.95/9.0,0.0001,"no silent distance correction");
            check("VELOCIDAD OEM (SIN CALIBRAR)".equals(s.distanceSource),
                "source from local CSV notes");
            // Incomplete/invalid callbacks must never count as a segment.
            Files.write(events.toPath(),(
                "timestamp_epoch_ms,channel,value_float_raw\n"+
                "1794426076969,NAFTA_500_m,4\n"+
                "1794426076970,ELÉCTRICO_500_m,14\n"+
                "1794426136969,NAFTA_500_m,7\n"+
                "1794426136970,ELÉCTRICO_500_m,NaN\n"+
                "1794426196969,ELÉCTRICO_500_m,-1\n"
            ).getBytes(StandardCharsets.UTF_8));
            s=T1SegmentConsumption.analyze(events,trip);
            check(s.segments==1,"only complete valid pairs");
            check(s.unmatchedFuel==1&&s.unmatchedElectric==1,
                "unmatched channels not silently treated as zero");
            near(s.hypotheticalFuelLiters(),0.02,0.00001,"one pair");
            check(Double.isNaN(s.distanceRatio()),"one event cannot calibrate distance");
            System.out.println("T1SegmentConsumptionTest OK");
        }finally {
            Files.deleteIfExists(events.toPath());
            Files.deleteIfExists(trip.toPath());
        }
    }
}
