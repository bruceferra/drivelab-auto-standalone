package ar.com.rango5gps.drivelabauto.boot;

public final class NavWazeRoutePolicyTest {
    private static void yes(boolean check,String desc){
        if(!check)throw new AssertionError(desc);
    }
    private static void denied(Runnable f,String desc){
        try{f.run();throw new AssertionError("Unsafe accepted: "+desc);}
        catch(SecurityException expected){}
    }
    private static String pre(){
        return "Stack id=22 displayId=1\n taskId=111: com.jetour.oem/.Main\n"+
            "Stack id=52 displayId=6\n taskId=222: com.google.android.apps.maps/.Maps\n"+
            "Stack id=53 displayId=7\n taskId=223: com.spotify.music/.Main\n";
    }
    private static String main(){
        return "Stack id=60 displayId=8\n taskId=240: com.waze/com.waze.FreeMapAppActivity\n"+
            "Stack id=74 displayId=8\n taskId=260: com.waze/com.waze.MainActivity\n";
    }
    public static void main(String[] args){
        NavWazeRoutePolicy.Target wanted=new NavWazeRoutePolicy.Target(8,6,7,74,260,123L);
        String report=pre()+main();
        NavWazeRelocationPolicy.Task a=NavWazeRoutePolicy.verify(report,wanted);
        NavWazeRelocationPolicy.Task b=NavWazeRoutePolicy.verify(report,wanted);
        yes(a.id==260&&a.stack==74&&a.display==8,"verified Waze task");
        yes(NavWazeRoutePolicy.same(a,b),"double verified identity");
        String c=NavWazeRoutePolicy.command(wanted,
            "waze://?ll=-34.55,-58.75&navigate=yes");
        yes(c.equals("am start --display 8 --task 260 --activity-single-top "+
            "-a android.intent.action.VIEW -d 'waze://?ll=-34.55,-58.75&navigate=yes' -p com.waze"),
            "exact shell command only targets existing Waze task");
        yes(!c.contains(" -n com.waze/com.waze.MainActivity"),
            "never launch private MainActivity directly");
        yes(!NavWazeRoutePolicy.delivered("Starting: Intent {}"),
            "new Activity is NOT confirmed same task");
        yes(NavWazeRoutePolicy.delivered(
            "Warning: Activity not started, intent has been delivered to currently running top-most instance."),
            "only Android existing-activity ACK");
        denied(()->NavWazeRoutePolicy.verify(pre()+"Stack id=74 displayId=0\n"+
            " taskId=260: com.waze/com.waze.MainActivity\n",wanted),
            "Waze still on physical display");
        denied(()->NavWazeRoutePolicy.verify(pre()+main()+
            "Stack id=81 displayId=0\n taskId=333: com.waze/.MainActivity\n",wanted),
            "additional physical Waze");
        denied(()->NavWazeRoutePolicy.verify(pre()+
            "Stack id=74 displayId=8\n taskId=260: com.waze/com.waze.MainActivity\n"+
            " taskId=333: com.jetour.oem/.Main\n",wanted),
            "mixed OEM/Waze task root");
        denied(()->NavWazeRoutePolicy.verify(pre()+main(),
            new NavWazeRoutePolicy.Target(8,6,7,74,261,123L)),
            "changed task identifier");
        denied(()->NavWazeRoutePolicy.command(
            new NavWazeRoutePolicy.Target(0,6,7,74,260,123L),
            "waze://?q=Buenos%20Aires&navigate=yes"),"physical display");
        denied(()->NavWazeRoutePolicy.command(wanted,
            "waze://?q=foo';am%20force-stop%20com.waze;&navigate=yes"),
            "injection");
        yes(NavWazeRoutePolicy.command(wanted,
            "waze://?q=Av.%20Mitre%20100&navigate=yes").contains(" --task 260 "),
            "search route valid");
        System.out.println("PASS NavWazeRoutePolicyTest");
    }
}
