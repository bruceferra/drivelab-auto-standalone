package ar.com.rango5gps.drivelabauto.receiver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strictly read-only dump parser for the reversible center-screen experiment.
 * Fail closed when the original task resize mode cannot be recovered.
 */
final class T1CenterWindowModePolicy {
    private static final Pattern HEADER=Pattern.compile(
        "(?:Task\\{[^\\r\\n]*?#|TaskRecord\\{[^\\r\\n]*?#|taskId\\s*[=:]\\s*)(\\d+)(?!\\d)");
    private static final Pattern MODE=Pattern.compile(
        "\\b(?:mResizeMode|resizeMode)\\s*[=:]\\s*(RESIZE_MODE_[A-Z_]+|[0-3])");
    private static final Pattern FULLSCREEN=Pattern.compile(
        "\\bmode\\s*[=:]\\s*fullscreen\\b|\\bmWindowingMode\\s*[=:]\\s*fullscreen\\b");
    private T1CenterWindowModePolicy(){}
    static boolean isSystemPackage(String pkg){
        return pkg==null||pkg.startsWith("com.autolink.")||
            pkg.startsWith("com.jetour.")||pkg.startsWith("com.chery.")||
            pkg.startsWith("com.android.")||pkg.startsWith("android.")||
            pkg.equals("android");
    }
    /** Task headers are task-specific; a resizeMode from any other task is
     * NEVER safe as the value to restore to this particular task.
     * The same task may appear in recents and activities; ambiguity fails.
     */
    static int originalResizeMode(String dump,int id,String pkg){
        if(dump==null||id<=0||isSystemPackage(pkg))return -1;
        String[] lines=dump.split("\\r?\\n");
        int found=-1;
        for(int i=0;i<lines.length;i++){
            String header=lines[i];
            Matcher h=HEADER.matcher(header);
            boolean match=false;
            while(h.find())if(Integer.parseInt(h.group(1))==id)match=true;
            if(!match)continue;
            // Check matching package on the task header, not on an activity
            // line in an adjacent task's dump.
            if(!header.contains(pkg))
                continue;
            for(int j=i;j<Math.min(lines.length,i+80);j++){
                String line=lines[j];
                if(j>i&&(HEADER.matcher(line).find()||
                    line.trim().startsWith("Display #")||
                    line.trim().startsWith("RootTask #")))break;
                Matcher m=MODE.matcher(line);
                if(!m.find())continue;
                int value=decodeMode(m.group(1));
                if(value<0)return -1;
                if(found>=0&&found!=value)return -1;
                found=value;
                break;
            }
        }
        return found;
    }
    static int decodeMode(String mode){
        if(mode==null)return -1;
        switch(mode){
            case "0":case "RESIZE_MODE_UNRESIZEABLE":return 0;
            case "1":case "RESIZE_MODE_CROP_WINDOWS":return 1;
            case "2":case "RESIZE_MODE_RESIZEABLE":return 2;
            case "3":case "RESIZE_MODE_RESIZEABLE_AND_PIPABLE":return 3;
            default:return -1;
        }
    }
    static boolean isFullscreen(String dump,int id,String pkg){
        if(dump==null||id<=0||isSystemPackage(pkg))return false;
        for(String line:dump.split("\\r?\\n")){
            Matcher m=HEADER.matcher(line);
            while(m.find()){
                if(Integer.parseInt(m.group(1))==id&&
                    line.contains(pkg)&&
                    FULLSCREEN.matcher(line).find())return true;
            }
        }
        return false;
    }
    static boolean shellRejected(String output){
        if(output==null)return true;
        String s=output.toLowerCase(java.util.Locale.ROOT);
        return s.contains("error:")||s.contains("unknown command")||
            s.contains("exception")||s.contains("permission denial")||
            s.contains("not supported")||s.contains("failed");
    }
}
