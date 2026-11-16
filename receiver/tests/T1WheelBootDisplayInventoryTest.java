package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class T1WheelBootDisplayInventoryTest{
 static String read(String n)throws Exception{return Files.readString(Path.of(
 "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));}
 static void yes(boolean a,String m){if(!a)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
 String w=read("T1WazeWheelService.java"),
  a=read("ReceiverActivity.java"),t=read("T1GlobalToolbar.java");
 yes(w.contains("describeAccessibleDisplays()")&&
     w.contains("getWindowsOnAllDisplays()")&&
     w.contains("WINDOW_DIAG")&&
     w.contains("Display seleccionado")&&
     w.contains("wazeVisible")&&w.contains("mapsVisible"),
     "Real selected-display inventory without touching OEM or guesses");
 yes(a.contains("hasCategory(Intent.CATEGORY_LAUNCHER)")&&
     a.contains("drivelab_open_after_boot")&&
     a.contains("new Intent(this,T1PremiumCockpitActivity.class)")&&
     a.contains("if(isFinishing()||quickControls==null)return;"),
     "Launch/boot cockpit without breaking explicit Inicio navigation");
 yes(t.indexOf("button(actions,\"COCKPIT\"")<
     t.indexOf("optimizer=button(actions,")&&
     t.indexOf("button(actions,\"COCKPIT\"")>
     t.indexOf("wheelPanelButton=button(actions,"),
     "Cockpit shortcut beside OPTIMIZADO");
 System.out.println("PASS boot cockpit, steering-window inventory");
 }
}
