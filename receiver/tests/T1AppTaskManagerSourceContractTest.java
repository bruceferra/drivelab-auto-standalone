package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression: a user-visible, explicit task manager, never an unguarded
 * ADB "kill everything" command in an Android Automotive head unit.
 */
public final class T1AppTaskManagerSourceContractTest {
    static void ck(boolean condition,String what){
        if(!condition)throw new AssertionError(what);
    }
    static String read(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    public static void main(String[] args)throws Exception{
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String manager=read(root+"T1AppTaskManagerActivity.java"),
            adb=read(root+"T1LocalAdbClient.java"),
            user=read(root+"T1CenterUserPackagePolicy.java"),
            mosaic=read(root+"T1CenterSavedFreeformMosaic.java"),
            home=read(root+"ReceiverActivity.java"),
            screen=read(root+"T1CenterMosaicActivity.java"),
            manifest=read("receiver/src/main/AndroidManifest.xml");
        ck(manifest.contains(".T1AppTaskManagerActivity")&&
            manifest.contains("android:exported=\"false\""),
            "Manager activity must be internal only");
        ck(home.contains("APPS ABIERTAS · CERRAR / LIMPIAR MEMORIA")&&
            screen.contains("APPS ABIERTAS · REINICIAR MOSAICO / CERRAR TODAS"),
            "Manager discoverable from Home and Mosaic");
        ck(manager.contains("ACTUALIZAR LISTA")&&
            manager.contains("REINICIAR MOSAICO · CERRAR SESIONES")&&
            manager.contains("FORZAR DETENCIÓN · TODAS LAS APPS PERMITIDAS")&&
            manager.contains("FORZAR DETENCIÓN · " ),
            "One, all and managed-mosaic reset controls");
        ck(manager.contains("setNegativeButton(\"CANCELAR\",null)")&&
            manager.contains("setPositiveButton(\"DETENER TODAS\"")&&
            manager.contains("setPositiveButton(\"REINICIAR\"")&&
            manager.contains("setPositiveButton(\"DETENER\""),
            "Destructive actions require explicit user confirmation");
        ck(manager.contains("T1CenterTaskInventory.parse(")&&
            manager.contains("T1LocalAdbClient.shellTaskInventory(app)")&&
            manager.contains("ActivityManager.MemoryInfo")&&
            manager.contains("memory.availMem"),
            "Use read-only bounded task list and real Android available RAM");
        ck(adb.contains("shellStopApprovedUserApp(Context context,String pkg)")&&
            adb.contains("verifyCenterUserApp(context,pkg)")&&
            adb.contains("T1AppStopPolicy.allowPackage(pkg)")&&
            adb.contains("pm.resolveActivity(homeQuery,0)")&&
            adb.contains("pm.queryIntentActivities(homeQuery,0)")&&
            adb.contains("T1CenterTaskInventory.canStop(selected)")&&
            adb.contains("confirmedUserStop&&command.matches("),
            "Force-stop requires verified user launcher, OEM, HOME and all-display guards");
        ck(manager.contains("shellStopApprovedUserApp(")&&
            manager.contains("T1CenterSavedFreeformMosaic.forget(app,pkg)")&&
            mosaic.contains("static void forget(Context context,String pkg)")&&
            mosaic.contains("static int rememberedTaskId(Context context,String pkg)"),
            "Stop invalidates stale saved task IDs before restart");
        ck(manager.contains("ActivityManager.AppTask")&&
            manager.contains("task.finishAndRemoveTask()")&&
            manager.contains("info.taskId==id")&&
            manager.contains("id==getTaskId()")&&
            manager.contains("T1PremiumCockpitActivity"),
            "Can remove only exact second app-owned Cockpit task without stopping own process");
        ck(!manager.contains("pm clear")&&
            !manager.contains("kill -9")&&
            !manager.contains("settings put")&&
            !manager.contains("am force-stop ")&&
            !manager.contains("am stack remove "),
            "No unguarded shell kill, data deletion, or global settings mutations");
        ck(user.contains("!pkg.startsWith(\"com.jetour.\")"),
            "OEM guard remains enabled");
        System.out.println("PASS safe app/task manager and saved mosaic reset contract");
    }
}
