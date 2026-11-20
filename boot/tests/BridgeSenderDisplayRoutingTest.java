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
        check(listener.contains("BridgeStandaloneWindows.routeOnTablero(this,pkg)")&&
            !listener.contains("getBoolean(\"cluster_enabled\"")&&
            !listener.contains("getInt(\"cluster_display_id\"")&&
            listener.contains("setLaunchDisplayId(1)")&&
            listener.contains("BridgeRouteTarget.baseKind("),
            "Sender routes must be Bridge-owned, not old Receiver flags");
        check(prefs.contains("TAB_CONFIRMED")&&
            prefs.contains("TAB_ROUTE")&&
            prefs.contains("TAB_CONFIRMED,false")&&
            prefs.contains("TAB_ROUTE,false")&&
            prefs.contains("pkg.equals(tabMap(c))"),
            "Display1 route must require owner visual confirmation and exact app");
        check(ui.contains("confirmTableroMap()")&&
            ui.contains("toggleSenderTablero()")&&
            ui.contains("CONFIRMAR: MAPA SE VE")&&
            ui.contains("SENDER → TABLERO: DESACTIVADO")&&
            ui.contains("TAB_CONFIRMED,true")&&
            ui.contains("TAB_ROUTE,true"),
            "Bridge UI must expose explicit two-stage route approval");
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
