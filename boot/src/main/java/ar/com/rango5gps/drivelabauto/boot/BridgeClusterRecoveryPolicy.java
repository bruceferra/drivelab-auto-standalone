package ar.com.rango5gps.drivelabauto.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse Android 11 'am stack list' conservatively. NO OEM stack mutation.
 * Only a root on Display 1 containing EXACTLY ONE Waze/Maps task qualifies.
 * Reject mixed or ambiguous stacks, missing display info, invalid roots.
 */
final class BridgeClusterRecoveryPolicy {
    static final class Task {
        final int stackId,taskId;
        final String pkg;
        Task(int s,int t,String p){stackId=s;taskId=t;pkg=p;}
    }
    private static final Pattern STACK=Pattern.compile(
        "(?:Stack|RootTask) id=([0-9]{1,7})\\b.*\\bdisplayId=([0-9]{1,2})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*"+
        "(com\\.waze|com\\.google\\.android\\.apps\\.maps)/"+
        "[A-Za-z0-9_.$]+(?:\\s|$)");
    private static final Pattern ANY_TASK=Pattern.compile("^\\s*taskId=");
    private BridgeClusterRecoveryPolicy(){}
    static List<Task> onlyVerifiedMapTasks(String report){
        if(report==null||report.length()>200_000||report.length()<24)
            return Collections.emptyList();
        String[] lines=report.split("\\r?\\n",-1);
        List<Task> found=new ArrayList<>();
        int root=-1,display=-1,tasks=0,matched=0,mapId=-1;
        String pkg=null;
        boolean malformed=false;
        for(String line:lines){
            Matcher head=STACK.matcher(line);
            if(head.find()){
                if(root>=0&&display==1&&matched>0){
                    if(tasks!=1||matched!=1||mapId<=10||root<=10)
                        return Collections.emptyList();
                    found.add(new Task(root,mapId,pkg));
                }
                root=Integer.parseInt(head.group(1));
                display=Integer.parseInt(head.group(2));
                // OEM/home stacks may coexist on Display 1, but are
                // NEVER candidates to remove; mixed map+OEM is rejected.
                tasks=0;matched=0;mapId=-1;pkg=null;
            }else if(ANY_TASK.matcher(line).find()){
                if(root<0)return Collections.emptyList();
                tasks++;
                Matcher task=TASK.matcher(line);
                if(task.find()){
                    mapId=Integer.parseInt(task.group(1));
                    pkg=task.group(2);
                    matched++;
                }
            }else if((line.contains("Stack id=")||line.contains("RootTask id="))&&
                    line.contains("displayId")){
                malformed=true;
            }
        }
        if(malformed)return Collections.emptyList();
        if(root>=0&&display==1&&matched>0){
            if(tasks!=1||matched!=1||mapId<=10||root<=10)
                return Collections.emptyList();
            found.add(new Task(root,mapId,pkg));
        }
        if(found.isEmpty()||found.size()>2)return Collections.emptyList();
        for(int i=0;i<found.size();i++){
            Task t=found.get(i);
            if(t.stackId<=10||t.taskId<=10||t.pkg==null)
                return Collections.emptyList();
            for(int j=0;j<i;j++)
                if(t.taskId==found.get(j).taskId||
                   t.stackId==found.get(j).stackId)
                    return Collections.emptyList();
        }
        return found;
    }
    static String removeCommand(Task task){
        if(task==null||task.taskId<=10||task.taskId>=10_000_000||
            (!"com.waze".equals(task.pkg)&&
             !"com.google.android.apps.maps".equals(task.pkg)))
            throw new SecurityException("No es una tarea de mapa verificable");
        return "am stack remove "+task.stackId;
    }
}
