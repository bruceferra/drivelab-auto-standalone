package ar.com.rango5gps.drivelabauto.receiver;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Deliberate voice request only; local OEM-independent GPS fix + HTTPS
 * Open-Meteo model weather. Does not save or upload GPS history.
 * Run strictly off UI thread. Requires active location permission.
 */
final class T1VoiceWeather {
    private T1VoiceWeather(){}
    static boolean asked(String phrase){
        if(phrase==null)return false;
        String s=T1VoiceMath.normalize(phrase);
        return s.matches(".*\\b(?:clima|pronostico|temperatura|lluvia|llover|"+
            "hace frio|hace calor|tiempo hoy|tiempo manana)\\b.*");
    }
    private static Location getLocation(Context c)throws Exception{
        if(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
            PackageManager.PERMISSION_GRANTED)return null;
        LocationManager lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
        if(lm==null)return null;
        Location best=null;
        for(String provider:new String[]{
                LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            try{
                Location p=lm.getLastKnownLocation(provider);
                if(p!=null&&(best==null||p.getTime()>best.getTime()))best=p;
            }catch(SecurityException ignored){}
        }
        if(best==null)return null;
        long age=System.currentTimeMillis()-best.getTime();
        if(age<0||age>10*60*1000L||!Double.isFinite(best.getLatitude())||
           !Double.isFinite(best.getLongitude()))return null;
        return best;
    }
    private static String condition(int code){
        if(code==0)return "despejado";
        if(code<=3)return "parcialmente nublado";
        if(code>=45&&code<=48)return "con niebla";
        if(code>=51&&code<=67)return "con lluvia";
        if(code>=71&&code<=77)return "con nieve";
        if(code>=80&&code<=82)return "con chaparrones";
        if(code>=95)return "con tormentas";
        return "con nubosidad variable";
    }
    static String now(Context c)throws Exception{
        Location loc=getLocation(c);
        if(loc==null)return "No tengo una posición GPS reciente. "+
            "No voy a inventarte el clima de donde estás.";
        String url=String.format(Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"+
            "&current=temperature_2m,apparent_temperature,weather_code"+
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"+
            "&timezone=auto&forecast_days=1",
            loc.getLatitude(),loc.getLongitude());
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(7000);conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept","application/json");
        try{
            if(conn.getResponseCode()!=200)
                throw new IllegalStateException("Clima HTTP "+conn.getResponseCode());
            StringBuilder body=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                    conn.getInputStream(),StandardCharsets.UTF_8))){
                char[] buf=new char[4096];int n;
                while((n=reader.read(buf))!=-1){
                    body.append(buf,0,n);
                    if(body.length()>20000)throw new IllegalStateException(
                        "Respuesta meteorológica excede tamaño");
                }
            }
            JSONObject root=new JSONObject(body.toString());
            JSONObject curr=root.getJSONObject("current");
            JSONObject daily=root.getJSONObject("daily");
            double temp=curr.getDouble("temperature_2m");
            double felt=curr.optDouble("apparent_temperature",Double.NaN);
            int weatherCode=curr.optInt("weather_code",-1);
            JSONArray maxima=daily.getJSONArray("temperature_2m_max");
            JSONArray minima=daily.getJSONArray("temperature_2m_min");
            JSONArray rain=daily.getJSONArray("precipitation_probability_max");
            if(!Double.isFinite(temp)||Math.abs(temp)>70)
                throw new IllegalStateException("Temperatura inválida");
            return "Según el pronóstico de Open Meteo, donde estás ahora hay "+
                Math.round(temp)+" grados y está "+condition(weatherCode)+
                (Double.isFinite(felt)?". La sensación térmica es de "+
                    Math.round(felt)+" grados":"")+
                ". Para hoy se esperan entre "+
                Math.round(minima.getDouble(0))+" y "+
                Math.round(maxima.getDouble(0))+
                " grados, con probabilidad máxima de lluvia del "+
                Math.round(rain.getDouble(0))+" por ciento.";
        }finally{conn.disconnect();}
    }
}