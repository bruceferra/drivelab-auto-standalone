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

/** In-place first-party Bridge OTA, independent of legacy Receiver.
 * Stable Boot uses the official release. The isolated LAB package uses
 * its own immutable signed APK channel and its own pinned manifest.
 * SHA-256, package, version and signing certificate must ALL agree.
 * Never installs silently, uses ADB, or replaces the OEM launcher.
 */
final class BootUpdater {
    private static final String PACKAGE="ar.com.rango5gps.drivelabauto.boot";
    private static final String LAB_PACKAGE=PACKAGE+".lab";
    // Read only release pointer. Updated ONLY after the signed APK and
    // its signer are checked by the protected publication workflow.
    private static final String LAB_MANIFEST=
        "https://raw.githubusercontent.com/bruceferra/drivelab-auto-standalone/"+
        "bridge-lab-ota/bridge-lab.json";
    private static final String RELEASE=
        "https://api.github.com/repos/bruceferra/drivelab-auto-standalone/releases/latest";
    private static final String DOWNLOAD_BASE=
        "https://github.com/bruceferra/drivelab-auto-standalone/releases/download/";
    private static final int META_MAX=256_000;
    private static final int LAB_META_MAX=16_384;
    private static final long APK_MAX=20L*1024*1024;
    private static boolean busy;
    interface Report {void result(String s);}
    private static final class Offer {
        final String version,url,sha;
        final long size,code;
        Offer(String v,String u,String h,long s){
            this(v,u,h,s,-1);
        }
        Offer(String v,String u,String h,long s,long n){
            version=v;url=u;sha=h;size=s;code=n;
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
    /** LAB is not allowed to browse stable Boot releases. The manifest
     * is a restricted pointer, NOT an authority to install arbitrary APKs:
     * downloaded bytes and Android certificate are checked afterwards.
     */
    private static Offer labOffer()throws Exception{
        byte[] payload;
        HttpURLConnection conn=open(LAB_MANIFEST);
        try(InputStream in=conn.getInputStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){
                if(out.size()+n>LAB_META_MAX)
                    throw new SecurityException("Manifiesto LAB demasiado grande");
                out.write(buffer,0,n);
            }
            payload=out.toByteArray();
        }finally{conn.disconnect();}
        JSONObject meta=new JSONObject(
            new String(payload,java.nio.charset.StandardCharsets.UTF_8));
        String pkg=meta.getString("packageName");
        String channel=meta.getString("channel");
        String version=meta.getString("versionName");
        long code=meta.getLong("versionCode");
        String hash=meta.getString("sha256").toLowerCase(Locale.ROOT);
        String tag=meta.getString("tag");
        String url=meta.getString("apkUrl");
        long size=meta.getLong("size");
        String file="DriveLab-Bridge-LAB-v"+version+".apk";
        if(!LAB_PACKAGE.equals(pkg)||!"bridge-lab".equals(channel)||
           !version.matches("0\\.1\\.[0-9]+-lab\\.[0-9]+")||
           code<5||code>1000000||size<1024||size>APK_MAX||
           !hash.matches("[a-f0-9]{64}")||
           !tag.matches("bridge-lab-v[0-9]+")||
           !url.equals(DOWNLOAD_BASE+tag+"/"+file))
            throw new SecurityException("Manifiesto LAB inválido o ajeno");
        return new Offer(version,url,hash,size,code);
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
        String self=a.getPackageName();
        if(!PACKAGE.equals(self)&&!LAB_PACKAGE.equals(self))
            throw new SecurityException("Paquete Bridge desconocido");
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
            self,PackageManager.GET_SIGNING_CERTIFICATES);
        if(candidate==null||!self.equals(candidate.packageName)||
           !item.version.equals(candidate.versionName)||
           (item.code!=-1&&candidate.getLongVersionCode()!=item.code)||
           candidate.getLongVersionCode()<=current.getLongVersionCode())
            throw new SecurityException(
                "La APK no corresponde a una versión posterior de "+self);
        if(!Arrays.equals(cert(candidate),cert(current)))
            throw new SecurityException(
                LAB_PACKAGE.equals(self)?
                "Firma anterior de LAB distinta: la primera LAB temporal "+
                "requiere migración UNA SOLA VEZ a LAB firmada permanente.":
                "Certificado de Boot estable no coincide");
        if(ready.exists())ready.delete();
        if(!partial.renameTo(ready))
            throw new java.io.IOException("No se pudo guardar actualización verificada");
        return ready;
    }
    static void check(Activity a,Report status){
        if(busy){status.result("Actualizador ocupado");return;}
        busy=true;
        final boolean lab=LAB_PACKAGE.equals(a.getPackageName());
        status.result(lab?"Buscando actualización firmada de Bridge LAB…":
            "Buscando actualización oficial de Boot…");
        new Thread(()->{
            try{
                Offer item=lab?labOffer():offer();
                long currentCode=a.getPackageManager().getPackageInfo(
                    a.getPackageName(),0).getLongVersionCode();
                a.runOnUiThread(()->{
                    if(item.code!=-1&&currentCode>=item.code){
                        busy=false;
                        status.result("Bridge LAB al día: versión instalada "+
                            currentCode+"; última publicada "+item.code+".");
                        return;
                    }
                    if(a.isFinishing()||a.isDestroyed()){busy=false;return;}
                    new AlertDialog.Builder(a)
                        .setTitle(lab?"ACTUALIZAR BRIDGE LAB":
                            "ACTUALIZAR BOOT ESTABLE")
                        .setMessage((lab?"Versión LAB firmada: ":
                            "Versión oficial: ")+item.version+
                            "\nSólo actualiza esta aplicación. "+
                            "Android pedirá confirmación. No se desinstalan "+
                            "apps, mapas, cuentas ni configuraciones. "+
                            "¿Descargar y verificar?")
                        .setNegativeButton("CANCELAR",
                            (d,w)->busy=false)
                        .setPositiveButton("DESCARGAR",(d,w)->{
                            if(android.os.Build.VERSION.SDK_INT>=26&&
                               !a.getPackageManager().canRequestPackageInstalls()){
                                busy=false;
                                status.result("Autorizá instalar apps desde "+
                                    "Bridge y volvé a Buscar actualización.");
                                try{
                                    a.startActivity(new Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:"+a.getPackageName())));
                                }catch(Exception rejected){
                                    status.result("Android no abrió permisos "+
                                        "de instalación: "+rejected.getMessage());
                                }
                                return;
                            }
                            status.result("Descargando APK y verificando firma…");
                            new Thread(()->{
                                try{
                                    File apk=download(a,item);
                                    a.runOnUiThread(()->{
                                        busy=false;
                                        try{
                                            Uri uri=FileProvider.getUriForFile(a,
                                                a.getPackageName()+".updatefiles",apk);
                                            Intent install=new Intent(
                                                Intent.ACTION_INSTALL_PACKAGE);
                                            install.setData(uri);
                                            install.addFlags(
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                            install.putExtra(
                                                Intent.EXTRA_NOT_UNKNOWN_SOURCE,true);
                                            a.startActivity(install);
                                            status.result("APK y firma verificadas; "+
                                                "confirmá la actualización en Android");
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
