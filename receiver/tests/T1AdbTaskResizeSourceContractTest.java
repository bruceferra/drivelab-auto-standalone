package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

public final class T1AdbTaskResizeSourceContractTest {
    private static String source(String file)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",file));
    }
    private static void check(boolean x,String reason){
        if(!x)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        String adb=source("T1LocalAdbClient.java");
        String resize=source("T1ClusterAdbResize.java");
        String ui=source("T1ClusterLayoutSettingsActivity.java");
        String policy=source("T1ClusterLayoutPolicy.java");
        String math=source("T1ClusterLayoutMath.java");
        String router=source("T1ClusterWazeRouter.java");
        check(adb.contains("LOOPBACK=\"127.0.0.1\"")&&
              adb.contains("PORT=5555")&&
              adb.contains("command.matches(")&&
              adb.contains("am task resize [1-9]")&&
              adb.contains("am stack list")&&
              adb.contains("dumpsys activity activities")&&
              adb.contains("new InetSocketAddress(LOOPBACK,PORT)")&&
              adb.contains("KeyPairGenerator.getInstance(\"RSA\")")&&
              adb.contains("generator.initialize(2048)")&&
              adb.contains("adbPublicKey(")&&
              adb.contains("signChallenge(")&&
              !adb.contains("su -c")&&
              !adb.contains("Runtime.getRuntime().exec("),
              "ADB host must use private RSA over loopback and strict shell command whitelist");
        check(resize.contains("T1AdbTaskParser.unique(")&&
              resize.contains("command(waze.id,left)")&&
              resize.contains("command(maps.id,right)")&&
              resize.contains("taskList(c)")&&
              resize.contains("String alternative=T1LocalAdbClient.shell(c,")&&
              resize.contains("\"dumpsys activity activities\"")&&
              resize.contains("wazeNow.bounds.near(wazeBox,20)")&&
              resize.contains("mapsNow.bounds.near(mapsBox,20)")&&
              resize.contains("if(!parked(c))")&&
              resize.contains("oem.gear()==1")&&
              resize.contains("oem.speedKmh()")&&
              resize.contains("restoreFull(Context c,Done done)")&&
              !resize.contains("am task resize 0 "),
              "Resize only verified Waze/Maps task IDs in selected display and parked vehicle");
        check(policy.contains("if(!T1ClusterAdbResize.enabled(c))")&&
              policy.contains("T1ClusterAdbResize.apply(c,")&&
              policy.contains("MODE_SPLIT.equals(mode(c))?WAZE:")&&
              policy.contains("if(verified==0||verified<time)return false")&&
              policy.contains("T1Adb") == false,
              "Direct navigation stays Waze and split requires ADB-backed verification");
        String recv=source("ReceiverService.java");
        check(recv.contains("DESTINO RECIBIDO, Waze BLOQUEADO")&&
              recv.contains("DESTINO RECIBIDO; Waze SOLICITADO EN CLÚSTER")&&
              recv.contains("DESTINO RECIBIDO; Waze NO ABIERTO EN CLÚSTER")&&
              recv.contains("return attempted;"),
              "Distinguish destination encrypted delivery from attempted Waze cluster launch");
        check(ui.contains("T1ClusterAdbResize.VERIFIED_AT,0L")&&
              ui.contains("T1ClusterLayoutPolicy.TEST_AT,verifiedAt"),
              "Verified bounds must use identical timestamp so opt-in never fails incorrectly");
        check(ui.contains("HABILITAR PUENTE ADB LOCAL")&&
              ui.contains("PROBAR CONEXIÓN ADB LOCAL")&&
              ui.contains("adjustSplit(-10)")&&
              ui.contains("adjustSplit(10)")&&
              ui.contains("T1ClusterAdbResize.apply(this,")&&
              ui.contains("T1ClusterAdbResize.restoreFull(this,verified)")&&
              ui.contains("T1ClusterLayoutPolicy.TEST_AT")&&
              ui.contains("oem.gear()")&&
              ui.contains("oem.speedKmh()")&&
              ui.contains("gear!=1")&&
              ui.contains("speed>0.5")&&
              ui.contains("50,30,70")&&
              math.contains("Frame left(")&&
              router.contains("T1ClusterLayoutPolicy.routeWazeIfActive("),
              "User has +/-10 and 50/50 with P-only physical test, Waze route preserved");
        System.out.println("PASS localhost ADB task resize and Waze/Maps split safety contract");
    }
}