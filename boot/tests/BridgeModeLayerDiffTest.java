package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression for read-only Jetour MODE / maps layer distinction. */
public final class BridgeModeLayerDiffTest {
    private static void check(boolean value,String explanation){
        if(!value)throw new AssertionError(explanation);
    }
    private static String file(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    public static void main(String[] args)throws Exception{
        String hidden=""+
            "Window #0 Window{ab21ff u0 com.waze/com.waze.MainActivity}:\n"+
            " mDisplayId=1\n"+
            " mAttrs=WM.LayoutParams{type=1} mBaseLayer=101\n"+
            " isOnScreen=true\n"+
            "Window #1 Window{abcdff u0 com.google.android.apps.maps/.MapsActivity}:\n"+
            " mDisplayId=1\n"+
            " mAttrs type=1 mBaseLayer=101\n"+
            " isVisible=true\n"+
            "Window #2 Window{fbc123 u0 com.jetour.mode.media.MODE}:\n"+
            " mDisplayId=1\n"+
            " mAttrs type=2038 mBaseLayer=150\n"+
            " isOnScreen=true\n"+
            "Window #3 Window{abcd35 u0 com.spotify.music/Player}:\n"+
            " mDisplayId=0\n"+
            " mAttrs type=1 mBaseLayer=50\n"+
            " isOnScreen=true\n";
        String shown=""+
            "Window #0 Window{ab21ff u0 com.waze/com.waze.MainActivity}:\n"+
            " mDisplayId=1\n"+
            " mAttrs type=1 mBaseLayer=120\n"+
            " isOnScreen=true\n"+
            "Window #1 Window{abcdff u0 com.google.android.apps.maps/.MapsActivity}:\n"+
            " mDisplayId=1\n"+
            " mAttrs type=1 mBaseLayer=120\n"+
            " isOnScreen=true\n";
        String surfaces=""+
            "+ Layer 1: name=com.jetour.mode.media.MODE z=150\n"+
            "+ Layer 2: name=com.waze z=110\n"+
            "+ Layer 3: name=com.google.android.apps.maps z=110\n";
        BridgeModeLayerDiff.Snapshot a=BridgeModeLayerDiff.summarize(
            "TAPADO",hidden,"Task{13 com.waze}\nTask{18 com.google.android.apps.maps}",
            surfaces);
        BridgeModeLayerDiff.Snapshot b=BridgeModeLayerDiff.summarize(
            "VISIBLE",shown,"Task{13 com.waze}\nTask{18 com.google.android.apps.maps}",
            surfaces);
        check(a.waze==1&&a.maps==1&&a.mode==1&&a.spotify==0,
            "Must count only Display 1; do not confuse Spotify from Display 0");
        check(b.waze==1&&b.maps==1&&b.mode==0,
            "Must detect MODE surface vanished while two maps remain");
        String diff=BridgeModeLayerDiff.compare(a,b);
        check(diff.contains("VENTANA RETIRADA")&&
              diff.contains("VENTANA CON CAMBIO DE CAPA")&&
              diff.contains("MODE ocultó o retiró")&&
              !diff.contains("com.jetour.mode.media.MODE")&&
              !diff.contains("ab21ff"),
            "Compare safe redacted window identities and actual transitions");
        check(BridgeModeLayerDiff.compare(null,b).contains("dos capturas"),
            "Single diagnostic cannot prove overlay origin");
        String reader=file(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeAdbReadOnly.java");
        String ui=file(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeLayersActivity.java");
        check(reader.contains("dumpsys window windows")&&
              reader.contains("dumpsys SurfaceFlinger")&&
              reader.contains("dumpsys activity activities")&&
              reader.contains("Solo diagnóstico sin modificaciones")&&
              !reader.contains("\"pm clear")&&
              !reader.contains("\"am force-stop")&&
              !reader.contains("\"am task resize")&&
              !reader.contains("\"settings put")&&
              !reader.contains("startActivity("),
            "Bridge ADB layers tool must never mutate apps or firmware");
        check(ui.contains("CAPTURAR CUANDO MODE TAPA MAPAS")&&
              ui.contains("CAPTURAR CUANDO LOS MAPAS SE VEN")&&
              ui.contains("compare(covered,visible)")&&
              !ui.contains("force-stop")&&
              !ui.contains("pm clear")&&
              !ui.contains("injectInputEvent"),
            "Explicit two-state owner comparison, no synthetic steering events");
        System.out.println("PASS: sanitized Display1 MODE overlay forensic comparison; read-only ADB");
    }
}