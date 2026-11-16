package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class T1WheelOemReconnectContractTest{
 static String src(String n)throws Exception{
  return Files.readString(Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
 }
 static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] a)throws Exception{
  String b=src("T1OemHardKeyBridge.java"),m=src("T1OemMicrophoneService.java"),w=src("T1WazeWheelService.java");
  ok(b.contains("if(requested&&registered())return;")&&b.contains("if(requested)stop();"),"Stale OEM Binder request rebinds");
  ok(m.contains("serviceAlive(Context c)")&&m.contains("maintainBinder")&&m.contains("!bridge.registered()"),"Mic listener reconnect watchdog");
  ok(w.contains("serviceAlive(this)")&&w.contains("oemBridge.start();")&&
     w.contains("T1ClusterWazeRouter.selected(this)!=null")&&
     !w.contains("lastActiveWindowAt")&&
     w.contains("no expone controles accesibles"),
     "Wheel stays subscribed during inaccessible map windows but cannot click them");
  System.out.println("PASS resilient OEM wheel after temporary focus/Binder loss");
 }
}
