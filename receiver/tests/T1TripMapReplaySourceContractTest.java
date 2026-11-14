package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1TripMapReplaySourceContractTest {
    private static void ok(boolean v,String message){
        if(!v)throw new AssertionError(message);
    }
    private static String source(String file)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",file));
    }
    public static void main(String[] args)throws Exception{
        String replay=source("T1TripMapReplay.java");
        String history=source("T1TripMapAnalysisActivity.java");
        String osm=source("T1OfflineRoadMap.java");
        String optimizer=source("T1OptimizedModeActivity.java");
        String cockpit=source("T1PremiumCockpitActivity.java");
        String service=source("T1OptimizedAutoService.java");
        String manifest=Files.readString(Path.of("receiver/src/main/AndroidManifest.xml"));
        ok(replay.contains("gps_lat")&&replay.contains("gps_lon")&&
            replay.contains("gps_accuracy_m")&&
            replay.contains("gps_fix_age_ms")&&
            replay.contains("MAX_GAP_MS")&&
            replay.contains("elapsed*0.06+25"),
            "Replay must reject stale, inaccurate and teleporting historical GPS fixes");
        ok(replay.contains("new BufferedReader(new FileReader(source))")&&
            !replay.contains("FileOutputStream(source")&&
            !replay.contains("new FileWriter(source")&&
            replay.contains("No cambia modos EV/HEV"),
            "Never modify original trip CSV or vehicle power modes");
        ok(replay.contains("T1TripMapPercentages stats=new T1TripMapPercentages()")&&
            replay.contains("stats.add(length,kinds)")&&
            replay.contains("stats.categoryMeters[i][c]")&&
            replay.contains("stats.sharedMeters[i][j]"),
            "Percentages and agreements must be weighted by real GPS meters");
        ok(replay.contains("T1OfflineRoadMap.roadAtHistorical(app,loc)")&&
            replay.contains("T1ShadowRoadMap.Source.OVERTURE")&&
            replay.contains("T1ShadowRoadMap.Source.IGN")&&
            osm.contains("roadAtHistoricalDataset")&&
            osm.contains("roadAtDatasetInternal(roadDb,here,source,false)")&&
            osm.contains("roadAtDatasetInternal(roadDb,here,source,true)"),
            "All three maps on identical historical fixes; original live GPS freshness preserved");
        ok(!replay.contains("startForegroundService(")&&
            !replay.contains("transact(")&&
            !replay.contains("http")&&
            !replay.contains("openConnection(")&&
            service.contains("T1OfflineRoadMap.roadAt(this,gps)"),
            "Trip comparison must be fully offline and isolated from OEM writes");
        ok(replay.contains("new File(app.getFilesDir(),\"t1_trip_map_reports\")")&&
            !replay.contains("getLatitude()+")&&
            !replay.contains("getLongitude()+"),
            "Only save aggregate report, no coordinates or uploads");
        ok(history.contains("T1TripRecorderService.tripDir(this)")&&
            history.contains("T1JourneyStore.history(this)")&&
            history.contains("T1TripMapReplay.analyze(")&&
            history.contains("AtomicBoolean busy"),
            "Historical whole-day/individual trip UI, asynchronous replay and single job");
        ok(optimizer.contains("ANALIZAR VIAJE GRABADO")&&
            cockpit.contains("ANALIZAR VIAJE")&&
            manifest.contains("android:name=\".T1TripMapAnalysisActivity\""),
            "Discoverable from optimizer and cockpit; declared Android Activity");
        System.out.println("PASS historical 3-map percentage replay, read-only OEM isolation, UI entry points");
    }
}
