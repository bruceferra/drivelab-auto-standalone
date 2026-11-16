package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Android source safety contract. Real car hardware still needs testing.
 * Check physical 1010 does NOT launch an external Google assistant window,
 * wheel 1000-1003 uses the exact SAME OEM Binder callback, and TTS never
 * pretends engine installation or audible playback succeeded.
 */
public final class T1UnifiedWheelQuietVoiceSourceContractTest {
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String mic=read("T1OemMicrophoneService.java");
        String wheel=read("T1WazeWheelService.java");
        String voice=read("T1VoiceRouteActivity.java");
        String layout=read("T1ClusterLayoutSettingsActivity.java");
        String bridge=read("T1OemHardKeyBridge.java");
        String manifest=Files.readString(
            Path.of("receiver/src/main/AndroidManifest.xml"));

        require(bridge.contains("(key>=1000&&key<=1003)||key==1010")&&
            mic.contains("KEY_MIC=1010")&&
            mic.contains("if(action!=100||!started)return;")&&
            mic.contains("dispatchFromSharedMicrophone(")&&
            mic.contains("releaseForSharedMicrophone()"),
            "One verified OEM Binder callback shares physical voice and wheel");
        require(wheel.contains("static volatile T1WazeWheelService connectedInstance")&&
            wheel.contains("dispatchFromSharedMicrophone(Context app,int code,")&&
            wheel.contains("if(code<1000||code>1003)return;")&&
            wheel.contains("if(shared){")&&
            wheel.contains("oemBridge.stop();")&&
            wheel.contains("active.handleOemKey(code,action,extra)")&&
            wheel.contains("if(action!=100)return;")&&
            wheel.contains("WAZE.equals(")&&
            wheel.contains("getWindowsOnAllDisplays()"),
            "Waze-only screen controls must share mic binding, never double subscribe");
        require(wheel.contains("zoomVisibleWazeMap(root,forward)")&&
            wheel.contains("wazeDisplayId!=display.getDisplayId()")&&
            wheel.contains("setDisplayId(wazeDisplayId)")&&
            wheel.contains("fingerA,0,240L")&&
            wheel.contains("fingerB,0,240L")&&
            wheel.contains("getBoolean(ZOOM_MAP,false)"),
            "OEM wheel map zoom only targets the visible Waze window on selected display");
        require(mic.contains("UNIFIED_WHEEL_CONFIGURED")&&
            layout.contains("UNIFIED_WHEEL_CONFIGURED")&&
            layout.contains("putBoolean(T1WazeWheelService.ENABLED,true)")&&
            layout.contains("putBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,true)"),
            "One-time migration + explicit physical wheel enable, manual OFF respected");
        require(voice.contains("ACTION_OEM_QUIET_RECOGNITION")&&
            mic.contains("setAction(T1VoiceRouteActivity.ACTION_OEM_QUIET_RECOGNITION)")&&
            voice.contains("Theme_Translucent_NoTitleBar")&&
            voice.contains("voiceHandler.postDelayed(this::listen,250L)")&&
            voice.contains("SpeechRecognizer.createSpeechRecognizer(this)")&&
            voice.contains("finishQuietAfterError(")&&
            voice.contains("finishQuietIfNavigation()")&&
            !mic.contains("setAction(T1VoiceRouteActivity.ACTION_SHORTCUT_GOOGLE_RECOGNITION)"),
            "Physical mic must attempt internal speech in transparent activity, not Google pop-up");
        require(voice.contains("T1VoiceIntentPolicy.routeDestination(phrase)")&&
            voice.contains("T1VoiceMath.calculate(phrase)")&&
            voice.contains("T1VoiceWeather.now(")&&
            voice.contains("T1VoiceNews.headlines(")&&
            voice.contains("T1ClusterWazeRouter.routeDestination(this,i)")&&
            voice.contains("requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}"),
            "Quiet speech still uses one existing dispatcher and requests audio permission");
        require(manifest.contains("android.speech.RecognitionService")&&
            manifest.contains("android.intent.action.TTS_SERVICE")&&
            manifest.contains("com.android.settings.TTS_SETTINGS")&&
            manifest.contains("android.speech.tts.engine.INSTALL_TTS_DATA")&&
            voice.contains("TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA")&&
            voice.contains("REINTENTAR MOTOR DE VOZ")&&
            voice.contains("AUDIO: " ),
            "Diagnose missing TTS engine rather than claim audible success");
        require(!mic.contains("AudioRecord")&&
            !mic.contains("MediaRecorder")&&
            !mic.contains("Runtime.getRuntime()")&&
            !wheel.contains("injectInputEvent(")&&
            !manifest.contains("android.intent.category.HOME"),
            "No covert recording, injected OEM taps or HOME substitution");
        System.out.println("PASS unified OEM wheel + invisible internal speech + TTS diagnostics");
    }
}