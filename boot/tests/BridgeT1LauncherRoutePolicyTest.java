package ar.com.rango5gps.drivelabauto.boot;

public final class BridgeT1LauncherRoutePolicyTest {
    static void ok(boolean b,String reason){
        if(!b)throw new AssertionError(reason);
    }
    private static final String MAP=
        "com.google.android.apps.maps/com.google.android.maps.MapsActivity";
    static String root(int stack,int display,int task,String name){
        return "Stack id="+stack+" bounds=null displayId="+display+
            " userId=0\n"+
            "  taskId="+task+": "+name+" bounds=null\n";
    }
    public static void main(String[] args){
        String own=root(100,0,700,MAP)+
            root(131,1,770,MAP)+
            root(189,13,725,MAP);
        BridgeT1LauncherRoutePolicy.Target found=
            BridgeT1LauncherRoutePolicy.find(own);
        ok(found!=null&&found.displayId==13&&
            found.taskId==725&&found.stackId==189,
            "Target must be the existing Maps task in virtual display 13");
        String cmd=BridgeT1LauncherRoutePolicy.command(
            found,"google.navigation:q=-34.603,-58.381&mode=d");
        ok(cmd.contains("--task 725")&&
           cmd.contains("--display 13")&&
           cmd.contains("--activity-single-top")&&
           cmd.contains("-n "+MAP)&&
           !cmd.contains("move-stack")&&
           !cmd.contains("force-stop")&&
           !cmd.contains("am task resize"),
           "Only an existing explicit Maps virtual task can receive route: "+cmd+
           " | --task "+cmd.contains("--task 725")+
           " --display "+cmd.contains("--display 13")+
           " --single-top "+cmd.contains("--activity-single-top")+
           " --component "+cmd.contains("-n "+MAP));
        ok(BridgeT1LauncherRoutePolicy.find(
            root(120,0,725,MAP)+root(130,1,726,MAP))==null,
            "No destination on physical display 0 or 1");
        ok(BridgeT1LauncherRoutePolicy.find(own+
            root(192,14,820,MAP))==null,
            "Two virtual Maps instances must fail closed");
        ok(BridgeT1LauncherRoutePolicy.find(
            own+"  taskId=812: com.jetour.hmi/.OEMActivity\n")==null,
            "Mixed OEM task in map stack must never be selected");
        BridgeT1LauncherRoutePolicy.Target second=
            BridgeT1LauncherRoutePolicy.find(own);
        ok(BridgeT1LauncherRoutePolicy.same(found,second),
            "Double-read same task before dispatch");
        ok(!BridgeT1LauncherRoutePolicy.same(found,
            BridgeT1LauncherRoutePolicy.find(
                root(189,13,730,MAP))),
            "Task ID changes must reject");
        boolean unsafe=false;
        try{
            BridgeT1LauncherRoutePolicy.command(found,
                "google.navigation:q=test' ; pm clear com.waze&mode=d");
        }catch(SecurityException expected){unsafe=true;}
        ok(unsafe,"Invalid shell escapes rejected");
        unsafe=false;
        try{
            BridgeT1LauncherRoutePolicy.command(
                new BridgeT1LauncherRoutePolicy.Target(
                    1,189,725,MAP),
                "google.navigation:q=-34.60,-58.38&mode=d");
        }catch(SecurityException expected){unsafe=true;}
        ok(unsafe,"Physical cluster MUST be rejected");
        ok(BridgeT1LauncherRoutePolicy.deliveredToExistingTop(
            "Starting: Intent {...}\nWarning: Activity not started, "+
            "intent has been delivered to currently running top-most instance."),
            "Android explicitly reports reuse");
        ok(!BridgeT1LauncherRoutePolicy.deliveredToExistingTop(
            "Starting: Intent {...}"),
            "Accepted start is not proof of existing-instance reuse");
        System.out.println("PASS T1Launcher existing virtual Maps task, "+
            "verified onNewIntent, no extra physical/freeform map");
    }
}
