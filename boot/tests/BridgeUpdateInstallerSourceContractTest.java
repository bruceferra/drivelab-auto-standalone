package ar.com.rango5gps.drivelabauto.boot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Validate Android's explicit installer consent and Jetour fallback,
 * keeping previously verified APK bytes for retry without ADB or data loss.
 * Source-level contract, not physical Android runtime verification.
 */
public final class BridgeUpdateInstallerSourceContractTest {
    static String read(String p)throws Exception{return Files.readString(Path.of(p));}
    static void check(boolean value,String description){
        if(!value)throw new AssertionError(description);
    }
    public static void main(String[] args)throws Exception{
        String root="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String updater=read(root+"BootUpdater.java");
        String status=read(root+"BridgeInstallResultReceiver.java");
        String ui=read(root+"BootActivity.java");
        String manifest=read("boot/src/main/AndroidManifest.xml");
        String files=read("boot/src/main/res/xml/bridge_update_files.xml");
        check(updater.contains("bridge-download.part.apk")&&
            updater.contains("verifyApk(a,partial,item)")&&
            updater.contains("getPackageArchiveInfo(")&&
            updater.contains("PackageManager.GET_SIGNING_CERTIFICATES")&&
            updater.contains("item.sha.equals(hex(digest.digest()))")&&
            updater.contains("Arrays.equals(cert(candidate),cert(current))")&&
            updater.contains("!self.equals(candidate.packageName)")&&
            updater.contains("candidate.getLongVersionCode()<=current.getLongVersionCode()"),
            "Staged filename and full SHA-256/Android certificate verification");
        check(updater.contains("DriveLab-Bridge-verified-update.apk")&&
            updater.contains("SharedPreferences.Editor e=")&&
            updater.contains("static void retryInstall(Activity a,Report status)")&&
            updater.contains("verifyApk(a,file,item)")&&
            updater.contains("static void openFileInstaller(Activity a,Report status)")&&
            updater.contains("PackageInstaller.SessionParams.MODE_FULL_INSTALL")&&
            updater.contains("params.setAppPackageName(a.getPackageName())")&&
            updater.contains("session.commit(result.getIntentSender())")&&
            updater.contains("Intent.ACTION_INSTALL_PACKAGE")&&
            updater.contains("Intent.ACTION_VIEW")&&
            updater.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION")&&
            updater.contains("ClipData.newRawUri(")&&
            updater.contains("INSTALAR AHORA")&&
            updater.contains("Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES"),
            "Retriable native session and explicit fallback intent, not just download");
        check(status.contains("PackageInstaller.STATUS_PENDING_USER_ACTION")&&
            status.contains("Intent.EXTRA_INTENT")&&
            status.contains("context.startActivity(confirm)")&&
            status.contains("PendingIntent.getActivity(")&&
            status.contains("INSTALAR actualización de Bridge")&&
            status.contains("STATUS_SUCCESS")&&
            status.contains("STATUS_FAILURE")&&
            manifest.contains("android:name=\".BridgeInstallResultReceiver\"")&&
            manifest.contains("android:exported=\"false\"")&&
            files.contains("path=\"boot_update/\""),
            "Android OEM consent callback must be registered and resumable");
        check(!updater.contains("canRequestPackageInstalls()")&&
            updater.contains("session.commit(result.getIntentSender())")&&
            updater.contains("installFallback(a,file,status)")&&
            updater.contains("bridge-download.part.apk")&&
            status.contains("pendingAndroidConsent=confirm")&&
            status.contains("static void confirmFromForeground(Activity activity")&&
            status.contains("activity.startActivity(confirm)")&&
            ui.contains("BridgeInstallResultReceiver.confirmFromForeground(this,"),
            "Jetour update must not pre-gate all paths on unavailable unknown-source Settings; "+
            "native pending user consent must be reopenable from foreground Bridge");
        check(ui.contains("INSTALAR / REINTENTAR APK YA DESCARGADA")&&
            ui.contains("INSTALADOR ALTERNATIVO DE ARCHIVOS")&&
            !updater.contains("pm install")&&
            !updater.contains("Runtime.getRuntime().exec")&&
            !updater.contains("am force-stop")&&
            !updater.contains("pm clear")&&
            !status.contains("pm install")&&
            !status.contains("Runtime.getRuntime()"),
            "Manual consent and no shell/ADB/uninstall as part of OTA");
        System.out.println("PASS Bridge OTA Installer: verified APK cache, Android consent, retry, file fallback");
    }
}
