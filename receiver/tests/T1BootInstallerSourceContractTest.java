package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Contract: separate Boot install never requires WebADB or replaces OEM HOME. */
public final class T1BootInstallerSourceContractTest {
    static String file(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    static void check(boolean result,String msg){
        if(!result)throw new AssertionError(msg);
    }
    public static void main(String[] args)throws Exception {
        String prefix="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String install=file(prefix+"T1BootInstaller.java");
        String results=file(prefix+"T1BootInstallResultReceiver.java");
        String ui=file(prefix+"ReceiverActivity.java");
        String manifest=file("receiver/src/main/AndroidManifest.xml");
        String paths=file("receiver/src/main/res/xml/t1_boot_install_files.xml");
        String build=file("receiver/build.gradle");

        check(install.contains("v0.3.97/DriveLab-Boot-v0.1.3.apk")&&
            install.contains("RELEASE_API=OWNER+\"releases/tags/v0.3.97\"")&&
            install.contains("fetchReleaseAsset()")&&
            install.contains("digest.matches(\"sha256:[a-f0-9]{64}\")")&&
            install.contains("!expected.equals(hex(md.digest()))")&&
            install.contains("expectedSize>LIMIT")&&
            install.contains("!URL.equals(address)"),
            "Boot update must match the exact trusted GitHub release SHA and URL");
        check(install.contains("PackageManager.GET_SIGNING_CERTIFICATES")&&
            install.contains("certHashes(candidate).equals(certHashes(receiver))")&&
            install.contains("PKG.equals(candidate.packageName)")&&
            install.contains("candidate.getLongVersionCode()!=CODE"),
            "Do not install Boot without same Receiver signing cert and exact package");
        check(install.contains("PackageInstaller.SessionParams.MODE_FULL_INSTALL")&&
            install.contains("params.setAppPackageName(PKG)")&&
            install.contains("session.commit(callback.getIntentSender())")&&
            install.contains("FileProvider.getUriForFile(")&&
            results.contains("STATUS_PENDING_USER_ACTION")&&
            results.contains("STATUS_SUCCESS"),
            "Use native, owner-confirmed Android installer with alternate path");
        check(ui.contains("INSTALAR DRIVELAB BOOT · SIN WEBADB")&&
            ui.contains("T1BootInstaller.prompt(this,this::report)")&&
            ui.contains("REINTENTAR INSTALACIÓN BOOT")&&
            ui.contains("ABRIR DRIVELAB BOOT")&&
            ui.contains("T1BootInstaller.status(this)"),
            "Install and troubleshooting must be reachable from Receiver Inicio");
        check(manifest.contains("android:name=\".T1BootInstallResultReceiver\"")&&
            manifest.contains(".bootfiles\"")&&
            manifest.contains("android:resource=\"@xml/t1_boot_install_files\"")&&
            manifest.contains("android:name=\"ar.com.rango5gps.drivelabauto.boot\"")&&
            paths.contains("path=\"boot_install/\""),
            "Scoped read-only APK grant and Android 11 Boot visibility required");
        check(!install.contains("T1LocalAdbClient")&&
            !install.contains("Runtime.getRuntime()")&&
            !install.contains("settings put ")&&
            !install.contains("android.intent.category.HOME")&&
            build.contains("versionCode 198")&&
            build.contains("versionName '0.3.97'"),
            "Installer must not change car HOME, use ADB, or reuse old version");
        System.out.println("PASS Boot native installer SHA, certificate, package, UI, consent and OEM isolation");
    }
}
