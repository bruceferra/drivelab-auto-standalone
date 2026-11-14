package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static safety assertions; NOT proof the Jetour renders freeform. */
public final class T1ClusterWindowAndMicSourceContractTest {
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    private static void require(boolean actual,String explanation){
        if(!actual)throw new AssertionError(explanation);
    }
    public static void main(String[] args)throws Exception{
        String manifest=Files.readString(
            Path.of("receiver/src/main/AndroidManifest.xml"));
        String router=read("T1ClusterWazeRouter.java");
        String policy=read("T1ClusterLayoutPolicy.java");
        String layout=read("T1ClusterLayoutSettingsActivity.java");
        String preview=read("T1ClusterLayoutPreview.java");
        String mic=read("T1OemMicrophoneService.java");
        String hardkey=read("T1OemHardKeyBridge.java");
        String boot=read("BootReceiver.java");
        String voice=read("T1VoiceRouteActivity.java");
        require(manifest.contains(".T1ClusterLayoutSettingsActivity")&&
                manifest.contains(".T1OemMicrophoneService"),
                "Register only private settings and service");
        require(policy.contains("setLaunchWindowingMode")&&
                policy.contains("freeform.invoke(options,5)")&&
                policy.contains("options.setLaunchBounds(bounds)")&&
                policy.contains("options.setLaunchDisplayId(display.getDisplayId())"),
                "OEM APK backed freeform-windowing mode=5 and bounds");
        require(policy.contains("prefs(c).getBoolean(ACTIVE,false)")&&
                policy.contains("TEST_AT")&&policy.contains("TEST_MODE")&&
                policy.contains("System.currentTimeMillis()-time>600000L")&&
                policy.contains("routeWazeIfActive(Context c,Intent waze)")&&
                router.contains("T1ClusterLayoutPolicy.routeWazeIfActive(c,waze)")&&
                router.contains("T1ClusterLayoutPolicy.boot(c)"),
                "Verified, opt-in layout never replaces known working default");
        require(layout.contains("SeekBar")&&
                layout.contains("SPLIT_PERCENT")&&
                layout.contains("WIDTH")&&
                layout.contains("HEIGHT")&&
                layout.contains("T1ClusterLayoutPolicy.X")&&
                layout.contains("T1ClusterLayoutPolicy.Y")&&
                layout.contains("oem.gear()")&&
                layout.contains("oem.speedKmh()")&&
                layout.contains("gear!=1")&&
                layout.contains("speed>0.5")&&
                preview.contains("T1ClusterLayoutMath.left(")&&
                preview.contains("T1ClusterLayoutMath.right("),
                "Live geometry preview and parked-only physical test");
        require(hardkey.contains("key==1010")&&
                mic.contains("KEY_MIC=1010")&&
                mic.contains("action!=100")&&
                mic.contains("T1VoiceRouteActivity.ACTION_SHORTCUT_GOOGLE_RECOGNITION")&&
                mic.contains("Settings.canDrawOverlays(this)")&&
                mic.contains("lastKeyAt<1200L")&&
                boot.contains("T1OemMicrophoneService.ENABLED")&&
                boot.contains("startForegroundService("),
                "Real mic key 1010 / down 100, explicit binding and boot restore");
        require(mic.contains("T1WazeWheelService.OEM_BINDER_ENABLED")&&
                layout.contains("BINDER OEM COMPARTIDO")&&
                layout.contains("DESACTIVAR RUEDITA Y PROBAR MIC"),
                "Avoid competing vendor hardkey callbacks without consent");
        require(!mic.contains("AudioRecord")&&
                !mic.contains("MediaRecorder")&&
                !mic.contains("su -c")&&
                !mic.contains("adb shell")&&
                !policy.contains("WindowManager.addView(")&&
                !manifest.contains("android.intent.category.HOME"),
                "No covert audio, root, new overlay or replacement launcher");
        require(voice.contains("ACTION_SHORTCUT_GOOGLE_RECOGNITION")&&
                policy.contains("WAZE.equals(waze.getPackage())"),
                "Physical button invokes existing voice entry, Waze only routes");
        System.out.println("PASS T1N134 mic 1010 + percent window/split layout contract");
    }
}