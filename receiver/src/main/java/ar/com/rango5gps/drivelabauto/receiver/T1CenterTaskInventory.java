package ar.com.rango5gps.drivelabauto.receiver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only task inventory; a task record is NOT proof that the process
 * is running or consuming RAM. Only later PackageManager verification
 * can authorize stopping its owning user-installed application.
 */
final class T1CenterTaskInventory {
    static final class Entry{
        final String pkg;
        int centerTasks=0,otherDisplayTasks=0;
        Entry(String pkg){this.pkg=pkg;}
    }
    private static final Pattern DISPLAY=Pattern.compile(
        "\\bdisplayId\\s*[=:]\\s*(\\d+)");
    private static final Pattern TASK=Pattern.compile(
        "^taskId\\s*=\\s*(\\d+)\\s*:\\s*"+
        "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)+)/");
    private T1CenterTaskInventory(){}
    static List<Entry> parse(String raw){
        LinkedHashMap<String,Entry> apps=new LinkedHashMap<>();
        int display=-1;
        if(raw==null)return new ArrayList<>();
        for(String original:raw.split("\\r?\\n")){
            String line=original.trim();
            if(line.startsWith("Stack ")){
                Matcher m=DISPLAY.matcher(line);
                display=m.find()?Integer.parseInt(m.group(1)):-1;
                continue;
            }
            Matcher m=TASK.matcher(line);
            if(!m.find()||display<0)continue;
            String pkg=m.group(2);
            if(!T1CenterUserPackagePolicy.allowed(pkg))continue;
            Entry e=apps.get(pkg);
            if(e==null){
                if(apps.size()>=100)throw new IllegalStateException(
                    "Demasiadas apps para inventario seguro");
                e=new Entry(pkg);apps.put(pkg,e);
            }
            if(display==0)e.centerTasks++;
            else e.otherDisplayTasks++;
        }
        return new ArrayList<>(apps.values());
    }
    static boolean canStop(Entry e){
        return e!=null&&e.centerTasks>0&&e.otherDisplayTasks==0&&
            T1CenterUserPackagePolicy.allowed(e.pkg);
    }
}
