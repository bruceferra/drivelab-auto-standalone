package ar.com.rango5gps.drivelabauto.boot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed evidence for ONE newly launched Waze MainActivity task.
 * Parse complete read-only "am stack list"; never identify tasks by package
 * alone and never touch Google Maps, Spotify, OEM, or pre-existing Waze.
 *
 * The caller MUST have independently proved there was no Waze task in its
 * pre-launch double-read snapshot and that the owned VirtualDisplay is live.
 */
final class NavWazeRelocationPolicy {
    private static final Pattern ROOT=Pattern.compile(
        "^\\s*(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*([^\\s]+)");
    private static final String MAIN="com.waze/com.waze.MainActivity";
    private static final String SHORT_MAIN="com.waze/.MainActivity";
    private static final Pattern WAZE=Pattern.compile(
        "com\\.waze/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}");

    static final class Task {
        final int display,stack,id;
        final String component;
        Task(int d,int s,int t,String c) {
            display=d;stack=s;id=t;component=c;
        }
        boolean same(Task other) {
            return other!=null&&display==other.display&&
                stack==other.stack&&id==other.id&&
                component.equals(other.component);
        }
    }
    static final class Candidate {
        final int stack,task;
        final String component;
        Candidate(int s,int t,String c){stack=s;task=t;component=c;}
        boolean same(Candidate o){
            return o!=null&&stack==o.stack&&task==o.task&&
                component.equals(o.component);
        }
    }
    private static final class Stack {
        final int id,display;
        final List<Task> tasks=new ArrayList<>();
        Stack(int root,int d){id=root;display=d;}
    }
    private NavWazeRelocationPolicy(){}

    private static List<Stack> read(String report,int own,int map,int media) {
        if(!NavOwnClusterWazePolicy.ownedIds(own,map,media)||
           report==null||report.length()<24||report.length()>128000)
            throw new SecurityException("Inventario o display NAV inválido");
        List<Stack> stacks=new ArrayList<>();
        Set<Integer> used=new HashSet<>();
        Stack active=null;
        int n=0;
        for(String line:report.split("\\r?\\n",-1)){
            if(++n>1800||line.length()>3000)
                throw new SecurityException("Inventario ADB truncado");
            Matcher head=ROOT.matcher(line);
            if(head.find()){
                int root=Integer.parseInt(head.group(1));
                int d=Integer.parseInt(head.group(2));
                if(root<=0||d>999)
                    throw new SecurityException("Stack inválido");
                active=new Stack(root,d);
                stacks.add(active);
                continue;
            }
            if((line.contains("Stack id=")||line.contains("RootTask id="))
               &&line.contains("displayId="))
                throw new SecurityException("Cabecera incompleta");
            if(!line.contains("taskId="))continue;
            if(active==null)throw new SecurityException("Tarea sin pantalla");
            Matcher task=TASK.matcher(line);
            if(!task.find())throw new SecurityException("Tarea sin ID");
            int id=Integer.parseInt(task.group(1));
            if(id<=0||!used.add(id))
                throw new SecurityException("Tarea duplicada");
            active.tasks.add(new Task(active.display,active.id,id,task.group(2)));
        }
        if(stacks.isEmpty())throw new SecurityException("Sin stacks ADB");
        return stacks;
    }
    static boolean noWazeBefore(String report,int own,int map,int media){
        for(Stack stack:read(report,own,map,media))
            for(Task t:stack.tasks){
                if(t.component.startsWith("com.waze/"))return false;
                if(stack.display==own)return false;
            }
        return true;
    }
    /**
     * Only Waze's newly created MainActivity on PHYSICAL display 0 qualifies.
     * The original public launcher may still be on our own virtual display.
     * Anything on another display or mixed stack aborts the experiment.
     */
    static Candidate physicalMain(String report,int own,int map,int media){
        Candidate found=null;
        int ownSplash=0;
        for(Stack stack:read(report,own,map,media)){
            for(Task t:stack.tasks){
                boolean waze=t.component.startsWith("com.waze/");
                if(stack.display==own){
                    if(!waze||!WAZE.matcher(t.component).matches()||
                       ++ownSplash>1)
                        throw new SecurityException("Cluster NAV tiene tareas ambiguas");
                }
                if(!waze)continue;
                if(stack.display==0){
                    if(stack.id<=10||t.id<=10||
                       stack.tasks.size()!=1||
                       !(MAIN.equals(t.component)||SHORT_MAIN.equals(t.component))||
                       found!=null)
                        throw new SecurityException("Waze físico no es una pila exclusiva nueva");
                    found=new Candidate(stack.id,t.id,t.component);
                }else if(stack.display!=own){
                    throw new SecurityException("Waze activo en pantalla ajena");
                }
            }
        }
        return found;
    }
    static String moveCommand(Candidate c,int own,int map,int media){
        if(!NavOwnClusterWazePolicy.ownedIds(own,map,media)||
           c==null||c.stack<=10||c.task<=10||
           c.stack>=10000000||c.task>=10000000||
           !(MAIN.equals(c.component)||SHORT_MAIN.equals(c.component)))
            throw new SecurityException("Pila Waze ajena o inválida");
        return "am display move-stack "+c.stack+" "+own;
    }
    /**
     * Only roots on NAV's owned display may be closed, and each root must
     * contain exactly one Waze task. Foreign or physical Waze aborts cleanup.
     */
    static List<Task> ownedWaze(String report,int own,int map,int media){
        List<Task> targets=new ArrayList<>();
        for(Stack stack:read(report,own,map,media)){
            if(stack.display==own){
                if(stack.tasks.size()>1)
                    throw new SecurityException("Pila mixta en display NAV");
                for(Task t:stack.tasks){
                    if(stack.id<=10||t.id<=10||
                       !WAZE.matcher(t.component).matches())
                        throw new SecurityException("Tarea NAV no verificable");
                    targets.add(t);
                    if(targets.size()>2)
                        throw new SecurityException("Más de dos tareas Waze NAV");
                }
            }else for(Task t:stack.tasks){
                if(t.component.startsWith("com.waze/"))
                    throw new SecurityException("Waze fuera de pantalla NAV");
            }
        }
        return targets;
    }
    static boolean sameOwned(List<Task> a,List<Task> b){
        if(a==null||b==null||a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)
            if(!a.get(i).same(b.get(i)))return false;
        return true;
    }
    static String removeOwned(Task t,int own,int map,int media){
        if(!NavOwnClusterWazePolicy.ownedIds(own,map,media)||
           t==null||t.display!=own||t.stack<=10||t.id<=10||
           t.stack>=10000000||t.id>=10000000||
           !WAZE.matcher(t.component).matches())
            throw new SecurityException("Prohibido cerrar stack Waze ajeno");
        return "am stack remove "+t.stack;
    }
    static boolean relocated(String report,Candidate wanted,
                             int own,int map,int media){
        if(wanted==null||!NavOwnClusterWazePolicy.ownedIds(own,map,media))
            return false;
        int count=0,ownWaze=0;
        for(Stack stack:read(report,own,map,media)){
            for(Task t:stack.tasks){
                boolean waze=t.component.startsWith("com.waze/");
                if(stack.display==own){
                    if(!waze||!WAZE.matcher(t.component).matches()||
                       ++ownWaze>2)
                        return false;
                }
                if(!waze)continue;
                if(stack.display!=own)return false;
                if(stack.id==wanted.stack&&t.id==wanted.task&&
                   t.component.equals(wanted.component)&&
                   stack.tasks.size()==1)count++;
            }
        }
        return count==1;
    }
}
