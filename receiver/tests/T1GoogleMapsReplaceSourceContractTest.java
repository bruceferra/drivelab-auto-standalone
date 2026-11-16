package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
/** Regression: no blind navigation stop, only Maps intent-scoped dismissal. */
public final class T1GoogleMapsReplaceSourceContractTest {
    private static String read(String n)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
    }
    private static void check(boolean b,String why){
        if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String u=read("T1GoogleMapsReplacement.java");
        String wheel=read("T1WazeWheelService.java");
        String routes=read("T1NavigationTargets.java");
        check(u.contains("TTL=12000L")&&u.contains("stopQuestion(")&&
            u.contains("stopAction(")&&u.contains("T1WheelTarget.mapsConfigured(c)")&&
            u.contains("startPending(Context c)")&&
            u.contains("startAction(String value)")&&
            u.contains("consumeStart(Context c)")&&
            !u.contains("n.equals(\"ok\")||n.equals(\"yes\")")&&
            !u.contains("performGlobalAction("),
            "Only short-lived request, trusted split window and explicit stop prompt");
        check(wheel.contains("private void maybeConfirmMapsReplacement()")&&
            wheel.contains("prompt.question")&&
            wheel.contains("prompt.differentButtons!=1")&&
            wheel.contains("T1GoogleMapsReplacement.consume(this)")&&
            wheel.contains("private void maybeStartMapsNavigation()")&&
            wheel.contains("T1GoogleMapsReplacement.consumeStart(this)")&&
            wheel.contains("start.unique!=1")&&
            wheel.contains("!T1WheelTarget.mapsConfigured(this)")&&
            wheel.contains("prompt.button.performAction(")&&
            !wheel.contains("performGlobalAction(GLOBAL_ACTION_BACK)"),
            "Only unambiguous Maps STOP action, never system BACK or blind tap");
        check(routes.contains("T1GoogleMapsReplacement.arm(c)")&&
            routes.contains("T1GoogleMapsReplacement.cancel(c)")&&
            routes.contains("BridgeRouteTarget.MAPS.equals(target)")&&
            routes.contains("BridgeRouteTarget.BOTH.equals(target)"),
            "Only explicitly supplied navigation destinations can arm replacement");
        System.out.println("PASS Maps explicit route replacement safeguarded");
    }
}
