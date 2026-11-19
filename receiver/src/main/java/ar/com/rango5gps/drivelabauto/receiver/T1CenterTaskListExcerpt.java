package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.charset.StandardCharsets;

/** Android 11 am stack list bounded streaming task inventory.
 * Keep only stack headers and taskId lines; never hold complete dumpsys
 * data, and never parse arbitrary shell tokens into commands.
 */
final class T1CenterTaskListExcerpt {
    private final StringBuilder line=new StringBuilder();
    private final StringBuilder output=new StringBuilder();
    private String stack="";
    private int found=0;
    void accept(byte[] bytes){
        String part=new String(bytes,StandardCharsets.UTF_8);
        for(int i=0;i<part.length();i++){
            char c=part.charAt(i);
            if(c=='\n'){keep(line.toString());line.setLength(0);}
            else if(line.length()<3072)line.append(c);
        }
    }
    void finish(){if(line.length()>0)keep(line.toString());}
    private void keep(String line){
        String clean=line.trim();
        if(clean.startsWith("Stack id=")||clean.startsWith("Stack ")){
            stack=line;
        }else if(clean.startsWith("taskId=")&&clean.contains("/")){
            if(++found>180||output.length()+stack.length()+line.length()>64000)
                throw new IllegalStateException(
                    "Demasiadas tareas; diagnóstico limitado a 180");
            if(!stack.isEmpty())output.append(stack).append('\n');
            output.append(line).append('\n');
        }
    }
    String result(){return output.toString();}
}
