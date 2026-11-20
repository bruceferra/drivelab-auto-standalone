package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression: the owner MUST leave diagnostic Activity to show a map.
 * A/B snapshots must survive leaving Bridge, activity recreation, reboot
 * and OTA; compare must work after returning without another first capture.
 */
public final class BridgeLayerCapturePersistenceSourceTest {
    static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot",name));
    }
    static void ok(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String screen=read("BridgeLayersActivity.java");
        String store=read("BridgeLayerCaptureStore.java");
        String diff=read("BridgeModeLayerDiff.java");
        String adb=read("BridgeAdbReadOnly.java");
        ok(screen.contains("BridgeLayerCaptureStore.save(")&&
           screen.contains("getApplicationContext(),state")&&
           screen.contains("BridgeLayerCaptureStore.load(this,\"TAPADO\")")&&
           screen.contains("BridgeLayerCaptureStore.load(this,\"VISIBLE\")")&&
           screen.contains("@Override protected void onResume()")&&
           screen.contains("restoreCaptures();")&&
           screen.contains("COMPARAR AMBAS CAPTURAS")&&
           screen.contains("1 · TABLERO ORIGINAL: ")&&
           screen.contains("2 · MAPA VISIBLE: ")&&
           screen.contains("LISTO PARA COMPARAR"),
           "Snapshots must be durable and loaded both on reopen and compare");
        ok(store.contains("getSharedPreferences(PREFS,Context.MODE_PRIVATE)")&&
           store.contains("putString(snapshot.name,")&&
           store.contains("json.toString()).commit()")&&
           store.contains("new BridgeModeLayerDiff.Snapshot(phase)")&&
           store.contains("snapshot.windowState.put(")&&
           store.contains("snapshot.lines.add(")&&
           store.contains("!phase.equals(json.getString(\"phase\"))")&&
           store.contains("remove(\"TAPADO\").remove(\"VISIBLE\").commit()"),
           "A and B must persist separately, with exact stage and hash IDs");
        ok(!store.contains("BridgeAdbReadOnly.read(")&&
           !store.contains("rawWindowDump=")&&
           !store.contains("BridgeAdbReadOnly.read(")&&
           !store.contains("startActivity(")&&
           !store.contains("sendBroadcast(")&&
           !store.contains("getExternalStorage")&&
           !screen.contains("am force-stop")&&
           diff.contains("raw window titles, routes, coordinates, tokens")&&
           adb.contains("Solo diagnóstico sin modificaciones"),
           "Persist redacted summaries only; no raw dumps or OEM actions");
        System.out.println("PASS: two sanitized captures persist across exiting/reopening Bridge and compare works");
    }
}
