package ar.com.rango5gps.drivelabauto.boot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict Android-11 "am stack list" parser to target ONLY an existing
 * Google Maps task on a T1Launcher-style virtual display, never Display 0
 * or the Jetour physical cluster Display 1. Ambiguity -> no launch.
 * Pure JVM; the current and the immediately-before-send snapshots must match.
 */
final class BridgeT1LauncherRoutePolicy {
    static final String MAPS="com.google.android.apps.maps";
    private static final Pattern HEADER=Pattern.compile(
        "(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*"+
        "(com\\.google\\.android\\.apps\\.maps)/"+
        "([a-zA-Z_][a-zA-Z0-9_.$]+)\\b");
    private BridgeT1LauncherRoutePolicy(){}

    static final class Target {
        final int displayId,stackId,taskId;
        final String component;
        Target(int display,int stack,int task,String name){
            displayId=display;stackId=stack;taskId=task;
            component=name;
        }
    }
    static Target find(String report){
        if(report==null||report.length()>128_000||report.length()<40)
            return null;
        Target matched=null;
        int stack=-1,display=-1,taskCount=0,mapsCount=0,taskId=-1;
        String component=null;
        String[] lines=report.split("\\r?\\n");
        for(int i=0;i<=lines.length;i++){
            String line=i==lines.length?"Stack id=0 displayId=0":lines[i];
            Matcher header=HEADER.matcher(line);
            if(header.find()){
                if(display>=2&&display<=99&&mapsCount>0){
                    if(stack<=10||taskCount!=1||mapsCount!=1||taskId<=10||
                        component==null)return null;
                    Target found=new Target(display,stack,taskId,component);
                    if(matched!=null)return null;
                    matched=found;
                }
                stack=Integer.parseInt(header.group(1));
                display=Integer.parseInt(header.group(2));
                taskCount=0;mapsCount=0;taskId=-1;component=null;
            }else if(line.contains("taskId=")){
                if(stack<0)return null;
                taskCount++;
                Matcher task=TASK.matcher(line);
                if(task.find()){
                    mapsCount++;
                    taskId=Integer.parseInt(task.group(1));
                    // Group 2 is the exact package; group 3 is the
                    // actual Activity component. Never accidentally
                    // target a synthetic package/package Activity.
                    component=MAPS+"/"+task.group(3);
                }
            }
        }
        return matched;
    }
    static boolean same(Target expected,Target actual){
        return expected!=null&&actual!=null&&
            expected.stackId==actual.stackId&&
            expected.taskId==actual.taskId&&
            expected.displayId==actual.displayId&&
            expected.component.equals(actual.component);
    }
    /**
     * Existing task and its current top component are EXPLICIT targets.
     * Android may still refuse or create a new Activity INSIDE that task;
     * do not claim it was delivered unless CLI returns DELIVERED_TO_TOP.
     * The URI is generated via Android Uri.encode; reject shell metacharacters.
     */
    static String command(Target t,String uri){
        if(t==null||t.displayId<2||t.displayId>99||
           t.stackId<=10||t.taskId<=10||
           !t.component.matches(
             "com\\.google\\.android\\.apps\\.maps/"+
             "[A-Za-z_][A-Za-z0-9_.$]+")||
           uri==null||uri.length()>600||
           !uri.matches("google\\.navigation:q=[A-Za-z0-9%.,_:+\\-]+&mode=d"))
            throw new SecurityException("Destino o tarea T1Launcher inválida");
        return "am start --display "+t.displayId+
            " --task "+t.taskId+
            " --activity-single-top -a android.intent.action.VIEW"+
            " -d '"+uri+"' -n "+t.component;
    }
    static boolean deliveredToExistingTop(String output){
        return output!=null&&output.contains(
            "intent has been delivered to currently running top-most instance.")&&
            !output.contains("Error:")&&!output.contains("Exception");
    }
}
