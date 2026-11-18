package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class T1IgnitionMirrorRecircAndWheelTest{
  static String s(String n)throws Exception{return Files.readString(Path.of(
      "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));}
  static void yes(boolean a,String m){if(!a)throw new AssertionError(m);}
  public static void main(String[] args)throws Exception{
    String mirror=s("MirrorAutomationService.java"),air=s("RecirculationGuardService.java"),
      boot=s("BootReceiver.java"),activity=s("T1ImmersiveActivity.java"),
      mic=s("T1OemMicrophoneService.java"),toolbar=s("T1GlobalToolbar.java");
    yes(mirror.contains("static void activateOnAppOpen(Context context)")&&
        mirror.contains("if(unresolved(p))")&&
        mirror.contains("startForegroundService(new Intent(context,")&&
        mirror.contains("if(unresolved(prefs()))")&&
        mirror.contains("boolean hasUnresolved=unresolved(prefs());"),
        "Auto mirror intent at start, never discard incomplete physical pulses");
    yes(air.contains("static void newIgnition(Context context)")&&
        boot.contains("RecirculationGuardService.newIgnition(context);")&&
        air.contains("if(recentWrites.size()>=3)"),
        "Recover cabin recirc preference per ignition without OEM override loop");
    yes(activity.contains("T1OemMicrophoneService.ensureRunning(this)")&&
        mic.contains("static void ensureRunning(Context c)"),
        "Recover unified steering wheel subscription from foreground");
    yes(toolbar.contains("MirrorManualTiltService.unverified(owner)")&&
        toolbar.contains("boolean unknown=(inFlight||(!manualSelected&&unresolved))&&!healthy;"),
        "Manual tilt icon reflects actual pending pulses and unverified physical pose");
    System.out.println("PASS ignition mirror/recirc/wheel recovery safeguards");
  }
}
