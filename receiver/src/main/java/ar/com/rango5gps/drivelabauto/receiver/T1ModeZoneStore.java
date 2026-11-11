package ar.com.rango5gps.drivelabauto.receiver;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** On-device only. No background geofencing and NEVER a powertrain setter. */
final class T1ModeZoneStore {
    private static final String PREFS="t1_mode_zone_local",KEY="zones";
    static final class Zone {
        final String name;
        final double lat,lon,radius;
        final int mode;
        Zone(String name,double lat,double lon,double radius,int mode) {
            this.name=name;this.lat=lat;this.lon=lon;
            this.radius=radius;this.mode=mode;
        }
    }
    private static SharedPreferences p(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static Location location(Context c){
        if(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
            PackageManager.PERMISSION_GRANTED)return null;
        LocationManager lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
        if(lm==null)return null;
        try{
            Location loc=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(loc==null||!loc.hasAccuracy()||loc.getAccuracy()>75||
                SystemClock.elapsedRealtimeNanos()-loc.getElapsedRealtimeNanos()>
                    120000000000L)return null;
            return loc;
        }catch(SecurityException ex){return null;}
    }
    static ArrayList<Zone> zones(Context c){
        ArrayList<Zone> out=new ArrayList<>();
        try{
            JSONArray data=new JSONArray(p(c).getString(KEY,"[]"));
            for(int i=0;i<data.length()&&i<10;i++){
                JSONObject x=data.optJSONObject(i);if(x==null)continue;
                Zone z=new Zone(x.optString("name","Zona"),x.optDouble("lat"),
                    x.optDouble("lon"),x.optDouble("radius",300),x.optInt("mode"));
                if((z.mode==1||z.mode==2)&&
                    Double.isFinite(T1ZonePolicy.haversineMeters(z.lat,z.lon,z.lat,z.lon)))
                    out.add(z);
            }
        }catch(Exception ignored){}
        return out;
    }
    static String add(Context c,String name,int desiredMode,int gear,double speed){
        if(!T1ZonePolicy.parked(gear,speed))
            return "Guardá zonas solo estacionado en P, velocidad cero.";
        if(desiredMode!=1&&desiredMode!=2)return "Elegí EV o HEV.";
        Location here=location(c);
        if(here==null)return "GPS sin posición reciente o precisión suficiente (75 m).";
        ArrayList<Zone> old=zones(c);
        if(old.size()>=5)return "Máximo cinco zonas: borrá alguna primero.";
        JSONArray data=new JSONArray();
        for(Zone z:old)put(data,z);
        put(data,new Zone(T1JourneyStore.cleanLabel(name),
            here.getLatitude(),here.getLongitude(),
            T1ZonePolicy.DEFAULT_RADIUS_METERS,desiredMode));
        if(!p(c).edit().putString(KEY,data.toString()).commit())
            return "No se guardó la zona";
        return "Zona guardada localmente · sugerirá "+T1ZonePolicy.name(desiredMode)+
            " a 300 m. NO cambia el vehículo.";
    }
    private static void put(JSONArray a,Zone z){
        try{
            JSONObject o=new JSONObject();
            o.put("name",z.name);o.put("lat",z.lat);o.put("lon",z.lon);
            o.put("radius",z.radius);o.put("mode",z.mode);a.put(o);
        }catch(Exception ignored){}
    }
    static String suggestion(Context c,int currentMode){
        Location l=location(c);
        if(l==null)return "GPS no disponible o desactualizado; sin sugerencias.";
        for(Zone z:zones(c)){
            if(T1ZonePolicy.nearby(l.getLatitude(),l.getLongitude(),
                    z.lat,z.lon,z.radius,l.getAccuracy())){
                if(z.mode==currentMode)return "Zona "+z.name+
                    " · ya está en "+T1ZonePolicy.name(z.mode);
                return "ZONA "+z.name+" · SUGERENCIA "+T1ZonePolicy.name(z.mode)+
                    " · elegí el modo desde controles OEM, solo cuando sea seguro.";
            }
        }
        return "Fuera de las zonas registradas · modo sin automatizar.";
    }
    static void clear(Context c){p(c).edit().remove(KEY).apply();}
    private T1ModeZoneStore(){}
}
