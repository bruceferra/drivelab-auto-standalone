package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Physical Jeep/Jetour Android 11 background launch contract. Permission
 * correctness is checked by Android at runtime; source tests only ensure the
 * dangerous legacy Settings dead-end and false success statuses are gone.
 */
public final class BridgeBackgroundLaunchSourceTest {
    private static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",name));
    }
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String home=read("BootActivity.java");
        String background=read("BridgeBackgroundAccess.java");
        String adb=read("BridgeLocalAdb.java");
        String receiver=read("BootLinkService.java");
        String auto=read("BootAutoService.java");
        String manifest=Files.readString(Path.of("boot/src/main/AndroidManifest.xml"));
        check(home.contains("HABILITAR SEGUNDO PLANO · ADB LOCAL")&&
            home.contains("BridgeBackgroundAccess.ask(this)")&&
            home.contains("BridgeBackgroundAccess.describe(this)")&&
            !home.contains("Settings.ACTION_MANAGE_OVERLAY_PERMISSION"),
            "Jetour's blocked standard settings page must not be offered");
        check(background.contains("new AlertDialog.Builder(owner)")&&
            background.contains("AUTORIZAR SEGUNDO PLANO")&&
            background.contains("Settings.canDrawOverlays(c)")&&
            background.contains("BridgeLocalAdb.shell(app,command)")&&
            background.contains("appops set "+ "\"+LAB+\""+ " SYSTEM_ALERT_WINDOW allow")&&
            background.contains("appops get "+ "\"+LAB+\""+ " SYSTEM_ALERT_WINDOW")&&
            background.contains("if(!authorized(app))")&&
            background.contains("putBoolean(\"enabled\",true).commit()")&&
            background.contains("app.startForegroundService(")&&
            background.contains("BootLinkService.START")&&
            !background.contains("onBoot(")&&
            !background.contains("force-stop"),
            "Owner consent, local appop, Android readback and own listener only");
        check(adb.contains("ownLab=")&&
            adb.contains("ar.com.rango5gps.drivelabauto.boot.lab")&&
            adb.contains("boolean overlayCommand=ownLab")&&
            adb.contains("\"appops get \"+self+\" SYSTEM_ALERT_WINDOW\"")&&
            adb.contains("\"appops set \"+self+\" SYSTEM_ALERT_WINDOW allow\"")&&
            !adb.contains("cmd appops set *")&&
            !adb.contains("WRITE_SECURE_SETTINGS"),
            "Only Bridge LAB's exact appop command is eligible for local ADB");
        check(receiver.contains("if(!BridgeBackgroundAccess.authorized(this)&&")&&
            receiver.contains("!BootActivity.isVisible()")&&
            receiver.contains("DESTINO RECIBIDO, PERO NO ABIERTO")&&
            receiver.contains("ANDROID RECHAZÓ ABRIR")&&
            receiver.contains("return BootConfig.enabled(this)?START_STICKY:START_NOT_STICKY;")&&
            auto.contains("startForegroundService(new Intent(this,BootLinkService.class)")&&
            manifest.contains("android.permission.SYSTEM_ALERT_WINDOW")&&
            manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
            "Background denial must be diagnosable and boot listener independent");
        System.out.println("PASS background navigation: no blocked Settings, owner ADB appop, "+
            "real overlay check, encrypted foreground listener");
    }
}
