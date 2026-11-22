package ar.com.rango5gps.drivelabauto.boot;

public final class NavWazeResumptionPolicyTest {
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static String old(){
        return "Stack id=21 displayId=1\n"+
            " taskId=100: com.jetour.oem/.Main\n"+
            "Stack id=52 displayId=6\n"+
            " taskId=222: com.google.android.apps.maps/.Maps\n"+
            "Stack id=53 displayId=7\n"+
            " taskId=223: com.spotify.music/.Main\n"+
            "Stack id=74 displayId=0\n"+
            " taskId=260: com.waze/com.waze.MainActivity\n";
    }
    private static NavWazeResumptionPolicy.Decision decide(String report,
            int stored,long when,long now,boolean approved){
        return NavWazeResumptionPolicy.inspect(report,8,6,7,
            stored,when,now,approved);
    }
    private static void kind(NavWazeResumptionPolicy.Decision d,
                             NavWazeResumptionPolicy.Kind k,String note){
        check(d.kind==k,note+" actual "+d.kind+" / "+d.reason);
    }
    public static void main(String[] args){
        long now=1700000000000L;
        NavWazeResumptionPolicy.Decision saved=
            decide(old(),260,now-1000L,now,false);
        kind(saved,NavWazeResumptionPolicy.Kind.RECLAIM,
            "stored same-Waze task may reattach after OTA");
        check(saved.candidate().stack==74&&
            saved.candidate().task==260,
            "reclaim targets exact old root and task");
        check(saved.same(decide(old(),260,now-1000,now,false)),
            "double independent ADB reads agree");
        kind(decide(old(),-1,0L,now,false),
            NavWazeResumptionPolicy.Kind.CONFIRM_LEGACY,
            "first upgrade from LAB51 requires user consent");
        kind(decide(old(),-1,0L,now,true),
            NavWazeResumptionPolicy.Kind.RECLAIM,
            "user can explicitly adopt one old physical Waze");
        kind(decide(old(),261,now-2000L,now,false),
            NavWazeResumptionPolicy.Kind.CONFIRM_LEGACY,
            "stale or mismatched ID cannot silently adopt");
        kind(decide(old(),260,now-15L*24*3600*1000,now,false),
            NavWazeResumptionPolicy.Kind.CONFIRM_LEGACY,
            "expired provenance cannot silently adopt");
        kind(decide(old().replace("displayId=0","displayId=1"),
            260,now-1000L,now,true),
            NavWazeResumptionPolicy.Kind.BLOCKED,
            "never touch OEM/T1Launcher display 1");
        kind(decide(old().replace("displayId=0","displayId=9"),
            260,now-1000L,now,true),
            NavWazeResumptionPolicy.Kind.BLOCKED,
            "never touch unrelated virtual display");
        kind(decide(old().replace(
            " taskId=260: com.waze/com.waze.MainActivity\n",
            " taskId=260: com.waze/com.waze.FreeMapAppActivity\n"),
            260,now-1000L,now,true),
            NavWazeResumptionPolicy.Kind.BLOCKED,
            "splash is not existing Waze MainActivity");
        kind(decide(old()+"Stack id=78 displayId=0\n"+
            " taskId=261: com.waze/com.waze.MainActivity\n",
            260,now-1000L,now,true),
            NavWazeResumptionPolicy.Kind.BLOCKED,
            "two Waze tasks are unsafe to rehome");
        kind(decide(old().replace(
            " taskId=260: com.waze/com.waze.MainActivity\n",
            " taskId=260: com.waze/com.waze.MainActivity\n"+
            " taskId=500: com.jetour.oem/.Other\n"),
            260,now-1000L,now,true),
            NavWazeResumptionPolicy.Kind.BLOCKED,
            "mixed Android root must never be moved");
        kind(decide(old().replace(
            "Stack id=74 displayId=0\n"+
            " taskId=260: com.waze/com.waze.MainActivity\n",""),
            260,now-1000L,now,false),
            NavWazeResumptionPolicy.Kind.EMPTY,
            "no orphan launches normal public Waze");
        kind(decide(old().replace("Stack id=74 displayId=0",
            "Stack id=74 displayId=8"),
            260,now-1000L,now,true),
            NavWazeResumptionPolicy.Kind.BLOCKED,
            "targeted fresh NAV display cannot already be occupied");
        kind(decide(null,260,now-1000L,now,true),
            NavWazeResumptionPolicy.Kind.BLOCKED,
            "no task inventory is fail closed");
        System.out.println("PASS NavWazeResumptionPolicyTest: saved task/stack identity, legacy consent, no OEM/root hijack");
    }
}
