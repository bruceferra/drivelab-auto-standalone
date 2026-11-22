package ar.com.rango5gps.drivelabauto.boot;

public final class NavExistingT1MapaPolicyTest {
    private static void yes(boolean b){if(!b)throw new AssertionError();}
    static String inventory(String t1){
        return "Stack id=99 displayId=1\n taskId=90: com.jetour.oem/.Main\n"+
           "Stack id=52 displayId=6\n taskId=222: com.google.android.apps.maps/.Maps\n"+
           "Stack id=53 displayId=7\n taskId=223: com.spotify.music/.Main\n"+
           "Stack id=58 displayId=8\n"+t1;
    }
    public static void main(String[] args){
        String before=inventory(" taskId=240: com.google.android.apps.maps/.Maps\n");
        NavExistingT1MapaPolicy.State a=NavExistingT1MapaPolicy.parse(before,8,6,7);
        yes(a.valid&&a.wazeTask==-1&&a.mapTask==240);
        yes(a.same(NavExistingT1MapaPolicy.parse(before,8,6,7)));
        String after=inventory(" taskId=241: com.waze/.Main\n");
        NavExistingT1MapaPolicy.State b=NavExistingT1MapaPolicy.parse(after,8,6,7);
        yes(b.valid&&b.wazeTask==241&&!a.same(b));
        yes(NavExistingT1MapaPolicy.parse(inventory(""),8,6,7).valid);
        yes(!NavExistingT1MapaPolicy.parse(
          before+"Stack id=89 displayId=0\n taskId=249: com.waze/.Main\n",
          8,6,7).valid);
        yes(!NavExistingT1MapaPolicy.parse(
          inventory(" taskId=240: com.google.android.apps.maps/.Maps\n"+
                    " taskId=241: com.waze/.Main\n"),8,6,7).valid);
        yes(!NavExistingT1MapaPolicy.parse(
          inventory(" taskId=240: com.jetour.oem/.Main\n"),8,6,7).valid);
        yes(!NavExistingT1MapaPolicy.parse(before,1,6,7).valid);
        yes(!NavExistingT1MapaPolicy.parse(before,6,6,7).valid);
        yes(("am start --display 8 -a android.intent.action.MAIN "+
            "-c android.intent.category.LAUNCHER --activity-single-top "+
            "-n com.waze/.Main").equals(
                NavExistingT1MapaPolicy.startCommand(8,"com.waze/.Main",6,7)));
        yes(NavExistingT1MapaPolicy.accepted("Starting: Intent { ... }"));
        yes(!NavExistingT1MapaPolicy.accepted("Error: Activity not found"));
        try {
            NavExistingT1MapaPolicy.startCommand(1,"com.waze/.Main",6,7);
            throw new AssertionError();
        }catch(SecurityException expected){}
        System.out.println("PASS NavExistingT1MapaPolicyTest");
    }
}
