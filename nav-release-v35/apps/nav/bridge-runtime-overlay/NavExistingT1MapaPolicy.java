package ar.com.rango5gps.drivelabauto.boot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict pre/post-flight inventory for reusing an EXISTING T1Launcher "T1Mapa"
 * display. Never creates or moves displays/tasks, never targets Display 0/1
 * or NAV's own map/Spotify displays. An Android task is not a visibility proof.
 */
final class NavExistingT1MapaPolicy {
    private static final Pattern STACK=Pattern.compile(
        "^\\s*(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK=Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*([^\\s]+)");
    static final String WAZE="com.waze";
    static final String MAPS="com.google.android.apps.maps";
    private NavExistingT1MapaPolicy(){}

    static final class State {
        final boolean valid;
        final String reason;
        final int target, wazeTask, mapTask, totalWaze;
        final String signature;
        State(boolean ok,String why,int id,int w,int m,int all,String snapshot){
            valid=ok;reason=why;target=id;wazeTask=w;mapTask=m;
            totalWaze=all;signature=snapshot;
        }
        boolean same(State other){
            return valid&&other!=null&&other.valid&&
                target==other.target&&signature.equals(other.signature);
        }
    }
    private static State invalid(String reason,int id){
        return new State(false,reason,id,-1,-1,0,"");
    }
    static State parse(String report,int target,int navMap,int navSpotify){
        if(target<2||target>99||target==navMap||target==navSpotify||
           navMap<2||navSpotify<2||navMap==navSpotify)
            return invalid("Pantalla T1Mapa no aislada",target);
        if(report==null||report.length()<24||report.length()>128000)
            return invalid("Inventario ADB incompleto",target);
        int display=-1,stack=-1,count=0,externalTasks=0;
        int waze=-1,maps=-1,totalWaze=0;
        boolean header=false;
        Set<Integer> taskIds=new HashSet<>();
        List<String> signatures=new ArrayList<>();
        int lines=0;
        for(String line:report.split("\\r?\\n",-1)){
            if(++lines>1800||line.length()>3000)
                return invalid("Inventario excesivo",target);
            Matcher h=STACK.matcher(line);
            if(h.find()){
                display=Integer.parseInt(h.group(2));
                stack=Integer.parseInt(h.group(1));
                if(stack<=0||display>999)
                    return invalid("Pila Android inválida",target);
                header=true;
                count=0;
                continue;
            }
            if(line.contains("Stack id=")||line.contains("RootTask id=")){
                if(line.contains("displayId="))
                    return invalid("Cabecera ambigua",target);
                continue;
            }
            if(!line.contains("taskId="))continue;
            if(!header)return invalid("Tarea sin pantalla",target);
            Matcher m=TASK.matcher(line);
            if(!m.find())return invalid("Tarea sin ID",target);
            int task=Integer.parseInt(m.group(1));
            if(task<=0||!taskIds.add(task))
                return invalid("Tarea duplicada",target);
            String component=m.group(2);
            int slash=component.indexOf('/');
            String pkg=slash<=0?"":component.substring(0,slash);
            if(WAZE.equals(pkg))totalWaze++;
            if(display!=target)continue;
            count++;externalTasks++;
            if(count!=1)
                return invalid("T1Mapa contiene una pila mixta",target);
            // A T1Launcher map virtual display can hold only a navigation
            // task for this experiment. Other packages or stacked tasks block.
            if(stack<=10||task<=10||
               !(MAPS.equals(pkg)||WAZE.equals(pkg))||
               !component.matches(Pattern.quote(pkg)+
                   "/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}"))
                return invalid("T1Mapa tiene tarea ajena/ambigua",target);
            signatures.add(stack+"|"+task+"|"+component);
            if(WAZE.equals(pkg)){
                if(waze!=-1)return invalid("Waze duplicado en T1Mapa",target);
                waze=task;
            }else{
                if(maps!=-1)return invalid("Maps duplicado en T1Mapa",target);
                maps=task;
            }
        }
        if(!header)return invalid("Sin pilas Android",target);
        if(externalTasks>2)return invalid("T1Mapa tiene demasiadas tareas",target);
        if(totalWaze>(waze==-1?0:1))
            return invalid("Waze ya está abierto en otra pantalla",target);
        signatures.sort(String::compareTo);
        return new State(true,"Inventario estable",target,waze,maps,
            totalWaze,String.join(";",signatures));
    }
    static String startCommand(int target,String component,int navMap,int navSpotify){
        if(target<2||target>99||target==navMap||target==navSpotify||
           !component.matches("com\\.waze/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}"))
            throw new SecurityException("Destino Waze/T1Mapa inválido");
        return "am start --display "+target+
            " -a android.intent.action.MAIN"+
            " -c android.intent.category.LAUNCHER"+
            " --activity-single-top -n "+component;
    }
    static boolean accepted(String response){
        return response!=null&&
            (response.contains("Starting: Intent")||
             response.contains("Activity not started,"))&&
            !response.matches("(?is).*(error:|exception|permission denial|denied).*");
    }
}
