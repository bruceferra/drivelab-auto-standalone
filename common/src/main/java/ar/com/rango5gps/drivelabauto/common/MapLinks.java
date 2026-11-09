package ar.com.rango5gps.drivelabauto.common;

import android.net.Uri;
import java.net.*;
import java.util.Locale;
import java.util.regex.*;

/** Parses only map/navigation links; never turns a map viewport into a destination. */
public final class MapLinks {
    private MapLinks(){}
    public static final class Destination {
        public final String kind,value;
        Destination(String k,String v){kind=k;value=v;}
        public Uri wazeUri(){return Uri.parse("waze://?"+("coord".equals(kind)?"ll=":"q=")+
            Uri.encode(value,"coord".equals(kind)?",":"")+"&navigate=yes");}
    }
    private static final Pattern URL=Pattern.compile(
        "(?:https?://|waze://|geo:|google\\.navigation:)[^\\s<>\\\"']+",Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKERS=Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern DIR_COORD=Pattern.compile("/(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:/|$)");
    private static boolean hostAllowed(String host){
        if(host==null)return false;
        String h=host.toLowerCase(Locale.US);
        return h.equals("maps.app.goo.gl")||h.equals("maps.google.com")||
            h.equals("google.com")||h.equals("www.google.com")||
            h.equals("waze.com")||h.equals("www.waze.com")||h.equals("ul.waze.com");
    }
    public static String extract(String text){
        if(text==null)return null;
        Matcher m=URL.matcher(text);
        if(!m.find())return null;
        return m.group().replaceAll("[),.;]+$","");
    }
    private static Destination coords(String text){
        if(text==null)return null;
        String[] parts=text.trim().split(",",2);
        if(parts.length!=2)return null;
        try{
            double lat=Double.parseDouble(parts[0]),lon=Double.parseDouble(parts[1]);
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||
                Math.abs(lon)>180||(lat==0&&lon==0))return null;
            // Preserve decimal spelling without localization.
            if(!text.trim().matches("-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?"))return null;
            return new Destination("coord",text.trim());
        }catch(NumberFormatException ex){return null;}
    }
    private static Destination text(String text){
        if(text==null)return null;
        Destination exact=coords(text);
        if(exact!=null)return exact;
        String cleaned=text.trim();
        if(cleaned.length()<3||cleaned.length()>180||cleaned.contains("\\n")||
            cleaned.contains("\\r")||!cleaned.matches("(?s).*\\p{L}.*"))return null;
        return new Destination("search",cleaned);
    }
    /**
     * Maps shares a named PLACE as a human-readable first line followed by a URL.
     * Use only the EXPLICIT shared place title, never a map viewport or an inferred
     * coordinate. Waze may select the first search match if coordinates are absent.
     */
    public static Destination fromShareLabel(String raw){
        String link=extract(raw);
        if(link==null)return null;
        try{
            Uri u=Uri.parse(link);
            if(!"https".equalsIgnoreCase(u.getScheme())||!hostAllowed(u.getHost()))return null;
            int at=raw.indexOf(link);
            if(at<=0)return null;
            String preceding=raw.substring(0,at).trim();
            if(preceding.isEmpty())return null;
            String label=preceding.split("[\\r\\n]",2)[0].trim();
            // The supplied title is useful as a Waze search, not a verified lat/lon.
            if(label.length()<4||label.length()>120||
                label.contains("://")||label.matches("(?i)^(compartir|share|maps|direcciones|ruta)[: ]*$"))
                return null;
            return text(label);
        }catch(RuntimeException ex){return null;}
    }
    private static Destination googleNamedPlace(Uri uri){
        String path=uri.getEncodedPath();
        if(path==null)return null;
        String decoded=Uri.decode(path);
        String prefix="/maps/place/";
        int at=decoded.indexOf(prefix);
        if(at<0)return null;
        String name=decoded.substring(at+prefix.length());
        int slash=name.indexOf('/');
        if(slash>=0)name=name.substring(0,slash);
        // A /maps/place/name is an explicit place; /maps/@lat,lon is only viewport.
        name=name.replace('+',' ').trim();
        if(name.matches("(?i)0x[0-9a-f]+:0x[0-9a-f]+")||
           name.equalsIgnoreCase("data")||name.startsWith("@"))return null;
        return text(name);
    }
    public static Destination parse(String raw){
        try{
            if(raw==null)return null;
            String link=extract(raw);
            if(link==null)return null;
            Uri u=Uri.parse(link);
            String scheme=u.getScheme();
            if(scheme==null)return null;
            scheme=scheme.toLowerCase(Locale.US);
            if(scheme.equals("waze")||scheme.equals("geo")||scheme.equals("google.navigation")){
                // geo: and google.navigation: are opaque URIs on Android.
                // Rebase their parameters to a synthetic hierarchical URI.
                Uri params=u;
                String specific=u.getSchemeSpecificPart();
                if(!scheme.equals("waze")){
                    String query=specific==null?"":specific;
                    int marker=query.indexOf('?');
                    if(marker>=0)query=query.substring(marker+1);
                    else if(scheme.equals("geo"))query="";
                    params=Uri.parse("https://local.invalid/?"+query);
                }
                String ll=params.getQueryParameter("ll");
                if(coords(ll)!=null)return coords(ll);
                String q=params.getQueryParameter("q");
                if(q!=null)return text(q);
                if(scheme.equals("geo")&&specific!=null)
                    return coords(specific.split("\\?",2)[0]);
                return null;
            }
            if(!scheme.equals("https")||!hostAllowed(u.getHost()))return null;
            String h=u.getHost().toLowerCase(Locale.US);
            if(h.contains("waze.com")){
                if(coords(u.getQueryParameter("ll"))!=null)return coords(u.getQueryParameter("ll"));
                return text(u.getQueryParameter("q"));
            }
            for(String param:new String[]{"destination","daddr","query","q"}){
                String v=u.getQueryParameter(param);
                Destination result=text(v);
                if(result!=null)return result;
            }
            String path=u.getEncodedPath();
            if(path==null)return null;
            Matcher markers=MARKERS.matcher(link);
            if(markers.find())return coords(markers.group(1)+","+markers.group(2));
            Destination named=googleNamedPlace(u);
            if(named!=null)return named;
            Matcher dir=DIR_COORD.matcher(Uri.decode(path));
            Destination result=null;
            while(dir.find())result=coords(dir.group(1)+","+dir.group(2));
            // Only direct /dir/ routes have a meaningful last coordinate.
            if(result!=null&&path.startsWith("/maps/dir/"))return result;
            return null;
        }catch(Exception e){return null;}
    }
    public static Destination resolve(String raw){
        String next=extract(raw);
        if(next==null)return null;
        Destination explicitShareTitle=fromShareLabel(raw);
        for(int i=0;i<6;i++){
            Destination direct=parse(next);
            if(direct!=null)return direct;
            try{
                Uri u=Uri.parse(next);
                if(!"https".equalsIgnoreCase(u.getScheme())||!hostAllowed(u.getHost()))return null;
                HttpURLConnection connection=(HttpURLConnection)new URL(next).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(3500);connection.setReadTimeout(3500);
                connection.setRequestMethod("GET");
                try{
                    int status=connection.getResponseCode();
                    if(status!=301&&status!=302&&status!=303&&status!=307&&status!=308)
                        return explicitShareTitle;
                    String target=connection.getHeaderField("Location");
                    if(target==null)return explicitShareTitle;
                    next=new URL(new URL(next),target).toString();
                    if(next.length()>4096)return null;
                }finally{connection.disconnect();}
            }catch(Exception e){return explicitShareTitle;}
        }
        Destination finalLink=parse(next);
        return finalLink!=null?finalLink:explicitShareTitle;
    }
}
