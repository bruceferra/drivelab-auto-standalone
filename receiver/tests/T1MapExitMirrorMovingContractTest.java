package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class T1MapExitMirrorMovingContractTest {
 static String src(String f)throws Exception{return Files.readString(Path.of(
  "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",f));}
 static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] a)throws Exception{
  String maps=src("T1GoogleMapsReplacement.java"),waze=src("T1WazePromptPolicy.java");
  ok(maps.contains("n.contains(\"salir\")&&n.contains(\"navegac\")")&&
     maps.contains("n.equals(\"salir\")")&&
     !maps.contains("n.equals(\"cancelar\")"),
     "Maps only accepts SALIR in a new-route-confirmation context");
  ok(waze.contains("quieres apagar waze")&&waze.contains("no apagar"),"Waze quit prompt understood");
  String mirrors=src("MirrorAutomationService.java"),fold=src("MirrorPulsePilot.java");
  int begin=mirrors.indexOf("if(ACTION_CONFIRM_NORMAL.equals(action))"),
      end=mirrors.indexOf("if(ACTION_STOP.equals(action)",begin);
  String ack=mirrors.substring(begin,end);
  ok(ack.contains("PULSE_IN_FLIGHT")&&ack.contains("PENDING_LEFT")&&
     ack.contains("PENDING_RIGHT")&&!ack.contains("runPulses(")&&
     !ack.contains("check.gear()"),"Mirror visual ACK in D cannot move mirrors");
  ok(mirrors.contains("if(rawGear!=1&&rawGear!=4)")&&
     mirrors.contains("}else if(gear!=1&&gear!=4)")&&
     fold.contains("requireSlowManeuver("),
     "Explicit angle adjustment in D, separate safe folding guard");
  System.out.println("PASS Google Maps exit, Waze close, and mirror D safety");
 }
}
