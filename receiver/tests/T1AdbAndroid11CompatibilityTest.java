package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1AdbAndroid11CompatibilityTest {
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception {
        String adb=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1LocalAdbClient.java"));
        check(adb.contains("return new String(output.toByteArray(),StandardCharsets.UTF_8);"),
            "Android 11 ADB shell must decode bytes using supported String constructor");
        check(!adb.contains("output.toString(StandardCharsets.UTF_8)"),
            "Unsupported ByteArrayOutputStream.toString(Charset) crashes Android 11");
        check(adb.contains("MAX_PACKET=1024*1024")&&
            adb.contains("command.matches(")&&adb.contains("LOOPBACK=\"127.0.0.1\""),
            "Preserve localhost ADB and strict command whitelist");
        System.out.println("PASS Android 11 ADB stream decode regression");
    }
}
