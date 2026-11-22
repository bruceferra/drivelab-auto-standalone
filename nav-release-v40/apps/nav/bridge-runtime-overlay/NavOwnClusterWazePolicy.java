package ar.com.rango5gps.drivelabauto.boot;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Task-only guard for Waze on a VirtualDisplay CREATED by DriveLab NAV.
 * No launch, task movement, or deletion is executed by this pure Java class.
 */
final class NavOwnClusterWazePolicy {
    private static final Pattern STACK=Pattern.compile(
        "^\\s*(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*([^\\s]+)");
    private static final String WAZE="com.waze";
    private NavOwnClusterWazePolicy(){}
    static final class State {
        final boolean valid;
        final String reason;
        final int display,task,stack;
        final String component;
        State(boolean ok,String why,int d,int t,int s,String c){
            valid=ok;reason=why;display=d;task=t;stack=s;component=c;
        }
        boolean same(State other){
            return valid&&other!=null&&other.valid&&
                display==other.display&&task==other.task&&
                stack==other.stack&&component.equals(other.component);
        }
    }
    private static State bad(String reason,int display){
        return new State(false,reason,display,-1,-1,"");
    }
    static boolean ownedIds(int display,int map,int media){
        return display>=2&&display<=99&&map>=2&&map<=99&&
            media>=2&&media<=99&&display!=map&&
            display!=media&&map!=media;
    }
    static State inspect(String text,int cluster,int map,int media){
        if(!ownedIds(cluster,map,media))
            return bad("Destino de NAV inválido",cluster);
        if(text==null||text.length()<24||text.length()>128000)
            return bad("Inventario ADB incompleto",cluster);
        int current=-1,stack=-1,stackTasks=0,clusterTasks=0,headers=0;
        int wazeTask=-1,wazeStack=-1;
        String wazeComponent="";
        Set<Integer> ids=new HashSet<>();
        String[] lines=text.split("\\r?\\n",-1);
        if(lines.length>1800)return bad("Inventario demasiado grande",cluster);
        for(String line:lines){
            if(line.length()>3000)return bad("Línea ADB inválida",cluster);
            Matcher root=STACK.matcher(line);
            if(root.find()){
                current=Integer.parseInt(root.group(2));
                stack=Integer.parseInt(root.group(1));
                if(stack<=0||current>999)
                    return bad("Stack inválido",cluster);
                headers++;stackTasks=0;continue;
            }
            if(line.contains("Stack id=")||line.contains("RootTask id=")){
                if(line.contains("displayId="))
                    return bad("Cabecera incompleta",cluster);
                continue;
            }
            if(!line.contains("taskId="))continue;
            if(stack<0)return bad("Tarea sin stack",cluster);
            Matcher task=TASK.matcher(line);
            if(!task.find())return bad("Tarea sin ID",cluster);
            int id=Integer.parseInt(task.group(1));
            String component=task.group(2);
            int slash=component.indexOf('/');
            String pkg=slash<=0?"":component.substring(0,slash);
            if(id<=0||!ids.add(id))
                return bad("Tarea duplicada",cluster);
            stackTasks++;
            if(current==cluster){
                clusterTasks++;
                if(id<=10||stack<=10||!WAZE.equals(pkg)||
                   !component.matches("com\\.waze/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}")||
                   stackTasks!=1||wazeTask!=-1)
                    return bad("Tarea ajena o múltiple en clúster NAV",cluster);
                wazeTask=id;wazeStack=stack;wazeComponent=component;
            }else if(WAZE.equals(pkg)){
                // Reusing Waze outside this VID risks moving central/OEM tasks.
                return bad("Waze ya existe en otra pantalla",cluster);
            }
        }
        if(headers==0||clusterTasks>1)
            return bad("Inventario Cluster ambiguo",cluster);
        return new State(true,"identidad estable",cluster,
            wazeTask,wazeStack,wazeComponent);
    }
    static String startCommand(int own,int map,int media,String component){
        if(!ownedIds(own,map,media)||component==null||
           !component.matches("com\\.waze/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}"))
            throw new SecurityException("Destino/componente Waze no autorizado");
        // Android's NEW_TASK + MULTIPLE_TASK requires a distinct task;
        // unlike SINGLE_TOP, it must not reuse a Waze task elsewhere.
        // The app itself could still start a later Activity on display 0:
        // the flight recorder detects that without moving any task.
        return "am start --display "+own+
            " --activity-new-task --activity-multiple-task"+
            " -a android.intent.action.MAIN"+
            " -c android.intent.category.LAUNCHER -n "+component;
    }
    static String stopCommand(State task,int own,int map,int media){
        if(task==null||!task.valid||!ownedIds(own,map,media)||
           task.display!=own||task.task<=10||task.stack<=10||
           task.task>=10000000||task.stack>=10000000||
           !task.component.matches("com\\.waze/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}"))
            throw new SecurityException("No es una tarea Waze propia de NAV");
        return "am stack remove "+task.stack;
    }
    static boolean accepted(String output){
        return output!=null&&
            (output.contains("Starting: Intent")||
             output.contains("Activity not started,"))&&
            !output.matches("(?is).*(error:|exception|permission denial|denied).*");
    }
}
