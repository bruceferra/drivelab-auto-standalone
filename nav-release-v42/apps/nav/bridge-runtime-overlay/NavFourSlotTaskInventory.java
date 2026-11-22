package ar.com.rango5gps.drivelabauto.boot;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only snapshot of Android's "am stack list" for a single exact NAV
 * VirtualDisplay. Never identify an app using a package name alone.
 * This class executes no shell commands.
 */
final class NavFourSlotTaskInventory {
    private static final Pattern ROOT=Pattern.compile(
        "^\s*(?:Stack|RootTask) id=([0-9]{1,7})\b.*?\bdisplayId=([0-9]{1,3})\b");
    private static final Pattern TASK=Pattern.compile(
        "^\s*taskId=([0-9]{1,7}):\s*([^\s]+)");
    private NavFourSlotTaskInventory(){}
    static boolean exact(String raw,NavFourSlotPolicy.Stream stream){
        if(raw==null||stream==null||raw.length()<24||
           raw.length()>128000)return false;
        int activeStack=-1,activeDisplay=-1;
        int matched=0,occupants=0,headers=0,rows=0;
        Set<Integer> allTaskIds=new HashSet<>();
        for(String line:raw.split("\r?\n",-1)){
            if(++rows>1800||line.length()>3000)return false;
            Matcher h=ROOT.matcher(line);
            if(h.find()){
                activeStack=Integer.parseInt(h.group(1));
                activeDisplay=Integer.parseInt(h.group(2));
                if(activeStack<=0||activeDisplay>999)return false;
                headers++;
                continue;
            }
            if((line.contains("Stack id=")||
                line.contains("RootTask id="))&&
               line.contains("displayId="))
                return false;
            if(!line.contains("taskId="))continue;
            if(activeStack<0||activeDisplay<0)return false;
            Matcher t=TASK.matcher(line);
            if(!t.find())return false;
            int task=Integer.parseInt(t.group(1));
            if(task<=0||!allTaskIds.add(task))return false;
            String component=t.group(2);
            int slash=component.indexOf('/');
            String pkg=slash>0?component.substring(0,slash):"";
            if(activeDisplay==stream.displayId){
                occupants++;
                if(task==stream.taskId&&activeStack==stream.stackId&&
                   pkg.equals(stream.pkg)&&
                   component.matches(
                     Pattern.quote(stream.pkg)+
                     "/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}"))
                    matched++;
            }
            if(task==stream.taskId&&
               (activeDisplay!=stream.displayId||
                activeStack!=stream.stackId||
                !pkg.equals(stream.pkg)))return false;
        }
        return headers>0&&occupants==1&&matched==1;
    }
}
