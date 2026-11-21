package ar.com.rango5gps.drivelabnav.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure JVM: only reuse one EXACT existing Maps top task in NAV's own
 * known virtual display, without touching any task in the T1Launcher VD.
 */
public final class NavMosaicTaskPolicy {
    private static final String MAPS="com.google.android.apps.maps";
    private static final Pattern HEADER=Pattern.compile(
        "(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*"+
        "(com\\.google\\.android\\.apps\\.maps)/"+
        "([A-Za-z_][A-Za-z0-9_.$]+)\\b");
    public static final class Target {
        public final int displayId,stackId,taskId;
        public final String component;
        private Target(int d,int s,int t,String c){
            displayId=d;stackId=s;taskId=t;component=c;
        }
    }
    private NavMosaicTaskPolicy(){}
    public static Target find(String stackReport,int ownedDisplay){
        if(ownedDisplay<2||ownedDisplay>99||
           stackReport==null||stackReport.length()<40||
           stackReport.length()>128000)return null;
        Target selected=null;
        int display=-1,stack=-1,count=0,mapsCount=0,task=-1;
        String component=null;
        String[] lines=stackReport.split("\\r?\\n");
        for(int i=0;i<=lines.length;i++){
            String line=i==lines.length?"Stack id=0 displayId=0":lines[i];
            Matcher h=HEADER.matcher(line);
            if(h.find()){
                if(display==ownedDisplay){
                    // Exactly one top Maps task and no other app in stack.
                    if(mapsCount!=1||count!=1||stack<=10||
                       task<=10||component==null)return null;
                    if(selected!=null)return null;
                    selected=new Target(display,stack,task,component);
                }
                stack=Integer.parseInt(h.group(1));
                display=Integer.parseInt(h.group(2));
                count=mapsCount=0;task=-1;component=null;
            }else if(line.contains("taskId=")){
                if(stack<0)return null;
                count++;
                Matcher m=TASK.matcher(line);
                if(m.find()){
                    mapsCount++;
                    task=Integer.parseInt(m.group(1));
                    component=MAPS+"/"+m.group(3);
                }
            }
        }
        return selected;
    }
    public static boolean same(Target a,Target b){
        return a!=null&&b!=null&&
            a.displayId==b.displayId&&a.stackId==b.stackId&&
            a.taskId==b.taskId&&a.component.equals(b.component);
    }
    public static String command(Target target,String uri,int ownedDisplay){
        if(target==null||target.displayId!=ownedDisplay||
           ownedDisplay<2||ownedDisplay>99||target.stackId<=10||
           target.taskId<=10||target.component==null||
           !target.component.matches("com\\.google\\.android\\.apps\\.maps/"+
                "[A-Za-z_][A-Za-z0-9_.$]+")||
           uri==null||uri.length()>600||
           !uri.matches("google\\.navigation:q=[A-Za-z0-9%.,_:+\\-]+&mode=d"))
            throw new SecurityException("NAV: invalid existing-map task or URI");
        return "am start --display "+target.displayId+
            " --task "+target.taskId+
            " --activity-single-top -a android.intent.action.VIEW"+
            " -d '"+uri+"' -n "+target.component;
    }
    public static boolean delivered(String response){
        return response!=null&&response.contains(
            "intent has been delivered to currently running top-most instance.")&&
            !response.contains("Error:")&&!response.contains("Exception");
    }
}
