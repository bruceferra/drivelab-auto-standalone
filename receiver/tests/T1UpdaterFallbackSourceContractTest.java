package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source contract: a Jetour HTTPS timeout cannot remove the independent
 * release API fallback or weaken APK identity/signing verification.
 * These static checks do not claim the vehicle network was exercised. */
public final class T1UpdaterFallbackSourceContractTest {
    static void require(boolean condition,String detail){
        if(!condition)throw new AssertionError(detail);
    }
    public static void main(String[] args)throws Exception {
        String updater=Files.readString(Path.of(
            "common/src/main/java/ar/com/rango5gps/drivelabauto/common/Updater.java"));
        String activity=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/ReceiverActivity.java"));
        require(updater.contains("https://api.github.com/repos/bruceferra/")&&
            updater.contains("latestReceiverViaApi(a)")&&
            updater.contains("latestFromManifest(a,app)")&&
            updater.contains("Consulta de versiones falló en API")&&
            updater.contains("GitHub",
            updater.lastIndexOf("github.com:")-30),
            "Failed GitHub frontends must report which version-check paths failed");
        require(updater.contains("getString(\"digest\")")&&
            updater.contains("sha256:[a-fA-F0-9]{64}")&&
            updater.contains("ASSET_API.concat(String.valueOf(id))")&&
            updater.contains("UpdaterManifestUrlPolicy.allowed(\"receiver\",version,url)")&&
            updater.contains("!a.getPackageName().equals(candidate.packageName)")&&
            updater.contains("!certificateHashes(current).equals(certificateHashes(candidate))"),
            "API asset URL, hash, package, version and signing certificate all mandatory");
        require(updater.contains("new String[]{offer.backupUrl,offer.url}")&&
            updater.contains("downloadVerifiedSource(a,sources[source],")&&
            updater.contains("catch(IOException e)")&&
            updater.contains("SHA-256 incorrecto")&&
            updater.contains("El servidor devolvió metadatos y no la APK"),
            "New APK tries two official routes, never accepting unverified data");
        require(updater.contains("return candidate.getLongVersionCode();")&&
            updater.contains(".putLong(READY_CODE,verifiedCode)")&&
            updater.contains("candidate.getLongVersionCode()<=current.getLongVersionCode()"),
            "Unknown GitHub API code resolved only from signed candidate, strictly newer");
        require(updater.contains("\"api.github.com\".equals(normalized)")&&
            updater.contains("application/octet-stream")&&
            !updater.contains("http://github.com")&&
            !updater.contains("setHostnameVerifier(")&&
            !updater.contains("TrustManager[]{"),
            "Only HTTPS official endpoints, no disabled TLS verification");
        require(updater.contains("if(\"receiver\".equals(app))")&&
            updater.contains("return latestFromManifest(a,app);")&&
            activity.contains("30L*60L*1000L")&&
            activity.contains("Updater.check(this,\"receiver\",this::report)"),
            "Sender remains on original manifest; Receiver automatic checks are throttled, manual immediate");
        System.out.println("PASS receiver API fallback, release signing, staged APK verification and throttled checks");
    }
}
