package ar.com.rango5gps.drivelabauto.receiver;

import android.location.Location;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** OSM road TYPE suggestion, not map matching for safety-critical control.
 * Explicit opt-in required by caller: sends rounded GPS to public Overpass.
 * Never writes vehicle signals. Network calls only off the UI thread.
 */
final class T1OsmRoadAdvisor {
    enum Road { CITY, MOTORWAY, UNKNOWN }
    static final class Result {
        final Road road;
        final String roadTag,name,detail;
        final double distanceMeters;
        Result(Road road,String roadTag,String name,double meters,String detail){
            this.road=road;this.roadTag=roadTag;this.name=name;
            this.distanceMeters=meters;this.detail=detail;
        }
        String recommendation(boolean forced){
            if(road==Road.CITY)return "SUGERENCIA: EV · ciudad";
            if(road==Road.MOTORWAY)
                return forced?"SUGERENCIA: HEV · SOC FORZADO":
                    "SUGERENCIA: HEV · SOC SMART";
            return "SIN CAMBIO SUGERIDO · vía no confirmada";
        }
        String explain(){
            return (name.isEmpty()?"Vía sin nombre":name)+
                " · highway="+roadTag+
                (Double.isFinite(distanceMeters)?
                    " · ~"+String.format(Locale.ROOT,"%.0f",distanceMeters)+" m":
                    "")+"\n"+detail;
        }
    }
    static Road classify(String tag){
        if("motorway".equals(tag)||"motorway_link".equals(tag))
            return Road.MOTORWAY;
        if("residential".equals(tag)||"living_street".equals(tag)||
            "service".equals(tag)||"tertiary".equals(tag)||
            "unclassified".equals(tag))
            return Road.CITY;
        // trunk, primary, secondary or road may be expressways or ordinary
        // routes depending on the country: NEVER infer motorways.
        return Road.UNKNOWN;
    }
    static Result lookup(Location loc){
        if(loc==null||!loc.hasAccuracy()||loc.getAccuracy()>35||
            SystemClock.elapsedRealtimeNanos()-loc.getElapsedRealtimeNanos()>
                35000000000L)
            return new Result(Road.UNKNOWN,"", "",Double.NaN,
                "GPS desactualizado o impreciso (requiere precisión <=35 m y <=35 s).");
        HttpURLConnection conn=null;
        try{
            double lat=loc.getLatitude(),lon=loc.getLongitude();
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||
                Math.abs(lat)>90||Math.abs(lon)>180)
                throw new IllegalArgumentException("Coordenadas inválidas");
            String query=String.format(Locale.ROOT,
                "[out:json][timeout:10];way(around:60,%.6f,%.6f)[highway];out geom;",
                lat,lon);
            conn=(HttpURLConnection)new URL(
                "https://overpass-api.de/api/interpreter").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);conn.setReadTimeout(12000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("User-Agent","DriveLabAuto/0.2.42 (OSM-road-advisory)");
            byte[] bytes=("data="+URLEncoder.encode(query,"UTF-8"))
                .getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            if(conn.getResponseCode()!=200)
                return new Result(Road.UNKNOWN,"","",Double.NaN,
                    "Overpass HTTP "+conn.getResponseCode()+
                    " (sin cambios en el vehículo).");
            StringBuilder body=new StringBuilder();
            try(BufferedReader in=new BufferedReader(
                    new InputStreamReader(conn.getInputStream(),
                        StandardCharsets.UTF_8))){
                char[] buf=new char[2048];int n;
                while((n=in.read(buf))!=-1){
                    body.append(buf,0,n);
                    if(body.length()>160000)throw new IllegalStateException(
                        "Respuesta OSM demasiado grande");
                }
            }
            JSONArray elements=new JSONObject(body.toString())
                .optJSONArray("elements");
            if(elements==null)return new Result(Road.UNKNOWN,"","",
                Double.NaN,"Sin vías OSM cercanas.");
            double first=Double.POSITIVE_INFINITY,other=Double.POSITIVE_INFINITY;
            String tag="",name="";Road best=Road.UNKNOWN,second=Road.UNKNOWN;
            int limit=Math.min(180,elements.length());
            for(int i=0;i<limit;i++){
                JSONObject element=elements.optJSONObject(i);
                if(element==null)continue;
                JSONObject tags=element.optJSONObject("tags");
                JSONArray geometry=element.optJSONArray("geometry");
                if(tags==null||geometry==null||geometry.length()<2)continue;
                String type=tags.optString("highway","");
                if(type.isEmpty()||"footway".equals(type)||
                    "cycleway".equals(type)||"pedestrian".equals(type))continue;
                double meters=closest(lat,lon,geometry);
                Road kind=classify(type);
                if(meters<first){
                    other=first;second=best;
                    first=meters;best=kind;tag=type;
                    name=tags.optString("name","");
                }else if(meters<other&&kind!=best){
                    other=meters;second=kind;
                }
            }
            double maxDist=Math.min(35,loc.getAccuracy()*1.5+7);
            if(!Double.isFinite(first)||first>maxDist)
                return new Result(Road.UNKNOWN,"","",first,
                    "No hubo calle OSM suficientemente próxima para "+
                    "la precisión de GPS actual.");
            if(second!=best&&Double.isFinite(other)&&other-first<=8)
                return new Result(Road.UNKNOWN,tag,name,first,
                    "Vías paralelas de distinta categoría muy cercanas: "+
                    "GPS ambiguo, sin sugerencia.");
            return new Result(best,tag,name,first,
                "Consulta OpenStreetMap de solo lectura; clasificación "+
                "aproximada y nunca una orden al vehículo.");
        }catch(Exception ex){
            return new Result(Road.UNKNOWN,"","",Double.NaN,
                "OSM no disponible: "+ex.getClass().getSimpleName()+
                ". Sin recomendación ni escritura.");
        }finally{if(conn!=null)conn.disconnect();}
    }
    private static double closest(double lat,double lon,JSONArray geom){
        double best=Double.POSITIVE_INFINITY;
        JSONObject prev=null;
        for(int i=0;i<geom.length();i++){
            JSONObject now=geom.optJSONObject(i);if(now==null)continue;
            if(prev!=null){
                double scaleLon=111320*Math.cos(Math.toRadians(lat));
                double ax=(prev.optDouble("lon")-lon)*scaleLon;
                double ay=(prev.optDouble("lat")-lat)*111320;
                double bx=(now.optDouble("lon")-lon)*scaleLon;
                double by=(now.optDouble("lat")-lat)*111320;
                double dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
                double t=den>0?Math.max(0,Math.min(1,
                    -(ax*dx+ay*dy)/den)):0;
                double x=ax+t*dx,y=ay+t*dy;
                double dist=Math.hypot(x,y);
                if(Double.isFinite(dist))best=Math.min(best,dist);
            }
            prev=now;
        }
        return best;
    }
    private T1OsmRoadAdvisor(){}
}
