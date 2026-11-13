package ar.com.rango5gps.drivelabauto.receiver;

/** Pure JVM regression for private SOC diagnostics sent via existing QR cloud. */
public final class T1OemReportCsvTest {
    static void ok(boolean pass,String note){
        if(!pass)throw new AssertionError(note);
    }
    static void rejected(Runnable action,String why){
        try{action.run();throw new AssertionError("Expected reject: "+why);}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        String diagnostic="ETAPA_1_OK marcha P/0\n"+
            "ETAPA_2_BINDER_OK id=1041 nuevo=20\n"+
            "ETAPA_3_INCIERTA id=1041 intRaw=56 \"floatRaw=NaN\"\n";
        String csv=T1OemReportCsv.encode(diagnostic,1794590000000L);
        ok(csv.startsWith("timestamp_ms,source,report\n"+
            "1794590000000,soc_regen_oem,\""),"CSV header and source");
        ok(csv.contains("ETAPA_2_BINDER_OK id=1041 nuevo=20"),
            "1041 write acknowledgment preserved");
        ok(csv.contains("intRaw=56 \"\"floatRaw=NaN\"\""),
            "quotes must be doubled inside RFC4180 CSV cell");
        ok(csv.endsWith("\"\n"),"report closed within CSV cell");
        ok(T1OemReportCsv.cloudFilename("code_diag_20261113_173200.csv"),
            "existing server cloud filename contract");
        ok(!T1OemReportCsv.cloudFilename("code_diag_20261113_173200.txt"),
            "no arbitrary file extension");
        ok(!T1OemReportCsv.cloudFilename("../code_diag_20261113_173200.csv"),
            "no paths/escape outside private directory");
        ok(!T1OemReportCsv.cloudFilename("trip_20261113_173200.csv"),
            "diagnostic uploads are separate from trip data");
        rejected(()->T1OemReportCsv.encode("",1000),"no report");
        rejected(()->T1OemReportCsv.encode(
            "Todavía no existe ningún diagnóstico EV/HEV.",1000),"no attempts");
        rejected(()->T1OemReportCsv.encode(diagnostic,0),"no valid timestamp");
        System.out.println("OK: SOC OEM CSV direct private cloud, 1041 raw logs and safe filename");
    }
}
