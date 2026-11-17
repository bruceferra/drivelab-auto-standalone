package ar.com.rango5gps.drivelabauto.receiver;

import java.util.regex.Pattern;

/** Pure parser/command policy for Android 11's per-display InputDispatcher.
 * A visible map task alone NEVER authorizes injecting keys: the exact
 * Waze/Maps window must have focus and accept keys on the SAME display.
 */
final class T1WheelAdbFocusPolicy {
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";
    private T1WheelAdbFocusPolicy(){}

    static boolean supported(int display,String pkg){
        return display>0&&display<10&&
            (WAZE.equals(pkg)||MAPS.equals(pkg));
    }

    /** Android 11 'dumpsys input' Input Dispatcher State / Windows contains
     * name='Window{... com.waze/...}', displayId=N, hasFocus=true,
     * visible=true, canReceiveKeys=true on the SAME window line.
     * Rejects duplicate/foreign focus, ambiguous dumps, default display,
     * unknown app and the absence of an explicit focused map window.
     */
    static boolean focusedOn(String dump,int display,String pkg){
        if(!supported(display,pkg)||dump==null||dump.length()>96000)
            return false;
        boolean windows=false,matched=false;
        int focused=0;
        for(String original:dump.split("\\r?\\n")){
            String line=original.trim();
            if(line.equals("Input Dispatcher State:"))windows=false;
            if(line.equals("Windows:"))windows=true;
            if(!windows||!line.contains("name=")||
               !line.contains("displayId="+display+",")||
               !line.contains("hasFocus=true"))continue;
            focused++;
            if(focused>1)return false;
            String normalized=line.replace("name='Window{","name=Window{");
            if(normalized.contains(" "+pkg+"/")&&
               line.contains("visible=true")&&
               line.contains("canReceiveKeys=true")&&
               !line.contains("paused=true"))
                matched=true;
        }
        return focused==1&&matched;
    }

    static String command(int display,int keyCode){
        if(display<=0||display>=10||
           (keyCode!=21&&keyCode!=22&&keyCode!=23))
            throw new IllegalArgumentException("Tecla/display ADB no permitido");
        return "input dpad -d "+display+" keyevent "+keyCode;
    }
}
