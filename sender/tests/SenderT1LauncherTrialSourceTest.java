package ar.com.rango5gps.drivelabauto.sender;
import java.nio.file.Files;
import java.nio.file.Path;
/** Experimental route is explicit; never another Maps task on physical cluster. */
public final class SenderT1LauncherTrialSourceTest {
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    static String read(String p)throws Exception{return Files.readString(Path.of(p));}
    public static void main(String[] ignored)throws Exception {
        String common=read("common/src/main/java/ar/com/rango5gps/drivelabauto/common/BridgeRouteTarget.java");
        String sender=read("sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderActivity.java");
        String target=read("sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderRouteTarget.java");
        String dispatch=read("sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/RouteDispatchService.java");
        String bridge=read("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootLinkService.java");
        String adb=read("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeLocalAdb.java");
        String parser=read("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeT1LauncherRoutePolicy.java");
        ok(common.contains("T1_LAUNCHER=\"t1\"")&&
           common.contains("\"coord_t1\"")&&
           common.contains("\"search_t1\""),
           "Explicit authenticated new kind, old wire unchanged");
        ok(sender.contains("PROBAR · ENVIAR RUTA DE UBER AL CLÚSTER T1LAUNCHER")&&
           sender.contains("this::chooseT1LauncherRouteTrial")&&
           sender.contains("ACTIVAR PARA PRÓXIMO UBER")&&
           sender.contains("SenderRouteTarget.choose(this,BridgeRouteTarget.T1_LAUNCHER)")&&
           sender.contains("T1LAUNCHER: no se envía ruta de prueba a La Plata")&&
           target.contains("BridgeRouteTarget.T1_LAUNCHER"),
           "Owner opt-in, no dummy route, no breaking Waze/Maps/Both choice");
        ok(dispatch.contains("BridgeRouteTarget.T1_LAUNCHER.equals(mode)")&&
           dispatch.contains("SenderClient.send(this,wireKind,d.value)")&&
           bridge.contains("BridgeLocalAdb.deliverToExistingT1Maps(this,uri)")&&
           bridge.contains("acknowledged=result.startsWith(\"ENTREGADO AL GOOGLE MAPS\")")&&
           parser.contains("--task ")&&parser.contains("--display ")&&
           parser.contains("display>=2")&&
           parser.contains("if(matched!=null)return null")&&
           adb.contains("BridgeT1LauncherRoutePolicy.same(expected,now)")&&
           adb.contains("boolean exactT1MapDelivery=verifiedT1"),
           "Only same existing virtual task; no physical Display1 fallback");
        ok(!sender.contains("startActivity(new Intent(this,Bridge")&&
           !bridge.contains("T1Launcher" + ".class")&&
           !parser.contains("am stack move-task")&&
           !parser.contains("pm clear")&&
           !parser.contains("force-stop"),
           "Never hijack OEM/cluster or spawn another T1Launcher activity");
        System.out.println("PASS experimental Sender T1Launcher existing-task route guard");
    }
}
