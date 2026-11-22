package ar.com.rango5gps.drivelabauto.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict READ-ONLY Android-11 am stack list inventory of navigation tasks.
 * Does not issue commands, launch apps, infer surface visibility, or identify
 * who owns a virtual display. The calling UI must separately verify that
 * external display names match an expected owner (e.g. T1Mapa).
 */
final class NavClusterTaskInventory {
    static final String MAPS="com.google.android.apps.maps";
    static final String WAZE="com.waze";
    private static final Pattern STACK=Pattern.compile(
        "^(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*([^\\s]+)");
    private static final Pattern PACKAGE=Pattern.compile(
        "^(com\\.waze|com\\.google\\.android\\.apps\\.maps)/"+
        "(\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200})$");

    static final class Task {
        final String pkg,component;
        final int displayId,stackId,taskId;
        final boolean dedicatedStack;
        Task(String p,String c,int display,int root,int task,boolean dedicated){
            pkg=p;component=c;displayId=display;stackId=root;taskId=task;
            dedicatedStack=dedicated;
        }
        String signature(){
            return pkg+"|"+displayId+"|"+stackId+"|"+taskId+"|"+
                component+"|"+dedicatedStack;
        }
    }
    static final class Snapshot {
        final boolean valid;
        final String reason;
        final List<Task> maps;
        final int navMapId,navMediaId;
        Snapshot(boolean ok,String why,List<Task> entries,int n,int m){
            valid=ok;reason=why;maps=Collections.unmodifiableList(entries);
            navMapId=n;navMediaId=m;
        }
        boolean same(Snapshot other){
            if(other==null||!valid||!other.valid||
               navMapId!=other.navMapId||navMediaId!=other.navMediaId||
               maps.size()!=other.maps.size())return false;
            for(int i=0;i<maps.size();i++)
                if(!maps.get(i).signature().equals(
                    other.maps.get(i).signature()))return false;
            return true;
        }
        List<Task> external(String pkg){
            ArrayList<Task> found=new ArrayList<>();
            if(!valid)return found;
            for(Task t:maps)
                if(t.pkg.equals(pkg)&&t.displayId>=2&&
                   t.displayId<=99&&t.displayId!=navMapId&&
                   t.displayId!=navMediaId&&t.dedicatedStack)
                    found.add(t);
            return found;
        }
        int count(String pkg){
            int n=0;
            for(Task t:maps)if(t.pkg.equals(pkg))n++;
            return n;
        }
    }
    private static final class Group {
        final int displayId,stackId;
        final List<Task> candidates=new ArrayList<>();
        int count;
        Group(int display,int stack){displayId=display;stackId=stack;}
    }
    private NavClusterTaskInventory(){}

    private static Snapshot invalid(String why,int nav,int media){
        return new Snapshot(false,why,new ArrayList<Task>(),nav,media);
    }
    private static void finish(Group group,List<Task> out){
        if(group==null)return;
        for(Task t:group.candidates)
            out.add(new Task(t.pkg,t.component,t.displayId,
                t.stackId,t.taskId,group.count==1));
    }
    static Snapshot parse(String report,int navMapId,int navMediaId){
        if(navMapId<2||navMapId>99||navMediaId<2||navMediaId>99||
           navMapId==navMediaId)
            return invalid("IDs propios NAV inválidos",navMapId,navMediaId);
        if(report==null||report.length()<24||report.length()>128_000)
            return invalid("ADB no devolvió un inventario válido",navMapId,navMediaId);
        Group current=null;
        ArrayList<Task> all=new ArrayList<>();
        boolean headerSeen=false;
        Set<Integer> taskIds=new HashSet<>();
        int lines=0;
        for(String line:report.split("\\r?\\n")){
            if(++lines>1800||line.length()>3000)
                return invalid("Inventario ADB demasiado largo",navMapId,navMediaId);
            Matcher head=STACK.matcher(line);
            if(head.find()){
                finish(current,all);
                int root=Integer.parseInt(head.group(1));
                int display=Integer.parseInt(head.group(2));
                if(root<=0||display>999)
                    return invalid("Raíz de pantalla inválida",navMapId,navMediaId);
                current=new Group(display,root);
                headerSeen=true;
                continue;
            }
            if(line.contains("Stack id=")||line.contains("RootTask id=")){
                if(line.contains("displayId="))
                    return invalid("Cabecera de pila ambigua",navMapId,navMediaId);
                continue;
            }
            if(!line.contains("taskId="))continue;
            if(current==null)
                return invalid("Tarea sin pantalla",navMapId,navMediaId);
            Matcher task=TASK.matcher(line);
            if(!task.find())
                return invalid("Tarea sin ID verificable",navMapId,navMediaId);
            int id=Integer.parseInt(task.group(1));
            if(id<=0||!taskIds.add(id))
                return invalid("Tarea duplicada o inválida",navMapId,navMediaId);
            current.count++;
            Matcher pkg=PACKAGE.matcher(task.group(2));
            if(pkg.matches())
                current.candidates.add(new Task(pkg.group(1),task.group(2),
                    current.displayId,current.stackId,id,false));
        }
        finish(current,all);
        if(!headerSeen)
            return invalid("No se encontró ningún Stack con displayId",navMapId,navMediaId);
        all.sort(Comparator.comparing(Task::signature));
        return new Snapshot(true,"Lectura ADB realizada; NO prueba visibilidad",
            all,navMapId,navMediaId);
    }
}
