package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression: changing map inputs MUST NEVER control vehicle modes. */
public final class T1ShadowMapsSourceContractTest {
    static void ok(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    static String read(String path)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",path));
    }
    public static void main(String[] argv)throws Exception{
        String control=read("T1OptimizedAutoService.java");
        String monitor=read("T1RoadShadowMonitor.java");
        String map=read("T1ShadowRoadMap.java");
        String offline=read("T1OfflineRoadMap.java");
        String ui=read("T1OptimizedModeActivity.java");
        String toolbar=read("T1GlobalToolbar.java");
        String voice=read("T1VoiceRouteActivity.java");
        ok(control.contains("T1OfflineRoadMap.roadAt(this,gps)")&&
            control.contains("T1RoadShadowMonitor.observe(this,gps,found,osmLookupMs)"),
            "OSM controls road mode, shadow receives same GPS fix");
        ok(!control.contains("T1ShadowRoadMap.read(this"),
            "Never gate production OEM commands on supplemental map");
        ok(monitor.contains("Executors.newSingleThreadExecutor")&&
            monitor.contains("overture.submit")&&monitor.contains("ign.submit"),
            "Independent nonblocking Overture and IGN observers");
        ok(monitor.contains("unknown_meters")&&
            monitor.contains("lag_sum_ms")&&
            monitor.contains("lookup_ms"),
            "Report missing-road, transition lag and lookup-time metrics");
        ok(!monitor.contains("startForegroundService(")&&
            !monitor.contains("transact("),
            "No OEM vehicle writes from read-only map comparison");
        ok(map.contains("manifest.json")&&map.contains("sha256")&&
            map.contains("roads.sqlite.gz")&&map.contains("OPEN_READONLY"),
            "Validate checksum and open both geodata sources read-only");
        ok(offline.contains("roadAtDataset(")&&
            offline.contains("source+\" highway=\""),
            "Common geometry classifier preserves provenance");
        ok(ui.contains("DESCARGAR OVERTURE + IGN")&&
            ui.contains("ACTUALIZAR MÉTRICAS · 3 MAPAS")&&
            ui.contains("SUBIR DIAGNÓSTICO 3 MAPAS"),
            "Install, audit and upload comparison via existing QR");
        ok(toolbar.contains("regenIndicator=readOnlyIndicator(header")&&
            toolbar.contains("batteryIndicator=readOnlyIndicator(header")&&
            toolbar.contains("socTargetIndicator=readOnlyIndicator(header")&&
            toolbar.contains("snapshot.regen1046")&&
            toolbar.contains("snapshot.socTargetInt"),
            "Permanent first-row indicators show actual OEM readbacks");
        ok(voice.contains("T1VoiceMath.calculate(phrase)")&&
            voice.contains("T1VoiceWeather.now(")&&
            voice.contains("T1VoiceFootball.latestSanLorenzo()")&&
            voice.contains("T1VoiceNews.headlines(")&&
            voice.contains("T1VoiceIntentPolicy.routeDestination(phrase)"),
            "Navigation unchanged, local math and online information routed");
        ok(Files.exists(Path.of("tools/build_shadow_maps.py")),
            "Offline map package builder and SHA256 manifests exist");
        System.out.println("PASS three concurrent shadow roads, OEM-safe dashboard, voice bundle");
    }
}