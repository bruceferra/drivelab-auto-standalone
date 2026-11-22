package ar.com.rango5gps.drivelabauto.boot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacted, read-only Android 11 stack report for Waze display migration.
 * Never logs navigation locations, user text, raw dumpsys, screen content
 * or unrelated applications. Takes no action on any task.
 */
final class NavWazeDisplayTrace {
    private static final Pattern STACK=Pattern.compile(
        "^\\s*(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]{1,100})");
    private NavWazeDisplayTrace(){}

    static String summarize(String input,int own,int centralMap,int media){
        if(input==null||input.length()>128000||input.length()<24)
            return "ADB sin inventario válido";
        int stack=-1,display=-1,found=0;
        StringBuilder sb=new StringBuilder();
        for(String line:input.split("\\r?\\n",-1)){
            if(line.length()>3000)continue;
            Matcher root=STACK.matcher(line);
            if(root.find()){
                stack=Integer.parseInt(root.group(1));
                display=Integer.parseInt(root.group(2));
                continue;
            }
            Matcher task=TASK.matcher(line);
            if(!task.find()||stack<0||display<0)continue;
            String pkg=task.group(2);
            String name;
            if("com.waze".equals(pkg))name="WAZE";
            else if("com.google.android.apps.maps".equals(pkg))name="MAPS";
            else if("com.spotify.music".equals(pkg))name="SPOTIFY";
            else continue;
            if(found++>=12){
                sb.append(" | ...");break;
            }
            String where=display==own?"CLUSTER-NAV":
                display==centralMap?"CENTRO-MAPS":
                display==media?"CENTRO-SPOTIFY":
                display==0?"CENTRAL-FISICA":"OTRO";
            if(sb.length()>0)sb.append(" | ");
            sb.append(name);
            if("WAZE".equals(name))sb.append("(").append(task.group(3)).append(")");
            sb.append(" @").append(where)
                .append("[d").append(display).append(" s")
                .append(stack).append(" t").append(task.group(1))
                .append("]");
        }
        return found==0?"sin tareas Waze/Maps/Spotify":sb.toString();
    }
}
