package ar.com.rango5gps.drivelabauto.common;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UpdaterManifestUrlPolicyTest {
    private static final String BASE=
        "https://github.com/bruceferra/drivelab-auto-standalone/releases/download/";
    private static void ok(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }
    private static String url(String tag,String app,String version){
        return BASE+tag+"/DriveLab-Auto-"+app+"-v"+version+".apk";
    }
    public static void main(String[] a)throws Exception {
        // The CURRENT Sender 0.2.19 before this fix expects this exact path.
        ok(UpdaterManifestUrlPolicy.allowed("sender","0.2.20",
            url("v0.2.20","Sender","0.2.20")),
            "Legacy numeric tag must work for installed Sender 0.2.19");
        ok(UpdaterManifestUrlPolicy.allowed("sender","0.2.19",
            url("sender-v0.2.19","Sender","0.2.19")),
            "Independent Sender release tags must also be supported");
        ok(UpdaterManifestUrlPolicy.allowed("receiver","0.3.14",
            url("v0.3.14","Receiver","0.3.14")),
            "Receiver legacy releases must remain compatible");
        ok(!UpdaterManifestUrlPolicy.allowed("receiver","0.3.14",
            url("sender-v0.3.14","Receiver","0.3.14")),
            "Receiver may not accept sender tag");
        ok(!UpdaterManifestUrlPolicy.allowed("sender","0.2.20",
            url("v0.3.14","Sender","0.2.20")),
            "Sender tag must match its own version");
        ok(!UpdaterManifestUrlPolicy.allowed("sender","0.2.20",
            url("v0.2.20","Receiver","0.2.20")),
            "Sender cannot fetch Receiver package");
        ok(!UpdaterManifestUrlPolicy.allowed("sender","0.2.20",
            "https://evil.example/release/DriveLab-Auto-Sender-v0.2.20.apk"),
            "Foreign hosts forbidden");
        ok(!UpdaterManifestUrlPolicy.allowed("sender","0.2.20",
            url("v0.2.20","Sender","0.2.20")+"?redirect=1"),
            "No query parameters allowed");
        ok(!UpdaterManifestUrlPolicy.allowed("sender","0.2.20",
            url("v0.2.20","Sender","0.2.20")+"/../bad"),
            "No path manipulation allowed");
        ok(!UpdaterManifestUrlPolicy.allowed("sender","../../0.2.20",
            url("v0.2.20","Sender","0.2.20")),
            "Version traversal forbidden");
        String updater=Files.readString(Path.of(
            "common/src/main/java/ar/com/rango5gps/drivelabauto/common/Updater.java"));
        ok(updater.contains("UpdaterManifestUrlPolicy.allowed(app,version,url)"),
            "Android Updater must use strictly tested URL policy");
        ok(updater.contains("verifyArchive(a,partial,offer)")&&
            updater.contains("verifyArchive(a,file,new Offer(")&&
            updater.contains("certificateHashes(current).equals(certificateHashes(candidate))")&&
            updater.contains("SHA-256 incorrecto")&&
            updater.contains("candidate.getLongVersionCode()<=current.getLongVersionCode()"),
            "Update must retain SHA256, installed signer and newer-version safety");
        System.out.println("PASS exact updater manifest URL policy legacy and sender-v tags");
    }
}
