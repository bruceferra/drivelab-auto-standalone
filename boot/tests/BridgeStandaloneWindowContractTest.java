package ar.com.rango5gps.drivelabauto.boot;
import java.nio.file.Files;
import java.nio.file.Path;
public final class BridgeStandaloneWindowContractTest {
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",name));
    }
    static void yes(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        String win=read("BridgeStandaloneWindows.java");
        String ui=read("BootActivity.java");
        String boot=read("BootAutoService.java");
        String route=read("BootLinkService.java");
        yes(win.contains("Method method=freeformMethod()")&&
            win.contains("setLaunchWindowingMode")&&
            win.contains("setLaunchBounds(")&&
            win.contains("setLaunchDisplayId(displayId)")&&
            win.contains("BridgeWindowMath.center(")&&
            win.contains("return false;"),
            "Freeform bounds and proper display must be owned by Bridge");
        yes(win.contains("getLaunchIntentForPackage(pkg)")&&
            win.contains("BridgeWindowsPolicy.allowed(pkg)")&&
            win.contains("validCenter(first,second,ratio)")&&
            win.contains("options.setLaunchDisplayId(1)")&&
            win.contains("100%, sin Freeform")&&
            !win.contains("BootConfig.send")&&
            !win.contains("assertReceiver")&&
            !win.contains("pm clear")&&
            !win.contains("am force-stop")&&
            !win.contains("FLAG_ACTIVITY_CLEAR_TASK")&&
            !win.contains("FLAG_ACTIVITY_MULTIPLE_TASK")&&
            !win.contains("FLAG_ACTIVITY_CLEAR_TOP"),
            "Independent launch must preserve Maps/Spotify routes and protect OEM");
        yes(ui.contains("PROBAR DOS VENTANAS · SÓLO BRIDGE")&&
            ui.contains("LIBERAR CLÚSTER")&&
            ui.contains("ACTIVAR DISPLAY 1 · PANTALLA COMPLETA 100%")&&
            !ui.contains("button(\"PROBAR MAPA TABLERO")&&
            ui.contains("BridgeStandaloneWindows.center(this)")&&
            ui.contains("BridgeStandaloneWindows.tablero(this)")&&
            ui.contains("BridgeStandaloneWindows.prefs(this)")&&
            ui.contains("chooseBridgeApp(")&&
            ui.contains("elegir Spotify y ProManager")&&
            ui.contains("No se abrirá DriveLab Auto")&&
            !boot.contains("BridgeStandaloneWindows.center(")&&
            !boot.contains("BridgeStandaloneWindows.tablero(")&&
            !route.contains("BridgeStandaloneWindows.center("),
            "Two screens manually owned by Bridge, no unexpected ignition launch");
        System.out.println("PASS standalone Bridge windows: safety, separate app choices, manual parked-only requests");
    }
}
