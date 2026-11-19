package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import ar.com.rango5gps.drivelabauto.common.Updater;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;

/**
 * Owner-initiated installation/update of the separate signed DriveLab Boot APK.
 * No USB/WebADB, shell, root or vehicle settings. Fetches SHA-256 from the
 * EXACT official GitHub release asset, verifies it on every install, then
 * requires the installed Receiver signing certificate and exact package.
 * Android's native PackageInstaller still requires explicit user approval.
 */
public final class T1BootInstaller {
    private T1BootInstaller(){}
    private static final String PKG="ar.com.rango5gps.drivelabauto.boot";
    private static final String VERSION="0.1.1";
    private static final long CODE=2L;
    private static final String OWNER=
        "https://api.github.com/repos/bruceferra/drivelab-auto-standalone/";
    private static final String RELEASE_API=OWNER+"releases/tags/v0.3.94";
    private static final String ASSET_API=OWNER+"releases/assets/";
    private static final String URL=
        "https://github.com/bruceferra/drivelab-auto-standalone/releases/download/"+
        "v0.3.94/DriveLab-Boot-v0.1.1.apk";
    private static final String EXPECTED_SHA="expected_boot_011_sha256";
    private static final String EXPECTED_SIZE="expected_boot_011_size";
    private static final long LIMIT=12L*1024*1024;
    private static final String PREFS="drivelab_boot_install";
    private static final String STATUS="last_status";
    private static final AtomicBoolean BUSY=new AtomicBoolean();

    static String status(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(STATUS,"Sin instalación solicitada");
    }
    static void state(Context context,String value){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString(STATUS,value).apply();
    }
    private static File apk(Context c){
        return new File(new File(c.getCacheDir(),"boot_install"),
            "DriveLab-Boot-v0.1.1.apk");
    }
    static boolean installed(Context c){
        try{
            PackageInfo boot=c.getPackageManager().getPackageInfo(PKG,
                PackageManager.GET_SIGNING_CERTIFICATES);
            PackageInfo receiver=c.getPackageManager().getPackageInfo(
                c.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
            return boot.getLongVersionCode()>=CODE&&
                certHashes(boot).equals(certHashes(receiver));
        }catch(Exception ignored){return false;}
    }
    static boolean cached(Context c){
        try{verify(c,apk(c));return true;}
        catch(Exception ignored){return false;}
    }

    static void prompt(Activity a,Updater.Reporter reporter){
        if(installed(a)){openBoot(a,reporter);return;}
        if(BUSY.get()){reporter.report("Instalación de DriveLab Boot en curso");return;}
        new AlertDialog.Builder(a)
            .setTitle("Instalar DriveLab Boot · sin WebADB")
            .setMessage("Descargar/actualizar DriveLab Boot 0.1.1 (aprox. 32 KB) desde la "+
                "publicación oficial. Se verificará SHA-256, paquete y "+
                "certificado idéntico al Receiver. Android pedirá confirmar "+
                "la instalación.\n\nNo reemplaza T1 Launcher, HOME ni capas OEM. "+
                "El inicio automático queda DESACTIVADO hasta que lo habilites.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("DESCARGAR E INSTALAR",
                (d,which)->downloadThenInstall(a,reporter)).show();
    }
    static void retry(Activity a,Updater.Reporter reporter){
        if(BUSY.get()){reporter.report("Ya hay una instalación en curso");return;}
        if(installed(a)){openBoot(a,reporter);return;}
        if(!BUSY.compareAndSet(false,true))return;
        new Thread(()->{
            try{
                verify(a,apk(a));
                notice(a,reporter,"DriveLab Boot verificada; abriendo Android…");
                commitSession(a,apk(a),reporter);
            }catch(Exception failure){
                if(cached(a)){
                    notice(a,reporter,"Instalador de sesión no disponible; "+
                        "probando instalador Android alternativo");
                    a.runOnUiThread(()->openFileInstaller(a,reporter));
                }else notice(a,reporter,"APK no válida: tocá INSTALAR para descargarla");
            }finally{BUSY.set(false);}
        },"DriveLab-Boot-Install-Retry").start();
    }
    static void alternate(Activity a,Updater.Reporter reporter){
        if(BUSY.get()){reporter.report("Instalador ocupado");return;}
        if(!BUSY.compareAndSet(false,true))return;
        new Thread(()->{
            try{
                verify(a,apk(a));
                a.runOnUiThread(()->openFileInstaller(a,reporter));
            }catch(Exception failure){
                notice(a,reporter,"Descarga no verificada: instalá Boot nuevamente");
            }finally{BUSY.set(false);}
        },"DriveLab-Boot-Install-Alternate").start();
    }
    static void requestPermission(Activity a,Updater.Reporter reporter){
        try{
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+a.getPackageName())));
            reporter.report("Autorizá instalaciones desde DriveLab Auto "+
                "si Android lo solicita. Después tocá REINTENTAR INSTALACIÓN BOOT.");
        }catch(Exception error){
            try{
                a.startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+a.getPackageName())));
                reporter.report("Abrí el permiso de instalación desde esta fuente "+
                    "si está disponible y después reintentá.");
            }catch(Exception ignored){
                reporter.report("La ROM no permite abrir los ajustes de instalación.");
            }
        }
    }
    static void openBoot(Activity a,Updater.Reporter reporter){
        if(!installed(a)){
            reporter.report("DriveLab Boot no está instalado con firma autorizada");
            return;
        }
        try{
            Intent launch=new Intent();
            launch.setClassName(PKG,PKG+".BootActivity");
            a.startActivity(launch);
        }catch(Exception e){
            reporter.report("Boot está instalado, pero Android no abrió su pantalla: "+
                e.getClass().getSimpleName());
        }
    }

    private static void downloadThenInstall(Activity a,Updater.Reporter reporter){
        if(!BUSY.compareAndSet(false,true)){
            reporter.report("Ya se está descargando DriveLab Boot");
            return;
        }
        state(a,"Descarga solicitada por el usuario");
        new Thread(()->{
            try{
                File target=apk(a),dir=target.getParentFile();
                if(!dir.isDirectory()&&!dir.mkdirs())
                    throw new IOException("No se pudo preparar almacenamiento privado");
                // Verify the exact release asset's immutable filename,
                // official HTTPS URL and server-published SHA-256 before
                // downloading; never accept a user-supplied URL or package.
                if(!cached(a)){
                    ReleaseAsset release=fetchReleaseAsset();
                    if(!a.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                        .edit().putString(EXPECTED_SHA,release.sha256)
                        .putLong(EXPECTED_SIZE,release.size).commit())
                        throw new IOException("No se guardó la firma de descarga");
                    File part=new File(dir,"boot-download.part");
                    Exception first=null;
                    for(String source:new String[]{URL,release.apiAssetUrl}){
                        if(part.isFile())part.delete();
                        try{
                            notice(a,reporter,"Descargando DriveLab Boot desde GitHub…");
                            transfer(source,part);
                            verify(a,part);
                            if(target.isFile()&&!target.delete())
                                throw new IOException("No se pudo renovar APK anterior");
                            if(!part.renameTo(target))
                                throw new IOException("No se pudo guardar la APK validada");
                            break;
                        }catch(IOException io){
                            first=first==null?io:first;
                            part.delete();
                            if(source.equals(release.apiAssetUrl))
                                throw new IOException("No se pudo descargar Boot de GitHub: "+
                                    first.getMessage()+"; "+io.getMessage(),io);
                        }catch(Exception invalid){
                            part.delete();
                            throw invalid; // Signature/hash failure: fail closed.
                        }
                    }
                }
                verify(a,target);
                notice(a,reporter,"APK Boot validada (SHA-256 y firma correctas)");
                try{commitSession(a,target,reporter);}
                catch(Exception noSession){
                    notice(a,reporter,"La sesión Android no abrió; probando instalador nativo…");
                    a.runOnUiThread(()->openFileInstaller(a,reporter));
                }
            }catch(Exception error){
                String detail=error.getMessage()==null?
                    error.getClass().getSimpleName():error.getMessage();
                if(detail.length()>170)detail=detail.substring(0,170);
                notice(a,reporter,"No se instaló DriveLab Boot: "+detail);
            }finally{BUSY.set(false);}
        },"DriveLab-Boot-Install-Download").start();
    }
    /** The only trusted metadata source for Boot v0.1.1.
     * GitHub asset SHA-256 is checked in addition to our own signing cert.
     */
    private static final class ReleaseAsset {
        final String sha256,apiAssetUrl;
        final long size;
        ReleaseAsset(String hash,String api,long length){
            sha256=hash;apiAssetUrl=api;size=length;
        }
    }
    private static ReleaseAsset fetchReleaseAsset()throws Exception{
        HttpURLConnection conn=openHttps(RELEASE_API);
        byte[] raw;
        try{
            if(conn.getContentLengthLong()>256000)
                throw new IOException("Respuesta de GitHub demasiado grande");
            try(InputStream in=conn.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=in.read(buffer))!=-1){
                    if(out.size()+n>256000)
                        throw new IOException("Metadatos GitHub demasiado grandes");
                    out.write(buffer,0,n);
                }
                raw=out.toByteArray();
            }
        }finally{conn.disconnect();}
        JSONObject release=new JSONObject(
            new String(raw,java.nio.charset.StandardCharsets.UTF_8));
        if(!"v0.3.94".equals(release.getString("tag_name"))||
           release.optBoolean("draft",false)||
           release.optBoolean("prerelease",false))
            throw new SecurityException("La publicación Boot no es estable");
        JSONArray assets=release.getJSONArray("assets");
        JSONObject found=null;
        for(int i=0;i<assets.length();i++){
            JSONObject a=assets.getJSONObject(i);
            if("DriveLab-Boot-v0.1.1.apk".equals(a.optString("name"))){
                if(found!=null)
                    throw new SecurityException("APK Boot duplicada en release");
                found=a;
            }
        }
        if(found==null)throw new IOException("Release sin DriveLab Boot 0.1.1");
        String digest=found.getString("digest").toLowerCase(Locale.ROOT);
        String address=found.getString("browser_download_url");
        String api=found.getString("url");
        long id=found.getLong("id");
        long size=found.getLong("size");
        if(!URL.equals(address)||id<1||!ASSET_API.concat(
                String.valueOf(id)).equals(api)||
           !digest.matches("sha256:[a-f0-9]{64}")||
           size<1024||size>LIMIT)
            throw new SecurityException("Release Boot inválida (URL/SHA/tamaño)");
        return new ReleaseAsset(digest.substring(7),api,size);
    }

    private static void transfer(String source,File destination)throws Exception{
        HttpURLConnection c=openHttps(source);
        try{
            long announced=c.getContentLengthLong();
            if(announced>LIMIT)throw new IOException("APK excede el tamaño permitido");
            String mime=c.getContentType();
            if(mime!=null&&(mime.contains("json")||mime.contains("text/html")))
                throw new IOException("GitHub no devolvió un archivo APK");
            long total=0;
            try(InputStream in=c.getInputStream();
                FileOutputStream out=new FileOutputStream(destination)){
                byte[] buf=new byte[16384];int n;
                while((n=in.read(buf))!=-1){
                    total+=n;
                    if(total>LIMIT)throw new IOException("Descarga demasiado grande");
                    out.write(buf,0,n);
                }
                out.getFD().sync();
            }
            if(total<1024)throw new IOException("APK incompleta");
        }finally{c.disconnect();}
    }
    private static HttpURLConnection openHttps(String source)throws Exception{
        URL current=new URL(source);
        for(int n=0;n<8;n++){
            String host=current.getHost().toLowerCase(Locale.ROOT);
            if(!"https".equals(current.getProtocol())||
                !(host.equals("github.com")||host.equals("api.github.com")||
                    host.endsWith(".githubusercontent.com")))
                throw new SecurityException("Origen de descarga no permitido");
            HttpURLConnection conn=(HttpURLConnection)current.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(14000);
            conn.setReadTimeout(70000);
            conn.setRequestProperty("User-Agent","DriveLab-Boot-Installer/0.3.94");
            if(host.equals("api.github.com")){
                conn.setRequestProperty("Accept",current.getPath().contains("/assets/")?
                    "application/octet-stream":"application/vnd.github+json");
                conn.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            }
            int status=conn.getResponseCode();
            if(status==200)return conn;
            if(status==301||status==302||status==303||
                status==307||status==308){
                String location=conn.getHeaderField("Location");
                if(location==null){conn.disconnect();throw new IOException("Sin redirección");}
                URL next=new URL(current,location);
                conn.disconnect();
                current=next;
                continue;
            }
            conn.disconnect();
            throw new IOException("GitHub HTTP "+status);
        }
        throw new IOException("Demasiadas redirecciones HTTPS");
    }
    private static void verify(Context c,File file)throws Exception{
        if(!file.isFile()||file.length()<1024||file.length()>LIMIT)
            throw new IOException("Archivo de Boot ausente o incompleto");
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){
            byte[] buf=new byte[16384];int n;
            while((n=in.read(buf))!=-1)md.update(buf,0,n);
        }
        android.content.SharedPreferences p=c.getSharedPreferences(
            PREFS,Context.MODE_PRIVATE);
        String expected=p.getString(EXPECTED_SHA,"");
        long expectedSize=p.getLong(EXPECTED_SIZE,0L);
        if(!expected.matches("[a-f0-9]{64}")||
           expectedSize<1024||expectedSize>LIMIT||file.length()!=expectedSize)
            throw new SecurityException(
                "La descarga no coincide con el manifiesto GitHub aprobado");
        if(!expected.equals(hex(md.digest())))
            throw new SecurityException("El SHA-256 de Boot no coincide");
        try(InputStream in=new FileInputStream(file)){
            if(in.read()!=0x50||in.read()!=0x4b)
                throw new SecurityException("El archivo no es una APK");
        }
        PackageManager pm=c.getPackageManager();
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),
            PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo receiver=pm.getPackageInfo(c.getPackageName(),
            PackageManager.GET_SIGNING_CERTIFICATES);
        if(candidate==null||!PKG.equals(candidate.packageName)||
            !VERSION.equals(candidate.versionName)||
            candidate.getLongVersionCode()!=CODE)
            throw new SecurityException("Paquete o versión de Boot incorrectos");
        if(!certHashes(candidate).equals(certHashes(receiver)))
            throw new SecurityException("Boot no está firmada como este Receiver");
    }
    private static TreeSet<String> certHashes(PackageInfo info)throws Exception{
        if(info.signingInfo==null)throw new SecurityException("APK sin firma");
        Signature[] signatures=info.signingInfo.getApkContentsSigners();
        if(signatures==null||signatures.length==0)
            throw new SecurityException("Sin certificado Android");
        TreeSet<String> hashes=new TreeSet<>();
        for(Signature s:signatures)
            hashes.add(hex(MessageDigest.getInstance("SHA-256")
                .digest(s.toByteArray())));
        return hashes;
    }
    private static String hex(byte[] bytes){
        StringBuilder s=new StringBuilder();
        for(byte b:bytes)s.append(String.format(Locale.US,"%02x",b&255));
        return s.toString();
    }
    private static void commitSession(Activity a,File file,Updater.Reporter reporter)
            throws Exception{
        verify(a,file);
        PackageInstaller installer=a.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(PKG);
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
            Intent result=new Intent(T1BootInstallResultReceiver.ACTION);
            result.setClass(a,T1BootInstallResultReceiver.class);
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent callback=PendingIntent.getBroadcast(a,id,result,flags);
            state(a,"Sesión Android "+id+
                " iniciada. Esperando confirmación del sistema.");
            notice(a,reporter,"Confirmá la instalación de DriveLab Boot en Android");
            session.commit(callback.getIntentSender());
            committed=true;
        }finally{if(!committed)installer.abandonSession(id);}
    }
    private static void openFileInstaller(Activity a,Updater.Reporter reporter){
        if(a.isFinishing()||a.isDestroyed())return;
        try{verify(a,apk(a));}
        catch(Exception failure){
            reporter.report("La APK descargada no pasó verificación: "+
                failure.getClass().getSimpleName());return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(a,
                a.getPackageName()+".bootfiles",apk(a));
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("DriveLab Boot",uri));
            try{
                a.startActivity(intent);
                state(a,"Instalador Android alternativo abierto");
                reporter.report("Confirmá la instalación de DriveLab Boot");
            }catch(Exception first){
                intent.setAction(Intent.ACTION_VIEW);
                try{
                    a.startActivity(intent);
                    state(a,"Instalador Android de archivos abierto");
                    reporter.report("Confirmá la instalación de DriveLab Boot");
                }catch(Exception second){
                    state(a,"Android no abre el instalador: "+
                        first.getClass().getSimpleName()+" / "+
                        second.getClass().getSimpleName());
                    reporter.report(status(a));
                }
            }
        }catch(Exception failure){
            state(a,"Android no pudo acceder a la APK verificada: "+
                failure.getClass().getSimpleName());
            reporter.report(status(a));
        }
    }
    private static void notice(Activity a,Updater.Reporter r,String message){
        state(a,message);
        a.runOnUiThread(()->{
            if(!a.isFinishing()&&!a.isDestroyed())r.report(message);
        });
    }
}
