package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression: a fullscreen DriveLab configuration task + success modal
 * must never obscure the newly arranged Freeform mosaic on Display 0.
 * A task bounds/ActivityRecord-visible success is not physical visibility.
 */
public final class T1CenterMosaicForegroundHandoffContractTest {
    static void require(boolean ok,String description){
        if(!ok)throw new AssertionError(description);
    }
    public static void main(String[] args)throws Exception {
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String ui=Files.readString(Path.of(root+"T1CenterMosaicActivity.java"));
        String engine=Files.readString(Path.of(root+"T1CenterSavedFreeformMosaic.java"));
        int callback=ui.indexOf("count,result->{");
        int next=ui.indexOf("private void testExternalFreeformOnCenter()",callback);
        String handoff=ui.substring(callback,next);
        require(ui.contains("private void showMosaicTiles()")&&
            ui.contains("moveTaskToBack(true)")&&
            ui.contains("MOSTRAR PANELES · OCULTAR CONFIGURACIÓN"),
            "Provide explicit, own-task-only way to reveal the mosaics");
        require(handoff.contains("ESTADO: VENTANAS PREPARADAS.")&&
            handoff.contains("showMosaicTiles();"),
            "On successful layout, hide fullscreen controller task");
        require(!handoff.contains("setTitle(\"RESULTADO · MOSAICO CONFIGURABLE\")")&&
            handoff.contains("setTitle(\"MOSAICO NO CONFIRMADO\")"),
            "Success must not open fullscreen modal; failures stay visible");
        require(engine.contains("ESTADO: VENTANAS PREPARADAS.")&&
            engine.contains("Android marcó visibles")&&
            engine.contains("tarea(s) anteriores de pantalla completa"),
            "Do not confuse ActivityRecord.visible with actual unobscured app pixels");
        require(engine.contains("other.id==taskIds[i]")&&
            engine.contains("tasks(c,packages[i])"),
            "Detect prior fullscreen app task by exact ID without modifying it");
        require(!engine.contains("am force-stop")&&
            !engine.contains("am stack remove ")&&
            !engine.contains("settings put"),
            "Foreground handoff must not modify the vehicle, third-party apps, or HOME");
        System.out.println("PASS mosaico foreground handoff and honest visual validation");
    }
}
