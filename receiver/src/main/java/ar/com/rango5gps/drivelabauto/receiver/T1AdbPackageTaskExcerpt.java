package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.charset.StandardCharsets;

/** Keep only task headers of one PackageManager-verified user-installed package.
 * Used to distinguish a newly opened external app from any existing task.
 * Always refuses OEM/system package names; installation verified by caller.
 */
final class T1AdbPackageTaskExcerpt {
    private final String target;
    private final StringBuilder line=new StringBuilder();
    private final StringBuilder result=new StringBuilder();
    private String stack="";
    private int hits=0;
    T1AdbPackageTaskExcerpt(String pkg){
        if(!T1CenterUserPackagePolicy.allowed(pkg))
            throw new SecurityException("No se puede leer tarea de paquete OEM");
        target=" "+pkg+"/";
    }
    void accept(byte[] bytes){
        String text=new String(bytes,StandardCharsets.UTF_8);
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='\n'){
                keep(line.toString());line.setLength(0);
            }else if(line.length()<3072){line.append(c);}
        }
    }
    void finish(){if(line.length()>0)keep(line.toString());}
    private void keep(String raw){
        String value=raw.trim();
        if(value.startsWith("Stack id=")||value.startsWith("Stack ")){
            stack=raw;
        }else if(value.startsWith("taskId=")&&raw.contains(target)){
            if(++hits>20||result.length()+raw.length()+stack.length()>24000)
                throw new IllegalStateException("Tareas duplicadas o excesivas");
            if(!stack.isEmpty())result.append(stack).append('\n');
            result.append(raw).append('\n');
        }
    }
    String result(){return result.toString();}
}
