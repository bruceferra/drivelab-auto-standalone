package ar.com.rango5gps.drivelabauto.boot;
import java.nio.file.Files;
import java.nio.file.Path;

/** Strict no-zoom, no-OEM-mutation receiver parity and manual-key tests. */
public final class BridgeWheelReceiverParitySourceTest {
    private static final String D="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
    private static String get(String f)throws Exception{
        return Files.readString(Path.of(D+f));
    }
    private static void yes(boolean v,String what){
        if(!v)throw new AssertionError(what);
    }
    public static void main(String[] args)throws Exception{
        String service=get("BridgeWheelAccessibilityService.java");
        String binder=get("BridgeWheelOemBinder.java");
        String ui=get("BootActivity.java");
        String access=get("BridgeWheelAccessRecovery.java");
        String adb=get("BridgeLocalAdb.java");
        String navigation=get("BridgeMapsStartPolicy.java");
        String link=get("BootLinkService.java");
        yes(service.contains("beginKeyCapture(Context c)")&&
            service.contains("CAPTURE_DURATION_MS=20000L")&&
            service.contains("if(isCaptureActive())")&&
            service.contains("return; // No selection/click/back/gesture during recapture.")&&
            service.contains("CAPTURE_COUNT")&&
            service.contains("onOemEvent(int code,int action)")&&
            service.contains("MapWindow visible=optIn?visibleMap():null;")&&
            service.contains("if(!oem.active())oem.start();")&&
            service.contains("main.postDelayed(refresh,700L)")&&
            service.contains("window.getDisplayId()!=selectedDisplay")&&
            service.contains("WINDOW_DISPLAY")&&
            service.contains("getWindowsOnAllDisplays()"),
            "Must capture key codes with 20s user opt-in; Binder independent "+
            "of accessibility map root; map actions still verified");
        yes(binder.contains("SystemClock.elapsedRealtime()-requestAt<6000L")&&
            binder.contains("if(registered)return;")&&
            binder.contains("stop(); // stale unregistered")&&
            binder.contains("code<1000||code>1003")&&
            binder.contains("transact(2)")&&
            !binder.contains("am force-stop"),
            "Binder requested-but-disconnected recovery, strict OEM key range");
        yes(ui.contains("this::askRecaptureKeys")&&
            ui.contains("this::chooseWheelDisplay")&&
            ui.contains("this::askStartExistingGoogleMapsRoute")&&
            ui.contains("RECAPTURAR TECLAS OEM · 20 S · SIN ZOOM")&&
            ui.contains("GOOGLE MAPS · INICIAR RUTA DEL RESUMEN")&&
            ui.contains("BridgeWheelAccessibilityService.captureState(this)")&&
            ui.contains("BridgeWheelAccessibilityService.mapsStartState(this)")&&
            ui.contains("BridgeWheelAccessibilityService.isConnected()")&&
            ui.contains("RECONECTAR ACCESIBILIDAD · ADB LOCAL")&&
            adb.contains("PERSISTENT_PORT=5556, MANUAL_PORT=5555")&&
            access.contains("BridgeLocalAdb.shell(app,"),
            "Bring Receiver recapture and 5556 diagnostics into Bridge UI");
        yes(service.contains("startMapsNavigationButton()")&&
            service.contains("explicitStart(label(node))")&&
            service.contains("found.size()!=1")&&
            service.contains("start.performAction(AccessibilityNodeInfo.ACTION_CLICK)")&&
            service.contains("visibleMap(MAPS)")&&
            navigation.contains("s.equals(\"iniciar navegacion\")")&&
            link.contains("google.navigation:q=")&&
            !link.contains("Intent.FLAG_ACTIVITY_CLEAR_TASK"),
            "Start only already-previewed Google Maps route, never new app/task");
        for(String src:new String[]{service,binder,ui,access}){
            yes(!src.contains("dispatchGesture(")&&
                !src.contains("GestureDescription")&&
                !src.contains("ZOOM_IN")&&
                !src.contains("am force-stop")&&
                !src.contains("pm clear"),
                "No gestures, zoom, wipe or app force-stop in wheel code");
        }
        System.out.println("PASS receiver-style 20s key recapture and Maps start, no zoom");
    }
}
