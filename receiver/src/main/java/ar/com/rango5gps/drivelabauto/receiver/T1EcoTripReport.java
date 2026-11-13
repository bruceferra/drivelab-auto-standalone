package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Background-only historical analysis, retaining all original trip files.
 * New recordings reuse per-sample OSM classification from a GPS-free sidecar;
 * preexisting CSVs can be reconstructed from recorded raw GPS + local map.
 * Never sends historical GPS to a public server.
 */
final class T1EcoTripReport {
    private static final Pattern TRIP=Pattern.compile("[0-9]{8}_[0-9]{6}");
    private T1EcoTripReport(){}
    private static int index(String[] names,String wanted){
        for(int i=0;i<names.length;i++)
            if(wanted.equals(names[i]))return i;
        return -1;
    }
    private static double num(String[] row,int i){
        if(i<0||i>=row.length||row[i].isEmpty())return Double.NaN;
        try{return Double.parseDouble(row[i]);}
        catch(Exception ex){return Double.NaN;}
    }
    static T1EcoTripMetrics readSidecar(File input,long from,long until){
        T1EcoTripMetrics result=new T1EcoTripMetrics();
        if(input==null||!input.isFile())return result;
        try(BufferedReader r=new BufferedReader(new FileReader(input))){
            String header=r.readLine();
            if(header==null||!header.startsWith("timestamp_ms,distance_km,"))
                return result;
            String line;
            while((line=r.readLine())!=null){
                String[] row=line.split(",",-1);
                if(row.length!=6&&row.length!=7)continue;
                double t=num(row,0);
                if(!Double.isFinite(t)||t<from||t>until)continue;
                double distance=num(row,1),sec=num(row,2),
                    cls=num(row,3),speed=num(row,4),accel=num(row,5);
                if(!Double.isFinite(cls))continue;
                double limit=row.length==7?num(row,6):-1;
                result.add((long)t,distance,sec,(int)cls,speed,accel,
                    Double.isFinite(limit)?(int)limit:-1);
            }
        }catch(IOException ignored){}
        return result;
    }

    static final class Result {
        final T1EcoTripMetrics metrics;
        final String source;
        final boolean partial;
        Result(T1EcoTripMetrics m,String s,boolean partial){
            metrics=m;source=s;this.partial=partial;
        }
        String summary(){
            return metrics.headline()+
                "\n"+metrics.breakdown()+
                "\nFUENTE: "+source+
                (partial?" · posible tramo incompleto por reanudación o GPS":"");
        }
    }
    static Result analyze(Context app,String tripChain,long started,long ended){
        T1EcoTripMetrics total=new T1EcoTripMetrics();
        if(tripChain==null||tripChain.isEmpty()||ended<started)
            return new Result(total,"sin TRIP OEM asociado",true);
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        Matcher m=TRIP.matcher(tripChain);
        while(m.find())ids.add(m.group());
        boolean incomplete=false;
        StringBuilder sources=new StringBuilder();
        for(String id:ids){
            File sidecar=T1EcoTripMonitor.file(app,id);
            T1EcoTripMetrics part;
            if(sidecar.isFile()){
                part=readSidecar(sidecar,started,ended);
                sources.append("SIDECAR OSM ").append(id).append("; ");
            }else{
                File source=new File(T1TripRecorderService.tripDir(app),
                    "trip_"+id+".csv");
                part=readRawTrip(app,source,started,ended);
                incomplete=true;
                sources.append("CSV HISTÓRICO ").append(id).append("; ");
            }
            total.combine(part);
        }
        if(ids.isEmpty())incomplete=true;
        return new Result(total,
            sources.length()==0?"sin fichero de viaje":sources.toString(),
            incomplete);
    }
    private static T1EcoTripMetrics readRawTrip(
            Context app,File input,long from,long until){
        T1EcoTripMetrics data=new T1EcoTripMetrics();
        if(!input.isFile())return data;
        try(BufferedReader reader=new BufferedReader(new FileReader(input))){
            String line=reader.readLine();
            if(line==null||!line.startsWith("timestamp_epoch_ms,"))return data;
            String[] head=line.split(",",-1);
            int it=index(head,"timestamp_epoch_ms"),
                ik=index(head,"distance_km_est"),
                iv=index(head,"speed_oem_raw"),
                ilat=index(head,"gps_lat"),ilon=index(head,"gps_lon"),
                ia=index(head,"gps_accuracy_m"),
                iage=index(head,"gps_fix_age_ms"),
                iacc=index(head,"longitudinal_accel_speed_est_mps2");
            if(it<0||ik<0||iv<0)return data;
            long prevTime=0,lastMatchAt=0,lastCoordinateAt=0;
            double prevKm=Double.NaN,prevSpeed=Double.NaN,
                lastLat=Double.NaN,lastLon=Double.NaN;
            int road=T1EcoTripMetrics.UNKNOWN,limit=-1;
            while((line=reader.readLine())!=null){
                String[] row=line.split(",",-1);
                if(row.length!=head.length)continue;
                double stamp=num(row,it),km=num(row,ik),
                    speed=num(row,iv);
                if(!Double.isFinite(stamp)||!Double.isFinite(km))continue;
                long ms=(long)stamp;
                double dt=(ms-prevTime)/1000.0;
                double distance=km-prevKm;
                double acceleration=num(row,iacc);
                if(!Double.isFinite(acceleration)&&prevTime>0&&
                    dt>=.5&&dt<=10&&Double.isFinite(speed)&&
                    Double.isFinite(prevSpeed))
                    acceleration=(speed-prevSpeed)/(3.6*dt);
                prevTime=ms;prevKm=km;prevSpeed=speed;
                if(ms<from||ms>until)continue;
                if(dt<.3||dt>10||!Double.isFinite(distance)||
                    distance<0||distance>.5)continue;
                double lat=num(row,ilat),lon=num(row,ilon),
                    accuracy=num(row,ia),age=num(row,iage);
                boolean coordinates=Double.isFinite(lat)&&
                    Double.isFinite(lon)&&Double.isFinite(accuracy)&&
                    Math.abs(lat)<=90&&Math.abs(lon)<=180&&
                    accuracy>=0&&accuracy<=35;
                if(coordinates&&(!Double.isFinite(lastLat)||
                    Math.abs(lat-lastLat)>0.0000001||
                    Math.abs(lon-lastLon)>0.0000001)){
                    lastCoordinateAt=ms;lastLat=lat;lastLon=lon;
                }
                boolean fresh=coordinates&&lastCoordinateAt>0&&
                    ms-lastCoordinateAt<=15000&&
                    (!Double.isFinite(age)||(age>=0&&age<=15000));
                if(!fresh){
                    road=T1EcoTripMetrics.UNKNOWN;limit=-1;lastMatchAt=0;
                }
                else if(lastMatchAt==0||ms-lastMatchAt>=9000){
                    Location location=new Location("historical-trip-local-map");
                    location.setLatitude(lat);location.setLongitude(lon);
                    location.setAccuracy((float)accuracy);
                    location.setTime(ms);
                    location.setElapsedRealtimeNanos(
                        SystemClock.elapsedRealtimeNanos());
                    T1OsmRoadAdvisor.Result matched=
                        T1OfflineRoadMap.roadAt(app,location);
                    limit=matched.speedLimitKmh;
                    road=matched.road==T1OsmRoadAdvisor.Road.CITY?
                        T1EcoTripMetrics.CITY:
                        matched.road==T1OsmRoadAdvisor.Road.ROAD?
                            T1EcoTripMetrics.ROAD:
                        matched.road==T1OsmRoadAdvisor.Road.MOTORWAY?
                            T1EcoTripMetrics.MOTORWAY:
                            T1EcoTripMetrics.UNKNOWN;
                    lastMatchAt=ms;
                }
                data.add(ms,distance,dt,road,speed,acceleration,limit);
            }
        }catch(IOException ignored){}
        return data;
    }
}
