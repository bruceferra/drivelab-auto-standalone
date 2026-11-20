package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Port of the working DriveLab Auto Receiver T1BootInstaller.commitSession()
 * /openFileInstaller() (same Android PackageInstaller contract, adapted ONLY
 * for Bridge's self-update package and FileProvider).
 *
 * The source of truth for the verified APK is BootUpdater.verifiedCache().
 * The owner must authorize installation in the native Android prompt.
 */
final class BridgeNativeInstaller {
    private BridgeNativeInstaller(){}

    static void commitSession(Activity a,File file,BootUpdater.Report reporter)
            throws Exception {
        // Never accept an arbitrary APK supplied by an outside Activity.
        if(!BootUpdater.verifiedCache(a).getCanonicalFile()
            .equals(file.getCanonicalFile()))
            throw new SecurityException("Instalación no corresponde a la APK verificada");
        PackageInstaller installer=a.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(a.getPackageName());
        params.setSize(file.length());
        if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(
            PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        int id=installer.createSession(params);
        boolean committed=false;
        try(PackageInstaller.Session session=installer.openSession(id)){
            try(InputStream in=new FileInputStream(file);
                OutputStream out=session.openWrite("base.apk",0,file.length())){
                byte[] buf=new byte[16384];int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
                session.fsync(out);
            }
            Intent result=new Intent(BridgeInstallResultReceiver.ACTION);
            result.setClass(a,BridgeInstallResultReceiver.class);
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent callback=PendingIntent.getBroadcast(a,id,result,flags);
            BootUpdater.record(a,"Sesión Android "+id+
                " iniciada. Esperando confirmación del sistema.");
            report(a,reporter,"Android recibió la sesión "+id+
                "; aguardando confirmación de instalación.");
            session.commit(callback.getIntentSender());
            committed=true;
        }finally{if(!committed)installer.abandonSession(id);}
    }

    static void openFileInstaller(Activity a,File file,
            BootUpdater.Report reporter) {
        if(a.isFinishing()||a.isDestroyed())return;
        try{
            if(!BootUpdater.verifiedCache(a).getCanonicalFile()
                .equals(file.getCanonicalFile()))
                throw new SecurityException("La APK dejó de estar verificada");
        }catch(Exception failure){
            report(a,reporter,"La APK no pasó la verificación final: "+
                failure.getClass().getSimpleName());return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(a,
                a.getPackageName()+".updatefiles",file);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("DriveLab Bridge",uri));
            try{
                a.startActivity(intent);
                report(a,reporter,"Se abrió solicitud del instalador Android; "+
                    "si la ROM la oculta, el archivo sigue guardado.");
            }catch(Exception first){
                intent.setAction(Intent.ACTION_VIEW);
                try{
                    a.startActivity(intent);
                    report(a,reporter,"Se abrió solicitud de instalación por archivos.");
                }catch(Exception second){
                    report(a,reporter,"Android bloqueó los dos intentos de abrir "+
                        "el instalador: "+first.getClass().getSimpleName()+
                        " / "+second.getClass().getSimpleName());
                }
            }
        }catch(Exception failure){
            report(a,reporter,"Android no pudo acceder a la APK verificada: "+
                failure.getClass().getSimpleName());
        }
    }
    private static void report(Activity a,BootUpdater.Report reporter,String message){
        BootUpdater.record(a,message);
        a.runOnUiThread(()->{
            if(!a.isFinishing()&&!a.isDestroyed())reporter.result(message);
        });
    }
}
