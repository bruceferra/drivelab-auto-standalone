package ar.com.rango5gps.drivelabauto.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed cleanup of ONLY the two live NAV-owned virtual-display tasks.
 * Never searches by package globally: Maps, Waze or Spotify might also run
 * in T1Launcher, another Android display, or an unrelated user session.
 *
 * This pure Java policy never executes commands. The Activity must double
 * read the stack inventory and re-check the same target before each removal.
 */
final class NavMosaicExitPolicy {
    static final String MAPS = "com.google.android.apps.maps";
    static final String WAZE = "com.waze";
    static final String SPOTIFY = "com.spotify.music";
    private static final Pattern STACK = Pattern.compile(
        "^\\s*(?:Stack|RootTask) id=([0-9]{1,7})\\b.*?\\bdisplayId=([0-9]{1,3})\\b");
    private static final Pattern TASK = Pattern.compile(
        "^\\s*taskId=([0-9]{1,7}):\\s*([^\\s]+)");

    static final class Task {
        final int displayId, stackId, taskId;
        final String pkg, component;
        Task(int display, int stack, int task, String pkg, String component) {
            this.displayId=display; this.stackId=stack; this.taskId=task;
            this.pkg=pkg; this.component=component;
        }
        String signature() {
            return displayId+"|"+stackId+"|"+taskId+"|"+pkg+"|"+component;
        }
    }
    static final class Snapshot {
        final boolean valid;
        final String reason;
        final Task map, spotify;
        Snapshot(boolean valid,String reason,Task map,Task spotify){
            this.valid=valid;this.reason=reason;this.map=map;this.spotify=spotify;
        }
        boolean same(Snapshot other){
            return valid&&other!=null&&other.valid&&
                signature(map).equals(signature(other.map))&&
                signature(spotify).equals(signature(other.spotify));
        }
        List<Task> targets(){
            if(!valid)return Collections.emptyList();
            List<Task> out=new ArrayList<>();
            // Stop Spotify audio first, then the map. A missing task is fine.
            if(spotify!=null)out.add(spotify);
            if(map!=null)out.add(map);
            return out;
        }
    }
    private static final class StackInfo{
        final int displayId,stackId;
        final List<Task> tasks=new ArrayList<>();
        StackInfo(int display,int stack){displayId=display;stackId=stack;}
    }
    private static String signature(Task task){
        return task==null?"none":task.signature();
    }
    private static Snapshot invalid(String reason){
        return new Snapshot(false,reason,null,null);
    }
    private NavMosaicExitPolicy(){}

    static Snapshot parse(String report,int mapDisplay,String mapPackage,
                          int spotifyDisplay){
        if(mapDisplay<2||mapDisplay>99||spotifyDisplay<2||
           spotifyDisplay>99||mapDisplay==spotifyDisplay||
           !(MAPS.equals(mapPackage)||WAZE.equals(mapPackage)))
            return invalid("Identidad NAV inválida");
        if(report==null||report.length()<24||report.length()>128000)
            return invalid("Inventario ADB inválido");
        StackInfo current=null;
        List<StackInfo> stacks=new ArrayList<>();
        Set<Integer> usedTaskIds=new HashSet<>();
        int lines=0;
        for(String line:report.split("\\r?\\n",-1)){
            if(++lines>1800||line.length()>3000)
                return invalid("Inventario demasiado grande");
            Matcher head=STACK.matcher(line);
            if(head.find()){
                int root=Integer.parseInt(head.group(1));
                int display=Integer.parseInt(head.group(2));
                if(root<=0||display>999)return invalid("Pila inválida");
                current=new StackInfo(display,root);
                stacks.add(current);
                continue;
            }
            if(line.contains("Stack id=")||line.contains("RootTask id=")){
                if(line.contains("displayId="))
                    return invalid("Cabecera ADB ambigua");
                continue;
            }
            if(!line.contains("taskId="))continue;
            if(current==null)return invalid("Tarea sin pila");
            Matcher m=TASK.matcher(line);
            if(!m.find())return invalid("Identidad de tarea incompleta");
            int taskId=Integer.parseInt(m.group(1));
            if(taskId<=0||!usedTaskIds.add(taskId))
                return invalid("Tarea duplicada o inválida");
            String component=m.group(2);
            int slash=component.indexOf('/');
            String pkg=slash>0?component.substring(0,slash):"";
            current.tasks.add(new Task(current.displayId,current.stackId,
                taskId,pkg,component));
        }
        if(stacks.isEmpty())return invalid("Sin inventario Android");
        Task map=null,spotify=null;
        int mapCount=0,spotifyCount=0;
        for(StackInfo stack:stacks){
            if(stack.displayId!=mapDisplay&&stack.displayId!=spotifyDisplay)
                continue; // Physical 0/1 and all foreign VDs are untouchable.
            String expected=stack.displayId==mapDisplay?mapPackage:SPOTIFY;
            for(Task t:stack.tasks){
                // An unrelated task in an owned NAV panel is NOT proof
                // that Maps/Spotify finished; never claim clean exit.
                if(!expected.equals(t.pkg))
                    return invalid("Hay una tarea ajena en pantalla NAV");
                if(stack.tasks.size()!=1||stack.stackId<=10||t.taskId<=10||
                   !t.component.matches(Pattern.quote(expected)+
                     "/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}"))
                    return invalid("Pila mixta o tarea NAV ambigua");
                if(stack.displayId==mapDisplay){
                    map=t;mapCount++;
                }else{
                    spotify=t;spotifyCount++;
                }
            }
        }
        if(mapCount>1||spotifyCount>1)
            return invalid("Más de una sesión en un panel NAV");
        return new Snapshot(true,"Identidades verificadas",map,spotify);
    }
    /**
     * Removing a root stack does not prove its task was finished. Android
     * may rehome a task onto another display. Verify the ORIGINAL taskId
     * disappeared from the ENTIRE read-only stack inventory before NAV
     * releases either virtual display. No other task is ever targeted.
     */
    static boolean taskStillExists(String report,Task expected){
        if(expected==null||expected.taskId<=10||
           report==null||report.length()<24||report.length()>128000)
            throw new SecurityException("Inventario total no verificable");
        int lines=0;
        for(String line:report.split("\\r?\\n",-1)){
            if(++lines>1800||line.length()>3000)
                throw new SecurityException("Inventario total inválido");
            if(!line.contains("taskId="))continue;
            Matcher matcher=TASK.matcher(line);
            if(!matcher.find())
                throw new SecurityException("Tarea sin identidad");
            if(Integer.parseInt(matcher.group(1))==expected.taskId)
                return true;
        }
        return false;
    }
    static boolean sameTask(Task wanted,Task current){
        return wanted!=null&&current!=null&&
            wanted.signature().equals(current.signature());
    }
    static String removeCommand(Task task,int mapDisplay,String mapPackage,
                                int spotifyDisplay){
        if(task==null||task.displayId<2||task.displayId>99||
           task.stackId<=10||task.stackId>=10000000||
           task.taskId<=10||task.taskId>=10000000||
           !((task.displayId==mapDisplay&&mapPackage.equals(task.pkg))||
             (task.displayId==spotifyDisplay&&SPOTIFY.equals(task.pkg)))||
           !task.component.matches(Pattern.quote(task.pkg)+
                "/\\.?[A-Za-z_][A-Za-z0-9_.$]{0,200}"))
            throw new SecurityException("Prohibido retirar una tarea ajena a NAV");
        return "am stack remove "+task.stackId;
    }
    static boolean rejected(String response){
        return response==null||
            response.matches("(?is).*(error|denied|exception|unknown command|failure).*");
    }
}
