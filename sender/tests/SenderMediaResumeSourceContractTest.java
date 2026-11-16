package ar.com.rango5gps.drivelabauto.sender;
import java.nio.file.Files;import java.nio.file.Path;
public final class SenderMediaResumeSourceContractTest{
 static String read(String p)throws Exception{return Files.readString(Path.of(p));}
 static void must(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception{
 String base="sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/";
 String audio=read(base+"SenderAudioBootstrap.java"),
        recv=read(base+"SenderAudioBootstrapReceiver.java"),
        ui=read(base+"SenderActivity.java");
 must(audio.contains("resumeLastMedia(Context context,boolean fromVerifiedConnection)")&&
      audio.contains("exactConnectedA2dp(c,p.getString(ADDRESS,\"\"))")&&
      audio.contains("routeMatches(am,p.getString(ADDRESS,\"\"),true)")&&
      audio.contains("KEYCODE_MEDIA_PLAY")&&
      audio.contains("KeyEvent.ACTION_DOWN")&&
      audio.contains("KeyEvent.ACTION_UP")&&
      !audio.contains("KEYCODE_MEDIA_PLAY_PAUSE")&&
      !audio.contains("setBluetoothA2dpOn("),
      "Standard PLAY only after exact A2DP choice, never unsafe toggle/profile hack");
 must(recv.contains("SenderAudioBootstrap.AUTO_RESUME,false")&&
      recv.contains("SenderAudioBootstrap.resumeLastMedia(app,true)")&&
      recv.contains("SenderAudioBootstrap.prime(app,true)"),
      "Old tone default remains; auto media resume is explicitly opt-in");
 must(ui.contains("2 · PROBAR PLAY MULTIMEDIA EN XIAOMI")&&
      ui.contains("AUTO PLAY AL CONECTAR · ON (APAGAR)")&&
      ui.contains("AUTO PLAY AL CONECTAR · OFF (ACTIVAR)")&&
      ui.contains("DIAGNÓSTICO · TONO A2DP (1,4 S)"),
      "Independent UI tests for media play and tone");
 System.out.println("PASS sender precise A2DP media play opt-in and trace");
 }
}
