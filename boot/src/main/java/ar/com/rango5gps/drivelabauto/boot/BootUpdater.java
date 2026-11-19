package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** In-place first-party Boot updater, independent of legacy Receiver.
 * Only official stable release, exact asset SHA-256, app ID and Android
 * signing certificate accepted. No shell, no WebADB, no silent install.
 */
final class BootUpdater {
    private static final String PACKAGE="ar.com.rango5gps.drivelabauto.boot";
    private static final String RELEASE=
        "https://api.github.com/repos/bruceferra/drivelab-auto-standalone/releases/latest";
    private static final String DOWNLOAD_BASE=
        "https://github.com/bruceferra/drivelab-auto-standalone/releases/download/";
    private static final int META_MAX=256_000;
    private static final long APK_MAX=20L*1024*1024;
    private static boolean busy;
    interface Report {void result(String s);}
    private static final class Offer {
        final String version,url,sha;
        final long size;
        Offer(String v,String u,String h,long s){
            version=v;url=u;sha=h;size=s;
        }
    }
    private BootUpdater(){}
    private static HttpURLConnection open(String url)throws Exception{
        URL target=new URL(url);
        for(int redirects=0;redirects<6;redirects++){
            if(!"https".equals(target.getProtocol())||
               !(target.getHost().equals("github.com")||
                 target.getHost().equals("api.github.com")||
                 target.getHost().equals("githubusercontent.com")||
                 target.getHost().endsWith(".githubusercontent.com")))
                throw new SecurityException("Fuente de actualización no autorizada");
            HttpURLConnection conn=(HttpURLConnection)target.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent","DriveLab-Boot-Independent-Updater");
            conn.setRequestProperty("Accept",
                target.getPath().contains("/releases/latest")?
                "application/vnd.github+json":"application/octet-stream");
            int status=conn.getResponseCode();
            if(status>=300&&status<400){
                String location=conn.getHeaderField("Location");
                conn.disconnect();
                if(location==null)throw new java.io.IOException("Redirección sin destino");
                target=new URL(target,location);
                continue;
            }
            if(status!=200){
                conn.disconnect();
                throw new java.io.IOException("HTTP "+status+" al consultar actualización");
            }
            return conn;
        }
        throw new SecurityException("Demasiadas redirecciones");
    }
    private static Offer offer()throws Exception {
        byte[] payload;
        HttpURLConnection conn=open(RELEASE);
        try(InputStream in=conn.getInputStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))>=0){
                if(out.size()+n>META_MAX)
                    throw new SecurityException("Metadatos demasiado grandes");
                out.write(buffer,0,n);
            }
            payload=out.toByteArray();
        }finally{conn.disconnect();}
        JSONObject release=new JSONObject(
            new String(payload,java.nio.charset.StandardCharsets.UTF_8));
        if(release.optBoolean("draft")||release.optBoolean("prerelease"))
            throw new SecurityException("Sólo se permiten versiones estables");
        String tag=release.getString("tag_name");
        if(!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+"))
            throw new SecurityException("Tag no autorizado");
        JSONArray assets=release.getJSONArray("assets");
        Offer found=null;
        for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.getJSONObject(i);
            String filename=asset.optString("name","");
            if(!filename.matches("DriveLab-Boot-v[0-9]+\\.[0-9]+\\.[0-9]+\\.apk"))
                continue;
            String version=filename.substring(
                "DriveLab-Boot-v".length(),filename.length()-4);
            String url=asset.getString("browser_download_url");
            String digest=asset.optString("digest","");
            long size=asset.optLong("size",0);
            if(!url.equals(DOWNLOAD_BASE+tag+"/"+filename)||
               !digest.matches("sha256:[a-fA-F0-9]{64}")||
               size<1024||size>APK_MAX||found!=null)
                throw new SecurityException("Release Boot no verificable");
            found=new Offer(version,url,
                digest.substring(7).toLowerCase(Locale.ROOT),size);
        }
        if(found==null)
            throw new java.io.IOException("No hay APK Boot en la release estable");
        return found;
    }
    private static String hex(byte[] bytes){
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static byte[] cert(PackageInfo pkg){
        if(pkg==null||pkg.signingInfo==null)
            throw new SecurityException("APK sin certificado verificable");
        android.content.pm.Signature[] signatures=
            pkg.signingInfo.getApkContentsSigners();
        if(signatures==null||signatures.length!=1)
            throw new SecurityException("Firmas Android ambiguas");
        return signatures[0].toByteArray();
    }
    private static File download(Activity a,Offer item)throws Exception{
        File dir=new File(a.getCacheDir(),"boot_update");
        if(!dir.isDirectory()&&!dir.mkdirs())
            throw new java.io.IOException("Sin caché privada de actualización");
        File partial=new File(dir,"boot.part");
        File ready=new File(dir,"DriveLab-Boot-update.apk");
        if(partial.exists())partial.delete();
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        HttpURLConnection conn=open(item.url);
        long size=0;
        try(InputStream in=conn.getInputStream();
            FileOutputStream out=new FileOutputStream(partial)){
            byte[] data=new byte[32768];int n;
            while((n=in.read(data))!=-1){
                size+=n;
                if(size>APK_MAX||size>item.size)
                    throw new SecurityException("APK supera el tamaño aprobado");
                digest.update(data,0,n);
                out.write(data,0,n);
            }
            out.getFD().sync();
        }finally{conn.disconnect();}
        if(size!=item.size||!item.sha.equals(hex(digest.digest())))
            throw new SecurityException("SHA-256 o tamaño de Boot incorrecto");
        PackageManager pm=a.getPackageManager();
        PackageInfo candidate=pm.getPackageArchiveInfo(
            partial.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo current=pm.getPackageInfo(
            PACKAGE,PackageManager.GET_SIGNING_CERTIFICATES);
        if(candidate==null||!PACKAGE.equals(candidate.packageName)||
           !item.version.equals(candidate.versionName)||
           candidate.getLongVersionCode()<=current.getLongVersionCode()||
           !Arrays.equals(cert(candidate),cert(current)))
            throw new SecurityException(
                "La APK no es una actualización firmada de este Boot");
        if(ready.exists())ready.delete();
        if(!partial.renameTo(ready))
            throw new java.io.IOException("No se pudo guardar actualización verificada");
        return ready;
    }
    static void check(Activity a,Report status){
        if(busy){status.result("Actualizador ocupado");return;}
        busy=true;
        status.result("Buscando actualización oficial de Boot…");
        new Thread(()->{
            try{
                Offer item=offer();
                a.runOnUiThread(()->{
                    if(a.isFinishing()||a.isDestroyed()){busy=false;return;}
                    new AlertDialog.Builder(a)
                        .setTitle("ACTUALIZAR BOOT & RECEIVER")
                        .setMessage("Versión oficial: "+item.version+
                            "\nSólo instala la aplicación Boot. "+
                            "Android pedirá confirmación. No se desinstalan "+
                            "apps, mapas, cuentas ni configuraciones. "+
                            "¿Descargar y verificar?")
                        .setNegativeButton("CANCELAR",
                            (d,w)->busy=false)
                        .setPositiveButton("DESCARGAR",(d,w)->{
                            status.result("Descargando APK y verificando firma…");
                            new Thread(()->{
                                try{
                                    File apk=download(a,item);
                                    a.runOnUiThread(()->{
                                        busy=false;
                                        try{
                                            Uri uri=FileProvider.getUriForFile(a,
                                                PACKAGE+".updatefiles",apk);
                                            Intent install=new Intent(
                                                Intent.ACTION_INSTALL_PACKAGE);
                                            install.setData(uri);
                                            install.addFlags(
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                            install.putExtra(
                                                Intent.EXTRA_NOT_UNKNOWN_SOURCE,true);
                                            a.startActivity(install);
                                            status.result("APK verificada; "+
                                                "Android debe confirmar instalación");
                                        }catch(Exception error){
                                            status.result("Android bloqueó "+
                                                "el instalador: "+error.getMessage());
                                        }
                                    });
                                }catch(Exception error){
                                    a.runOnUiThread(()->{
                                        busy=false;
                                        status.result("Actualización rechazada: "+
                                            error.getClass().getSimpleName()+
                                            ": "+error.getMessage());
                                    });
                                }
                            },"Boot-Own-Verified-Apk").start();
                        }).show();
                });
            }catch(Exception error){
                a.runOnUiThread(()->{
                    busy=false;
                    status.result("Sin actualización verificada: "+
                        error.getClass().getSimpleName()+
                        ": "+error.getMessage());
                });
            }
        },"Boot-Release-Check").start();
    }
}
