package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class T1WheelAutomaticBootResumeTest{
 static String read(String f)throws Exception{return Files.readString(
     Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",f));}
 static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
   String r=read("ReceiverService.java"),w=read("T1WazeWheelService.java");
   int boot=r.indexOf("T1ClusterWazeRouter.onReceiverBoot(this)"),
       route=r.indexOf("T1LastRoute.restore(this)",boot),
       wheel=r.indexOf("T1WazeWheelService.requestClusterReady(this)",route);
   ck(boot>0&&route>boot&&wheel>route,
       "Open cluster then restore destination then reconnect window control");
   ck(w.contains("static void requestClusterReady(Context context)")&&
       w.contains("live.setServiceInfo(info)")&&
       w.contains("live.describeAccessibleDisplays()")&&
       w.contains("FLAG_RETRIEVE_INTERACTIVE_WINDOWS")&&
       w.contains("TYPE_WINDOW_CONTENT_CHANGED"),
       "Update accessibility window cache automatically after boot");
   ck(!w.contains("performGlobalAction(GLOBAL_ACTION_BACK)"),
       "Do not touch unrelated screens to restore wheel");
   System.out.println("PASS auto wheel window requery after cold boot");
 }
}
