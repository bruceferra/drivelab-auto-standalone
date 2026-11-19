package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

public final class T1CenterSavedFreeformMosaicSourceContractTest {
    static void ck(boolean v,String message){if(!v)throw new AssertionError(message);}
    static String read(String f)throws Exception{return Files.readString(Path.of(f));}
    public static void main(String[] args)throws Exception{
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String apply=read(root+"T1CenterSavedFreeformMosaic.java");
        String ui=read(root+"T1CenterMosaicActivity.java");
        String adb=read(root+"T1LocalAdbClient.java");
        String parser=read(root+"T1AdbPackageTaskExcerpt.java");
        ck(ui.contains("APLICAR MOSAICO GUARDADO · FREEFORM REAL")&&
            ui.contains("T1CenterSavedFreeformMosaic.apply(this,requested,desired,")&&
            ui.contains("last_saved_freeform")&&
            ui.contains("T1CenterMosaicGeometry.layout("),
            "Must launch saved actual 2/3 tile geometry from explicit UI button");
        ck(ui.contains("T1CenterUserPackagePolicy.allowed(pkg)")&&
            ui.contains("ApplicationInfo.FLAG_SYSTEM")&&
            ui.contains("ApplicationInfo.FLAG_UPDATED_SYSTEM_APP"),
            "Only legitimate user-installed apps can appear in chooser");
        ck(apply.contains("count<2||count>3")&&
            apply.contains("T1CenterUserPackagePolicy.allowed(pkg)")&&
            apply.contains("FLAG_SYSTEM")&&
            apply.contains("FLAG_UPDATED_SYSTEM_APP"),
            "Mosaic validates 2 or 3 distinct non-system applications");
        ck(apply.contains("previous.contains(t.id)")&&
            apply.contains("shellStartCenterUserFreeform(c,launcher)")&&
            apply.contains("newTask")==false&&
            apply.contains("exact(c,fresh.id,pkg)"),
            "New Freeform launches must be identified by unique newly allocated task");
        ck(apply.contains("prefs.edit().putInt(\"task_\"+pkg,checked.id)")&&
            apply.contains("managed(c,managed,pkg)")&&
            apply.contains("return exact(c,id,pkg)"),
            "Only tasks created by this mosaic can be reused for a later apply");
        ck(apply.contains("T1CenterFreeformGeometry.tile(")&&
            apply.contains("visible(c,t.id,packages[i])")&&
            apply.contains("commonOffset"),
            "Require actual tile geometry, consistent Y offset and simultaneous visibility");
        ck(adb.contains("verifyCenterUserApp(context,pkg)")&&
            adb.contains("shellStartCenterUserFreeform(")&&
            adb.contains("userAppLaunch&&command.matches(")&&
            adb.contains("getLaunchIntentForPackage(pkg)"),
            "Generic ADB launch requires verified installed, non-system launcher");
        ck(parser.contains("T1CenterUserPackagePolicy.allowed(pkg)")&&
            apply.contains("shellApprovedAppTasks(c,pkg)"),
            "Dynamic package task listing must be bounded and approved");
        ck(!apply.contains("am stack remove ")&&
            !apply.contains("am force-stop ")&&
            !apply.contains("settings put")&&
            !apply.contains("am task resizeable ")&&
            !apply.contains("com.autolink.bluetooth"),
            "Daily app mosaic cannot mutate OEM tasks/global settings");
        System.out.println("PASS configurable Freeform 2/3 mosaic with task provenance and OEM exclusion");
    }
}
