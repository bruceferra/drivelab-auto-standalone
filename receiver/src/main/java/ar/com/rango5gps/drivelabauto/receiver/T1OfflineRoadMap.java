package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Read-only, spatially indexed OSM province roads (IGN extent, © OSM contributors).
 *  Requests are explicitly initiated by user; does not transmit GPS history.
 */
final class T1OfflineRoadMap {
    private T1OfflineRoadMap(){}
    public interface Progress {void update(String message,int percent);}
    private static final String MANIFEST="https://promanager.com.ar/drivelab/maps/pba-caba-roads-manifest.json";
    private static final String PACKAGE="https://promanager.com.ar/drivelab/maps/pba-caba-roads.sqlite.gz";
    private static final int MAX_COMPRESSED_MB=180;
    private static final String PREFS="t1_offline_pba_caba";
    private static File dir(Context c){return new File(c.getFilesDir(),"r5_buenos_aires_map");}
    private static File dataset(Context c){return new File(dir(c),"roads.sqlite");}
    public static boolean installed(Context ctx){
        File file=dataset(ctx);
        if(!file.isFile()||file.length()<1000000L)return false;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.toString(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
            try(Cursor c=db.rawQuery("SELECT value FROM metadata WHERE k='region'",null)){
                if(!c.moveToFirst()||!"Provincia de Buenos Aires y Ciudad Autónoma de Buenos Aires, Argentina".equals(c.getString(0)))return false;
                try(Cursor v=db.rawQuery("SELECT value FROM metadata WHERE k='spatial_index'",null)){
                    return v.moveToFirst()&&"portable_btree_bounds_v3".equals(v.getString(0));
                }
            }
        }catch(Exception ex){return false;}
    }
    private static HttpURLConnection open(String url)throws IOException{
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();
        con.setUseCaches(false);
        con.setRequestProperty("Cache-Control","no-cache");
        con.setRequestProperty("Pragma","no-cache");
        con.setConnectTimeout(10000);con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent","R5DriveLab/0.27 offline province map");
        con.setRequestProperty("Accept","application/json, application/octet-stream");
        int status=con.getResponseCode();
        if(status!=200){con.disconnect();throw new IOException("Servidor de mapas HTTP "+status);}
        return con;
    }
    private static String readSmall(InputStream in,int maxBytes)throws IOException{
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        byte[] bytes=new byte[8192];int n;
        while((n=in.read(bytes))!=-1){if(buffer.size()+n>maxBytes)throw new IOException("Manifiesto demasiado grande");buffer.write(bytes,0,n);}
        return buffer.toString("UTF-8");
    }
    private static String hex(byte[] bytes){
        StringBuilder b=new StringBuilder();
        for(byte x:bytes)b.append(String.format(Locale.US,"%02x",x&255));
        return b.toString();
    }
    public static void install(Context ctx,Progress progress)throws Exception{
        File folder=dir(ctx);
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("No se puede crear carpeta del mapa");
        JSONObject manifest;
        // The manifest is mutable; never read a stale Android/edge cache.
        HttpURLConnection mc=open(MANIFEST+"?t="+System.currentTimeMillis());
        try(InputStream in=mc.getInputStream()){manifest=new JSONObject(readSmall(in,100000));}
        finally{mc.disconnect();}
        if(!"AR-B+AR-C".equals(manifest.optString("region"))||manifest.optInt("roadCount")<1000||
           !"pba-caba-roads.sqlite.gz".equals(manifest.optString("filename"))||
           !manifest.optBoolean("completeRoadCoverage")||!manifest.optBoolean("includesCaba")||
           manifest.optInt("schema",0)!=3||!"roads_bounds".equals(manifest.optString("spatialIndex")))
            throw new IOException("Manifiesto provincial no válido");
        final long expected=manifest.getLong("bytes");
        final String checksum=manifest.getString("sha256");
        if(!checksum.matches("[a-f0-9]{64}")||expected<500000||expected>MAX_COMPRESSED_MB*1024L*1024L)
            throw new IOException("Tamaño o checksum de mapa inválidos");
        if(installed(ctx)&&checksum.equals(
                ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                    .getString("sha256",""))){
            if(progress!=null)progress.update("Mapa PBA+CABA ya actualizado",100);
            ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putLong("checked_at",System.currentTimeMillis()).apply();
            return;
        }
        if(folder.getUsableSpace()<200L*1024*1024)
            throw new IOException("Espacio insuficiente: se requieren 200 MB libres");
        File compressed=new File(folder,"roads.sqlite.gz.part");
        File unpacked=new File(folder,"roads.sqlite.part");
        try{
            if(progress!=null)progress.update("Descargando mapa único de Provincia y CABA",0);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long count=0;
            // Immutable URL per package content: Cloudflare previously returned
            // the 42MB RTree v1 from cache while manifest already said 45MB v2.
            // The verified SHA in the URL changes only with the actual file.
            HttpURLConnection pc=open(PACKAGE+"?sha256="+checksum);
            try(InputStream in=pc.getInputStream();OutputStream out=new FileOutputStream(compressed)){
                byte[] buffer=new byte[65536];int n;long last=0;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Descarga interrumpida");
                    count+=n;
                    if(count>expected||count>MAX_COMPRESSED_MB*1024L*1024L)throw new IOException("Paquete demasiado grande");
                    digest.update(buffer,0,n);out.write(buffer,0,n);
                    if(count-last>=500000){
                        last=count;if(progress!=null)progress.update("Descargando mapa único de Provincia y CABA",(int)Math.min(95,count*90/expected));
                    }
                }
            }finally{pc.disconnect();}
            if(count!=expected)
                throw new IOException("Descarga no coincide con manifiesto: servidor entregó "+
                    count+" bytes, esperados "+expected+" bytes. Comprobá actualización o caché.");
            String actual=hex(digest.digest());
            if(!actual.equals(checksum))
                throw new IOException("El servidor entregó un SHA-256 diferente (recibido "+
                    actual.substring(0,12)+"..., esperado "+checksum.substring(0,12)+
                    "...). El mapa anterior se conserva.");
            if(progress!=null)progress.update("Instalando índice de calles",95);
            try(InputStream in=new GZIPInputStream(new FileInputStream(compressed));
                OutputStream out=new FileOutputStream(unpacked)){
                byte[] buffer=new byte[65536];int n;long bytes=0;
                while((n=in.read(buffer))!=-1){
                    bytes+=n;if(bytes>1024L*1024L*1024L)throw new IOException("Mapa descomprimido demasiado grande");
                    out.write(buffer,0,n);
                }
            }
            validate(unpacked,manifest.getInt("roadCount"));
            File current=dataset(ctx),backup=new File(folder,"roads.sqlite.backup");
            if(backup.exists()&&!backup.delete())throw new IOException("No se puede actualizar mapa antiguo");
            if(current.exists()&&!current.renameTo(backup))throw new IOException("No se puede conservar mapa anterior");
            if(!unpacked.renameTo(current)){
                if(backup.exists())backup.renameTo(current);
                throw new IOException("No se pudo instalar mapa nuevo");
            }
            if(backup.exists())backup.delete();
            ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putString("sha256",checksum).putInt("roads",manifest.getInt("roadCount"))
                .putString("builtAt",manifest.optString("builtAt",""))
                .putLong("checked_at",System.currentTimeMillis()).apply();
            if(progress!=null)progress.update("Mapa provincial instalado y verificado",100);
        }finally{
            compressed.delete();unpacked.delete();
        }
    }
    static boolean checkDue(Context c){
        // Also install on first launch: the old logic returned false when
        // there was NO map, leaving OSM permanently UNKNOWN after enabling GPS.
        if(!installed(c))return true;
        return System.currentTimeMillis()-
            c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getLong("checked_at",0L)>24L*60*60*1000;
    }
    static boolean automaticRetryAllowed(Context c){
        long last=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getLong("last_auto_attempt",0L);
        long now=System.currentTimeMillis();
        return last<=0||now-last>=30L*60L*1000||now<last;
    }
    static void markAutoAttempt(Context c){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putLong("last_auto_attempt",System.currentTimeMillis()).apply();
    }
    static String status(Context c){
        return installed(c)?"MAPA PBA+CABA INSTALADO · consulta offline":
            "MAPA NO INSTALADO · descargar PBA+CABA (una vez)";
    }
    /** Read-only file exported by SQLite/Python, without android_metadata.
     * Android's localized collator setup may reject databases generated externally.
     * Report precisely which query failed rather than swallowing the SQLite exception.
     */
    private static void validate(File file,int expected)throws IOException{
        if(!file.isFile()||file.length()<1000000L)
            throw new IOException("Archivo SQLite ausente o demasiado pequeño: "+
                (file.exists()?file.length():-1)+" bytes");
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.toString(),null,
                SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
            int count;
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM roads",null)){
                if(!c.moveToFirst())throw new IOException("Faltan registros de vías");
                count=c.getInt(0);
            }
            if(count!=expected)throw new IOException(
                "Conteo de calles incorrecto: "+count+" de "+expected);
            String boundary;
            try(Cursor c=db.rawQuery("SELECT value FROM metadata WHERE k='boundary_id'",null)){
                boundary=c.moveToFirst()?c.getString(0):"ausente";
            }
            if(!"06,02".equals(boundary))
                throw new IOException("Región incorrecta: "+boundary+" (esperada 06,02: Provincia y CABA)");
            int rtree;
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM roads_bounds",null)){
                if(!c.moveToFirst())throw new IOException("No se pudo consultar índice espacial");
                rtree=c.getInt(0);
            }
            if(rtree!=expected)
                throw new IOException("Índice portátil de calles incompleto: "+rtree+" de "+expected);
            try(Cursor c=db.rawQuery("SELECT value FROM metadata WHERE k='spatial_index'",null)){
                if(!c.moveToFirst()||!"portable_btree_bounds_v3".equals(c.getString(0)))
                    throw new IOException("La base no tiene el índice portátil requerido");
            }
        }catch(IOException known){
            throw known;
        }catch(Exception ex){
            String detail=ex.getMessage();
            if(detail==null||detail.trim().isEmpty())detail=ex.getClass().getSimpleName();
            if(detail.length()>170)detail=detail.substring(0,170);
            throw new IOException("SQLite Android no pudo leer la base provincial: "+detail,ex);
        }
    }
    private static int speed(String source){
        if(source==null)return 0;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("[0-9]{1,3}").matcher(source);
        if(!m.find())return 0;
        try{return Integer.parseInt(m.group());}catch(Exception e){return 0;}
    }
    private static List<double[]> readPolyline(byte[] compressed)throws IOException{
        ArrayList<double[]> result=new ArrayList<>();
        try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(compressed));
            ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=in.read(buffer))!=-1){
                if(output.size()+count>2_000_000)throw new IOException("Vía con demasiados puntos");
                output.write(buffer,0,count);
            }
            byte[] bytes=output.toByteArray();
            if(bytes.length%8!=0)throw new IOException("Geometría provincial inválida");
            ByteBuffer coordinates=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            while(coordinates.remaining()>=8)result.add(new double[]{
                coordinates.getInt()/1.0e7,coordinates.getInt()/1.0e7});
        }
        return result;
    }

    // Nearby road from the same SQLite v3 downloaded by Uber Driver.
    // No Overpass, no network, no uploaded GPS coordinates.
    static T1OsmRoadAdvisor.Result roadAt(Context context,android.location.Location here){
        if(!installed(context))return unknown("Mapa offline no instalado. "+
            "Tocá DESCARGAR MAPA PBA+CABA una sola vez.");
        return roadAtDataset(dataset(context),here,"OSM");
    }
    /** Shared read-only map matcher for SHADOW datasets. Must never write OEM
     * modes or modify the existing production OSM database.
     * Shadow SQLite has the SAME roads/roads_bounds v3 geometry schema.
     */
    static T1OsmRoadAdvisor.Result roadAtDataset(
            File roadDb,android.location.Location here,String source){
        if(roadDb==null||!roadDb.isFile())
            return unknown(source+" · mapa NO INSTALADO");
        if(here==null||!here.hasAccuracy()||here.getAccuracy()>65||
                android.os.SystemClock.elapsedRealtimeNanos()-
                here.getElapsedRealtimeNanos()>60000000000L)
            return unknown("Falta GPS reciente o precisión suficiente para "+
                "clasificar vía usando el mapa offline.");
        double lat=here.getLatitude(),lon=here.getLongitude();
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||
                Math.abs(lat)>90||Math.abs(lon)>180)
            return unknown("Posición GPS inválida");
        double latSpan=60d/111320d;
        double lonSpan=60d/(111320d*Math.max(.2,
            Math.abs(Math.cos(Math.toRadians(lat)))));
        String[] args={Double.toString(lon+lonSpan),
            Double.toString(lon-lonSpan),Double.toString(lat+latSpan),
            Double.toString(lat-latSpan)};
        double nearest=Double.POSITIVE_INFINITY,runner=Double.POSITIVE_INFINITY;
        String highway="",ref="",maxspeed="";
        T1OsmRoadAdvisor.Road best=T1OsmRoadAdvisor.Road.UNKNOWN,
            next=T1OsmRoadAdvisor.Road.UNKNOWN;
        int checked=0,ignoredUnknown=0;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(
                roadDb.getAbsolutePath(),null,
                SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            Cursor rows=db.rawQuery(
                "SELECT r.highway,r.ref,r.vertices,r.maxspeed,r.motorroad,r.expressway "+
                "FROM roads_bounds i JOIN roads r ON i.osm_id=r.osm_id "+
                "WHERE i.min_lon <= ? AND i.max_lon >= ? "+
                "AND i.min_lat <= ? AND i.max_lat >= ? LIMIT 1200",args)){
            while(rows.moveToNext()){
                String type=rows.getString(0);
                if(type==null||type.isEmpty())continue;
                byte[] bytes=rows.getBlob(2);if(bytes==null)continue;
                double meters=distanceToRoad(lat,lon,bytes);
                if(!Double.isFinite(meters))continue;
                T1OsmRoadAdvisor.Road kind=T1OsmRoadAdvisor.Road.valueOf(
                    T1RoadSpeedClassifier.category(type,rows.getString(1),
                        rows.getString(3),rows.getString(4),
                        rows.getString(5)).name());
                // The original nearest-road selector also chose pedestrian
                // paths, cycleways and non-driveable UNKNOWN classes. That
                // could hide a perfectly valid city street 2 m further away.
                if(kind==T1OsmRoadAdvisor.Road.UNKNOWN){
                    ignoredUnknown++;continue;
                }
                checked++;
                if(meters<nearest){
                    if(kind!=best){
                        runner=nearest;next=best;
                    }
                    nearest=meters;best=kind;
                    highway=type;ref=rows.getString(1);
                    maxspeed=rows.getString(3);
                }else if(meters<runner&&kind!=best){
                    runner=meters;next=kind;
                }
            }
        }catch(Exception ex){
            String detail=ex.getMessage();
            if(detail==null||detail.isEmpty())detail=ex.getClass().getSimpleName();
            if(detail.length()>130)detail=detail.substring(0,130);
            return unknown("Error de lectura SQLite local: "+
                ex.getClass().getSimpleName()+" · "+detail);
        }
        // Advisory classification only: accept usable GPS fixes but retain
        // uncertainty when a different road type is almost equally near.
        double max=Math.min(65,Math.max(30,here.getAccuracy()*1.5+10));
        if(checked==0||nearest>max)
            return unknown("No se encontró calle vehicular a "+
                String.format(Locale.ROOT,"%.0f",max)+
                " m del GPS. Calles consideradas="+checked+
                ", rutas no compatibles="+ignoredUnknown+
                ", precisión GPS="+
                String.format(Locale.ROOT,"%.0f",here.getAccuracy())+" m.");
        if(next!=best&&Double.isFinite(runner)&&runner-nearest<=8)
            return unknown("Vías cercanas de distinta categoría: "+
                "clasificación ambigua, sin sugerencia.");
        String name=ref==null||ref.trim().isEmpty()?"Vía sin referencia":"Ruta "+ref;
        int limit=T1RoadSpeedClassifier.maxKmh(maxspeed);
        return new T1OsmRoadAdvisor.Result(best,highway,name,nearest,
            source+" highway="+highway+" · máxima publicada="+
            (limit<0?"SIN DATO":limit+" km/h")+
            " · SQLite PBA+CABA v3. Solo sugerencias, sin comandos.",
            limit);
    }
    private static T1OsmRoadAdvisor.Result unknown(String note){
        return new T1OsmRoadAdvisor.Result(
            T1OsmRoadAdvisor.Road.UNKNOWN,"","",Double.NaN,note);
    }
    private static double distanceToRoad(double lat,double lon,byte[] compressed)
            throws IOException{
        double min=Double.POSITIVE_INFINITY;
        double x1=0,y1=0;
        boolean have=false;
        double scaleLon=111320*Math.cos(Math.toRadians(lat));
        try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(compressed));
            DataInputStream raw=new DataInputStream(in)){
            byte[] buf=new byte[8];
            int n=0;
            while(n<20000){
                int filled=0;
                while(filled<8){
                    int got=raw.read(buf,filled,8-filled);
                    if(got<0){
                        if(filled!=0)throw new IOException("Geometría truncada");
                        return min;
                    }
                    filled+=got;
                }
                ByteBuffer b=ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
                double la=b.getInt()/1e7,lo=b.getInt()/1e7;
                double x=(lo-lon)*scaleLon,y=(la-lat)*111320;
                if(have){
                    double dx=x-x1,dy=y-y1,den=dx*dx+dy*dy;
                    double t=den>0?Math.max(0,Math.min(1,
                        -(x1*dx+y1*dy)/den)):0;
                    min=Math.min(min,Math.hypot(x1+t*dx,y1+t*dy));
                }
                x1=x;y1=y;have=true;n++;
            }
        }
        return min;
    }
}
