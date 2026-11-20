package ar.com.rango5gps.drivelabauto.boot;

import java.util.ArrayList;
import java.util.List;

/** Restricted accessibility list policy. Never erase or reorder OEM entries. */
final class BridgeAccessPolicy {
    static final String PACKAGE="ar.com.rango5gps.drivelabauto.boot.lab";
    static final String SHORT=PACKAGE+"/.BridgeWheelAccessibilityService";
    static final String LONG=PACKAGE+"/"+PACKAGE.replace(".lab","")+
        ".BridgeWheelAccessibilityService";
    // Android expands relative class names using the manifest namespace
    // (ar.com.rango5gps.drivelabauto.boot), NOT the LAB applicationId.
    static final String FULL=PACKAGE+
        "/ar.com.rango5gps.drivelabauto.boot.BridgeWheelAccessibilityService";
    private BridgeAccessPolicy(){}
    static boolean own(String s){return SHORT.equals(s)||FULL.equals(s);}
    static List<String> parse(String input){
        if(input==null||input.length()>1024||
           !input.matches("[A-Za-z0-9_.$/:]*"))return null;
        List<String> names=new ArrayList<>();
        if(input.isEmpty())return names;
        for(String p:input.split(":",-1)){
            if(p.isEmpty()||!p.contains("/")||
               p.indexOf('/')!=p.lastIndexOf('/'))return null;
            names.add(p);
        }
        return names;
    }
    static String addOwn(String input){
        List<String> all=parse(input);if(all==null)return null;
        int n=0;
        for(String p:all)if(own(p))n++;
        if(n>1)return null;
        if(n==1)return input;
        if(all.size()>=32)return null;
        String next=input==null||input.isEmpty()?SHORT:input+":"+SHORT;
        return next.length()>1024?null:next;
    }
    static String removeOwn(String input){
        List<String> all=parse(input);if(all==null)return null;
        int n=0;
        List<String> rest=new ArrayList<>();
        for(String p:all)if(own(p))n++;else rest.add(p);
        // Never toggle if ours absent or if this would disable last service.
        return n!=1||rest.isEmpty()?null:String.join(":",rest);
    }
    static boolean enabled(String input){
        List<String> all=parse(input);
        if(all==null)return false;
        int n=0;for(String p:all)if(own(p))n++;
        return n==1;
    }
    static String command(String exact){
        if(exact==null||exact.isEmpty()||exact.length()>1024||
           parse(exact)==null)
            throw new IllegalArgumentException("Lista segura de servicios requerida");
        return "settings put secure enabled_accessibility_services "+exact;
    }
}
