package ar.com.rango5gps.drivelabauto.common;

/** Signed release URL allowlist. Never trust a URL merely because its host
 * looks like GitHub: it must match this repo's exact expected APK asset.
 * Sender's old vX.Y.Z location remains supported by installed 0.2.19;
 * newer independent sender-vX.Y.Z releases are allowed as well.
 */
public final class UpdaterManifestUrlPolicy {
    private static final String BASE=
        "https://github.com/bruceferra/drivelab-auto-standalone/releases/download/";
    private UpdaterManifestUrlPolicy(){}
    public static boolean allowed(String app,String version,String url){
        if(url==null||version==null||
            !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))return false;
        final String proper;
        if("sender".equals(app))proper="Sender";
        else if("receiver".equals(app))proper="Receiver";
        else return false;
        String filename="DriveLab-Auto-"+proper+"-v"+version+".apk";
        String numeric=BASE+"v"+version+"/"+filename;
        if(numeric.equals(url))return true;
        return "sender".equals(app)&&
            (BASE+"sender-v"+version+"/"+filename).equals(url);
    }
}
