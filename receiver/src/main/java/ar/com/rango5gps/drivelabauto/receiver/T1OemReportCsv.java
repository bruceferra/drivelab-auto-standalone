package ar.com.rango5gps.drivelabauto.receiver;

/**
 * OEM-only diagnostic snapshot into a single RFC4180-compatible CSV cell.
 * This preserves the last and previous readback logs without adding VIN,
 * GPS, location, credentials or new data collection.
 *
 * The existing private cloud endpoint accepts code_diag_YYYYMMDD_HHMMSS.csv.
 */
final class T1OemReportCsv {
    private T1OemReportCsv(){}

    static String encode(String report,long timeMs){
        if(report==null||report.trim().isEmpty()||
            report.startsWith("Todavía no existe"))
            throw new IllegalArgumentException(
                "No hay diagnóstico SOC para subir");
        if(timeMs<=0)
            throw new IllegalArgumentException("Fecha de captura inválida");
        // Newlines stay INSIDE the quoted report CSV cell. Original
        // OEM integer/float results are kept verbatim for debugging.
        String escaped=report.replace("\r\n","\n")
            .replace('\r','\n')
            .replace("\"","\"\"");
        return "timestamp_ms,source,report\n"+timeMs+
            ",soc_regen_oem,\""+escaped+"\"\n";
    }

    static boolean cloudFilename(String name){
        return name!=null&&name.matches(
            "code_diag_[0-9]{8}_[0-9]{6}\\.csv");
    }
}
