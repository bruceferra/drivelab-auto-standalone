package ar.com.rango5gps.drivelabauto.common;
import java.nio.file.Files;
import java.nio.file.Path;
public final class RoutePreferenceEndToEndSourceContractTest {
    private static String s(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    private static void ck(boolean v,String why){if(!v)throw new AssertionError(why);}
    private static String sender(String name){return "sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/"+name;}
    private static String recv(String name){return "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/"+name;}
    public static void main(String[] args)throws Exception{
        String activity=s(sender("SenderActivity.java")),
            dispatch=s(sender("RouteDispatchService.java")),
            targets=s(recv("T1NavigationTargets.java")),
            receiver=s(recv("ReceiverService.java")),
            router=s(recv("T1ClusterWazeRouter.java")),
            resized=s(recv("T1ClusterAdbResize.java"));
        ck(activity.contains("DESTINO PREDETERMINADO DE UBER")&&
            activity.contains("setSingleChoiceItems(choices,selected")&&
            activity.contains("SenderRouteTarget.choose(this")&&
            dispatch.contains("BridgeRouteTarget.sendKind(d.kind,mode)")&&
            dispatch.contains("SenderClient.send(this,wireKind,d.value)")&&
            !dispatch.contains("SenderClient.send(this,d.kind,d.value)"),
            "Sender must persist and encrypt selected navigation target");
        ck(receiver.contains("BridgeRouteTarget.baseKind(msg.kind)")&&
            receiver.contains("BridgeRouteTarget.target(msg.kind)")&&
            receiver.contains("T1NavigationTargets.send(")&&
            targets.contains("Uri.parse(\"google.navigation:q=\"")&&
            targets.contains("Uri.parse(\"waze://?\"")&&
            targets.contains("routeGoogleMaps(c,")&&
            router.contains("static String routeGoogleMaps("),
            "Receiver must route explicit Google Maps or Waze intent");
        ck(targets.contains("T1ClusterLayoutPolicy.active(c)")&&
            targets.contains("MODE_SPLIT.equals(")&&
            targets.contains("secondPackage(c)")&&
            targets.contains("T1ClusterLayoutPolicy.geometry(c,display,false)")&&
            targets.contains("T1ClusterLayoutPolicy.geometry(c,display,true)")&&
            !targets.contains("am task resize")&&!targets.contains("am stack remove")&&
            resized.contains("am stack remove "),
            "Dual mode only on pre-approved split, no moving ADB changes");
        System.out.println("PASS sender 3-way target UI and receiver split routing");
    }
}
