package ar.com.rango5gps.drivelabauto.receiver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Android 11 shell task-list parser; fail closed on ambiguous task/display.
 * Never guess a task ID from the app process PID or a recent-activity row.
 */
final class T1AdbTaskParser {
    private static final Pattern DISPLAY=Pattern.compile(
        "(?:displayId\\s*[=:]\\s*|Display\\s*#)([0-9]+)");
    private static final Pattern TASK_ID=Pattern.compile(
        "(?:taskId\\s*[=:]\\s*|Task\\s+id\\s+#|Task\\{[^}]*?#)([0-9]+)");
    private static final Pattern RECT=Pattern.compile(
        "(?:bounds|mBounds)\\s*[=:]\\s*\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]");
    private static final Pattern RECT_ALT=Pattern.compile(
        "(?:bounds|mBounds)\\s*[=:]\\s*Rect\\(([0-9]+),\\s*([0-9]+)\\s*-\\s*([0-9]+),\\s*([0-9]+)\\)");
    static final class Box {
        final int left,top,right,bottom;
        Box(int a,int b,int c,int d){
            left=a;top=b;right=c;bottom=d;
        }
        boolean near(Box other,int tolerance){
            return other!=null&&
                Math.abs(left-other.left)<=tolerance&&
                Math.abs(top-other.top)<=tolerance&&
                Math.abs(right-other.right)<=tolerance&&
                Math.abs(bottom-other.bottom)<=tolerance;
        }
        String geometry(){
            return left+" "+top+" "+right+" "+bottom;
        }
        @Override public String toString(){return "["+geometry()+"]";}
    }
    static final class Task {
        final int id,display;
        final String app;
        final Box bounds;
        Task(int id,int display,String app,Box bounds){
            this.id=id;this.display=display;this.app=app;
            this.bounds=bounds;
        }
        @Override public String toString(){
            return "task="+id+" display="+display+
                " pkg="+app+" bounds="+bounds;
        }
    }
    private static final class Pending {
        int task=-1,display=-1;
        Box bounds=null;
        String app=null;
    }
    private T1AdbTaskParser(){}
    private static Box bounds(String line){
        Matcher m=RECT.matcher(line);
        if(!m.find())m=RECT_ALT.matcher(line);
        if(!m.find(0))return null;
        try{
            int a=Integer.parseInt(m.group(1)),b=Integer.parseInt(m.group(2));
            int c=Integer.parseInt(m.group(3)),d=Integer.parseInt(m.group(4));
            if(a<0||b<0||c<=a||d<=b||c>8192||d>8192)return null;
            return new Box(a,b,c,d);
        }catch(NumberFormatException e){return null;}
    }
    private static String knownApp(String line){
        if(line.contains("com.waze/")||line.contains("A=com.waze")||
           line.contains(" com.waze ")||line.endsWith("com.waze"))
            return "com.waze";
        if(line.contains("com.google.android.apps.maps/")||
           line.contains("A=com.google.android.apps.maps")||
           line.contains(" com.google.android.apps.maps ")||
           line.endsWith("com.google.android.apps.maps"))
            return "com.google.android.apps.maps";
        if(line.contains("com.spotify.music/")||
            line.contains("A=com.spotify.music")||
            line.contains(" com.spotify.music ")||
            line.endsWith("com.spotify.music"))
            return "com.spotify.music";
        return null;
    }
    static List<Task> scan(String output){return scan(output,null);}
    private static List<Task> scan(String output,String centerPackage){
        LinkedHashMap<Integer,Task> found=new LinkedHashMap<>();
        if(output==null)return new ArrayList<>();
        int display=-1;
        Pending current=new Pending();
        int linesAfterTask=0;
        for(String original:output.split("\\r?\\n")){
            String line=original.trim();
            Matcher dm=DISPLAY.matcher(line);
            if(dm.find()){
                display=Integer.parseInt(dm.group(1));
                if((line.startsWith("Display ")||line.startsWith("Stack "))&&
                   current.task>0){
                    // Flush current task with its original display first.
                    if(current.app!=null&&current.display>=0)
                        found.put(current.task,new Task(current.task,
                            current.display,current.app,current.bounds));
                    current=new Pending();
                }
            }
            Matcher task=TASK_ID.matcher(line);
            if(task.find()){
                if(current.task>0&&current.app!=null&&current.display>=0)
                    found.put(current.task,new Task(current.task,
                        current.display,current.app,current.bounds));
                current=new Pending();
                current.task=Integer.parseInt(task.group(1));
                current.display=display;
                linesAfterTask=0;
            }else if(current.task>0)linesAfterTask++;
            if(current.task<=0||linesAfterTask>9)continue;
            String app=knownApp(line);
            if(centerPackage!=null && (line.contains(centerPackage+"/") ||
                line.contains("A="+centerPackage+" ") || line.endsWith("A="+centerPackage) ||
                line.contains(" "+centerPackage+" ")))app=centerPackage;
            if(app!=null)current.app=app;
            Box b=bounds(line);
            if(b!=null)current.bounds=b;
        }
        if(current.task>0&&current.app!=null&&current.display>=0)
            found.put(current.task,new Task(current.task,current.display,
                current.app,current.bounds));
        return new ArrayList<>(found.values());
    }
    /** The car may have multiple Waze tasks on several displays. Reject all
     * ambiguity instead of accidentally resizing a center-screen window.
     */
    /** Explicit center-screen tasks only; never match cluster or OEM apps. */
    static Task uniqueCenter(String raw,String pkg){
        if(pkg==null||!pkg.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"))return null;
        Task found=null;
        for(Task t:scan(raw,pkg)){
            if(t.display!=0||!t.app.equals(pkg))continue;
            if(found!=null&&found.id!=t.id)return null;
            found=t;
        }
        return found;
    }
    static Task unique(String raw,int display,String pkg){
        if(display<=0||(!pkg.equals("com.waze")&&
            !pkg.equals("com.google.android.apps.maps")))return null;
        Task selected=null;
        for(Task t:scan(raw)){
            if(t.display!=display||!t.app.equals(pkg))continue;
            if(selected!=null&&selected.id!=t.id)return null;
            selected=t;
        }
        return selected;
    }
}