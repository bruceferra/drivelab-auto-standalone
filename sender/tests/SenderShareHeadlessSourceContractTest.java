package ar.com.rango5gps.drivelabauto.sender;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression: Android Uber/share/Maps intents must not surface the Sender UI.
 * UI framework effects require physical Android verification beyond source tests. */
public final class SenderShareHeadlessSourceContractTest {
    private static void require(boolean ok,String msg){
        if(!ok)throw new AssertionError(msg);
    }
    public static void main(String[] ignored)throws Exception{
        String manifest=Files.readString(Path.of("sender/src/main/AndroidManifest.xml"));
        String activity=Files.readString(Path.of(
            "sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/RouteActivity.java"));
        String dispatch=Files.readString(Path.of(
            "sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/RouteDispatchService.java"));
        int route=manifest.indexOf("<activity android:name=\".RouteActivity\"");
        int end=manifest.indexOf("</activity>",route);
        require(route>=0&&end>route,"Share trampoline must be declared");
        String routeManifest=manifest.substring(route,end);
        require(routeManifest.contains("android:taskAffinity=\"\"")&&
                routeManifest.contains("android:noHistory=\"true\"")&&
                routeManifest.contains("android:excludeFromRecents=\"true\"")&&
                routeManifest.contains("Theme.Translucent.NoTitleBar"),
            "RouteActivity must never reuse the app's normal Sender UI task");
        require(routeManifest.contains("android.intent.action.SEND")&&
                routeManifest.contains("android.intent.action.VIEW")&&
                routeManifest.contains("android:scheme=\"geo\"")&&
                routeManifest.contains("android:scheme=\"waze\""),
            "Uber/share/geo/Waze deep links must keep their existing coverage");
        require(activity.contains("startForegroundService(worker)")&&
                activity.contains("if(isTaskRoot())finishAndRemoveTask();")&&
                activity.contains("else finish();")&&
                activity.indexOf("startForegroundService(worker)")<
                activity.indexOf("if(isTaskRoot())finishAndRemoveTask();"),
            "Dispatch begins before temporary task is dismissed");
        require(!activity.contains("SenderActivity.class")&&
                !activity.contains("moveTaskToBack(")&&
                !dispatch.contains("startActivity(new Intent(this,SenderActivity.class))"),
            "No background share may navigate into Sender configuration UI");
        require(dispatch.contains("SenderClient.send(this,wireKind,d.value)")&&
                dispatch.contains("startForeground(")&&
                dispatch.contains("stopSelf(startId)")&&
                dispatch.contains("if(!only&&sent.accepted)"),
            "Encrypted delivery remains background; optional phone map preserved");
        System.out.println("PASS share/Uber background task isolation; dispatch and navigation preserved");
    }
}
