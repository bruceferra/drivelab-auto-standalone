package ar.com.rango5gps.drivelabnav.core;

public final class NavMosaicTaskPolicyTest {
    private static final String URI="google.navigation:q=-34.23%2C-58.41&mode=d";
    private static final String MAPS="com.google.android.apps.maps/";
    private static void yes(boolean flag,String why){
        if(!flag)throw new AssertionError(why);
    }
    private static void fails(Runnable run,String why){
        try{run.run();throw new AssertionError("Allowed "+why);}
        catch(SecurityException expected){}
    }
    private static String report(int ownDisplay){
        return "Stack id=22 bounds=[0,0][1280,720] displayId="+ownDisplay+"\n"+
            "  taskId=48: "+MAPS+"com.google.android.maps.MapsActivity\n"+
            "Stack id=33 bounds=[0,0][960,540] displayId=8\n"+
            "  taskId=50: "+MAPS+"com.google.android.maps.MapsActivity\n"+
            "Stack id=2 displayId=0\n  taskId=3: com.spotify.music/.Main\n";
    }
    public static void main(String[] args){
        String stack=report(7);
        NavMosaicTaskPolicy.Target found=NavMosaicTaskPolicy.find(stack,7);
        yes(found!=null&&found.taskId==48&&found.displayId==7,
            "must select only NAV-owned virtual display");
        yes(NavMosaicTaskPolicy.find(stack,8)!=null,
            "T1Launcher possible second task must not cause NAV misidentification");
        yes(NavMosaicTaskPolicy.find(stack,1)==null,
            "physical Display 1 never target");
        yes(NavMosaicTaskPolicy.find(stack,0)==null,
            "central Display 0 never target");
        yes(NavMosaicTaskPolicy.find(stack,9)==null,
            "missing own map must not open another");
        String cmd=NavMosaicTaskPolicy.command(found,URI,7);
        yes(cmd.contains(" --display 7 --task 48 "), "explicit display and task");
        fails(()->NavMosaicTaskPolicy.command(found,URI,8),
            "T1Launcher task must not be used when NAV owns display 7");
        fails(()->NavMosaicTaskPolicy.command(found,"google.navigation:q=x';rm&mode=d",7),
            "shell injection");
        fails(()->NavMosaicTaskPolicy.command(found,"waze://?q=x",7),
            "unexpected URI scheme");
        yes(!NavMosaicTaskPolicy.delivered("Starting: Intent { }"),
            "startActivity request is NOT delivery to old task");
        yes(NavMosaicTaskPolicy.delivered(
            "Warning: Activity not started, intent has been delivered to currently running top-most instance."),
            "onNewIntent explicit delivery");
        yes(NavMosaicTaskPolicy.find(
            "Stack id=22 displayId=7\n"+
            "  taskId=48: "+MAPS+"com.google.android.maps.MapsActivity\n"+
            "  taskId=49: "+MAPS+"com.google.android.maps.Other\n"+
            "Stack id=33 displayId=8\n"+
            "  taskId=50: "+MAPS+"com.google.android.maps.MapsActivity\n",7)==null,
            "ambiguous NAV task");
        yes(NavMosaicTaskPolicy.find(
            "Stack id=22 displayId=7\n"+
            "  taskId=48: "+MAPS+"com.google.android.maps.MapsActivity\n"+
            "  taskId=51: com.spotify.music/.Main\n",7)==null,
            "mixed NAV stack");
        NavMosaicTaskPolicy.Target changed=NavMosaicTaskPolicy.find(
            report(9),9);
        yes(!NavMosaicTaskPolicy.same(found,changed),
            "task moved to another display");
        yes(NavMosaicTaskPolicy.same(found,
            NavMosaicTaskPolicy.find(stack,7)),
            "stable two-snapshot identity");
        System.out.println("PASS: NAV map routing targets only one existing Maps task on the owned virtual display");
    }
}
