package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.location.Location;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline, READ-ONLY historical trip replay. All three maps see the SAME
 * GPS segment representative. No OEM commands, network, upload or GPS export.
 * Distance-weighted category percentages include UNKNOWN in the denominator.
 * Original trip CSV is opened read-only and is NEVER changed.
 */
final class T1TripMapReplay {
    interface Progress { void update(String message); }
    private static final int MAX_ROWS=220000,MAX_BUCKETS=950;
    private static final long MAX_GAP_MS=18000L;
    private static final double MAX_ACCURACY_M=30.0;
    private static final String[] SOURCES={"OSM","OVERTURE","IGN"};
    private static final String[] NAMES={"CIUDAD","CARRETERA / RUTA",
        "AUTOPISTA","DESCONOCIDA"};
    private static final int UNKNOWN=3;
    private T1TripMapReplay(){}

    static final class Report {
        final String text;
        final int gpsSegments,matchedBuckets;
        final double gpsKm;
        Report(String text,int segments,int buckets,double km){
            this.text=text;gpsSegments=segments;matchedBuckets=buckets;gpsKm=km;
        }
    }
    private static final class Fix {
        final long at;
        final double lat,lon,accuracy;
        Fix(long at,double lat,double lon,double accuracy){
            this.at=at;this.lat=lat;this.lon=lon;this.accuracy=accuracy;
        }
    }
    private static final class Leg {
        final Fix at;
        final double meters;
        Leg(Fix at,double meters){this.at=at;this.meters=meters;}
    }
    private static int index(String[] headers,String key){
        for(int i=0;i<headers.length;i++)
            if(key.equals(headers[i].trim()))return i;
        return -1;
    }
    private static double value(String[] row,int column){
        if(column<0||column>=row.length)return Double.NaN;
        try{return Double.parseDouble(row[column].trim());}
        catch(RuntimeException ignored){return Double.NaN;}
    }
    private static String num(double x){
        return String.format(Locale.ROOT,"%.2f",x);
    }
    private static String pct(double distance,double total){
        return total>0?String.format(Locale.ROOT,"%.1f%%",100*distance/total):"N/D";
    }
    private static int classification(T1OsmRoadAdvisor.Result result){
        if(result==null||result.road==null||
                !Double.isFinite(result.distanceMeters)||
                result.distanceMeters<0||result.distanceMeters>35)return UNKNOWN;
        switch(result.road){
            case CITY:return 0;
            case ROAD:return 1;
            case MOTORWAY:return 2;
            default:return UNKNOWN;
        }
    }
    private static Location point(Fix fix){
        Location l=new Location("historical-trip");
        l.setLatitude(fix.lat);
        l.setLongitude(fix.lon);
        l.setAccuracy((float)fix.accuracy);
        // Historical lookup uses explicit API that does NOT test the age
        // of a fix. This is never fed to the LIVE optimized-mode controller.
        l.setTime(fix.at);
        return l;
    }
    private static File validateSource(Context ctx,String id)throws IOException{
        if(id==null||!id.matches("[0-9]{8}_[0-9]{6}"))
            throw new IOException("ID de viaje inválido");
        File dir=T1TripRecorderService.tripDir(ctx).getCanonicalFile();
        File source=new File(dir,"trip_"+id+".csv").getCanonicalFile();
        if(!dir.equals(source.getParentFile())||!source.isFile())
            throw new IOException("No existe el CSV original del viaje");
        if(source.length()>85L*1024*1024)
            throw new IOException("CSV excesivamente grande: análisis no iniciado");
        return source;
    }
    static Report analyze(Context app,String tripId,long from,long until,
                          Progress progress)throws Exception{
        File source=validateSource(app,tripId);
        if(from<0||until<from)throw new IOException("Rango temporal inválido");
        if(!T1OfflineRoadMap.installed(app))
            throw new IOException("Primero descargá el mapa OSM PBA+CABA");
        if(!T1ShadowRoadMap.installed(app,T1ShadowRoadMap.Source.OVERTURE)||
           !T1ShadowRoadMap.installed(app,T1ShadowRoadMap.Source.IGN))
            throw new IOException("Faltan Overture o IGN. Entrá a CONFIGURAR "+
                "OPTIMIZADOR y descargá ambos mapas desde VPS.");
        if(progress!=null)progress.update("Leyendo GPS del CSV original…");
        List<Leg> legs=new ArrayList<>();
        Fix prev=null;
        int rows=0,validFixes=0,rejected=0,gaps=0,stationary=0;
        double gpsMeters=0;
        try(BufferedReader reader=new BufferedReader(new FileReader(source))){
            String header=reader.readLine();
            if(header==null)throw new IOException("CSV vacío");
            String[] columns=header.split(",",-1);
            int ts=index(columns,"timestamp_epoch_ms");
            int lat=index(columns,"gps_lat");
            int lon=index(columns,"gps_lon");
            int acc=index(columns,"gps_accuracy_m");
            int age=index(columns,"gps_fix_age_ms");
            if(ts<0||lat<0||lon<0||acc<0)
                throw new IOException("Este viaje no tiene columnas GPS compatibles");
            String line;
            while((line=reader.readLine())!=null){
                if(Thread.currentThread().isInterrupted())
                    throw new IOException("Análisis interrumpido");
                if(++rows>MAX_ROWS)
                    throw new IOException("Demasiadas muestras; CSV intacto");
                String[] values=line.split(",",-1);
                if(values.length!=columns.length){rejected++;prev=null;continue;}
                double t=value(values,ts),la=value(values,lat),
                    lo=value(values,lon),accuracy=value(values,acc);
                if(!Double.isFinite(t)||t<1600000000000L||
                   t<from||t>until){prev=null;continue;}
                boolean stale=age>=0&&
                    (!Double.isFinite(value(values,age))||
                     value(values,age)<0||value(values,age)>12000);
                if(!Double.isFinite(la)||!Double.isFinite(lo)||
                   Math.abs(la)>90||Math.abs(lo)>180||
                   (la==0&&lo==0)||!Double.isFinite(accuracy)||
                   accuracy<=0||accuracy>MAX_ACCURACY_M||stale){
                    rejected++;prev=null;continue;
                }
                Fix fix=new Fix((long)t,la,lo,accuracy);
                validFixes++;
                if(prev!=null){
                    long elapsed=fix.at-prev.at;
                    if(elapsed>0&&elapsed<=MAX_GAP_MS){
                        float[] dist=new float[1];
                        Location.distanceBetween(prev.lat,prev.lon,
                            fix.lat,fix.lon,dist);
                        double m=dist[0];
                        // Exclude GPS jumps, impossible speed or parked drift;
                        // do not fabricate route distance from OEM-only intervals.
                        if(Double.isFinite(m)&&m>=2.5&&
                           m<=Math.min(800,elapsed*0.06+25)){
                            Fix mid=new Fix((prev.at+fix.at)/2,
                                (prev.lat+fix.lat)/2,
                                (prev.lon+fix.lon)/2,
                                Math.max(prev.accuracy,fix.accuracy));
                            legs.add(new Leg(mid,m));
                            gpsMeters+=m;
                        }else if(m<2.5)stationary++;
                        else{gaps++;prev=null;}
                    }else gaps++;
                }
                prev=fix;
            }
        }
        if(legs.size()<2||gpsMeters<25)
            throw new IOException("El viaje no tiene suficiente GPS consecutivo "+
                "y preciso para clasificar kilómetros. "+
                "Registros GPS útiles: "+validFixes+", tramos: "+legs.size());
        // Group consecutive short GPS legs to bounded, evenly distributed
        // map queries. Every accepted meter belongs to exactly ONE bucket.
        // Representative = weighted-distance midpoint of that local group.
        // This avoids thousands of SQLite opens on an Android 11 head unit.
        int stride=Math.max(1,(legs.size()+MAX_BUCKETS-1)/MAX_BUCKETS);
        double[][] metres=new double[3][4];
        double[][] shared=new double[3][3],agree=new double[3][3];
        double allThree=0;
        int buckets=0;
        for(int start=0;start<legs.size();start+=stride){
            if(Thread.currentThread().isInterrupted())
                throw new IOException("Análisis interrumpido");
            int stop=Math.min(legs.size(),start+stride);
            double length=0;
            for(int j=start;j<stop;j++)length+=legs.get(j).meters;
            double half=length/2,walked=0;
            Fix representative=legs.get(start).at;
            for(int j=start;j<stop;j++){
                walked+=legs.get(j).meters;
                if(walked>=half){representative=legs.get(j).at;break;}
            }
            Location loc=point(representative);
            int[] kinds={
                classification(T1OfflineRoadMap.roadAtHistorical(app,loc)),
                classification(T1OfflineRoadMap.roadAtHistoricalDataset(
                    T1ShadowRoadMap.db(app,T1ShadowRoadMap.Source.OVERTURE),
                    loc,"OVERTURE")),
                classification(T1OfflineRoadMap.roadAtHistoricalDataset(
                    T1ShadowRoadMap.db(app,T1ShadowRoadMap.Source.IGN),
                    loc,"IGN"))
            };
            for(int i=0;i<3;i++)metres[i][kinds[i]]+=length;
            if(kinds[0]!=UNKNOWN&&kinds[0]==kinds[1]&&
               kinds[1]==kinds[2])allThree+=length;
            for(int i=0;i<3;i++)for(int j=i+1;j<3;j++){
                if(kinds[i]!=UNKNOWN&&kinds[j]!=UNKNOWN){
                    shared[i][j]+=length;
                    if(kinds[i]==kinds[j])agree[i][j]+=length;
                }
            }
            buckets++;
            if(progress!=null&&(buckets==1||buckets%25==0||
                    stop==legs.size()))
                progress.update("Comparando OSM / Overture / IGN · "+
                    stop+" de "+legs.size()+" tramos GPS ("+
                    Math.round(100.0*stop/legs.size())+"%)");
        }
        StringBuilder report=new StringBuilder();
        report.append("ANÁLISIS HISTÓRICO · OSM / OVERTURE / IGN\n")
          .append("Viaje ").append(tripId)
          .append(from>0?" · tramo seleccionado":" · jornada completa")
          .append("\nDistancia GPS comparable: ").append(num(gpsMeters/1000))
          .append(" km · ").append(legs.size()).append(" tramos válidos")
          .append("\nPrecisión requerida ≤30 m; saltos y GPS antiguos excluidos.")
          .append("\nPuntos descartados: ").append(rejected)
          .append(" · discontinuidades: ").append(gaps)
          .append(" · intervalos sin desplazamiento: ").append(stationary)
          .append("\nConsultas espaciales por mapa: ").append(buckets)
          .append(" · resultados aproximados al agrupar tramos contiguos.")
          .append("\n\n");
        for(int i=0;i<3;i++){
            report.append(SOURCES[i]).append(" · DISTRIBUCIÓN POR KILÓMETROS\n");
            for(int c=0;c<4;c++)
                report.append("  ").append(NAMES[c]).append(": ")
                    .append(num(metres[i][c]/1000)).append(" km · ")
                    .append(pct(metres[i][c],gpsMeters)).append("\n");
            report.append("  Cobertura identificada: ")
                .append(pct(gpsMeters-metres[i][UNKNOWN],gpsMeters))
                .append("\n\n");
        }
        report.append("COINCIDENCIA SOBRE EL MISMO RECORRIDO\n");
        for(int i=0;i<3;i++)for(int j=i+1;j<3;j++){
            report.append(SOURCES[i]).append(" vs ").append(SOURCES[j])
                .append(": ").append(pct(agree[i][j],shared[i][j]))
                .append(" cuando ambos clasifican (")
                .append(num(shared[i][j]/1000)).append(" km comparables)")
                .append("\n");
        }
        report.append("Los tres coinciden: ").append(num(allThree/1000))
            .append(" km · ").append(pct(allThree,gpsMeters))
            .append(" del GPS comparable.\n\n")
            .append("Los porcentajes incluyen DESCONOCIDA y suman 100% por fuente.")
            .append("\nLa distancia GPS comparable puede diferir del odómetro OEM.")
            .append("\nNo cambia modos EV/HEV, datos de viaje ni mapas.")
            .append("\nSin coordenadas exportadas o subidas al servidor.");
        // Save only the aggregate anonymized report (no individual GPS coords).
        File folder=new File(app.getFilesDir(),"t1_trip_map_reports");
        if(folder.isDirectory()||folder.mkdirs()){
            String suffix=from>0?"_"+from+"_"+until:"";
            File dest=new File(folder,"map_"+tripId+suffix+".txt");
            try(FileWriter w=new FileWriter(dest,false)){
                w.write(report.toString());
            }catch(IOException ignored){}
        }
        return new Report(report.toString(),legs.size(),buckets,gpsMeters/1000);
    }
}
