package ar.com.rango5gps.drivelabauto.boot;
import java.nio.file.Files;
import java.nio.file.Path;
public final class BridgeSenderDisplayRoutingTest {
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",name));
    }
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        String listener=read("BootLinkService.java");
        String prefs=read("BridgeStandaloneWindows.java");
        String ui=read("BootActivity.java");
        String ignition=read("BootReceiver.java");
        check(listener.contains("BridgeDualRoutePolicy.plan(")&&
            listener.contains("options.setLaunchDisplayId(displayId)")&&
            listener.contains("startActivity(map,options.toBundle())")&&
            listener.contains("BridgeRouteTarget.baseKind(")&&
            !listener.contains("getBoolean(\"cluster_enabled\"")&&
            !listener.contains("getInt(\"cluster_display_id\""),
            "Sender Waze/Maps/BOTH must target each assigned display independently");
        check(prefs.contains("TAB_CONFIRMED")&&
            prefs.contains("TAB_ROUTE")&&
            prefs.contains("static boolean routeOnTablero(Context c,String pkg)")&&
            prefs.contains("static void disableTablero(Context c)")&&
            prefs.contains("options.setLaunchDisplayId(1)")&&
            !prefs.contains("BridgeWindowMath.display1("),
            "Owner controls full-screen Display1 with no legacy Freeform geometry");
        check(ui.contains("LIBERAR CLÚSTER")&&
            ui.contains("ACTIVAR DISPLAY 1 · PANTALLA COMPLETA 100%")&&
            ui.contains("DESACTIVAR DISPLAY 1 · ENVIAR A CENTRAL")&&
            ui.contains("this::toggleDisplay1")&&
            !ui.contains("BridgeClusterRecovery.disableNewCluster(this)")&&
            !ui.contains("button(\"PROBAR MAPA TABLERO"),
            "Explicit parked activation, persisted until owner disables or ignition resets");
        check(ignition.contains("BridgeStandaloneWindows.TAB_CONFIRMED,false")&&
            ignition.contains("BridgeStandaloneWindows.TAB_ROUTE,false")&&
            ignition.contains("if(!BootConfig.enabled(context))return;"),
            "No stale display confirmation across ignition; no forced app opening");
        check(!listener.contains("pm clear")&&
            !listener.contains("am force-stop")&&
            !listener.contains("getSharedPreferences(\"drivelab_receiver"),
            "No map data wipe or Receiver preference dependency");
        System.out.println("PASS independent Sender routing with visual Display1 confirmation and ignition reset");
    }
}
