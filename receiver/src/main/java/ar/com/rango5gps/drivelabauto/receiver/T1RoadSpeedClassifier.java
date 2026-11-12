package ar.com.rango5gps.drivelabauto.receiver;

import java.util.Locale;

/** OSM legal maximum speed + road-class policy for the live offline adviser.
 * The observed vehicle speed is NEVER the maximum-speed input.
 * The limits in OSM are indicative and may be missing or out-of-date.
 */
final class T1RoadSpeedClassifier {
    static int maxKmh(String raw){
        if(raw==null)return -1;
        String s=raw.trim().toLowerCase(Locale.ROOT);
        // Ambiguous conditional/listed speeds aren't interpreted as an
        // unconditional legal maximum.
        if(s.isEmpty()||s.indexOf(';')>=0||s.indexOf('@')>=0||
            s.indexOf('|')>=0||s.contains("variable")||
            s.contains("none")||s.contains("signals"))return -1;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
            "^([0-9]{1,3})(?:\\s*(km/h|kmh|kph|mph))?$").matcher(s);
        if(!m.matches())return -1;
        int n;
        try{n=Integer.parseInt(m.group(1));}
        catch(NumberFormatException ex){return -1;}
        if(n<5||n>180)return -1;
        if("mph".equals(m.group(2)))n=(int)Math.round(n*1.609344d);
        return n;
    }
    static boolean provincialOrNational(String ref){
        if(ref==null)return false;
        String s=ref.trim().toUpperCase(Locale.ROOT);
        // Match common OSM Argentine route references; not every 'RP' in
        // an unrelated street name counts as a provincial highway.
        return s.matches("(?s).*(?:^|[^A-Z])(?:RN|RP|RUTA\\s+(?:NACIONAL|PROVINCIAL)|NATIONAL|PROVINCIAL)(?:\\s*|[-:])(?:[0-9]+).*");
    }
    static T1OptimizedModePolicy.Road category(
          String highway,String ref,String maxspeed,String motorroad,String expressway){
        T1OptimizedModePolicy.Road R=T1OptimizedModePolicy.Road.ROAD;
        T1OptimizedModePolicy.Road C=T1OptimizedModePolicy.Road.CITY;
        T1OptimizedModePolicy.Road M=T1OptimizedModePolicy.Road.MOTORWAY;
        T1OptimizedModePolicy.Road U=T1OptimizedModePolicy.Road.UNKNOWN;
        if(highway==null)return U;
        String h=highway.trim().toLowerCase(Locale.ROOT);
        if(h.equals("motorway")||h.equals("motorway_link"))return M;
        int kmh=maxKmh(maxspeed);
        boolean route=provincialOrNational(ref);
        boolean fast="yes".equalsIgnoreCase(motorroad)||
            "yes".equalsIgnoreCase(expressway);
        // A designated motorway remains motorway even at a temporary
        // 60 km/h restriction; do not confuse infrastructure with traffic.
        if((h.equals("trunk")||h.equals("trunk_link"))&&fast)
            return M;
        if(h.equals("primary")||h.equals("primary_link")||
           h.equals("secondary")||h.equals("secondary_link")||
           h.equals("trunk")||h.equals("trunk_link")||
           h.equals("tertiary")||h.equals("tertiary_link")){
            // Fix: Buenos Aires urban avenues can be mapped as primary
            // or secondary. Posted 50/60 km/h means city for our profile.
            if(kmh>=0&&kmh<=60)return C;
            if(kmh>=80)return R;
            // 70 km/h: distinguish a signed RN/RP from an urban avenue.
            if(kmh>=65)return route?R:C;
            // No maxspeed: route refs are a useful, but not infallible
            // proxy; default primary/secondary to CITY, not carretera.
            return route?R:C;
        }
        if(h.equals("residential")||h.equals("living_street")||
           h.equals("service")||h.equals("unclassified"))
            return C;
        return U; // pedestrian/cycleway/track/unknown remain unknown
    }
    private T1RoadSpeedClassifier(){}
}
