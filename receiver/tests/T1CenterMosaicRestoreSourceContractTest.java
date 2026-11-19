package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Return to the SAME Freeform windows after leaving to OEM HOME. */
public final class T1CenterMosaicRestoreSourceContractTest {
    static void ck(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    static String source(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/"+
            name+".java"));
    }
    public static void main(String[] args)throws Exception{
        String recover=source("T1CenterMosaicRecovery");
        String saved=source("T1CenterSavedFreeformMosaic");
        String settings=source("T1CenterMosaicActivity");
        String toolbar=source("T1GlobalToolbar");
        String home=source("ReceiverActivity");
        String pin=source("T1MosaicLauncherShortcut");
        String target=source("T1MosaicShortcutActivity");
        String manifest=Files.readString(Path.of(
            "receiver/src/main/AndroidManifest.xml"));
        ck(recover.contains("verifiedRememberedTask(")&&
            recover.contains("T1CenterMosaicViewport.disjoint(windows)")&&
            recover.contains("moveTaskToFront(verified[i],0)")&&
            recover.contains("owner.moveTaskToBack(true)"),
            "Foreground recovery must validate and raise exact existing IDs");
        ck(recover.contains("ownsTile")&&
            recover.contains("id==owner.getTaskId()"),
            "Do not background an own Cockpit tile");
        ck(!recover.contains("am task resize ")&&
            !recover.contains("am force-stop ")&&
            !recover.contains("shellStartCenterUserFreeform(")&&
            !recover.contains("shellStartOwnCockpitFreeform(")&&
            !recover.contains("finishAndRemoveTask()")&&
            !recover.contains("settings put "),
            "Recovery never spawns/resizes/stops tasks or vehicle systems");
        ck(saved.contains("static T1AdbTaskParser.Task verifiedRememberedTask(")&&
            saved.contains("return exact(context,task,pkg)")&&
            saved.contains("T1CenterWindowModePolicy.isFreeform("),
            "Saved tasks require package/Display0/Freeform verification");
        ck(settings.contains("VOLVER A MIS VENTANAS · SIN REABRIR APPS")&&
            settings.contains("VOLVER A LOS PANELES · RECUPERAR TAREAS")&&
            settings.contains("CREAR ACCESO «MOSAICO» EN T1 LAUNCHER")&&
            settings.contains("T1CenterMosaicRecovery.restore(this,"),
            "UI offers restore and an optional pinned shortcut");
        ck(home.contains("▦ VOLVER AL MOSAICO · RECUPERAR VENTANAS")&&
            toolbar.contains("Button recoverMosaic=button(header,\"▦\"")&&
            toolbar.contains("this::recoverExistingMosaic")&&
            toolbar.contains("Button update=button(header,\"⟳\""),
            "Accessible from Home and fixed toolbar; updater preserved");
        ck(pin.contains("isRequestPinShortcutSupported()")&&
            pin.contains("requestPinShortcut(")&&
            pin.contains("T1MosaicShortcutActivity.class")&&
            pin.contains("setIntent(target)")&&
            pin.contains("FLAG_ACTIVITY_NEW_TASK"),
            "Launcher shortcut is opt-in, explicit and capability-checked");
        ck(target.contains("T1CenterMosaicRecovery.restore(this")&&
            target.contains("finishAndRemoveTask()"),
            "Only transient shortcut task is removed after restore");
        ck(manifest.contains("android:name=\".T1MosaicShortcutActivity\"")&&
            manifest.contains("android:taskAffinity=\"${applicationId}.restoremosaic\"")&&
            manifest.contains("android.permission.REORDER_TASKS"),
            "Independent shortcut task and REORDER_TASKS are required");
        int begin=manifest.indexOf(
            "android:name=\".T1MosaicShortcutActivity\"");
        int end=manifest.indexOf("/>",begin);
        ck(begin>=0&&end>begin&&
            !manifest.substring(begin,end).contains("LAUNCHER")&&
            !manifest.substring(begin,end).contains("MAIN"),
            "Do not register second MAIN LAUNCHER icon for T1 Launcher");
        System.out.println("PASS exact existing Freeform restore, fixed shortcut, no duplicate apps or OEM changes");
    }
}
