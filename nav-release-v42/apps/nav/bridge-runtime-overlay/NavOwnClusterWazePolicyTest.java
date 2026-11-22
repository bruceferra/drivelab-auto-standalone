package ar.com.rango5gps.drivelabauto.boot;
public final class NavOwnClusterWazePolicyTest {
    static void yes(boolean ok){if(!ok)throw new AssertionError();}
    static String read(String in,String external){
        return "Stack id=22 displayId=1\n taskId=111: com.jetour.oem/.Main\n"+
            "Stack id=52 displayId=6\n taskId=222: com.google.android.apps.maps/.Maps\n"+
            "Stack id=53 displayId=7\n taskId=223: com.spotify.music/.Main\n"+
            "Stack id=60 displayId=8\n"+in+external;
    }
    public static void main(String[] args){
        NavOwnClusterWazePolicy.State empty=
            NavOwnClusterWazePolicy.inspect(read("",""),8,6,7);
        yes(empty.valid&&empty.task==-1&&empty.stack==-1);
        yes(empty.same(NavOwnClusterWazePolicy.inspect(read("",""),8,6,7)));
        String one=read(" taskId=240: com.waze/.Main\n","");
        NavOwnClusterWazePolicy.State post=
            NavOwnClusterWazePolicy.inspect(one,8,6,7);
        yes(post.valid&&post.task==240&&post.stack==60&&!empty.same(post));
        yes("am stack remove 60".equals(
            NavOwnClusterWazePolicy.stopCommand(post,8,6,7)));
        yes(!NavOwnClusterWazePolicy.inspect(read(
            " taskId=241: com.jetour.oem/.Main\n",""),8,6,7).valid);
        yes(!NavOwnClusterWazePolicy.inspect(read(
            " taskId=240: com.waze/.Main\n taskId=241: com.waze/.Other\n",""),
            8,6,7).valid);
        yes(!NavOwnClusterWazePolicy.inspect(read("",
            "Stack id=74 displayId=0\n taskId=260: com.waze/.Main\n"),8,6,7).valid);
        yes(!NavOwnClusterWazePolicy.inspect(read("",
            "Stack id=74 displayId=9\n taskId=260: com.waze/.Main\n"),8,6,7).valid);
        yes(!NavOwnClusterWazePolicy.inspect(one,1,6,7).valid);
        yes(!NavOwnClusterWazePolicy.inspect(one,6,6,7).valid);
        yes(!NavOwnClusterWazePolicy.inspect(one,8,6,8).valid);
        yes("am start --display 8 -n com.waze/com.waze.MainActivity".equals(
            NavOwnClusterWazePolicy.startCommand(8,6,7,
                "com.waze/com.waze.MainActivity")));
        try{
            NavOwnClusterWazePolicy.startCommand(8,6,7,
                "com.waze/com.waze.FreeMapAppActivity");
            throw new AssertionError("accepted the splash launcher");
        }catch(SecurityException expected){}
        yes(NavOwnClusterWazePolicy.accepted("Starting: Intent { }"));
        yes(!NavOwnClusterWazePolicy.accepted("Error: denied"));
        try{
            NavOwnClusterWazePolicy.stopCommand(post,8,5,8);
            throw new AssertionError("accepted wrong display");
        }catch(SecurityException expected){}
        try{
            NavOwnClusterWazePolicy.startCommand(8,6,7,"com.jetour.oem/.Main");
            throw new AssertionError("accepted wrong component");
        }catch(SecurityException expected){}
        System.out.println("PASS NavOwnClusterWazePolicyTest");
    }
}
