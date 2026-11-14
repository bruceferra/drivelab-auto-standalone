package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

public final class T1VoiceShortcutSourceContractTest {
    private static void ok(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception {
        String manifest=Files.readString(Path.of("receiver/src/main/AndroidManifest.xml"));
        String voice=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1VoiceRouteActivity.java"));
        ok(manifest.contains("android:name=\".T1VoiceRouteActivity\" android:exported=\"false\""),
            "Internal activity remains private");
        ok(manifest.contains("android:name=\".VoiceAssistantShortcut\"") &&
           manifest.contains("android:targetActivity=\".T1VoiceShortcutActivity\"") &&
           manifest.contains("android:label=\"DriveLab · Asistente de voz\"") &&
           manifest.contains("<category android:name=\"android.intent.category.LAUNCHER\" />"),
            "External launcher can choose dedicated universal assistant icon");
        ok(voice.contains("T1VoiceIntentPolicy.routeDestination(phrase)") &&
           voice.contains("T1VoiceMath.calculate(phrase)") &&
           voice.contains("T1VoiceWeather.now(") &&
           voice.contains("T1VoiceNews.headlines(") &&
           voice.contains("T1VoiceFootball.latestSanLorenzo()"),
            "Voice router distinguishes navigation, math, weather and live topics");
        String shortcut=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1VoiceShortcutActivity.java"));
        ok(shortcut.contains("T1VoiceRouteActivity.ACTION_SHORTCUT_SYSTEM_VOICE") &&
           voice.contains("root.post(this::openSystemVoice)") &&
           voice.contains("ACTION_SHORTCUT_SYSTEM_VOICE.equals(getIntent().getAction())"),
            "Shortcut must launch the native voice session on first tap");
        ok(voice.contains("HABLAR · VOZ DEL SISTEMA (UN TOQUE)") &&
           voice.contains("RECONOCIMIENTO INTERNO · DIAGNÓSTICO") &&
           voice.contains("voiceHandler.postDelayed(recognitionTimeout,6500L)") &&
           !voice.contains("if(state==null)root.post(this::listen)"),
            "Never auto-start a hanging Android recognizer; 1 tap uses working system voice");
        ok(!voice.contains("transact(15"),
            "Voice entry never sends vehicle commands");
        System.out.println("PASS safe external launcher shortcut for T1 universal voice");
    }
}
