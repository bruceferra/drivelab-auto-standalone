package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source contract: physical cluster incident must never be repeated. */
public final class BridgeClusterSafetySourceTest {
    static String file(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    static void yes(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        String dir="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String window=file(dir+"BridgeStandaloneWindows.java");
        String ui=file(dir+"BootActivity.java");
        String recovery=file(dir+"BridgeClusterRecovery.java");
        String parse=file(dir+"BridgeClusterRecoveryPolicy.java");
        String adb=file(dir+"BridgeLocalAdb.java");
        String listener=file(dir+"BootLinkService.java");
        yes(window.contains("static boolean routeOnTablero(Context c,String pkg)")&&
            window.contains("p.getBoolean(TAB_CONFIRMED,false)")&&
            window.contains("p.getBoolean(TAB_ROUTE,false)")&&
            window.contains("options.setLaunchDisplayId(1)")&&
            !window.contains("BridgeWindowMath.display1(")&&
            !window.contains("method.invoke(options,FREEFORM);\n        options.setLaunchDisplayId(1)"),
            "Display1 manual fullscreen only, with no bounded/unsafe Freeform bounds");
        yes(!ui.contains("BridgeClusterRecovery.disableNewCluster(this)")&&
            ui.contains("BridgeWheelAccessibilityService.ENABLED,false")&&
            ui.contains("LIBERAR CLÚSTER")&&
            ui.contains("ACTIVAR DISPLAY 1 · PANTALLA COMPLETA 100%")&&
            ui.contains("this::toggleWheel")&&
            ui.contains("isManuallyArmed()")&&
            !ui.contains("this::openWheelPermission"),
            "Manual only Display1; wheel opt-in requires per-process arm");
        yes(recovery.contains("BridgeWheelAccessibilityService.ENABLED,false")&&
            recovery.contains("BridgeStandaloneWindows.TAB_ROUTE,false")&&
            recovery.contains("BridgeStandaloneWindows.TAB_CONFIRMED,false")&&
            recovery.contains("BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(")&&
            recovery.contains("BridgeLocalAdb.shell(app,\"am stack list\")")&&
            recovery.contains("removeSelected(owner,tasks)")&&
            recovery.contains("RETIRAR SÓLO MAPAS")&&
            recovery.contains("BridgeClusterRecoveryPolicy.removeCommand(wanted)")&&
            recovery.contains("if(!same)throw new SecurityException(")&&
            !recovery.contains("force-stop")&&
            !recovery.contains("pm clear"),
            "Parked, twice validated, manual map-only cleanup must not touch OEM");
        yes(parse.contains("return \"am stack remove \"+task.stackId;")&&
            !parse.contains("return \"am stack remove \"+task.taskId;")&&
            parse.contains("display==1")&&
            parse.contains("tasks!=1||matched!=1")&&
            parse.contains("task.stackId<=10"),
            "Android 11 root STACK id only, never a task ID or mixed Jetour stack");
        yes(adb.contains("\"am stack list\".equals(command)")&&
            adb.contains("am stack remove [1-9][0-9]{1,6}")&&
            !adb.contains("am force-stop")&&
            !adb.contains("pm clear")&&
            !adb.contains("am display move-stack")&&
            listener.contains("BridgeDualRoutePolicy.plan(")&&
            listener.contains("options.setLaunchDisplayId(displayId)"),
            "Local ADB allowlist unchanged, Senders route only approved Waze/Maps apps");
        System.out.println("PASS manual fullscreen Display1, no Freeform, wheel OFF, recovery never OEM");
    }
}
