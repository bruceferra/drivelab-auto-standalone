package ar.com.rango5gps.drivelabauto.receiver;

import java.util.ArrayList;
import java.util.List;

/** Exact user-authorized Android accessibility setting; never guess or
 * overwrite OEM/T1Launcher services. Null means no safe recovery possible.
 * Pure Java so all list handling is independently tested before shipping.
 */
final class T1WheelAccessListPolicy {
    static final String TARGET="ar.com.rango5gps.drivelabauto.receiver/.T1WazeWheelService";
    private static final String LONG_TARGET=
        "ar.com.rango5gps.drivelabauto.receiver/"+
        "ar.com.rango5gps.drivelabauto.receiver.T1WazeWheelService";
    private T1WheelAccessListPolicy(){}

    static boolean target(String component){
        return TARGET.equals(component)||LONG_TARGET.equals(component);
    }
    static String withoutOurService(String existing){
        if(existing==null||existing.isEmpty()||existing.length()>1024||
           !existing.matches("[A-Za-z0-9_.$/:]+"))return null;
        String[] entries=existing.split(":",-1);
        int own=0;
        List<String> remaining=new ArrayList<>();
        for(String entry:entries){
            if(entry.isEmpty()||!entry.contains("/")||entry.indexOf('/')!=entry.lastIndexOf('/'))
                return null;
            if(target(entry))own++;
            else remaining.add(entry);
        }
        // The system's accessibility service list is user-managed. Refuse
        // ambiguous aliases/duplicates or a first-time service enable.
        if(own!=1||remaining.isEmpty())return null;
        return String.join(":",remaining);
    }
    static String command(String listing){
        if(listing==null||listing.isEmpty()||listing.length()>1024||
           !listing.matches("[A-Za-z0-9_.$/:]+"))
            throw new IllegalArgumentException("Listado accesibilidad no seguro");
        return "settings put secure enabled_accessibility_services "+listing;
    }
}
