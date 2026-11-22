package ar.com.rango5gps.drivelabauto.boot;
public final class NavWazeDisplayTraceTest {
    private static void yes(boolean c){if(!c)throw new AssertionError();}
    public static void main(String[]args){
        String base="Stack id=52 displayId=6\n"+
            " taskId=222: com.google.android.apps.maps/.Maps\n"+
            "Stack id=53 displayId=7\n"+
            " taskId=223: com.spotify.music/.Main\n";
        String before=NavWazeDisplayTrace.summarize(base,8,6,7);
        yes(before.contains("MAPS @CENTRO-MAPS[d6 s52 t222]"));
        yes(before.contains("SPOTIFY @CENTRO-SPOTIFY[d7 s53 t223]"));
        yes(!before.contains("WAZE"));
        String splash=base+"Stack id=60 displayId=8\n"+
            " taskId=240: com.waze/.Splash\n";
        yes(NavWazeDisplayTrace.summarize(splash,8,6,7)
           .contains("WAZE(.Splash) @CLUSTER-NAV[d8 s60 t240]"));
        String main=base+"Stack id=73 displayId=0\n"+
            " taskId=241: com.waze/.Main\n";
        String moved=NavWazeDisplayTrace.summarize(main,8,6,7);
        yes(moved.contains("WAZE(.Main) @CENTRAL-FISICA[d0 s73 t241]"));
        yes(moved.contains("MAPS @CENTRO-MAPS"));
        yes(moved.contains("SPOTIFY @CENTRO-SPOTIFY"));
        yes(!moved.contains("com.jetour"));
        yes("ADB sin inventario válido".equals(
           NavWazeDisplayTrace.summarize(null,8,6,7)));
        yes(NavWazeDisplayTrace.launchReply(
            "Starting: Intent { act=android.intent.action.MAIN cmp=com.waze/.Main }")
            .startsWith("ADB: Starting: Intent"));
        yes(NavWazeDisplayTrace.launchReply(
            "Error: Unknown option: --activity-multiple-task")
            .contains("Unknown option"));
        yes(NavWazeDisplayTrace.launchReply(
            "Warning: Cannot open https://example.com/private")
            .contains("[URI]"));
        yes(!NavWazeDisplayTrace.launchReply(
            "Warning: Cannot open https://example.com/private")
            .contains("example.com"));
        yes(NavWazeDisplayTrace.launchReply("")
            .contains("sin respuesta"));
        System.out.println("PASS NavWazeDisplayTraceTest");
    }
}
