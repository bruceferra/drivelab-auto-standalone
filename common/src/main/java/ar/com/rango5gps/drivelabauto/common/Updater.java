package ar.com.rango5gps.drivelabauto.common;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.Locale;
import org.json.JSONObject;

/** User-initiated, SHA-256 validated HTTPS APK updates, installed by Android. */
public final class Updater {
    private Updater(){}
    public interface Reporter {void report(String text);}
    public static final String BASE=
        "https://github.com/bruceferra/drivelab-auto-standalone/releases/latest/download/";
    public static void check(Activity activity,String app,Reporter reporter){
        final String manifest=BASE+app+".json";
        new Thread(()->{
            try{
                byte[] contents=readHttps(manifest,24000);
                JSONObject data=new JSONObject(new String(contents,java.nio.charset.StandardCharsets.UTF_8));
                String packageName=data.getString("packageName");
                long code=data.getLong("versionCode");
                String url=data.getString("apkUrl"),hash=data.getString("sha256").toLowerCase(Locale.US);
                if(!packageName.equals(activity.getPackageName())||
                    !hash.matches("[0-9a-f]{64}")||
                    !url.startsWith("https://github.com/bruceferra/drivelab-auto-standalone/releases/download/")||
                    !url.endsWith(".apk"))throw new SecurityException("Invalid update manifest");
                PackageInfo installedInfo=activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(),0);
                long installed=Build.VERSION.SDK_INT>=28?
                    installedInfo.getLongVersionCode():installedInfo.versionCode;
                if(code<=installed){post(activity,()->reporter.report("Ya tenés la última versión"));return;}
                post(activity,()->new AlertDialog.Builder(activity)
                    .setTitle("DriveLab Auto · Actualización "+data.optString("versionName"))
                    .setMessage("Se descargará la APK firmada y Android pedirá tu confirmación.")
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("DESCARGAR",(d,w)->download(activity,url,hash,code,reporter))
                    .show());
            }catch(Exception error){
                post(activity,()->reporter.report("No se pudo consultar el canal HTTPS ("+
                    error.getClass().getSimpleName()+"). Comprobá la publicación."));
            }
        },"DriveLab-Update-Check").start();
    }
    private static void download(Activity activity,String url,String sha,long code,Reporter reporter){
        reporter.report("Descargando actualización verificada…");
        new Thread(()->{
            try{
                File folder=new File(activity.getCacheDir(),"updates");
                if(!folder.exists()&&!folder.mkdirs())throw new IOException("Cache unavailable");
                File file=new File(folder,"drivelab-update.apk");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                URL endpoint=new URL(url);
                if(!"https".equals(endpoint.getProtocol()))throw new SecurityException("HTTPS required");
                HttpURLConnection c=(HttpURLConnection)endpoint.openConnection();
                c.setConnectTimeout(10000);c.setReadTimeout(20000);
                c.setInstanceFollowRedirects(true);
                long bytes=0;
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){
                    byte[] buf=new byte[16384];int n;
                    while((n=in.read(buf))!=-1){
                        bytes+=n;
                        if(bytes>50000000)throw new IOException("Oversized APK");
                        digest.update(buf,0,n);out.write(buf,0,n);
                    }
                }finally{c.disconnect();}
                if(bytes<10000)throw new SecurityException("Invalid APK size");
                StringBuilder actual=new StringBuilder();
                for(byte b:digest.digest())actual.append(String.format(Locale.US,"%02x",b&255));
                if(!actual.toString().equals(sha))throw new SecurityException("SHA-256 mismatch");
                PackageInfo pkg=activity.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);
                if(pkg==null||!activity.getPackageName().equals(pkg.packageName)||
                    (Build.VERSION.SDK_INT>=28?pkg.getLongVersionCode():pkg.versionCode)!=code)
                    throw new SecurityException("Wrong package/version");
                post(activity,()->install(activity,file,reporter));
            }catch(Exception error){post(activity,()->reporter.report(
                "No se instaló: "+error.getClass().getSimpleName()+". La versión actual permanece intacta."));}
        },"DriveLab-Update-Download").start();
    }
    private static void install(Activity a,File file,Reporter reporter){
        if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){
            reporter.report("Android requiere autorizar instalaciones desde DriveLab Auto. Luego pulsá Actualizar nuevamente.");
            try{a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+a.getPackageName())));}catch(Exception ignored){}
            return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".fileprovider",file);
            Intent install=new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri,"application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(install);
            reporter.report("APK comprobada. Confirmá la actualización en Android.");
        }catch(Exception e){reporter.report("Android no pudo iniciar la instalación: "+e.getClass().getSimpleName());}
    }
    private static byte[] readHttps(String link,int max) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(link).openConnection();
        c.setConnectTimeout(9000);c.setReadTimeout(12000);
        c.setRequestProperty("Accept","application/octet-stream, application/json");
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>max)throw new IOException("Manifest too large");}
            return out.toByteArray();
        }finally{c.disconnect();}
    }
    private static void post(Activity a,Runnable task){a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())task.run();});}
}
