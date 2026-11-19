package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.charset.StandardCharsets;

/** Regression: this vehicle's full ADB task output exceeds 96 KiB.
 * The streaming path must discard unrelated tasks and preserve the matching
 * stack header, task ID, package, bounds, and freeform state.
 */
public final class T1AdbTaskExcerptTest {
    private static void ok(boolean yes,String msg){
        if(!yes)throw new AssertionError(msg);
    }
    private static String collect(String command,int task,String source){
        T1AdbTaskExcerpt f=new T1AdbTaskExcerpt(command,task);
        byte[] raw=source.getBytes(StandardCharsets.UTF_8);
        for(int i=0;i<raw.length;i+=197){
            int len=Math.min(197,raw.length-i);
            byte[] piece=new byte[len];
            System.arraycopy(raw,i,piece,0,len);
            f.accept(piece);
        }
        f.finish();
        return f.result();
    }
    public static void main(String[] args){
        String pkg="ar.com.rango5gps.drivelabauto.receiver";
        StringBuilder huge=new StringBuilder();
        huge.append("Stack id=4 bounds=[0,0][1920,720] displayId=1 userId=0\n");
        for(int i=0;i<1900;i++){
            huge.append(" taskId=").append(10000+i)
                .append(": com.example.other/.Activity bounds=[0,0][1920,720]\n");
        }
        huge.append("Stack id=2810 bounds=[0,0][1920,1080] displayId=0 userId=0\n");
        huge.append(" taskId=2810: ").append(pkg)
            .append("/.T1CenterFreeformTestActivity bounds=[0,0][1920,1080]\n");
        huge.append("Stack id=2811 bounds=[0,0][1920,1080] displayId=0 userId=0\n");
        huge.append(" taskId=2811: com.autolink.bluetooth/.Home bounds=[0,0][1920,1080]\n");
        ok(huge.length()>96*1024,"Synthetic Android dump must exceed old 96KB limit");
        String selected=collect("am stack list",2810,huge.toString());
        ok(selected.contains("Stack id=2810")&&
            selected.contains("displayId=0")&&
            selected.contains("taskId=2810: "+pkg+"/.T1CenterFreeformTestActivity"),
            "Exact stack header and disposable task retained");
        ok(selected.length()<1024&&!selected.contains("com.autolink.bluetooth")&&
            !selected.contains("taskId=2811")&&
            !selected.contains("displayId=1"),
            "Unrelated and OEM task output discarded");

        String android="Display #0:\n"+
            "  * Task{a1 #28101 visible=true type=standard mode=fullscreen A=123:other}\n"+
            "  * Task{a2 #2810 visible=true type=standard mode=freeform A=10068:"+
            pkg+" U=0 StackId=2810 sz=1}\n"+
            "  * Task{a3 #2811 visible=false type=standard mode=fullscreen "+
            "A=10069:com.autolink.bluetooth U=0 StackId=2811 sz=1}\n";
        String out=collect("dumpsys activity activities",2810,android);
        ok(out.contains("#2810")&&out.contains("mode=freeform")&&
            out.contains(pkg)&&!out.contains("#28101")&&
            !out.contains("com.autolink.bluetooth"),
            "Dumpsys filters exact freeform task only");
        boolean rejected=false;
        try{new T1AdbTaskExcerpt("sh -c anything",2810);}
        catch(SecurityException ex){rejected=true;}
        ok(rejected,"Arbitrary commands must be denied");
        System.out.println("PASS >96KiB task excerpt with exact task, bounds and freeform mode");
    }
}
