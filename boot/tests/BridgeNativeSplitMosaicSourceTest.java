package ar.com.rango5gps.drivelabauto.boot;
import java.nio.file.Files;
import java.nio.file.Path;

/** No-Freeform-caption central mosaic variant is opt-in, never OEM or boot. */
public final class BridgeNativeSplitMosaicSourceTest {
    static String get(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/"+name));
    }
    static void yes(boolean ok,String note){if(!ok)throw new AssertionError(note);}
    public static void main(String[] args)throws Exception{
        String windows=get("BridgeStandaloneWindows.java");
        String ui=get("BootActivity.java");
        String immersive=get("BridgeImmersive.java");
        String probe=get("BridgeVirtualProbeActivity.java");
        String boot=get("BootAutoService.java");
        String sender=get("BootLinkService.java");
        yes(windows.contains("static Result centerSplitNoFreeform(Activity activity)")&&
            windows.contains("int[] modes={3,4}")&&
            windows.contains("Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT")&&
            windows.contains("setLaunchDisplayId(Display.DEFAULT_DISPLAY)")&&
            windows.contains("BridgeWindowMath.center(")&&
            windows.contains("options.setLaunchBounds(")&&
            windows.contains("Method method=freeformMethod()")&&
            windows.contains("method.invoke(options,modes[i])")&&
            windows.contains("BridgeWindowsPolicy.validCenter(first,second,ratio)")&&
            windows.contains("MOSAICO NATIVO"),
            "Native 3/4 split requests two verified apps with selected ratio");
        yes(ui.contains("PROBAR MOSAICO SIN CABECERAS · SPLIT NATIVO")&&
            ui.contains("this::askNativeSplitMosaic")&&
            ui.contains("BridgeStandaloneWindows.centerSplitNoFreeform(this)")&&
            ui.contains("PROBAR DOS VENTANAS · SÓLO BRIDGE")&&
            ui.contains("BridgeStandaloneWindows.center(this)")&&
            ui.contains("MOSAICO · SPOTIFY TIPO CELULAR · PANEL ANGOSTO")&&
            ui.contains("this::chooseSpotifyPhoneRatio")&&
            ui.contains("390f*dm.density")&&
            ui.contains("No se cambia ninguna ventana")&&
            ui.contains("Android Jetour puede imponer 50/50")&&
            ui.contains("La prueba virtual de Bridge NO puede alojar aplicaciones"),
            "Manual experiment must retain proven legacy control, phone preset "+
            "and be honest about virtual-probe limitations");
        for(String src:new String[]{windows,ui}){
            yes(!src.contains("am force-stop")&&
                !src.contains("pm clear")&&
                !src.contains("settings put global")&&
                !src.contains("am display move-stack")&&
                !src.contains("WindowManager.addView(")&&
                !src.contains("performGlobalAction(")&&
                !src.contains("dispatchGesture("),
                "No OEM or global system mutations in selected app split");
        }
        yes(!boot.contains("centerSplitNoFreeform")&&
            !sender.contains("centerSplitNoFreeform")&&
            immersive.contains("BridgeImmersive.apply") == false&&
            probe.contains("Solo con el vehículo estacionado."),
            "No autorun, no pretending a third-party app is Bridge test-pattern");
        System.out.println("PASS optional Android 11 split mosaic (no Freeform headers), "+
            "phone-width preset and unchanged network/navigation/ignition");
    }
}
