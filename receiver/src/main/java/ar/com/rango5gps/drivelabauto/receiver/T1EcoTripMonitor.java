package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Streaming, GPS-free per-master analytics sidecar, keyed by the OEM trip ID.
 * Never changes or extends the original master CSV's schema.
 * Does not send location anywhere and never controls vehicle properties.
 */
final class T1EcoTripMonitor implements AutoCloseable {
    private static final String PREFS="t1_eco_trip_live_v1";
    private static final long MATCH_INTERVAL_MS=8500L;
    private final Context app;
    private final String tripId;
    private final T1EcoTripMetrics rolling;
    private T1EcoTripMetrics client1=new T1EcoTripMetrics(),
        client2=new T1EcoTripMetrics();
    private long client1Start=0,client2Start=0;
    private FileOutputStream writer;
    private long lastMatchedAt=0,lastMatchedFixNanos=0,lastSample=0;
    private int cachedRoad=T1EcoTripMetrics.UNKNOWN,samples=0;

    static File file(Context c,String id){
        if(id==null||!id.matches("[0-9]{8}_[0-9]{6}"))
            throw new IllegalArgumentException("ID de TRIP no válido");
        return new File(T1TripRecorderService.tripDir(c),
            "eco_"+id+".csv");
    }
    static String live(Context c){return live(c,0);}
    static String live(Context c,int scope){
        SharedPreferences p=c.getApplicationContext().getSharedPreferences(
            PREFS,Context.MODE_PRIVATE);
        long age=System.currentTimeMillis()-p.getLong("sampleAt",0L);
        String status=p.getString(scope==1?"summary1":scope==2?
            "summary2":"summary","");
        if(status.isEmpty()||age<0||age>45000L)
            return "ECO EN VIVO · sin muestra reciente; no se inventa porcentaje";
        return status;
    }
    T1EcoTripMonitor(Context c,String id,boolean resuming){
        app=c.getApplicationContext();tripId=id;
        // Rehydrate a resumed master sidecar, so a pause doesn't start the
        // percentages from zero. The file contains aggregates, no raw GPS.
        rolling=resuming?T1EcoTripReport.readSidecar(
            file(app,tripId),0,Long.MAX_VALUE):new T1EcoTripMetrics();
        File destination=file(app,tripId);
        try{
            boolean append=resuming&&destination.isFile()&&destination.length()>0;
            writer=new FileOutputStream(destination,append);
            if(!append)writer.write(
                "timestamp_ms,distance_km,seconds,road_class,speed_kmh,accel_mps2\n"
                .getBytes(StandardCharsets.UTF_8));
        }catch(IOException ex){writer=null;}
    }
    private int matched(Location point,long timestamp){
        if(point==null||!point.hasAccuracy()||
            point.getAccuracy()>35)return T1EcoTripMetrics.UNKNOWN;
        long age=SystemClock.elapsedRealtimeNanos()-
            point.getElapsedRealtimeNanos();
        if(age<0||age>12000000000L)
            return T1EcoTripMetrics.UNKNOWN;
        long fixNanos=point.getElapsedRealtimeNanos();
        if(fixNanos!=lastMatchedFixNanos&&
            timestamp-lastMatchedAt>=MATCH_INTERVAL_MS){
            T1OsmRoadAdvisor.Result result=T1OfflineRoadMap.roadAt(app,point);
            cachedRoad=result.road==T1OsmRoadAdvisor.Road.CITY?
                T1EcoTripMetrics.CITY:
                result.road==T1OsmRoadAdvisor.Road.ROAD?
                    T1EcoTripMetrics.ROAD:
                result.road==T1OsmRoadAdvisor.Road.MOTORWAY?
                    T1EcoTripMetrics.MOTORWAY:T1EcoTripMetrics.UNKNOWN;
            lastMatchedAt=timestamp;
            lastMatchedFixNanos=fixNanos;
        }
        return lastMatchedAt>0&&timestamp-lastMatchedAt<15000?
            cachedRoad:T1EcoTripMetrics.UNKNOWN;
    }
    void sample(long ms,double km,long elapsedMs,double speed,
                double acceleration,Location location){
        if(ms<=lastSample||!Double.isFinite(speed)||speed<0||speed>220||
            elapsedMs<300||elapsedMs>10000||!Double.isFinite(km)||
            km<0||km>.5)return;
        lastSample=ms;
        double seconds=elapsedMs/1000.0;
        int road=matched(location,ms);
        rolling.add(ms,km,seconds,road,speed,acceleration);
        if(T1JourneyStore.active(app)&&!T1JourneyStore.paused(app)){
            long start=T1JourneyStore.startedAt(app);
            if(start>0&&start!=client1Start){
                client1Start=start;
                client1=T1EcoTripReport.readSidecar(file(app,tripId),
                    start,ms-1);
            }
            if(start>0)client1.add(ms,km,seconds,road,speed,acceleration);
        }
        if(T1JourneyStore2.active(app)&&!T1JourneyStore2.paused(app)){
            long start=T1JourneyStore2.startedAt(app);
            if(start>0&&start!=client2Start){
                client2Start=start;
                client2=T1EcoTripReport.readSidecar(file(app,tripId),
                    start,ms-1);
            }
            if(start>0)client2.add(ms,km,seconds,road,speed,acceleration);
        }
        if(writer!=null)try{
            String line=ms+","+
                String.format(Locale.ROOT,"%.7f",km)+","+
                String.format(Locale.ROOT,"%.3f",seconds)+","+
                road+","+
                String.format(Locale.ROOT,"%.3f",speed)+","+
                (Double.isFinite(acceleration)?
                    String.format(Locale.ROOT,"%.4f",acceleration):"")+"\n";
            writer.write(line.getBytes(StandardCharsets.UTF_8));
            if(++samples%5==0)writer.flush();
        }catch(IOException ex){
            try{writer.close();}catch(IOException ignored){}
            writer=null;
        }
        if(samples%3==0)publish(ms);
    }
    private void publish(long ms){
        app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putLong("sampleAt",ms)
            .putString("id",tripId)
            .putString("summary",
                "VIAJE TOTAL · "+rolling.headline()+
                "
"+T1EcoTripMetrics.fmt(rolling.km(),2)+" km medidos"+
                " · "+rolling.abruptAccelerationEvents()+
                " aceleraciones y "+rolling.abruptBrakeEvents()+
                " frenadas fuertes estimadas")
            .putString("summary1",client1Start==0?
                "VIAJE 1 · sin registro activo":"VIAJE 1 · "+client1.headline())
            .putString("summary2",client2Start==0?
                "VIAJE 2 · sin registro activo":"VIAJE 2 · "+client2.headline())
            .apply();
    }
    @Override public void close(){
        if(lastSample>0)publish(lastSample);
        if(writer!=null)try{writer.flush();writer.close();}
        catch(IOException ignored){}
    }
}
