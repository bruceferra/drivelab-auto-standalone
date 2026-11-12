package ar.com.rango5gps.drivelabauto.receiver;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Local-only automatic name. Generated at the actual OEM checkpoint, not
 * at the time a tap requests a delayed startup. No GPS or labels are uploaded
 * in private client summaries.
 */
final class T1AutoTripLabel {
    static String token(int slot){return "__AUTO_TRIP_"+slot+"__";}
    static String resolve(Context c,String request,int slot,long checkpoint){
        if(!token(slot).equals(request))return T1JourneyStore.cleanLabel(request);
        String prefix="VIAJE "+slot+" "+
            new SimpleDateFormat("dd/MM/yy HH:mm",Locale.getDefault())
                .format(new Date(checkpoint));
        Location gps=recent(c);
        if(gps==null)return prefix;
        return prefix+" "+
            String.format(Locale.ROOT,"%.5f,%.5f",
                gps.getLatitude(),gps.getLongitude());
    }
    private static Location recent(Context c){
        if(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
                PackageManager.PERMISSION_GRANTED)return null;
        LocationManager mgr=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
        if(mgr==null)return null;
        try{
            Location result=mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(result==null||!result.hasAccuracy()||result.getAccuracy()>50)
                return null;
            long age=SystemClock.elapsedRealtimeNanos()-
                result.getElapsedRealtimeNanos();
            if(age<0||age>20000000000L)return null;
            if(!Double.isFinite(result.getLatitude())||
                !Double.isFinite(result.getLongitude()))return null;
            return result;
        }catch(SecurityException ex){return null;}
    }
    private T1AutoTripLabel(){}
}
