package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1WheelOwnerAdbRestoreSourceTest {
    static String read(String file)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",file));
    }
    static void ok(boolean b,String why){
        if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception {
        String recover=read("T1WheelAccessibilityRecovery.java");
        String ui=read("T1WheelOwnerAdbDialog.java");
        String adb=read("T1LocalAdbClient.java");
        String policy=read("T1WheelAccessListPolicy.java");
        String home=read("ReceiverActivity.java");
        String wheel=read("T1WheelSettingsActivity.java");
        ok(recover.contains("reauthorizeWithOwnerApproval(Context context)")&&
            recover.contains("T1WazeWheelService.SERVICE_AT,0L")&&
            recover.contains("RUNNING.compareAndSet(false,true)")&&
            recover.contains("getString(PENDING,\"\")")&&
            recover.contains("T1WheelAccessListPolicy.withOurService(old)")&&
            recover.contains("settings get secure enabled_accessibility_services")&&
            recover.contains("Lecturas ADB/Android difieren")&&
            recover.contains("!java.util.Objects.equals(old,current(app))")&&
            recover.contains("settings put secure accessibility_enabled 1")&&
            recover.contains("Settings.Secure.ACCESSIBILITY_ENABLED"),
            "Explicit owner restoring an already-connected service must verify local ADB, Android list and no races");
        ok(policy.contains("static String withOurService(String existing)")&&
            policy.contains("if(own>1)return null;")&&
            policy.contains("return updated.length()>1024?null:updated"),
            "Preserve other accessibility services and reject duplicates");
        ok(ui.contains("new AlertDialog.Builder(owner)")&&
            ui.contains("AUTORIZO RESTAURAR POR ADB")&&
            ui.contains("reauthorizeWithOwnerApproval(owner)")&&
            ui.contains("AJUSTES ANDROID")&&
            ui.contains("127.0.0.1:5556 o :5555")&&
            ui.contains("sin celular, internet ni Termux"),
            "User confirmation and embedded local loopback ADB (never silent auto granting)");
        ok(home.contains("T1WheelOwnerAdbDialog.show(this)")&&
            wheel.contains("T1WheelOwnerAdbDialog.show(this)")&&
            wheel.contains("RESTAURAR PERMISO POR ADB LOCAL · SIN CELULAR"),
            "One tap appears in main and wheel settings");
        ok(adb.contains("LOOPBACK=\"127.0.0.1\"")&&
            adb.contains("settings get secure enabled_accessibility_services")&&
            adb.contains("settings put secure accessibility_enabled 1")&&
            !adb.contains("input keyevent 4")&&
            !adb.contains("settings put secure accessibility_enabled 0"),
            "ADB loopback whitelist allows only narrowly scoped read and requested enable");
        System.out.println("PASS owner-approved local port 5555/5556 wheel reauthorization, no other service deleted");
    }
}
