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
           manifest.contains("android:label=\"DriveLab · Asistente de voz\""),
            "Retain explicit voice shortcut component without a duplicate drawer icon");
        String app=manifest.substring(manifest.indexOf("<application"));
        String launcherCategory="android.intent.category.LAUNCHER";
        int launcherCount=app.split(launcherCategory,-1).length-1;
        ok(launcherCount==1,
            "Exactly ONE app drawer icon: ReceiverActivity, never voice alias");
        int mainIndex=app.indexOf("<activity android:name=\".ReceiverActivity\"");
        int aliasIndex=app.indexOf("<activity-alias android:name=\".VoiceAssistantShortcut\"");
        ok(mainIndex>=0 && aliasIndex>mainIndex &&
           app.substring(mainIndex,aliasIndex).contains(launcherCategory) &&
           !app.substring(aliasIndex,app.indexOf("</application>")).contains(launcherCategory),
            "Voice alias must NOT claim MAIN LAUNCHER");
        ok(voice.contains("startActivity(new Intent(this,ReceiverActivity.class)") ||
           voice.contains("new Intent(this,ReceiverActivity.class)"),
            "Voice screen can open the Receiver main screen directly");
        ok(voice.contains("T1VoiceIntentPolicy.routeDestination(phrase)") &&
           voice.contains("T1VoiceMath.calculate(phrase)") &&
           voice.contains("T1VoiceWeather.now(") &&
           voice.contains("T1VoiceNews.headlines(") &&
           voice.contains("T1VoiceFootball.latestSanLorenzo()"),
            "Voice router distinguishes navigation, math, weather and live topics");
        String shortcut=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1VoiceShortcutActivity.java"));
        ok(shortcut.contains("T1VoiceRouteActivity.ACTION_SHORTCUT_GOOGLE_RECOGNITION") &&
           voice.contains("root.post(this::listenExternal)") &&
           voice.contains("ACTION_SHORTCUT_GOOGLE_RECOGNITION.equals(getIntent().getAction())"),
            "Shortcut must open Google external recognizer on first tap");
        ok(voice.contains("🎤 HABLAR · WAZE, CLIMA, NOTICIAS Y CUENTAS") &&
           voice.contains("root.post(this::listenExternal)") &&
           voice.contains("if(heard!=null)heard.setText(") &&
           voice.contains("lastAudible=audible;") &&
           !voice.contains("MICRÓFONO GOOGLE · REPETIR RECONOCIMIENTO") &&
           !voice.contains("USAR CHATGPT PARA LAS CONSULTAS") &&
           !voice.contains("USAR GEMINI PARA LAS CONSULTAS"),
            "One visible voice entry with transcript/answer and no misleading AI switch");
        ok(voice.contains("dispatch(said,true)") &&
           voice.contains("if(fromVoice)") &&
           voice.contains("navigate(target);") &&
           voice.contains("confirmDestination(target);"),
            "Spoken navigation still goes directly to Waze without changing maps");
        ok(!voice.contains("shareWithAi(selectedAi(),phrase.trim());") &&
           !voice.contains("openSystemVoice();\n        status.setText(\"PREGUNTA LIBRE") &&
           voice.contains("say(\"CONSULTA LIBRE"),
            "Unknown spoken requests must not launch fullscreen Gemini");
        ok(voice.contains("USAGE_ASSISTANCE_NAVIGATION_GUIDANCE") &&
           voice.contains("AudioFocusRequest.Builder(") &&
           voice.contains("speaker.setAudioAttributes(speechAudioAttributes())") &&
           voice.contains("UtteranceProgressListener") &&
           voice.contains("releaseSpeechFocus();") &&
           voice.contains("speechReadyAt=android.os.SystemClock.uptimeMillis()+550L") &&
           voice.contains("voiceHandler.postDelayed(this::readQueuedSpeech,180L)") &&
           voice.contains("AUDIO: ") &&
           voice.contains("🔊 PROBAR / REPETIR AUDIO"),
            "Audio answer must defer recognizer focus, acquire navigation focus, release on TTS completion and expose test control");
        ok(!voice.contains("transact(15"),
            "Voice entry never sends vehicle commands");
        System.out.println("PASS safe external launcher shortcut for T1 universal voice");
    }
}
