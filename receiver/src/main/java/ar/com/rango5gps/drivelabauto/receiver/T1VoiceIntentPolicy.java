package ar.com.rango5gps.drivelabauto.receiver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative command routing for voice input. Never guesses addresses. */
final class T1VoiceIntentPolicy {
    private static final Pattern NAV=Pattern.compile(
        "(?iu)^\\s*(?:por\\s+favor[,]?\\s+)?(?:"+
        "(?:naveg[aá](?:r|me)?|ir|ll[eé]vame|gu[ií]ame|dirigime)"+
        "\\s+(?:hacia\\s+|hasta\\s+|al\\s+|a\\s+la\\s+|a\\s+los\\s+|a\\s+)|"+
        "c[oó]mo\\s+(?:llego|llegar)\\s+(?:a|al)\\s+|"+
        "(?:quiero|necesito)\\s+ir\\s+(?:a|al|hasta)\\s+|"+
        "(?:ruta|indicaciones)\\s+(?:a|al|hacia|hasta)\\s+)");
    private T1VoiceIntentPolicy(){}
    static String routeDestination(String said) {
        if(said==null)return null;
        Matcher m=NAV.matcher(said);
        if(!m.find())return null;
        String target=said.substring(m.end()).replaceFirst("[.?!\\s]+$","").trim();
        if(target.length()<3||target.length()>230)return null;
        return target;
    }
    static String described(String said) {
        String route=routeDestination(said);
        return route==null?"CONSULTA DE IA":"NAVEGACIÓN WAZE: "+route;
    }
}