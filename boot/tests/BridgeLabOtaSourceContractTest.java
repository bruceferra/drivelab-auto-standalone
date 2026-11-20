package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fail-closed OTA safety contract, pure JVM; never requires real keys. */
public final class BridgeLabOtaSourceContractTest {
    private static String src(String p)throws Exception{
        return Files.readString(Path.of(p));
    }
    private static void ok(boolean condition,String what){
        if(!condition)throw new AssertionError(what);
    }
    public static void main(String[] args)throws Exception{
        String updater=src("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java");
        String nativeInstall=src(
            "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeNativeInstaller.java");
        String gradle=src("boot/build.gradle");
        String manifest=src("boot/src/main/AndroidManifest.xml");
        String workflow=src(".github/workflows/publish-bridge-lab-ota.yml");
        ok(updater.contains("LAB_PACKAGE=PACKAGE+\".lab\"")&&
           updater.contains("bridge-lab-ota/bridge-lab.json")&&
           updater.contains("LAB_PACKAGE.equals(a.getPackageName())")&&
           updater.contains("lab?labOffer():offer()")&&
           updater.contains("item.code!=-1&&currentCode>=item.code")&&
           updater.contains("LAB_PACKAGE.equals(pkg)")&&
           updater.contains("bridge-lab-v[0-9]+"),
           "LAB must check its own channel, package and monotonically newer version");
        ok(updater.contains("MessageDigest.getInstance(\"SHA-256\")")&&
           updater.contains("!item.sha.equals(hex(digest.digest()))")&&
           updater.contains("PackageManager.GET_SIGNING_CERTIFICATES")&&
           updater.contains("Arrays.equals(cert(candidate),cert(current))")&&
           updater.contains("candidate.getLongVersionCode()!=item.code")&&
           updater.contains("!self.equals(candidate.packageName)")&&
           nativeInstall.contains("a.getPackageName()+\".updatefiles\"")&&
           nativeInstall.contains("Intent.ACTION_INSTALL_PACKAGE")&&
           nativeInstall.contains("PackageInstaller.SessionParams.MODE_FULL_INSTALL")&&
           updater.contains("BridgeNativeInstaller.commitSession(a,file,status)")&&
           !updater.contains("Runtime.getRuntime().exec(")&&
           !updater.contains("pm clear")&&
           !updater.contains("am force-stop"),
           "No ADB: require intact APK, same signer and Android user confirmation");
        ok(gradle.contains("applicationIdSuffix '.lab'")&&
           gradle.contains("lab {")&&
           gradle.contains("initWith release")&&
           gradle.contains("versionNameSuffix '-lab.8'")&&
           gradle.contains("versionCode 12")&&
           gradle.contains("assembleLab")&&
           manifest.contains("android:authorities=\"${applicationId}.updatefiles\"")&&
           manifest.contains("android:allowBackup=\"false\""),
           "LAB must be separately packaged, persistently signed and keep data private");
        ok(workflow.contains("RECEIVER_KEYSTORE_B64")&&
           workflow.contains(":boot:assembleLab")&&
           workflow.contains("oldcert")&&
           workflow.contains("test \"$oldcert\" = \"$newcert\"")&&
           workflow.contains("--prerelease")&&
           workflow.contains("bridge-lab-ota")&&
           workflow.contains("Point isolated OTA manifest to published signed asset LAST")&&
           workflow.contains("gh release create bridge-lab-v8"),
           "Permanent LAB signing, signer proof, separate prerelease and manifest must publish atomically");
        System.out.println("PASS separate permanent-signed LAB OTA, verified package/cert/hash and Android install consent");
    }
}
