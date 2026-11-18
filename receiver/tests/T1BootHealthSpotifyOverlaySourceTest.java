package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Boot must not silently grant Accessibility or manipulate OEM Spotify.
 * All actual map focus actions are limited to existing Display 1 tasks
 * while 0 km/h is confirmed, and only on persistent owner's opt-in.
 */
public final class T1BootHealthSpotifyOverlaySourceTest {
    private static String source(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception{
        String receiver=source("ReceiverService.java");
        String wheel=source("T1WheelAccessibilityRecovery.java");
        String settings=source("T1WheelSettingsActivity.java");
        String bootFocus=source("T1ClusterBootFocus.java");
        String focus=source("T1ClusterForegroundRecovery.java");
        String clusters=source("T1ClusterWazeSettingsActivity.java");

        ok(receiver.contains("scheduleWheelBootAudit();")&&
            receiver.contains("T1WheelAccessibilityRecovery.checkAtBoot(this)")&&
            receiver.contains("boot.postDelayed(")&&
            receiver.contains("T1ClusterBootFocus.afterClusterBoot(this,boot,routeAt)")&&
            receiver.indexOf("T1LastRoute.restore(this)")<
                receiver.indexOf("T1ClusterBootFocus.afterClusterBoot(this,boot,routeAt)"),
            "After boot audit wheel and only refocus maps after cluster route");
        ok(wheel.contains("AUTO_BOOT_ADB=")&&
            wheel.contains("static String checkAtBoot(Context context)")&&
            wheel.contains("p.getBoolean(AUTO_BOOT_ADB,false)")&&
            wheel.contains("p.getLong(T1WazeWheelService.SERVICE_AT,0L)<=0")&&
            wheel.contains("!state.startsWith(\"DESHABILITADA EN ANDROID\")")&&
            wheel.contains("reauthorizeWithOwnerApproval(app)")&&
            wheel.contains("T1WazeWheelService.requestClusterReady(app)"),
            "ADB must require separate persistent consent, prior binding, and actual disabled state");
        ok(settings.contains("AUTORRECUPERAR RUEDITA POR ADB AL ENCENDER")&&
            settings.contains("AUTORIZAR AL ENCENDER")&&
            settings.contains("AUTO_BOOT_ADB,false")&&
            settings.contains("T1WazeWheelService.isServiceConnected()")&&
            settings.contains("Explicit wheel OFF also revokes the boot ADB grant"),
            "Explicit opt-in and opt-out revoke wheel boot ADB permission");
        ok(bootFocus.contains("static final String ENABLED=")&&
            bootFocus.contains("if(!p.getBoolean(ENABLED,false))return;")&&
            bootFocus.contains("T1ClusterForegroundRecovery.request(app,result->")&&
            bootFocus.contains("T1ClusterForegroundRecovery.request(app,second->")&&
            bootFocus.contains("EXECUTING.set(false)")&&
            bootFocus.contains("T1ClusterWazeRouter.autoBoot(app)")&&
            bootFocus.contains("T1ClusterLayoutPolicy.savedDualSplit(app)")&&
            bootFocus.contains("LAST_REQUEST_MS"),
            "Max two opt-in focus attempts tied to actual cold-boot Waze/Maps panels");
        ok(focus.contains("if(!stopped(app))")&&
            focus.contains("T1AdbTaskParser.unique(list,1,pkg)")&&
            focus.contains("am.moveTaskToFront(task.id,")&&
            !focus.contains("am force-stop com.spotify.music")&&
            !bootFocus.contains("force-stop com.spotify.music")&&
            !bootFocus.contains("startActivity("),
            "Never close Spotify nor change OEM audio; only existing map task");
        ok(clusters.contains("MAPAS DELANTE AL ENCENDER")&&
            clusters.contains("T1ClusterBootFocus.describe(this)")&&
            clusters.contains("ACTIVAR PRUEBA")&&
            clusters.contains("La capa de Spotify puede ser una ventana OEM"),
            "Optional Spotify-overlay recovery shows limitations and status in clúster settings");
        System.out.println("PASS opt-in wheel health and Spotify overlay boot map focus never modify OEM");
    }
}
