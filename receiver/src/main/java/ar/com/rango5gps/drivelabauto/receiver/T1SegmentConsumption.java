package ar.com.rango5gps.drivelabauto.receiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Read-only reconstruction of actual paired ClusterInteraction 500 m callbacks.
 * A raw float is NOT validated litres or kWh. The scale used here is a
 * deliberately labelled experiment: raw (candidate /100 km) x 0.5 / 100.
 * Never feed these estimates into tank calibration, billing or vehicle control.
 */
final class T1SegmentConsumption {
    static final double NOMINAL_SEGMENT_KM = 0.5d;
    private static final long MAX_PAIR_LAG_MS = 2000L;
    private static final long MAX_TRIP_MATCH_LAG_MS = 10000L;

    static final class Summary {
        final int segments, unmatchedFuel, unmatchedElectric;
        final double fuelRawSum, electricRawSum, firstKm, lastKm;
        final long firstEventTime, lastEventTime;
        final String distanceSource;

        Summary(int segments, int unmatchedFuel, int unmatchedElectric,
                double fuelRawSum, double electricRawSum, long firstEventTime,
                long lastEventTime, double firstKm, double lastKm,
                String distanceSource) {
            this.segments=segments;
            this.unmatchedFuel=unmatchedFuel;
            this.unmatchedElectric=unmatchedElectric;
            this.fuelRawSum=fuelRawSum;
            this.electricRawSum=electricRawSum;
            this.firstEventTime=firstEventTime;
            this.lastEventTime=lastEventTime;
            this.firstKm=firstKm;
            this.lastKm=lastKm;
            this.distanceSource=distanceSource;
        }
        double hypotheticalFuelLiters() {
            return fuelRawSum * NOMINAL_SEGMENT_KM / 100.0d;
        }
        double hypotheticalElectricKwh() {
            return electricRawSum * NOMINAL_SEGMENT_KM / 100.0d;
        }
        double nominalDistanceKm() {
            return segments * NOMINAL_SEGMENT_KM;
        }
        double observedKmPerEventInterval() {
            return segments >= 2 && Double.isFinite(firstKm) &&
                Double.isFinite(lastKm) && lastKm >= firstKm ?
                (lastKm-firstKm)/(segments-1) : Double.NaN;
        }
        double distanceRatio() {
            double observed=observedKmPerEventInterval();
            return Double.isFinite(observed)?
                observed/NOMINAL_SEGMENT_KM : Double.NaN;
        }
    }

    private static final class Reading {
        final long ts;
        final double raw;
        Reading(long ts, double raw){this.ts=ts;this.raw=raw;}
    }
    private static final class DistancePoint {
        final double km;
        final String source;
        final long lag;
        DistancePoint(double km,String source,long lag){
            this.km=km;this.source=source;this.lag=lag;
        }
    }

    static Summary analyze(File events, File trip) throws IOException {
        if(events==null||!events.isFile())
            return new Summary(0,0,0,0,0,0,0,Double.NaN,Double.NaN,"SIN CSV");
        ArrayList<Reading> fuels=new ArrayList<>();
        ArrayList<Reading> electrics=new ArrayList<>();
        try(BufferedReader in=new BufferedReader(new FileReader(events))) {
            String row;
            while((row=in.readLine())!=null) {
                String[] f=row.split(",",-1);
                if(f.length!=3)continue;
                boolean fuel="NAFTA_500_m".equals(f[1]);
                boolean electric="ELÉCTRICO_500_m".equals(f[1]);
                if(!fuel&&!electric)continue; // exclude repeated 50 km callbacks
                try {
                    long ts=Long.parseLong(f[0]);
                    double raw=Double.parseDouble(f[2]);
                    if(!Double.isFinite(raw)||ts<=0)continue;
                    (fuel?fuels:electrics).add(new Reading(ts,raw));
                }catch(NumberFormatException ignored) {
                    // Keep partial appends and corrupted rows out of estimates.
                }
            }
        }
        int pairs=0, electricCursor=0;
        double fuelSum=0,electricSum=0;
        long first=0,last=0;
        for(Reading f:fuels) {
            while(electricCursor<electrics.size() &&
                electrics.get(electricCursor).ts < f.ts-MAX_PAIR_LAG_MS)
                electricCursor++;
            if(electricCursor>=electrics.size())break;
            Reading e=electrics.get(electricCursor);
            if(Math.abs(e.ts-f.ts)>MAX_PAIR_LAG_MS)continue;
            electricCursor++;
            pairs++;
            fuelSum+=f.raw;
            electricSum+=e.raw;
            if(first==0)first=f.ts;
            last=f.ts;
        }
        int fuelUnpaired=fuels.size()-pairs;
        int electricUnpaired=electrics.size()-pairs;
        double startKm=Double.NaN,endKm=Double.NaN;
        String source="SIN DATOS";
        if(pairs>0&&trip!=null&&trip.isFile()) {
            DistancePoint[] points=findEndpoints(trip,first,last);
            if(points[0]!=null)startKm=points[0].km;
            if(points[1]!=null)endKm=points[1].km;
            if(points[0]!=null&&points[1]!=null)
                source=points[0].source.equals(points[1].source)?
                    points[0].source:"MIXTA";
            else if(points[0]!=null)source=points[0].source;
            else if(points[1]!=null)source=points[1].source;
        }
        return new Summary(pairs,fuelUnpaired,electricUnpaired,
            fuelSum,electricSum,first,last,startKm,endKm,source);
    }

    private static DistancePoint[] findEndpoints(File trip,long first,long last)
        throws IOException {
        DistancePoint[] best=new DistancePoint[2];
        try(BufferedReader in=new BufferedReader(new FileReader(trip))) {
            in.readLine(); // CSV header, kept compatible with old 23+ columns
            String row;
            while((row=in.readLine())!=null) {
                String[] f=row.split(",",-1);
                if(f.length<23)continue;
                try {
                    long ts=Long.parseLong(f[0]);
                    double km=Double.parseDouble(f[16]);
                    if(!Double.isFinite(km)||km<0)continue;
                    long[] targets={first,last};
                    String source="GPS".equals(f[22])?"GPS":
                        "OEM_SPEED_UNIT_CANDIDATE".equals(f[22])?
                        "VELOCIDAD OEM (SIN CALIBRAR)":"NO IDENTIFICADA";
                    for(int i=0;i<2;i++) {
                        long lag=Math.abs(ts-targets[i]);
                        if(lag<=MAX_TRIP_MATCH_LAG_MS &&
                            (best[i]==null||lag<best[i].lag))
                            best[i]=new DistancePoint(km,source,lag);
                    }
                }catch(NumberFormatException ignored) {
                    // A concurrent writer may leave one incomplete final row.
                }
            }
        }
        return best;
    }

    private T1SegmentConsumption(){}
}
