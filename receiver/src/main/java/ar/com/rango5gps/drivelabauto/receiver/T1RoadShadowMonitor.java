package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Same-GPS-fix A/B/C audit: OSM is the LIVE vehicle source;
 * Overture and IGN are SHADOW/read-only and can never command OEM changes.
 * Separate workers keep either supplemental SQLite from delaying the other
 * or the existing optimizer thread. No coordinates are persisted or uploaded.
 */
final class T1RoadShadowMonitor {
    private static final String PREFS="t1_shadow_map_comparison";
    private static final long PERIOD_MS=3500,MAX_WAIT_MS=1500,SWITCH_WINDOW_MS=60000;
    private static final AtomicBoolean inFlight=new AtomicBoolean(false);
    private static volatile long lastScheduled;
    private static final ExecutorService watcher=Executors.newSingleThreadExecutor(
        r->{Thread t=new Thread(r,"T1-Shadow-Compare");t.setDaemon(true);return t;});
    private static final ExecutorService overture=Executors.newSingleThreadExecutor(
        r->{Thread t=new Thread(r,"T1-Shadow-Overture");t.setDaemon(true);return t;});
    private static final ExecutorService ign=Executors.newSingleThreadExecutor(
        r->{Thread t=new Thread(r,"T1-Shadow-IGN");t.setDaemon(true);return t;});
    private static final String[] SOURCE={"osm","overture","ign"};
    private static String[] lastRoad={"","",""};
    private static long[] changeTime={0L,0L,0L};
    private static boolean[] hasPrevious={false,false,false};
    private static double lastLat=Double.NaN,lastLon=Double.NaN;
    private static long lastPositionAt=0L;
    private T1RoadShadowMonitor(){}
    static final class Timed {
        final T1OsmRoadAdvisor.Result road;
        final long elapsedMs;
        Timed(T1OsmRoadAdvisor.Result road,long elapsed){
            this.road=road;elapsedMs=elapsed;
        }
    }
    private static String label(T1OsmRoadAdvisor.Result x){
        if(x==null||x.road==T1OsmRoadAdvisor.Road.UNKNOWN||
           !Double.isFinite(x.distanceMeters)||x.distanceMeters>35||
           x.distanceMeters<0)return "UNKNOWN";
        return x.road.name();
    }
    static void observe(Context app,Location gps,T1OsmRoadAdvisor.Result osm,
                        long osmLookupMs){
        if(gps==null||osm==null||!gps.hasAccuracy()||gps.getAccuracy()>30)return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastScheduled<PERIOD_MS||!inFlight.compareAndSet(false,true))return;
        lastScheduled=now;
        final Location here=new Location(gps);
        final Context context=app.getApplicationContext();
        try{
            watcher.execute(()->{
                try{compare(context,here,osm,Math.max(0,osmLookupMs));}
                catch(Exception e){
                    context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                        .edit().putString("error",e.getClass().getSimpleName()).apply();
                }finally{inFlight.set(false);}
            });
        }catch(RejectedExecutionException ignored){inFlight.set(false);}
    }
    private static Timed lookup(Context c,T1ShadowRoadMap.Source src,Location gps){
        long start=SystemClock.elapsedRealtime();
        T1OsmRoadAdvisor.Result r=T1ShadowRoadMap.read(c,src,gps);
        return new Timed(r,Math.max(0,SystemClock.elapsedRealtime()-start));
    }
    private static Timed get(Future<Timed> task){
        if(task==null)return null;
        try{return task.get(MAX_WAIT_MS,TimeUnit.MILLISECONDS);}
        catch(Exception err){task.cancel(true);return null;}
    }
    private static void compare(Context app,Location gps,
                                T1OsmRoadAdvisor.Result osm,long osmTime){
        boolean ov=T1ShadowRoadMap.installed(app,T1ShadowRoadMap.Source.OVERTURE);
        boolean ig=T1ShadowRoadMap.installed(app,T1ShadowRoadMap.Source.IGN);
        if(!ov&&!ig)return;
        // Both map reads start with the SAME GPS fix and run independently.
        Future<Timed> o=ov?overture.submit(()->lookup(app,
            T1ShadowRoadMap.Source.OVERTURE,gps)):null;
        Future<Timed> i=ig?ign.submit(()->lookup(app,
            T1ShadowRoadMap.Source.IGN,gps)):null;
        Timed a=get(o),b=get(i);
        String[] roads={label(osm),a==null?"UNKNOWN":label(a.road),
            b==null?"UNKNOWN":label(b.road)};
        boolean[] available={true,ov&&a!=null,ig&&b!=null};
        double[] latency={osmTime,a==null?Double.NaN:a.elapsedMs,
            b==null?Double.NaN:b.elapsedMs};
        long time=System.currentTimeMillis();
        double distance=0;
        if(Double.isFinite(lastLat)&&lastPositionAt>0&&
           time>=lastPositionAt&&time-lastPositionAt<=30000L){
            float[] result=new float[1];
            Location.distanceBetween(lastLat,lastLon,
                gps.getLatitude(),gps.getLongitude(),result);
            if(result[0]>=0&&result[0]<=450)
                distance=result[0];
        }
        lastLat=gps.getLatitude();lastLon=gps.getLongitude();
        lastPositionAt=time;
        SharedPreferences p=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        SharedPreferences.Editor e=p.edit();
        long observations=p.getLong("observations",0)+1;
        e.putLong("observations",observations)
         .putLong("updatedAt",time).putString("error","");
        boolean osmChanged=false;
        if(hasPrevious[0]&&!roads[0].equals(lastRoad[0])){
            osmChanged=true;
        }
        if(osmChanged)changeTime[0]=time;
        for(int j=0;j<3;j++){
            String key=SOURCE[j];
            if(!available[j])continue; // missing map is not a classification failure
            e.putLong(key+"_samples",p.getLong(key+"_samples",0)+1);
            e.putFloat(key+"_meters",
                p.getFloat(key+"_meters",0)+(float)distance);
            if("UNKNOWN".equals(roads[j])){
                e.putLong(key+"_unknown",p.getLong(key+"_unknown",0)+1);
                e.putFloat(key+"_unknown_meters",
                    p.getFloat(key+"_unknown_meters",0)+(float)distance);
            }
            if(j>0&&!roads[j].equals(roads[0])){
                e.putLong(key+"_disagree",p.getLong(key+"_disagree",0)+1);
            }
            if(Double.isFinite(latency[j])){
                e.putLong(key+"_lookup_ms",
                    p.getLong(key+"_lookup_ms",0)+(long)latency[j]);
                e.putLong(key+"_lookup_count",
                    p.getLong(key+"_lookup_count",0)+1);
            }
            boolean changed=hasPrevious[j]&&!roads[j].equals(lastRoad[j]);
            if(changed){
                e.putLong(key+"_switches",p.getLong(key+"_switches",0)+1);
                changeTime[j]=time;
            }
            if(j>0&&"UNKNOWN".equals(roads[j])==false&&
               roads[j].equals(roads[0])&&
               changeTime[0]>0&&Math.abs(time-changeTime[0])<=SWITCH_WINDOW_MS&&
               (changed||osmChanged)){
                long lagMs=(changed?changeTime[j]:0)>0?
                    changeTime[j]-changeTime[0]:time-changeTime[0];
                if(Math.abs(lagMs)<=SWITCH_WINDOW_MS){
                    // One observation per OSM transition for each source.
                    String marker=key+"_last_reference";
                    if(p.getLong(marker,0)!=changeTime[0]){
                        e.putLong(key+"_lag_sum_ms",
                            p.getLong(key+"_lag_sum_ms",0)+lagMs);
                        e.putLong(key+"_lag_count",
                            p.getLong(key+"_lag_count",0)+1);
                        e.putLong(marker,changeTime[0]);
                    }
                }
            }
            lastRoad[j]=roads[j];hasPrevious[j]=true;
        }
        e.apply();
        // No GPS coordinates, VIN, or OEM commands in the private audit CSV.
        File output=new File(app.getFilesDir(),"t1_road_shadow_comparison.csv");
        try(FileOutputStream out=new FileOutputStream(output,true)){
            if(output.length()==0)out.write(
                "timestamp_ms,osm,overture,ign,osm_ms,overture_ms,ign_ms,delta_m,over_available,ign_available\n"
                    .getBytes(StandardCharsets.UTF_8));
            String row=time+","+String.join(",",roads)+","+
                (long)latency[0]+","+
                (Double.isFinite(latency[1])?(long)latency[1]:"")+","+
                (Double.isFinite(latency[2])?(long)latency[2]:"")+","+
                String.format(Locale.ROOT,"%.1f",distance)+","+
                (available[1]?1:0)+","+(available[2]?1:0)+"\n";
            out.write(row.getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }
    private static String pct(long part,long total){
        return total>0?String.format(Locale.ROOT,"%.1f%%",
            100.0*part/total):"N/D";
    }
    private static String fmt(double value){
        return String.format(Locale.ROOT,"%.1f",value);
    }
    static String report(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        StringBuilder s=new StringBuilder(
            "OSM continúa CONTROLANDO el optimizador. Overture e IGN solo se observan.\n"+
            "La demora de cambio es relativa a OSM, no una verdad de la ruta.\n"+
            "Sin GPS almacenado o enviado; registro CSV local.\n");
        for(int j=0;j<3;j++){
            String src=SOURCE[j],tag=src.toUpperCase(Locale.ROOT);
            boolean available=j==0?T1OfflineRoadMap.installed(c):
                T1ShadowRoadMap.installed(c,j==1?
                    T1ShadowRoadMap.Source.OVERTURE:
                    T1ShadowRoadMap.Source.IGN);
            if(!available){
                s.append("\n").append(tag).append(": BASE NO INSTALADA\n");
                continue;
            }
            long total=p.getLong(src+"_samples",0);
            double metres=p.getFloat(src+"_meters",0);
            double unknownM=p.getFloat(src+"_unknown_meters",0);
            long unknown=p.getLong(src+"_unknown",0);
            long diff=p.getLong(src+"_disagree",0);
            long q=p.getLong(src+"_lookup_count",0);
            long delays=p.getLong(src+"_lag_count",0);
            s.append("\n").append(tag).append(": muestras ").append(total)
             .append(" · DESCONOCIDO ")
             .append(metres>50?fmt(100*unknownM/metres)+"% km":
                 pct(unknown,total)+" muestras")
             .append(" · consultas ")
             .append(q>0?fmt((double)p.getLong(src+"_lookup_ms",0)/q)+" ms":"N/D")
             .append("\n  Cambios de tipo: ")
             .append(p.getLong(src+"_switches",0));
            if(j>0)s.append(" · desacuerdos vs OSM ")
                .append(pct(diff,total))
                .append(" · retraso cambio vs OSM: ")
                .append(delays>0?
                    fmt((double)p.getLong(src+"_lag_sum_ms",0)/delays/1000)+" s":
                    "SIN PARES COMPARABLES");
            s.append("\n");
        }
        s.append("\nLas IGN incluyen principalmente redes viales oficiales; "+
            "una cobertura urbana menor no indica necesariamente peor mapa.");
        return s.toString();
    }
}
