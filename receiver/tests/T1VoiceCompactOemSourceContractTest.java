package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1VoiceCompactOemSourceContractTest {
    static void ck(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        String mic=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1VoiceRouteActivity.java"));
        String service=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1OemMicrophoneService.java"));
        ck(mic.contains("Theme_Translucent_NoTitleBar")&&
            mic.contains("getWindow().setContentView(card)")&&
            mic.contains("getWindow().setLayout(T1JetourTheme.dp(this,520)")&&
            mic.contains("FLAG_DIM_BEHIND")&&
            mic.contains("FLAG_NOT_TOUCH_MODAL")&&
            mic.contains("voiceHandler.postDelayed(this::listen,250L)")&&
            mic.contains("finishQuietIfNavigation()"),
            "Voice must use compact visible floating window, not empty fullscreen black");
        ck(service.contains("ACTION_OEM_QUIET_RECOGNITION")&&
            service.contains("startActivity(open)")&&
            mic.contains("SpeechRecognizer.createSpeechRecognizer(this)"),
            "Preserve working wheel mic recognition and routing");
        System.out.println("PASS compact OEM mic window without black fullscreen");
    }
}
