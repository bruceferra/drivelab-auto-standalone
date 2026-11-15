package ar.com.rango5gps.drivelabauto.receiver;

public final class T1AdbTaskParserTest {
    private static void ok(boolean b,String message){
        if(!b)throw new AssertionError(message);
    }
    public static void main(String[] args){
        String stack="Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0\n"+
            " taskId=81: com.waze/com.waze.MainActivity bounds=[0,0][960,720]\n"+
            " taskId=82: com.google.android.apps.maps/com.google.Main bounds=[960,0][1920,720]\n"+
            "Stack id=1 bounds=[0,0][1920,1080] displayId=0 userId=0\n"+
            " taskId=5: com.waze/com.waze.MainActivity bounds=[0,0][1920,1080]";
        T1AdbTaskParser.Task w=
            T1AdbTaskParser.unique(stack,1,"com.waze");
        T1AdbTaskParser.Task m=
            T1AdbTaskParser.unique(stack,1,"com.google.android.apps.maps");
        ok(w!=null&&w.id==81&&w.display==1,
            "Select only physical cluster Waze task");
        ok(m!=null&&m.id==82&&m.display==1,
            "Select only physical cluster Google Maps task");
        ok(w.bounds!=null&&w.bounds.near(new T1AdbTaskParser.Box(
            0,0,960,720),0),"Waze bounds verified");
        ok(m.bounds!=null&&m.bounds.near(new T1AdbTaskParser.Box(
            960,0,1920,720),0),"Maps bounds verified");
        ok(T1AdbTaskParser.unique(stack,0,"com.waze")==null,
            "No commands to center display");
        ok(T1AdbTaskParser.unique(stack,1,"com.android.settings")==null,
            "No commands to arbitrary OEM package");
        String duplicates="Stack id=2 displayId=1\n"+
            "taskId=81: com.waze/com.waze.Main bounds=[0,0][960,720]\n"+
            "taskId=88: com.waze/com.waze.Main bounds=[0,0][960,720]";
        ok(T1AdbTaskParser.unique(duplicates,1,"com.waze")==null,
            "Reject two Waze tasks on same display");
        String missingDisplay="taskId=81: com.waze/com.waze.Main "+
            "bounds=[0,0][960,720]";
        ok(T1AdbTaskParser.unique(missingDisplay,1,"com.waze")==null,
            "Never guess display");
        String dump="Display #1 (activities from top to bottom):\n"+
            "  RootTask #9: type=standard mode=freeform\n"+
            "    * Task{a1b2 #78 type=standard A=com.waze U=0 "+
            "bounds=Rect(0, 0 - 960, 720)}\n"+
            "    * Task{c1d2 #79 type=standard A=com.google.android.apps.maps U=0 "+
            "bounds=Rect(960, 0 - 1920, 720)}\n"+
            "Display #0 (activities from top to bottom):\n"+
            "    * Task{zzzz #2 type=home A=com.android.launcher U=0}";
        w=T1AdbTaskParser.unique(dump,1,"com.waze");
        m=T1AdbTaskParser.unique(dump,1,"com.google.android.apps.maps");
        ok(w!=null&&w.id==78&&w.bounds!=null&&w.bounds.right==960,
            "Android dumpsys AOSP task format");
        ok(m!=null&&m.id==79&&m.bounds!=null&&m.bounds.left==960,
            "Android dumpsys alternative parser");
        ok(!new T1AdbTaskParser.Box(0,0,1920,720).near(
            new T1AdbTaskParser.Box(0,0,960,720),20),
            "Full-screen Waze must not pass 50-percent verification");
        System.out.println("PASS ADB parse exact per-display tasks and 50/50 bounds");
    }
}