package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source-only invariant: 20s user-consented media projection, not app move. */
public final class BridgeMediaProjectionLabContractTest {
    private static final String BASE=
        "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
    private static String read(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    private static void yes(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String ui=read(BASE+"BootActivity.java");
        String activity=read(BASE+"BridgeProjectionLabActivity.java");
        String svc=read(BASE+"BridgeProjectionLabService.java");
        String manifest=read("boot/src/main/AndroidManifest.xml");
        String link=read(BASE+"BootLinkService.java");
        String boot=read(BASE+"BootReceiver.java");
        String auto=read(BASE+"BootAutoService.java");
        yes(ui.contains("this::askProjectionLab")&&
            ui.contains("LAB · COPIAR CENTRAL A TABLERO · PERMISO · 20 S")&&
            ui.contains("startActivity(new Intent(this,BridgeProjectionLabActivity.class)")&&
            !link.contains("BridgeProjectionLabService")&&
            !boot.contains("BridgeProjectionLabService")&&
            !auto.contains("BridgeProjectionLabService"),
            "No route/ignition/auto start; only foreground owner action");
        yes(activity.contains("createScreenCaptureIntent()")&&
            activity.contains("startActivityForResult(")&&
            activity.contains("resultCode!=RESULT_OK||data==null")&&
            activity.contains("startForegroundService(svc)")&&
            activity.contains("BridgeProjectionLabService.ACTION_STOP")&&
            activity.contains("Requiere permiso por sesión"),
            "Per-session Android consent and explicit stop");
        yes(svc.contains("LIMIT_MS=20000L")&&
            svc.contains("main.postDelayed(timeout,LIMIT_MS)")&&
            svc.contains("FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION")&&
            svc.contains("getMediaProjection(consentCode,consentData)")&&
            svc.contains("registerCallback(projectionCallback,main)")&&
            svc.contains("onStop()")&&
            svc.contains("projection.createVirtualDisplay(")&&
            svc.contains("VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR")&&
            svc.contains("new Presentation(this,physical)")&&
            svc.contains("dm.getDisplay(Display.DEFAULT_DISPLAY)")&&
            svc.contains("dm.getDisplay(1)")&&
            svc.contains("viewHeight=Math.round(clusterSize.y*0.65f)")&&
            svc.contains("FLAG_NOT_TOUCHABLE")&&
            svc.contains("main.removeCallbacks(timeout)")&&
            svc.contains("captureDisplay.release()")&&
            svc.contains("projection.stop()")&&
            svc.contains("cluster.dismiss()")&&
            svc.contains("stopForeground(STOP_FOREGROUND_REMOVE)")&&
            svc.contains("START_NOT_STICKY"),
            "20s foreground projection, callback and complete release");
        yes(manifest.contains("android:name=\".BridgeProjectionLabActivity\"")&&
            manifest.contains("android:name=\".BridgeProjectionLabService\"")&&
            manifest.contains("android:foregroundServiceType=\"mediaProjection\"")&&
            manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")&&
            manifest.contains("android:exported=\"false\""),
            "Non-exported consent Activity, isolated mediaProjection FGS");
        for(String forbidden:new String[]{
            "am force-stop","pm clear","am stack","am display move-stack",
            "FLAG_ACTIVITY_CLEAR_TASK","new MediaRecorder(","new MediaCodec(",
            "HttpURLConnection","Socket(","setType(","TYPE_APPLICATION_OVERLAY",
            "MediaProjectionConfig","MANAGE_MEDIA_PROJECTION","captureAudio"
        }){
            yes(!activity.contains(forbidden)&&!svc.contains(forbidden),
                "Capture lab must not mutate OEM, record, phone home or move tasks: "+forbidden);
        }
        System.out.println("PASS consent-only 20s local Display0->Display1 copy, no Waze tasks");
    }
}
