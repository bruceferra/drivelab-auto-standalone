package ar.com.rango5gps.drivelabauto.boot;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bridge must preserve RECEIVER's proved automatic Maps route workflow. */
public final class BridgeMapsReceiverAutoRouteSourceTest {
    private static String read(String p)throws Exception{
        return Files.readString(Path.of(p));
    }
    private static void yes(boolean pass,String problem){
        if(!pass)throw new AssertionError(problem);
    }
    private static String range(String s,String from,String to){
        int i=s.indexOf(from),j=s.indexOf(to,i+from.length());
        if(i<0||j<=i)throw new AssertionError("Missing source policy "+from);
        return s.substring(i,j);
    }
    public static void main(String[] args)throws Exception{
        String base="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String boot="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String original=read(base+"T1GoogleMapsReplacement.java");
        String copy=read(boot+"BridgeGoogleMapsReplacement.java");
        String service=read(boot+"BridgeWheelAccessibilityService.java");
        String link=read(boot+"BootLinkService.java");
        String ui=read(boot+"BootActivity.java");
        String originalWheel=read(base+"T1WazeWheelService.java");
        yes(range(original,"    static boolean startAction(",
            "    static void cancel(").equals(
            range(copy,"    static boolean startAction(","    static void cancel(")),
            "Start action recognition must match DriveLab Auto Receiver");
        yes(range(original,"    private static String norm(",
            "\n}").equals(range(copy,"    private static String norm(","\n}")),
            "Stop/exit dialog recognition must match DriveLab Auto Receiver");
        yes(copy.contains("TTL=12000L")&&
            copy.contains("static void arm(Context c,int displayId)")&&
            copy.contains("START_PENDING")&&
            copy.contains("TARGET_DISPLAY")&&
            copy.contains("consumeStart(Context c)")&&
            copy.contains("consume(Context c)"),
            "Same 12-second one-shot intent-scoped route lifecycle");
        yes(service.contains("maybeConfirmMapsReplacement()")&&
            service.contains("maybeStartMapsNavigation()")&&
            service.contains("if(stop.question)return;")&&
            service.contains("start.unique!=1")&&
            service.contains("BridgeGoogleMapsReplacement.consumeStart(this)")&&
            service.contains("BridgeGoogleMapsReplacement.displayId(this)")&&
            service.contains("if(routePending)main.postDelayed(refresh,650L)")&&
            originalWheel.contains("T1GoogleMapsReplacement.consumeStart(this)"),
            "Automatic route preview Start click and replacement copied");
        yes(link.contains("google.navigation:q=")&&link.contains("&mode=d")&&
            link.contains("BridgeGoogleMapsReplacement.arm(this,displayId)")&&
            link.contains("BridgeGoogleMapsReplacement.cancel(this)")&&
            link.contains("BridgeWheelAccessibilityService.navigationIntentArrived(this)")&&
            !link.contains("Intent.FLAG_ACTIVITY_CLEAR_TASK"),
            "Only a new authenticated Sender map request arms Start");
        yes(ui.contains("BridgeGoogleMapsReplacement.status(this)")&&
            !ui.contains("INICIAR RUTA DEL RESUMEN")&&
            !ui.contains("askStartExistingGoogleMapsRoute")&&
            !service.contains("dispatchGesture(")&&
            !service.contains("GestureDescription")&&
            !service.contains("ZOOM_IN")&&
            !service.contains("startMapsRouteFromSummary"),
            "No manual Start, zoom, gestures or blind navigation");
        System.out.println("PASS copy of Receiver 12s automatic Maps navigation, no zoom");
    }
}
