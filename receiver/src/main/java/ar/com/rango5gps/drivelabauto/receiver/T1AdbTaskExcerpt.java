package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.charset.StandardCharsets;

/** Bounded streaming filter for Android 11 task diagnostics.
 * Caller consumes full ADB output, retaining ONLY the exact selected task
 * plus its immediately preceding stack header, for one disposable activity.
 * No arbitrary shell pipelines, no shell interpolation, no OEM task changes.
 */
final class T1AdbTaskExcerpt {
    private final boolean stacks;
    private final String exactTask,taskHeader;
    private final StringBuilder line=new StringBuilder();
    private final StringBuilder selected=new StringBuilder();
    private String latestStack="";
    private int captured=0;
    T1AdbTaskExcerpt(String command,int id){
        if(id<=0||id>9999999||(!command.equals("am stack list")&&
            !command.equals("dumpsys activity activities")))
            throw new SecurityException("Filtro de tarea ADB no permitido");
        stacks=command.equals("am stack list");
        exactTask="taskId="+id+":";
        taskHeader="#"+id;
    }
    void accept(byte[] payload){
        // Android structural identifiers are ASCII; user labels elsewhere
        // are intentionally ignored. The bounded line buffer also prevents
        // memory growth on malformed or unusual output.
        String text=new String(payload,StandardCharsets.UTF_8);
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            if(ch=='\n'){
                takeLine(line.toString());
                line.setLength(0);
            }else if(line.length()<3072){
                line.append(ch);
            }
        }
    }
    void finish(){if(line.length()>0)takeLine(line.toString());}
    private void takeLine(String raw){
        String trimmed=raw.trim();
        if(stacks){
            if(trimmed.startsWith("Stack id=")||
                trimmed.startsWith("Stack ")){
                latestStack=raw;
                return;
            }
            if(raw.contains(exactTask)){
                if(!latestStack.isEmpty())append(latestStack);
                append(raw);
            }
        }else if(raw.contains(taskHeader)){
            // #2810 cannot inadvertently match #28101.
            int pos=raw.indexOf(taskHeader);
            if(pos>=0&&(pos+taskHeader.length()==raw.length()||
                !Character.isDigit(raw.charAt(pos+taskHeader.length()))))
                append(raw);
        }
    }
    private void append(String text){
        if(++captured>12||selected.length()+text.length()>16384)
            throw new IllegalStateException(
                "Demasiadas coincidencias de tarea ADB");
        selected.append(text).append('\n');
    }
    String result(){return selected.toString();}
}
