package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1MapsYesAndFuelVoiceSourceTest{
 static String read(String n)throws Exception{
  return Files.readString(Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  String policy=read("T1GoogleMapsReplacement.java"),
         wheel=read("T1WazeWheelService.java");
  check(policy.contains("exitQuestion(String value)")&&
   policy.contains("genericExitAction(String value)")&&
   wheel.contains("prompt.genericAction&&!prompt.exactExitQuestion")&&
   wheel.contains("prompt.differentButtons!=1")&&
   wheel.contains("T1GoogleMapsReplacement.consume(this)"),
   "Maps YES/OK only when the exact route-exit dialog is present after new route");
  String math=read("T1VoiceMath.java");
  check(math.contains("fuelConversion(s)")&&math.contains("new BigDecimal(\"100\").divide(")&&
    math.contains("litros cada 100 kilómetros."),"Offline km/l to L/100 conversion");
  System.out.println("PASS Google Maps explicit YES and local fuel conversion");
 }
}
