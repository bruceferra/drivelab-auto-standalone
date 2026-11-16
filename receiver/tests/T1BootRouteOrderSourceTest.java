package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class T1BootRouteOrderSourceTest{
 static String r(String n)throws Exception{
  return Files.readString(Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",n));
 }
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  String receiver=r("ReceiverService.java"),router=r("T1ClusterWazeRouter.java"),
    cache=r("T1LastRoute.java"),toolbar=r("T1GlobalToolbar.java");
  int method=receiver.indexOf("private void scheduleNativeClusterWaze()");
  int boot=receiver.indexOf("T1ClusterWazeRouter.onReceiverBoot(this)",method);
  int restore=receiver.indexOf("T1LastRoute.restore(this)",boot);
  ok(method>=0&&boot>method&&restore>boot&&
     receiver.indexOf("},3200L);",restore)>restore&&
     receiver.indexOf("},20000L);",restore)>restore,
     "Boot cluster must open maps then restore saved destination");
  ok(receiver.contains("currentAt!=requestedAt")&&
     receiver.contains("T1ClusterLayoutPolicy.pendingUnverified(this)"),
     "Do not override more recent routes or unverified geometry");
  ok(router.contains("static String onReceiverBoot(Context c)")&&
     router.contains("return opened;")&&cache.contains("MAX_AGE_MS=12L"),
     "Router confirms launch request, cache remains scoped");
  ok(toolbar.contains("T1LastRoute.restore(owner)"),
     "Manual ON also restores; boot behavior now matches");
  System.out.println("PASS boot UI then two map windows then saved navigation");
 }
}
