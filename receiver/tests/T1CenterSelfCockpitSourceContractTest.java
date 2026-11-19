package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Secondary Cockpit must never overwrite the original Receiver task. */
public final class T1CenterSelfCockpitSourceContractTest {
    static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    static String read(String file)throws Exception{
        return Files.readString(Path.of(file));
    }
    public static void main(String[] args)throws Exception{
        String dir="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String receiver=read(dir+"ReceiverActivity.java");
        String mosaic=read(dir+"T1CenterSavedFreeformMosaic.java");
        String ui=read(dir+"T1CenterMosaicActivity.java");
        String adb=read(dir+"T1LocalAdbClient.java");
        String filter=read(dir+"T1AdbPackageTaskExcerpt.java");
        check(adb.contains("shellStartOwnCockpitFreeform(Context context)")&&
            adb.contains("am start --display 0 --windowingMode 5 -f 0x18000000")&&
            adb.contains("receiver/.ReceiverActivity ")&&
            adb.contains("--ez drivelab_center_cockpit_tile true"),
            "Only an explicitly new Freeform task may launch own Receiver");
        check(receiver.contains("getBooleanExtra(")&&
            receiver.contains("drivelab_center_cockpit_tile")&&
            receiver.contains("new Intent(this,T1PremiumCockpitActivity.class)")&&
            receiver.contains("finish();"),
            "New task routes into actual Cockpit instead of closing controller");
        check(mosaic.contains("shellOwnReceiverTasks(c)")&&
            mosaic.contains("SELF.equals(pkg)?")&&
            mosaic.contains("shellStartOwnCockpitFreeform(c)")&&
            mosaic.contains("T1PremiumCockpitActivity")&&
            mosaic.contains("previous.contains(t.id)")&&
            mosaic.contains("exact(c,fresh.id,pkg)"),
            "New Cockpit is selected only by newly created exact task ID");
        check(filter.contains("ownReceiver")&&
            filter.contains("ownReceiver&&pkg.equals(")&&
            adb.contains("shellInternal(context,\"am stack list\",-1,"),
            "Own task diagnostics may not expose arbitrary OEM tasks");
        check(ui.contains("DriveLab T1 · Cockpit (segunda ventana)")&&
            ui.contains("pkg.equals(getPackageName())"),
            "Only a deliberate user selection enables own Cockpit in mosaic");
        check(!mosaic.contains("am force-stop ")&&
            !mosaic.contains("am stack remove ")&&
            !mosaic.contains("settings put"),
            "No original task or OEM shutdown allowed");
        System.out.println("PASS secondary DriveLab Cockpit new-task safety checks");
    }
}
