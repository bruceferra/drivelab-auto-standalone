package ar.com.rango5gps.drivelabauto.boot;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LAB52: decide whether an existing isolated Waze MainActivity can safely
 * be reattached to a BRAND NEW NAV-owned cluster VirtualDisplay.
 *
 * A prior NAV session stores only the exact verified Android Waze task ID,
 * stack ID and timestamp, never an arbitrary app package to force-stop.
 * Display IDs are transient, and MUST NOT be persisted or reused after OTA.
 *
 * First migration from LAB51 has no persisted record. It can be recovered
 * only after an EXPLICIT tap confirming the single isolated Waze task on
 * physical display 0. Do not adopt Waze on OEM/T1Launcher displays 1+.
 *
 * Source-only parser: no Binder, shell command, Android app launch or stop.
 */
final class NavWazeResumptionPolicy {
    private static final Pattern ROOT=Pattern.compile(
        "^\\s*(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*([^\\s]+)");
    static final String MAIN="com.waze/com.waze.MainActivity";
    static final String SHORT_MAIN="com.waze/.MainActivity";
    static final long MAX_AGE_MS=14L*24*3600*1000;
    enum Kind { EMPTY, RECLAIM, CONFIRM_LEGACY, BLOCKED }
    static final class Decision {
        final Kind kind;
        final int oldDisplay,stack,task;
        final String component,reason;
        Decision(Kind k,int display,int root,int id,String activity,
                 String note){
            kind=k;oldDisplay=display;stack=root;task=id;
            component=activity;reason=note;
        }
        boolean same(Decision d){
            return d!=null&&kind==d.kind&&oldDisplay==d.oldDisplay&&
                stack==d.stack&&task==d.task&&
                component.equals(d.component);
        }
        NavWazeRelocationPolicy.Candidate candidate(){
            if(kind!=Kind.RECLAIM||stack<=10||task<=10)
                throw new SecurityException("Waze huérfano no autorizado");
            return new NavWazeRelocationPolicy.Candidate(
                stack,task,component);
        }
    }
    private static Decision bad(String why){
        return new Decision(Kind.BLOCKED,-1,-1,-1,"",why);
    }
    private NavWazeResumptionPolicy(){}
    static Decision inspect(String report,int own,int map,int media,
                            int savedTask,long savedAt,long now,
                            boolean userConfirmedLegacy){
        if(!NavOwnClusterWazePolicy.ownedIds(own,map,media)||
           report==null||report.length()<24||report.length()>128000)
            return bad("Inventario NAV incompleto");
        if(savedTask>0&&savedTask<=10)
            return bad("Tarea previa inválida");
        if(now<0||savedAt<0)return bad("Reloj de sesión inválido");
        int display=-1,root=-1,inRoot=0,rootCount=0,headers=0;
        int wazeCount=0,taskId=-1,taskRoot=-1,taskDisplay=-1;
        String component="";
        Set<Integer> ids=new HashSet<>();
        for(String line:report.split("\\r?\\n",-1)){
            if(++rootCount>1800||line.length()>3000)
                return bad("Inventario ADB demasiado grande");
            Matcher header=ROOT.matcher(line);
            if(header.find()){
                root=Integer.parseInt(header.group(1));
                display=Integer.parseInt(header.group(2));
                if(root<=0||display>999)
                    return bad("Root de Android inválido");
                headers++;inRoot=0;continue;
            }
            if((line.contains("Stack id=")||
                line.contains("RootTask id="))&&
               line.contains("displayId="))
                return bad("Cabecera Android ilegible");
            if(!line.contains("taskId="))continue;
            if(root<0)return bad("Tarea sin stack");
            Matcher task=TASK.matcher(line);
            if(!task.find())return bad("Tarea Android ilegible");
            int taskNumber=Integer.parseInt(task.group(1));
            if(taskNumber<=0||!ids.add(taskNumber))
                return bad("Tarea duplicada");
            String activity=task.group(2);
            inRoot++;
            if(display==own)return bad(
                "El nuevo display NAV todavía contiene tareas");
            if(!activity.startsWith("com.waze/"))continue;
            if(++wazeCount>1||inRoot!=1||
               taskNumber<=10||root<=10||
               !(MAIN.equals(activity)||SHORT_MAIN.equals(activity)))
                return bad("Hay varias tareas Waze o no es MainActivity");
            if(display!=0)
                return bad("Waze está en otro display; no se toca");
            taskId=taskNumber;taskRoot=root;taskDisplay=display;
            component=activity;
        }
        if(headers==0)return bad("Sin stacks Android");
        if(wazeCount==0)
            return new Decision(Kind.EMPTY,-1,-1,-1,"",
                "No hay Waze previo; iniciar como siempre");
        // The candidate's stack must contain only its Waze task.
        // Parse again strictly so a following non-Waze task in that
        // same stack cannot accidentally satisfy the earlier check.
        int inCandidate=0;boolean candidate=false;
        for(String line:report.split("\\r?\\n",-1)){
            Matcher h=ROOT.matcher(line);
            if(h.find()){
                candidate=Integer.parseInt(h.group(1))==taskRoot&&
                    Integer.parseInt(h.group(2))==taskDisplay;
            }else if(candidate&&line.contains("taskId="))inCandidate++;
        }
        if(inCandidate!=1)return bad("La pila Waze incluye otra aplicación");
        boolean match=savedTask==taskId&&savedTask>10&&savedAt>0&&
            now>=savedAt&&now-savedAt<=MAX_AGE_MS;
        if(match||userConfirmedLegacy)
            return new Decision(Kind.RECLAIM,taskDisplay,taskRoot,
                taskId,component,
                match?"Tarea Waze NAV registrada recuperable":
                    "Adopción autorizada del Waze físico único");
        return new Decision(Kind.CONFIRM_LEGACY,taskDisplay,taskRoot,
            taskId,component,
            "Existe un Waze anterior en pantalla central; "+
            "se requiere confirmación para moverlo al clúster NAV");
    }
}
