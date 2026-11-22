package ar.com.rango5gps.drivelabauto.boot;

public final class NavWazeRelocationPolicyTest {
    static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static String baseline(){
        return "Stack id=22 displayId=1\n taskId=111: com.jetour.oem/.Main\n"+
               "Stack id=52 displayId=6\n taskId=222: com.google.android.apps.maps/.Maps\n"+
               "Stack id=53 displayId=7\n taskId=223: com.spotify.music/.Main\n"+
               "Stack id=60 displayId=8\n";
    }
    static String splash(){
        return "Stack id=60 displayId=8\n"+
               " taskId=240: com.waze/com.waze.FreeMapAppActivity\n";
    }
    static String physical(){
        return "Stack id=74 displayId=0\n"+
               " taskId=260: com.waze/com.waze.MainActivity\n";
    }
    static void denied(String report,String why){
        try{
            NavWazeRelocationPolicy.physicalMain(report,8,6,7);
            throw new AssertionError("Unsafe allowed: "+why);
        }catch(SecurityException expected){}
    }
    public static void main(String[]args){
        yes(NavWazeRelocationPolicy.noWazeBefore(baseline(),8,6,7),
            "initial task inventory");
        yes(!NavWazeRelocationPolicy.noWazeBefore(
                baseline()+physical(),8,6,7),
            "preexisting Waze must refuse");
        yes(!NavWazeRelocationPolicy.noWazeBefore(
                baseline()+splash(),8,6,7),
            "own splash not baseline");
        String start=baseline()+splash()+physical();
        NavWazeRelocationPolicy.Candidate c=
            NavWazeRelocationPolicy.physicalMain(start,8,6,7);
        yes(c!=null&&c.stack==74&&c.task==260,"main identity");
        yes(c.same(NavWazeRelocationPolicy.physicalMain(start,8,6,7)),
            "double snapshot equality");
        yes("am display move-stack 74 8".equals(
            NavWazeRelocationPolicy.moveCommand(c,8,6,7)),
            "exact command to NAV display");
        yes(!NavWazeRelocationPolicy.relocated(start,c,8,6,7),
            "do not report relocated before actual move");
        String after=baseline()+splash()+
            "Stack id=74 displayId=8\n"+
            " taskId=260: com.waze/com.waze.MainActivity\n";
        yes(NavWazeRelocationPolicy.relocated(after,c,8,6,7),
            "same Waze task and stack on NAV");
        denied(baseline()+splash()+
            "Stack id=74 displayId=0\n"+
            " taskId=260: com.waze/com.waze.MainActivity\n"+
            " taskId=261: com.jetour.oem/.Main\n",
            "mixed physical root");
        denied(baseline()+splash()+physical()+physical().replace(
               "id=74","id=75").replace("id=260","id=261"),
            "ambiguous two main Waze tasks");
        denied(baseline()+splash()+
            "Stack id=74 displayId=9\n"+
            " taskId=260: com.waze/com.waze.MainActivity\n",
            "other virtual display");
        denied(baseline()+splash()+
            "Stack id=74 displayId=0\n"+
            " taskId=260: com.waze/com.waze.FreeMapAppActivity\n",
            "not Waze MainActivity");
        denied(baseline()+
            "Stack id=60 displayId=8\n"+
            " taskId=240: com.jetour.oem/.Main\n"+physical(),
            "foreign task on owned display");
        denied(baseline()+splash()+
            "Stack id=74 displayId=0\n"+
            " taskId=240: com.waze/com.waze.MainActivity\n",
            "duplicated task ID");
        yes(!NavWazeRelocationPolicy.relocated(
            after+"Stack id=76 displayId=0\n"+
            " taskId=333: com.waze/.MainActivity\n",c,8,6,7),
            "foreign Waze invalidates postmove");
        try{
            NavWazeRelocationPolicy.moveCommand(c,1,6,7);
            throw new AssertionError("physical display accepted");
        }catch(SecurityException expected){}
        System.out.println("PASS NavWazeRelocationPolicyTest");
    }
}
