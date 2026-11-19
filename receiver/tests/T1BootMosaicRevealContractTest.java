package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Strong regression for pre-existing hidden Freeform panes on Jetour T1. */
public final class T1BootMosaicRevealContractTest {
    static String read(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    static void check(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception {
        String b="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String r="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String ui=read(b+"BootActivity.java");
        String config=read(b+"BootConfig.java");
        String bridge=read(r+"T1BootMosaicBridgeReceiver.java");
        String service=read(r+"T1BootMosaicBridgeService.java");
        String recover=read(r+"T1CenterBootReveal.java");
        check(ui.contains("MOSTRAR MOSAICO · RECUPERAR VENTANAS")&&
            ui.contains("PREPARAR / CAMBIAR DISTRIBUCIÓN")&&
            ui.contains("BootConfig.revealCenter(this)")&&
            ui.contains("BootConfig.sendCenterOnly(this)")&&
            !ui.contains("Se configuró solamente la pantalla central")&&
            !ui.contains("VER VENTANAS YA ABIERTAS · OCULTAR BOOT"),
            "Showing and preparing must be separate, without false confirmation");
        check(ui.contains("implements SharedPreferences.OnSharedPreferenceChangeListener")&&
            ui.contains("registerOnSharedPreferenceChangeListener(this)")&&
            ui.contains("MOSAICO_RECUPERADO_SOLICITADO:")&&
            ui.contains("revealPending=false")&&
            ui.contains("moveTaskToBack(true)")&&
            ui.contains("La conexión Sender/Receiver es independiente"),
            "Boot hides UI only upon receiver acknowledgement and separates Sender state");
        check(config.contains("ACTION_REVEAL")&&
            config.contains("static void revealCenter(Context context)")&&
            config.contains("request.putExtra(\"reveal_count\",count)")&&
            bridge.contains("ACTION_REVEAL.equals(intent.getAction())")&&
            bridge.contains("service.putExtra(\"reveal_only\""),
            "Reveal IPC must be explicit and restricted to the signed Boot package");
        check(service.contains("if(incoming.getBooleanExtra(\"reveal_only\",false))")&&
            service.contains("T1CenterBootReveal.request(this,packages,result->")&&
            service.contains("return START_NOT_STICKY;"),
            "Revealing existing panes must not enter the app-launch path");
        check(recover.contains("verifiedRememberedTask(")&&
            recover.contains("actual.display!=0")&&
            recover.contains("T1CenterMosaicViewport.disjoint(boxes)")&&
            recover.contains("am.moveTaskToFront(")&&
            recover.contains("MOSAICO_RECUPERADO_SOLICITADO:")&&
            !recover.contains("startActivity(")&&
            !recover.contains("am force-stop")&&
            !recover.contains("pm clear")&&
            !recover.contains("new Intent(")&&
            !recover.contains("T1CenterSavedFreeformMosaic.apply("),
            "Only verified owned Freeform IDs may move forward; no data loss or duplication");
        System.out.println("PASS Boot real reveal of existing center windows, confirmation and isolation");
    }
}
