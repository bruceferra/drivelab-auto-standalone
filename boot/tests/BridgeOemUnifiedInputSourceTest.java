package ar.com.rango5gps.drivelabauto.boot;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression: physical mic 1010 & wheel 1000-1003 share ONE callback. */
public final class BridgeOemUnifiedInputSourceTest {
    static String source(String f)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/"+f));
    }
    static void yes(boolean good,String why){if(!good)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        String binder=source("BridgeWheelOemBinder.java");
        String service=source("BridgeWheelAccessibilityService.java");
        String activity=source("BootActivity.java");
        String voice=source("BridgeOemVoiceLauncher.java");
        String original=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1OemHardKeyBridge.java"));
        String manifest=Files.readString(Path.of("boot/src/main/AndroidManifest.xml"));
        yes(binder.contains("if(transaction==1||transaction==2)")&&
            original.contains("if(code==1||code==2)")&&
            binder.contains("transaction!=3")&&
            binder.contains("code!=1010")&&
            binder.contains("listener.event(code,action,extra)")&&
            binder.contains("void start()")&&binder.contains("void stop()")&&
            binder.contains("KEY_COUNT")&&binder.contains("KEY_MIC_COUNT")&&
            binder.contains("KEY_LAST")&&
            binder.contains("BINDER OEM REGISTRADO")&&
            binder.contains("monitorPrefs(Context c)"),
            "Must copy Receiver callback compatibility, one unified OEM subscription "+
            "and show actual registration/key counters");
        yes(service.contains("MIC_ENABLED")&&
            service.contains("setMicrophoneArmed(boolean value)")&&
            service.contains("microphoneArmed=false")&&
            service.contains("if(!capture&&!optIn&&!mic)")&&
            service.contains("onOemEvent(int code,int action,int extra)")&&
            service.contains("if(code==1010)")&&
            service.contains("BridgeOemVoiceLauncher.trigger(this,action)")&&
            service.contains("if(isCaptureActive())")&&
            service.contains("return; // No selection/click/back/gesture during recapture.")&&
            !service.contains("GestureDescription")&&
            !service.contains("dispatchGesture("),
            "Mic and wheel share one Android AccessibilityService callback, "+
            "20s capture doesn't execute actions, no zoom");
        yes(activity.contains("BridgeWheelOemBinder.monitorPrefs(this)")&&
            activity.contains("CAPTURA VOLANTE · EN VIVO")&&
            activity.contains("BINDER OEM REAL: ")&&
            activity.contains("this::toggleOemMicrophone")&&
            activity.contains("setMicrophoneArmed(true)")&&
            activity.contains("setMicrophoneArmed(false)")&&
            activity.contains("MICRÓFONO OEM 1010")&&
            activity.contains("BridgeOemVoiceLauncher.state(this)")&&
            !manifest.contains("BridgeOemVoiceService")&&
            !manifest.contains("BridgeMicrophoneService"),
            "Live callbacks, explicit mic opt-in, no second hardkey service");
        yes(voice.contains("Intent.ACTION_VOICE_COMMAND")&&
            voice.contains("action!=100")&&
            voice.contains("BridgeBackgroundAccess.authorized(c)")&&
            voice.contains("notifyUser(c,voice)")&&
            voice.contains("MICRÓFONO 1010/100 RECIBIDO")&&
            !voice.contains("T1VoiceRouteActivity")&&
            !voice.contains("MediaRecorder")&&
            !voice.contains("AudioRecord")&&
            !voice.contains("dispatchGesture"),
            "1010 only requests OS voice assistant after explicit owner opt-in; "+
            "does not pretend to run original Receiver speech stack");
        System.out.println("PASS unified OEM wheel/microphone Binder, live telemetry and 1010 voice opt-in");
    }
}
