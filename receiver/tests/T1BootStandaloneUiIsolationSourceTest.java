package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Stop regression where Boot unexpectedly navigated into Receiver menus. */
public final class T1BootStandaloneUiIsolationSourceTest {
    private static String read(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    private static void ck(boolean check,String what){
        if(!check)throw new AssertionError(what);
    }
    public static void main(String[] args)throws Exception{
        String boot="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String receiver="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String ui=read(boot+"BootActivity.java");
        String config=read(boot+"BootConfig.java");
        String bridge=read(receiver+"T1BootMosaicBridgeReceiver.java");
        String service=read(receiver+"T1BootMosaicBridgeService.java");
        String install=read(receiver+"T1BootInstaller.java");
        String manifest=read("boot/src/main/AndroidManifest.xml");
        ck(ui.contains("CENTRAL · 0")&&
            ui.contains("CLÚSTER · 1")&&
            ui.contains("ELEGIR MAPA ")&&
            ui.contains("WAZE")&&
            ui.contains("GOOGLE MAPS")&&
            ui.contains("DRIVELAB BRIDGE")&&
            ui.contains("legacyAdvanced")&&
            ui.contains("VINCULAR POR QR"),
            "Boot must expose distinct, versioned two-tab configuration");
        ck(ui.contains("▶ PROBAR SOLO DISPLAY 1 · SIN SALIR DE BOOT")&&
            ui.contains("BootConfig.sendClusterOnly(this)")&&
            ui.contains("MOSTRAR MOSAICO · RECUPERAR VENTANAS")&&
            ui.contains("PREPARAR / CAMBIAR DISTRIBUCIÓN")&&
            ui.contains("BootConfig.sendCenterOnly(this)")&&
            !ui.contains("T1MosaicShortcutActivity")&&
            !ui.contains("startActivity(target)"),
            "Boot controls must not jump to Receiver settings or another screen");
        ck(config.contains("ACTION_CLUSTER")&&
            config.contains("cluster_only")&&
            config.contains("sendCenterOnly(Context context)")&&
            bridge.contains("ACTION_CLUSTER.equals(intent.getAction())")&&
            service.contains("if(incoming.getBooleanExtra(\"cluster_only\",false))")&&
            service.contains("T1BootClusterLayout.apply(")&&
            service.contains("return START_NOT_STICKY;"),
            "Manual Display 1 test must use signed direct service with no Display0 launch");
        ck(manifest.contains("android:taskAffinity=\"ar.com.rango5gps.drivelabauto.boot.config\"")&&
            manifest.contains("android:launchMode=\"singleTask\"")&&
            manifest.contains("android:icon=\"@drawable/ic_drivelab_boot\"")&&
            install.contains("Intent.FLAG_ACTIVITY_NEW_TASK|")&&
            install.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP|")&&
            read("boot/src/main/res/drawable/ic_drivelab_boot.xml")
                .contains("<vector"),
            "Launching Boot from Receiver must use a separate task and icon");
        ck(ui.contains("MOSAICO_RECUPERADO_SOLICITADO:")&&
            ui.contains("revealPending=false")&&
            !ui.contains("Se configuró solamente la pantalla central")&&
            ui.contains("BootConfig.revealCenter(this)"),
            "Only verified Receiver acknowledgement permits backgrounding Boot");
        System.out.println("PASS Boot independent task, tabs, Waze/Maps, isolated actions and icon");
    }
}
