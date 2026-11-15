package ar.com.rango5gps.drivelabauto.sender;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static-only guarantees for the opt-in T1 A2DP bridge.
 * It is NOT a physical AVRCP compatibility test.
 */
public final class SenderAudioBootstrapSourceContractTest {
    private static String read(String relative)throws Exception{
        return Files.readString(Path.of(relative));
    }
    private static void must(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String manifest=read("sender/src/main/AndroidManifest.xml");
        String bridge=read(
            "sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderAudioBootstrap.java");
        String receiver=read(
            "sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderAudioBootstrapReceiver.java");
        String activity=read(
            "sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderActivity.java");
        String recManifest=read("receiver/src/main/AndroidManifest.xml");
        String voice=read(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1VoiceRouteActivity.java");
        must(manifest.contains("android.permission.BLUETOOTH_CONNECT")&&
            manifest.contains("SenderAudioBootstrapReceiver")&&
            manifest.contains("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"),
            "Sender must receive A2DP connect after user grants Nearby Devices");
        must(receiver.contains("BluetoothProfile.STATE_CONNECTED")&&
            receiver.contains("SenderAudioBootstrap.sameDevice(context,device)")&&
            receiver.contains("goAsync()")&&receiver.contains("pending.finish()"),
            "Never send audio to another Bluetooth device or work indefinitely");
        must(bridge.contains("p.getBoolean(ENABLED,false)")&&
            bridge.contains("device.getAddress()")&&
            bridge.contains("exactConnectedA2dp(")&&
            bridge.contains("TYPE_BLUETOOTH_A2DP")&&
            bridge.contains("COOLDOWN_MS=60000L")&&
            bridge.contains("am.isMusicActive()"),
            "Audio may only be attempted to the selected A2DP device when idle");
        must(bridge.contains("AudioTrack.Builder()")&&
            bridge.contains("MediaSession(")&&
            bridge.contains("MediaMetadata.METADATA_KEY_TITLE")&&
            bridge.contains("AudioAttributes.USAGE_MEDIA")&&
            bridge.contains("am.abandonAudioFocusRequest(focus)")&&
            bridge.contains("session.setActive(false);session.release();"),
            "Prime a short native media session and always clean up audio focus");
        must(activity.contains("ELEGIR T1 EMPAREJADA")&&
            activity.contains("DESACTIVAR INICIO DE AUDIO T1")&&
            activity.contains("BLUETOOTH_CONNECT")&&
            activity.contains("getBondedDevices()")&&
            !activity.contains("getRemoteDevice(\"00:"),
            "Require one explicit selection, never invent vehicle Bluetooth address");
        int pickerStart=activity.indexOf("private void chooseCarAudio(){");
        int pickerEnd=activity.indexOf("private void testCarAudio(){",pickerStart);
        must(pickerStart>=0&&pickerEnd>pickerStart,
            "Bluetooth selection Activity must exist");
        String picker=activity.substring(pickerStart,pickerEnd);
        int dialogStart=picker.indexOf(".setTitle(\"ELEGIR T1");
        int choices=picker.indexOf(".setItems(labels.toArray(",dialogStart);
        must(dialogStart>=0&&choices>dialogStart&&
            !picker.substring(dialogStart,choices).contains(".setMessage(")&&
            picker.contains("ACTION_BLUETOOTH_SETTINGS")&&
            picker.contains("openBluetoothSettings()"),
            "Xiaomi AlertDialog must SHOW bonded-device list, not hide it behind setMessage");
        must(!bridge.contains("com.spotify.music")&&
            !bridge.contains("MediaController.TransportControls")&&
            !bridge.contains("setBluetoothA2dpOn("),
            "Never launch Spotify, hijack another media app or force system profile");
        must(recManifest.contains("android.intent.action.TTS_SERVICE")&&
            voice.contains("completeSpeechInitialization(int result)")&&
            voice.contains("AudioAttributes.USAGE_MEDIA")&&
            !voice.contains("ABRIR GEMINI (AUTO DETENIDO)"),
            "Receiver must see Android TTS engine and never open Gemini as dead-end");
        System.out.println("PASS Sender opt-in T1 A2DP bootstrap / Receiver TTS contract");
    }
}
