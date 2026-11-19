package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Keep update at far-right of the clock on every screen.
 * It must invoke the SAME signed Receiver OTA path as Inicio > Xiaomi.
 */
public final class T1ToolbarUpdateShortcutContractTest {
    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception {
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String toolbar=Files.readString(Path.of(root+"T1GlobalToolbar.java"));
        String base=Files.readString(Path.of(root+"T1ImmersiveActivity.java"));
        String home=Files.readString(Path.of(root+"ReceiverActivity.java"));
        String updater=Files.readString(Path.of(
            "common/src/main/java/ar/com/rango5gps/drivelabauto/common/Updater.java"));
        int clock=toolbar.indexOf("header.addView(clockBox,");
        int repair=toolbar.indexOf("Button reload=button(header,");
        int update=toolbar.indexOf("Button update=button(header,");
        int arrange=toolbar.indexOf("enableRowReorder(leading,\"first\")");
        require(clock>=0&&repair>clock&&update>repair&&arrange>update,
            "Top right order must be clock, small Maps repair, last update icon");
        require(toolbar.contains("button(header,\"MAPAS\"")&&
            toolbar.contains("Button update=button(header,\"⟳\"")&&
            toolbar.contains("T1JetourTheme.GREEN_BUTTON,46,"),
            "Updater should be a distinct small square refresh icon");
        require(toolbar.contains("android.widget.Toast.makeText(owner,")&&
            toolbar.contains("Updater.check(owner,\"receiver\",message->")&&
            home.contains("Updater.check(this,\"receiver\",this::report)"),
            "Shortcut must reuse the exact existing Receiver OTA flow");
        require(toolbar.contains("row==firstButtons && clockAnchor!=null")&&
            toolbar.contains("int end=row==firstButtons")&&
            toolbar.contains("for(int i=0;i<end;i++)"),
            "Drag reorder cannot pull last top-right actions away from clock");
        require(base.contains("globalToolbar=new T1GlobalToolbar(this)")&&
            base.contains("shell.addView(globalToolbar.view()"),
            "Shortcut must be persistent across Receiver activity screens");
        require(updater.contains("certificateHashes(current)")&&
            updater.contains("downloadAndVerify(a,offer,reporter)")&&
            updater.contains("requestInstaller(a,apk,offer,reporter)"),
            "Keep signed package verification and Android native installer");
        System.out.println("PASS fixed top-right update icon and verified Receiver OTA");
    }
}
