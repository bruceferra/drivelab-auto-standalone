package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1AdbDualPortFallbackSourceContractTest {
    static void ck(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        String adb=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1LocalAdbClient.java"));
        String resize=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1ClusterAdbResize.java"));
        ck(adb.contains("PERSISTENT_PORT=5556, MANUAL_PORT=5555")&&
            adb.contains("Socket openLocal()")&&
            adb.contains("new int[]{first,second}")&&
            adb.contains("new InetSocketAddress(LOOPBACK,port)")&&
            adb.contains("try(Socket sock=openLocal())")&&
            adb.contains("lastLocalPort=port"),
            "Must try both Jetour localhost ADB ports");
        ck(!adb.contains("Runtime.getRuntime().exec")&&
            !adb.contains("setprop ")&&
            !adb.contains("su ")&&
            adb.contains("command.matches(")&&
            adb.contains("KEY_FILE="),
            "No root, OEM config changes or key rotation");
        ck(resize.contains("T1LocalAdbClient.connectedPort()")&&
            resize.contains("ADB LOCAL CONECTADO en puerto "),
            "Show actual ADB local port");
        System.out.println("PASS local ADB probe 5556 then 5555");
    }
}
