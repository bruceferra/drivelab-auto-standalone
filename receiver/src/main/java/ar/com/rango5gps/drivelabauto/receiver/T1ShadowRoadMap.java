package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Two independent, read-only road map datasets packaged on the existing VPS.
 * OSM remains the SOLE source of optimized vehicle mode decisions.
 * This class never transmits GPS coordinates or commands vehicle electronics.
 */
final class T1ShadowRoadMap {
    enum Source {
        OVERTURE("overture","OVERTURE"), IGN("ign","IGN");
        final String folder,display;
        Source(String dir,String name){folder=dir;display=name;}
    }
    interface Progress { void update(String text,int pct); }
    private static final String URL_ROOT=
        "https://promanager.com.ar/drivelab/maps/shadow/";
    private static final int MAX_ZIP_MB=240,MAX_SQL_MB=1100;
    private T1ShadowRoadMap(){}
    private static File folder(Context c,Source src){
        return new File(new File(c.getFilesDir(),"t1_shadow_maps"),src.folder);
    }
    static File db(Context c,Source src){
        return new File(folder(c,src),"roads.sqlite");
    }
    private static String meta(SQLiteDatabase db,String key){
        try(Cursor cursor=db.rawQuery("SELECT value FROM metadata WHERE k=?",
                                    new String[]{key})){
            return cursor.moveToFirst()?cursor.getString(0):"";
        }
    }
    static boolean installed(Context c,Source src){
        File f=db(c,src);
        if(!f.isFile()||f.length()<10000)return false;
        try(SQLiteDatabase road=SQLiteDatabase.openDatabase(
                f.getAbsolutePath(),null,
                SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
            return src.display.equals(meta(road,"source"))&&
                "AR-B+AR-C".equals(meta(road,"region_code"))&&
                "portable_btree_bounds_v3".equals(meta(road,"spatial_index"));
        }catch(Exception ignored){return false;}
    }
    static T1OsmRoadAdvisor.Result read(Context c,Source src,Location point){
        if(!installed(c,src))return new T1OsmRoadAdvisor.Result(
            T1OsmRoadAdvisor.Road.UNKNOWN,"","",Double.NaN,
            src.display+" · BASE NO INSTALADA");
        return T1OfflineRoadMap.roadAtDataset(db(c,src),point,src.display);
    }
    private static HttpURLConnection open(String url)throws IOException{
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(50000);
        conn.setInstanceFollowRedirects(false);
        conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent","DriveLab-Auto-ShadowMaps/1");
        if(conn.getResponseCode()!=200)throw new IOException(
            "HTTP "+conn.getResponseCode()+" en "+url);
        return conn;
    }
    private static String hex(byte[] bytes){
        StringBuilder b=new StringBuilder();
        for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&255));
        return b.toString();
    }
    static void install(Context app,Source src,Progress progress)throws Exception{
        File dir=folder(app,src);
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Sin carpeta de mapas");
        String base=URL_ROOT+src.folder+"/";
        JSONObject m;
        HttpURLConnection manifest=open(base+"manifest.json?t="+System.currentTimeMillis());
        try(InputStream in=manifest.getInputStream()){
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            byte[] tmp=new byte[4096];int n;
            while((n=in.read(tmp))!=-1){
                if(buf.size()+n>32768)throw new IOException("Manifiesto demasiado grande");
                buf.write(tmp,0,n);
            }
            m=new JSONObject(buf.toString("UTF-8"));
        }finally{manifest.disconnect();}
        if(m.optInt("schema")!=3||
           !src.display.equals(m.optString("source"))||
           !"AR-B+AR-C".equals(m.optString("region"))||
           !"roads.sqlite.gz".equals(m.optString("filename"))||
           !"portable_btree_bounds_v3".equals(m.optString("spatialIndex"))||
           m.optInt("roadCount")<100||
           !m.optBoolean("extractComplete",false))
            throw new IOException("Manifiesto shadow incompleto o incorrecto");
        long size=m.getLong("bytes");
        String sha=m.getString("sha256").toLowerCase(Locale.ROOT);
        if(size<10000||size>MAX_ZIP_MB*1024L*1024L||
           !sha.matches("[a-f0-9]{64}"))
            throw new IOException("Tamaño o checksum no válido");
        File zip=new File(dir,"roads.sqlite.gz.part");
        File candidate=new File(dir,"roads.sqlite.part");
        File previous=new File(dir,"roads.sqlite.backup");
        try{
            if(progress!=null)progress.update(src.display+" · descargando del VPS",5);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            HttpURLConnection download=open(base+"roads.sqlite.gz");
            try(InputStream in=download.getInputStream();
                FileOutputStream out=new FileOutputStream(zip,false)){
                byte[] buffer=new byte[65536];long count=0;int n;
                while((n=in.read(buffer))!=-1){
                    count+=n;
                    if(count>size||count>MAX_ZIP_MB*1024L*1024L)
                        throw new IOException("Paquete excede tamaño anunciado");
                    out.write(buffer,0,n);
                    digest.update(buffer,0,n);
                    if(progress!=null&&count%(4L*1024*1024)<65536)
                        progress.update(src.display+" · descarga",Math.min(70,
                            5+(int)(count*65/size)));
                }
                if(count!=size||!sha.equals(hex(digest.digest())))
                    throw new IOException("SHA256 o tamaño no coincide con manifiesto");
            }finally{download.disconnect();}
            if(progress!=null)progress.update(src.display+" · validando SQLite",75);
            try(InputStream in=new GZIPInputStream(new FileInputStream(zip));
                FileOutputStream out=new FileOutputStream(candidate,false)){
                byte[] buffer=new byte[65536];long total=0;int n;
                while((n=in.read(buffer))!=-1){
                    total+=n;
                    if(total>MAX_SQL_MB*1024L*1024L)
                        throw new IOException("SQLite supera límite local");
                    out.write(buffer,0,n);
                }
            }
            try(SQLiteDatabase road=SQLiteDatabase.openDatabase(
                    candidate.getAbsolutePath(),null,
                    SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
                if(!src.display.equals(meta(road,"source"))||
                   !"AR-B+AR-C".equals(meta(road,"region_code"))||
                   !"portable_btree_bounds_v3".equals(meta(road,"spatial_index")))
                    throw new IOException("SQLite no corresponde a la fuente/region");
                int roads,bounds;
                try(Cursor cur=road.rawQuery("SELECT COUNT(*) FROM roads",null)){
                    roads=cur.moveToFirst()?cur.getInt(0):0;
                }
                try(Cursor cur=road.rawQuery("SELECT COUNT(*) FROM roads_bounds",null)){
                    bounds=cur.moveToFirst()?cur.getInt(0):0;
                }
                if(roads!=m.getInt("roadCount")||bounds!=roads)
                    throw new IOException("Conteo/índice de rutas incorrecto");
            }
            File current=db(app,src);
            if(previous.exists()&&!previous.delete())
                throw new IOException("No se pudo quitar backup anterior");
            if(current.exists()&&!current.renameTo(previous))
                throw new IOException("No se pudo resguardar mapa actual");
            if(!candidate.renameTo(current)){
                if(previous.exists())previous.renameTo(current);
                throw new IOException("No se pudo activar SQLite nuevo");
            }
            if(previous.exists())previous.delete();
            if(progress!=null)progress.update(src.display+" · instalado offline",100);
        }finally{
            if(zip.exists())zip.delete();
            if(candidate.exists())candidate.delete();
        }
    }
}
