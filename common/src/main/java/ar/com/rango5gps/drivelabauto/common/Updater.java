package ar.com.rango5gps.drivelabauto.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import org.json.JSONArray;
import java.util.Locale;
import java.util.TreeSet;
import org.json.JSONObject;

/**
 * In-app, explicitly user-started update: check -> download -> SHA-256 ->
 * package/version/signing certificate verification -> Android native installer.
 * No browser, GitHub Activity, silent installation or background APK execution.
 *
 * GitHub hosts signed release assets, but only direct HTTPS requests from this
 * class are made. The UI stays in DriveLab until Android asks for installation
 * confirmation, as in the existing DriveLab Uber beta updater.
 */
public final class Updater {
    private Updater() {}
    public interface Reporter { void report(String text); }

    private static final String OWNER=
        "https://github.com/bruceferra/drivelab-auto-standalone/";
    private static final String BASE=OWNER+"releases/latest/download/";
    private static final String GITHUB_API=
        "https://api.github.com/repos/bruceferra/drivelab-auto-standalone/releases/";
    private static final String RELEASE_API=GITHUB_API+"latest";
    private static final String ASSET_API=GITHUB_API+"assets/";
    private static final int MAX_RELEASE_META=256_000;
    private static final int MAX_MANIFEST=24_000;
    private static final long MAX_APK=50L*1024*1024;
    private static final String PREFS="drivelab_native_update";
    private static final String READY_SHA="ready_sha";
    private static final String READY_CODE="ready_code";
    private static final String NEED_PERMISSION="waiting_for_android_permission";
    private static final String INSTALL_STATUS="last_install_status";
    private static volatile boolean busy=false;
    private static volatile boolean permissionRecovery=false;

    /** A previous PackageInstaller error is not an update-check error.
     * When a newer APK was installed through ADB, discard obsolete cached
     * installer state before displaying the update panel. */
    public static void reconcileInstalled(Activity a){
        SharedPreferences prefs=a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);
        final long installed;
        try{
            installed=a.getPackageManager().getPackageInfo(a.getPackageName(),0)
                .getLongVersionCode();
        }catch(Exception error){return;}
        long pendingCode=prefs.getLong(READY_CODE,0L);
        if(pendingCode>installed)return;
        if(prefs.contains(INSTALL_STATUS)||prefs.contains(READY_CODE)||
           prefs.contains(READY_SHA)||prefs.getBoolean(NEED_PERMISSION,false))
            prefs.edit().remove(READY_CODE).remove(READY_SHA)
                .remove(NEED_PERMISSION).remove(INSTALL_STATUS).apply();
    }
    public static boolean hasPendingInstall(Activity a){
        reconcileInstalled(a);
        SharedPreferences prefs=a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);
        long pending=prefs.getLong(READY_CODE,0L);
        try{
            long installed=a.getPackageManager().getPackageInfo(a.getPackageName(),0)
                .getLongVersionCode();
            return pending>installed&&prefs.getString(READY_SHA,"")
                .matches("[a-f0-9]{64}")&&UpdateApkProvider.apk(a).isFile();
        }catch(Exception error){return false;}
    }
    public static String installedVersion(Activity a){
        try{
            PackageInfo p=a.getPackageManager().getPackageInfo(a.getPackageName(),0);
            return p.versionName+" · código "+p.getLongVersionCode();
        }catch(Exception error){return "no disponible";}
    }
    public static String installationStatus(Activity a){
        reconcileInstalled(a);
        return hasPendingInstall(a)?
            a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE)
                .getString(INSTALL_STATUS,"APK nueva descargada; instalación pendiente"):
            "Sin instalación pendiente";
    }

    private static final class Offer {
        final String name,url,hash,backupUrl;
        final long code;
        Offer(String version,String apkUrl,String sha,long versionCode){
            this(version,apkUrl,sha,versionCode,null);
        }
        Offer(String version,String apkUrl,String sha,long versionCode,
              String apiAssetUrl){
            name=version;url=apkUrl;hash=sha;code=versionCode;
            backupUrl=apiAssetUrl;
        }
    }

    /**
     * Search for an update when the user opens Sender or Receiver.
     * Only the manifest is fetched automatically. A download/installer is
     * started exclusively AFTER the user confirms the offer in this dialog.
     */
    public static void checkOnOpen(Activity a,String app,Reporter reporter){
        reconcileInstalled(a);
        String proper;
        if("sender".equals(app))proper="Sender";
        else if("receiver".equals(app))proper="Receiver";
        else return;
        if(!a.getPackageName().equals("ar.com.rango5gps.drivelabauto."+app)
                ||busy||permissionRecovery)return;
        busy=true;
        notify(a,reporter,"Buscando actualizaciones de "+proper+"…");
        new Thread(()->{
            try{
                Offer offer=latest(a,app,proper);
                if(offer==null){
                    notify(a,reporter,"Esta es la última versión de DriveLab Auto "+proper+".");
                    return;
                }
                notify(a,reporter,"Actualización "+offer.name+" disponible para "+proper+".");
                a.runOnUiThread(()->{
                    if(a.isFinishing()||a.isDestroyed())return;
                    // Jetour's themed dialog body previously rendered in
                    // very low-contrast grey and printed literal \\n.
                    // Explicit black-on-white message, real line break and
                    // dark colored action buttons for legibility at night.
                    android.widget.TextView messageView=
                        new android.widget.TextView(a);
                    messageView.setText("Versión nueva: "+offer.name+
                        "\n¿Querés descargarla e instalarla desde DriveLab Auto?");
                    messageView.setTextColor(android.graphics.Color.BLACK);
                    messageView.setTextSize(18f);
                    int pad=(int)(20f*a.getResources()
                        .getDisplayMetrics().density+0.5f);
                    messageView.setPadding(pad,pad,pad,pad);
                    AlertDialog dlg=new AlertDialog.Builder(a,
                        android.R.style.Theme_Material_Light_Dialog_Alert)
                        .setTitle("Actualización disponible · "+proper)
                        .setView(messageView)
                        .setNegativeButton("AHORA NO",null)
                        .setPositiveButton("DESCARGAR E INSTALAR",
                            (dialog,which)->check(a,app,reporter))
                        .create();
                    dlg.show();
                    dlg.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(0xFF425466);
                    dlg.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(0xFF006A63);
                });
            }catch(Exception ex){
                String detail=ex.getMessage()==null?
                    ex.getClass().getSimpleName():ex.getMessage();
                if(detail.length()>140)detail=detail.substring(0,140);
                notify(a,reporter,"No se pudo comprobar la actualización: "+detail);
            }finally{
                busy=false;
            }
        },"DriveLab-Update-Check-On-Open").start();
    }

    public static void check(Activity a,String app,Reporter reporter){
        reconcileInstalled(a);
        String proper;
        if("sender".equals(app))proper="Sender";
        else if("receiver".equals(app))proper="Receiver";
        else {
            reporter.report("Canal de actualización inválido");
            return;
        }
        if(!a.getPackageName().equals("ar.com.rango5gps.drivelabauto."+app)){
            reporter.report("La aplicación no corresponde a este canal");
            return;
        }
        if(busy){reporter.report("Ya se está comprobando una actualización. El resultado aparecerá aquí.");return;}
        busy=true;
        notify(a,reporter,"Buscando actualización de "+proper+"…");
        new Thread(()->{
            try{
                Offer offer=latest(a,app,proper);
                if(offer==null){
                    notify(a,reporter,"Esta es la última versión de DriveLab Auto "+proper+".");
                    a.runOnUiThread(()->{
                        if(!a.isFinishing()&&!a.isDestroyed())
                            android.widget.Toast.makeText(a,
                                "Esta es la última versión de DriveLab Auto "+proper+".",
                                android.widget.Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                notify(a,reporter,"Nueva versión "+offer.name+" · descargando dentro de la app…");
                File apk=downloadAndVerify(a,offer,reporter);
                notify(a,reporter,"Versión "+offer.name+" descargada y firmada correctamente");
                a.runOnUiThread(()->requestInstaller(a,apk,offer,reporter));
            }catch(Exception e){
                String detail=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                if(detail.length()>220)detail=detail.substring(0,220);
                String status="No se pudo actualizar: "+detail;
                notify(a,reporter,status);
                a.runOnUiThread(()->{
                    if(!a.isFinishing()&&!a.isDestroyed())
                        new AlertDialog.Builder(a).setTitle("Actualización no completada")
                            .setMessage(status+"\n\nLa aplicación instalada no se modificó.")
                            .setPositiveButton("ACEPTAR",null).show();
                });
            }finally{busy=false;}
        },"DriveLab-Native-Update").start();
    }

    /** Receiver uses GitHub's JSON release index first. Unlike
     * github.com/releases/latest/download/receiver.json, the release index
     * does not redirect through the GitHub web frontend or CDN.
     * The old signed-manifest flow remains a fallback and is unchanged for
     * Sender. Never claim "up to date" when both network paths fail. */
    private static Offer latest(Activity a,String app,String proper)throws Exception {
        if("receiver".equals(app)){
            Exception apiError;
            try{return latestReceiverViaApi(a);}
            catch(Exception failure){apiError=failure;}
            try{return latestFromManifest(a,app);}
            catch(Exception directError){
                throw new IOException("Consulta de versiones falló en API "+
                    "(api.github.com: "+shortError(apiError)+") y en GitHub "+
                    "(github.com: "+shortError(directError)+")",directError);
            }
        }
        return latestFromManifest(a,app);
    }

    private static String shortError(Exception e){
        String msg=e.getMessage();
        if(msg==null||msg.trim().isEmpty())msg=e.getClass().getSimpleName();
        if(msg.length()>65)msg=msg.substring(0,65);
        return msg.replace('\\n',' ').replace('\\r',' ');
    }

    /** Release tag, HTTPS URL, immutable asset ID and GitHub SHA-256 must all
     * match this exact repository and APK before an update is offered.
     * The archive itself still MUST match the installed signing certificate. */
    private static Offer latestReceiverViaApi(Activity a)throws Exception{
        byte[] raw=fetchBytes(RELEASE_API,MAX_RELEASE_META);
        JSONObject release=new JSONObject(new String(raw,StandardCharsets.UTF_8));
        if(release.optBoolean("draft",false)||
           release.optBoolean("prerelease",false))
            throw new SecurityException("GitHub devolvió una versión no estable");
        String tag=release.getString("tag_name");
        if(!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+"))
            throw new SecurityException("Etiqueta de actualización no válida");
        String version=tag.substring(1);
        PackageInfo installed=a.getPackageManager().getPackageInfo(
            a.getPackageName(),0);
        if(!"ar.com.rango5gps.drivelabauto.receiver".equals(a.getPackageName()))
            throw new SecurityException("Canal del Receiver incorrecto");
        String filename="DriveLab-Auto-Receiver-v"+version+".apk";
        JSONArray assets=release.getJSONArray("assets");
        JSONObject chosen=null;
        for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.getJSONObject(i);
            if(filename.equals(asset.optString("name"))){
                if(chosen!=null)throw new SecurityException("APK duplicada en GitHub");
                chosen=asset;
            }
        }
        if(chosen==null)throw new IOException("Release sin APK del Receiver");
        String url=chosen.getString("browser_download_url");
        String digest=chosen.getString("digest");
        long id=chosen.getLong("id");
        long size=chosen.getLong("size");
        String assetUrl=chosen.getString("url");
        if(id<1||size<1024||size>MAX_APK||
           !digest.matches("sha256:[a-fA-F0-9]{64}")||
           !ASSET_API.concat(String.valueOf(id)).equals(assetUrl)||
           !UpdaterManifestUrlPolicy.allowed("receiver",version,url))
            throw new SecurityException("Release no cumple verificación de origen y SHA-256");
        if(compareVersions(version,installed.versionName)<=0)return null;
        // API release index does not expose versionCode. Archive verifier
        // reads the real code after download and requires it to be NEWER.
        return new Offer(version,url,digest.substring(7).toLowerCase(Locale.US),
            -1L,assetUrl);
    }

    private static int compareVersions(String proposed,String installed){
        if(installed==null||!installed.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
            throw new IllegalArgumentException("Versión local desconocida");
        String[] next=proposed.split("\\."),old=installed.split("\\.");
        for(int i=0;i<3;i++){
            int cmp=Long.compare(Long.parseLong(next[i]),Long.parseLong(old[i]));
            if(cmp!=0)return cmp;
        }
        return 0;
    }

    private static Offer latestFromManifest(Activity a,String app)throws Exception {
        byte[] body=fetchBytes(BASE+app+".json",MAX_MANIFEST);
        JSONObject data=new JSONObject(new String(body,StandardCharsets.UTF_8));
        String pkg=data.getString("packageName");
        String version=data.getString("versionName");
        long code=data.getLong("versionCode");
        String url=data.getString("apkUrl");
        String sha=data.getString("sha256").toLowerCase(Locale.US);
        if(!a.getPackageName().equals(pkg)||
           !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||
           code<1||!sha.matches("[a-f0-9]{64}")||
           !UpdaterManifestUrlPolicy.allowed(app,version,url))
            throw new SecurityException("Manifiesto de actualización inválido");
        PackageInfo installed=a.getPackageManager().getPackageInfo(a.getPackageName(),0);
        long current=installed.getLongVersionCode();
        return code>current?new Offer(version,url,sha,code):null;
    }

    private static File downloadAndVerify(Activity a,Offer offer,Reporter reporter)
            throws Exception {
        File target=UpdateApkProvider.apk(a);
        File parent=target.getParentFile();
        if(!parent.isDirectory()&&!parent.mkdirs())
            throw new IOException("No se pudo crear la caché de actualizaciones");
        File partial=new File(parent,"drivelab-download.part");
        // On GitHub releases, github.com and api.github.com reach the same
        // asset through different frontends. A failed transfer can try the
        // other route, but every byte must still match the SHA-256 supplied
        // by GitHub and the APK signing certificate already installed.
        String[] sources=offer.backupUrl==null?
            new String[]{offer.url}:
            new String[]{offer.backupUrl,offer.url};
        IOException firstNetworkFailure=null;
        for(int source=0;source<sources.length;source++){
            partial.delete();
            try{
                notify(a,reporter,source==0?
                    "Descargando APK · canal GitHub "+(offer.backupUrl==null?
                        "directo":"API")+"…":
                    "Descarga alternativa · GitHub Releases…");
                long verifiedCode=downloadVerifiedSource(a,sources[source],
                    partial,offer,reporter);
                if(target.isFile()&&!target.delete())
                    throw new IOException("No se pudo reemplazar la descarga anterior");
                if(!partial.renameTo(target))
                    throw new IOException("No se pudo guardar la APK verificada");
                SharedPreferences p=a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);
                if(!p.edit().putString(READY_SHA,offer.hash)
                    .putLong(READY_CODE,verifiedCode).commit())
                    throw new IOException("No se pudo conservar el estado de instalación");
                return target;
            }catch(IOException e){
                partial.delete();
                if(firstNetworkFailure==null)firstNetworkFailure=e;
                if(source==sources.length-1){
                    throw new IOException("La APK no se pudo descargar desde "+
                        (sources.length==1?"GitHub":"API ni GitHub")+" ("+
                        shortError(firstNetworkFailure)+
                        (sources.length>1?"; "+shortError(e):"")+")",e);
                }
            }catch(Exception error){
                partial.delete();
                throw error; // Never bypass SHA-256 or signature failures.
            }
        }
        throw new IOException("Descarga sin canales disponibles");
    }

    private static long downloadVerifiedSource(Activity a,String url,
                   File partial,Offer offer,Reporter reporter)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        long bytes=0;
        int lastProgress=-1;
        HttpURLConnection connection=openHttps(url);
        try{
            long length=connection.getContentLengthLong();
            if(length>MAX_APK)throw new IOException("La APK supera el tamaño máximo");
            String type=connection.getContentType();
            if(type!=null&&(type.toLowerCase(Locale.ROOT).contains("text/html")||
                            type.toLowerCase(Locale.ROOT).contains("application/json")))
                throw new IOException("El servidor devolvió metadatos y no la APK");
            try(InputStream in=connection.getInputStream();
                FileOutputStream out=new FileOutputStream(partial)){
                byte[] buffer=new byte[32768];int read;
                while((read=in.read(buffer))!=-1){
                    bytes+=read;
                    if(bytes>MAX_APK)
                        throw new IOException("La APK excede el máximo permitido");
                    digest.update(buffer,0,read);
                    out.write(buffer,0,read);
                    if(length>0){
                        int progress=(int)Math.min(100,(bytes*100)/length);
                        if(progress/10!=lastProgress/10){
                            lastProgress=progress;
                            notify(a,reporter,"Descargando APK "+progress+" %");
                        }
                    }
                }
                out.getFD().sync();
            }
            if(bytes<1024)throw new IOException("Descarga de APK vacía o incompleta");
            if(!offer.hash.equals(hex(digest.digest())))
                throw new SecurityException("SHA-256 incorrecto; se canceló la instalación");
            if(!hasApkSignature(partial))
                throw new SecurityException("El servidor no entregó una APK");
            return verifyArchive(a,partial,offer);
        }finally{connection.disconnect();}
    }

    private static long verifyArchive(Activity a,File file,Offer offer)throws Exception {
        if(!file.isFile()||file.length()<1024||file.length()>MAX_APK)
            throw new SecurityException("Archivo de instalación inválido");
        if(!hasApkSignature(file))
            throw new SecurityException("El archivo no tiene firma ZIP de APK");
        PackageManager pm=a.getPackageManager();
        int flags=PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo current=pm.getPackageInfo(a.getPackageName(),flags);
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(candidate==null||!a.getPackageName().equals(candidate.packageName)||
           !offer.name.equals(candidate.versionName)||
           (offer.code>0&&candidate.getLongVersionCode()!=offer.code)||
           candidate.getLongVersionCode()<=current.getLongVersionCode())
            throw new SecurityException("APK de otro paquete o versión");
        if(!certificateHashes(current).equals(certificateHashes(candidate)))
            throw new SecurityException("La firma de la APK es distinta de la instalada");
        return candidate.getLongVersionCode();
    }

    private static TreeSet<String> certificateHashes(PackageInfo packageInfo)
            throws Exception {
        if(packageInfo.signingInfo==null)
            throw new SecurityException("Android no pudo verificar la firma");
        Signature[] signatures=packageInfo.signingInfo.getApkContentsSigners();
        if(signatures==null||signatures.length==0)
            throw new SecurityException("APK sin certificado de firma");
        TreeSet<String> hashes=new TreeSet<>();
        for(Signature signature:signatures)
            hashes.add(hex(MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())));
        return hashes;
    }

    private static void requestInstaller(Activity a,File apk,Offer offer,Reporter reporter){
        if(a.isFinishing()||a.isDestroyed())return;
        // OEM Android 11 Settings does not reliably open unknown-app-sources.
        // A standard PackageInstaller session can request confirmation directly.
        if(isT1Receiver(a)){
            installVerified(a,reporter);
            return;
        }
        if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(a)
                .setTitle("Permiso de actualización")
                .setMessage("Android requiere autorizar una vez a DriveLab Auto "+
                    "para actualizarse desde su propia pantalla. "+
                    "Concedé «Permitir desde esta fuente» y volvé a DriveLab Auto. "+
                    "La actualización ya está descargada y verificada.")
                .setNegativeButton("MÁS TARDE",null)
                .setPositiveButton("AUTORIZAR",(d,w)->{
                    try{
                        a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).edit()
                            .putBoolean(NEED_PERMISSION,true).apply();
                        a.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+a.getPackageName())));
                    }catch(Exception e){
                        notify(a,reporter,"No se pudieron abrir los permisos de Android");
                    }
                }).show();
            return;
        }
        installVerified(a,reporter);
    }

    /**
     * Called by either Activity.onResume after the one-time Android permission
     * page. Does NOT trigger installs unless the owner already downloaded and
     * explicitly authorized one in this app.
     */
    public static void onResume(Activity a,Reporter reporter){
        reconcileInstalled(a);
        if(permissionRecovery||busy)return;
        SharedPreferences p=a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);
        if(!p.getBoolean(NEED_PERMISSION,false)||
           !a.getPackageManager().canRequestPackageInstalls())return;
        permissionRecovery=true;
        p.edit().putBoolean(NEED_PERMISSION,false).apply();
        new Thread(()->{
            try{
                verifyReady(a);
                if(isT1Receiver(a))commitT1Session(a,UpdateApkProvider.apk(a),reporter);
                else a.runOnUiThread(()->openInstaller(a,reporter));
            }catch(Exception e){
                notify(a,reporter,"La APK ya no es válida. Tocá Actualizar nuevamente.");
            }finally{permissionRecovery=false;}
        },"DriveLab-Update-Permission-Resume").start();
    }

    private static void installVerified(Activity a,Reporter reporter){
        new Thread(()->{
            try{
                verifyReady(a);
                if(isT1Receiver(a))
                    commitT1Session(a,UpdateApkProvider.apk(a),reporter);
                else a.runOnUiThread(()->openInstaller(a,reporter));
            }catch(Exception error){
                notify(a,reporter,"No se pudo completar la instalación: "+
                    error.getClass().getSimpleName());
                if(isT1Receiver(a))
                    status(a,"Sesión Android no iniciada ("+
                        error.getClass().getSimpleName()+"). "+
                        "Probá el instalador alternativo o los Ajustes de Receiver.");
            }
        },"DriveLab-Verify-Install").start();
    }

    private static boolean isT1Receiver(Activity a){
        return "ar.com.rango5gps.drivelabauto.receiver".equals(a.getPackageName());
    }

    private static void status(Activity a,String message){
        a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).edit()
            .putString(INSTALL_STATUS,message).apply();
    }

    /**
     * Standard Android PackageInstaller session. No special OEM APIs or
     * silent installation: system prompts for user consent through callback.
     * The session itself may be disallowed by a restrictive Jetour firmware.
     */
    private static void commitT1Session(Activity a,File apk,Reporter reporter)
            throws Exception {
        PackageInstaller installer=a.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(a.getPackageName());
        params.setSize(apk.length());
        if(Build.VERSION.SDK_INT>=31)
            params.setRequireUserAction(
                PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        int sessionId=installer.createSession(params);
        boolean committed=false;
        try(PackageInstaller.Session session=installer.openSession(sessionId)){
            try(InputStream in=new FileInputStream(apk);
                OutputStream out=session.openWrite("base.apk",0,apk.length())){
                byte[] chunk=new byte[32768];int n;
                while((n=in.read(chunk))!=-1)out.write(chunk,0,n);
                session.fsync(out);
            }
            Intent result=new Intent("drivelab.auto.RECEIVER_INSTALL_RESULT");
            result.setClassName(a.getPackageName(),
                a.getPackageName()+".ReceiverInstallResultReceiver");
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent callback=PendingIntent.getBroadcast(a,sessionId,result,flags);
            status(a,"APK verificada. Sesión Android "+sessionId+
                " enviada; esperando autorización o resultado del sistema…");
            notify(a,reporter,"Receiver: solicitando confirmación al instalador Android…");
            session.commit(callback.getIntentSender());
            committed=true;
        }finally{
            if(!committed)installer.abandonSession(sessionId);
        }
    }

    /** Retry an already downloaded and verified APK without transferring it. */
    public static void retryInstall(Activity a,Reporter reporter){
        if(!isT1Receiver(a))return;
        if(!hasPendingInstall(a)){
            notify(a,reporter,"No hay una actualización nueva pendiente de instalar.");
            return;
        }
        notify(a,reporter,"Verificando APK descargada y reintentando instalación…");
        installVerified(a,reporter);
    }

    /** Open the normal Android app settings if the specialized page is missing. */
    public static void openReceiverSettings(Activity a,Reporter reporter){
        if(!isT1Receiver(a))return;
        a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).edit()
            .putBoolean(NEED_PERMISSION,true).apply();
        try{
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+a.getPackageName())));
            notify(a,reporter,"Abrí el permiso para instalar apps desde Receiver si "+
                "está disponible. Si no aparece, volvé y tocá REINTENTAR.");
        }catch(Exception error){
            notify(a,reporter,"Jetour no abrió Ajustes: "+
                error.getClass().getSimpleName());
        }
    }

    /** Alternate OEM package installer intent, without opening a browser. */
    public static void tryNativeIntent(Activity a,Reporter reporter){
        if(!isT1Receiver(a))return;
        if(!hasPendingInstall(a)){
            notify(a,reporter,"No hay una actualización nueva pendiente de instalar.");
            return;
        }
        new Thread(()->{
            try{
                verifyReady(a);
                a.runOnUiThread(()->openInstaller(a,reporter));
            }catch(Exception error){
                notify(a,reporter,"No hay APK verificada para instalar: "+
                    error.getClass().getSimpleName());
            }
        },"DriveLab-T1-Native-Intent").start();
    }

    private static void verifyReady(Activity a)throws Exception{
        SharedPreferences p=a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);
        String expected=p.getString(READY_SHA,"");
        long code=p.getLong(READY_CODE,0);
        if(!expected.matches("[0-9a-f]{64}")||code<=0)
            throw new SecurityException("No hay actualización autorizada");
        File file=UpdateApkProvider.apk(a);
        if(!file.isFile()||file.length()<1024)
            throw new IOException("APK no disponible");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){
            byte[] buffer=new byte[32768];int n;
            while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
        }
        if(!expected.equals(hex(digest.digest())))
            throw new SecurityException("La APK cambió después de descargarse");
        verifyArchive(a,file,code);
    }

    private static void openInstaller(Activity a,Reporter reporter){
        if(a.isFinishing()||a.isDestroyed())return;
        Uri content=UpdateApkProvider.uri(a);
        Intent install=new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setDataAndType(content,"application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.setClipData(ClipData.newRawUri("DriveLab Auto",content));
        install.putExtra(Intent.EXTRA_RETURN_RESULT,false);
        try{
            a.startActivity(install);
            notify(a,reporter,"Instalador Android solicitado. Confirmá si aparece la ventana.");
        }catch(Exception first){
            Intent fallback=new Intent(Intent.ACTION_VIEW);
            fallback.setDataAndType(content,"application/vnd.android.package-archive");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fallback.setClipData(ClipData.newRawUri("DriveLab Auto",content));
            try{
                a.startActivity(fallback);
                notify(a,reporter,"Instalador Android alternativo abierto.");
            }catch(Exception second){
                notify(a,reporter,"Android no pudo abrir el instalador ("+
                    first.getClass().getSimpleName()+" / "+
                    second.getClass().getSimpleName()+")");
            }
        }
    }

    private static boolean hasApkSignature(File file)throws IOException{
        try(InputStream in=new FileInputStream(file)){
            return in.read()==0x50 && in.read()==0x4b;
        }
    }

    private static byte[] fetchBytes(String url,int max)throws Exception {
        HttpURLConnection c=openHttps(url);
        try{
            if(c.getContentLengthLong()>max)
                throw new IOException("Manifiesto demasiado grande");
            try(InputStream in=c.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1){
                    if(out.size()+n>max)throw new IOException("Metadatos exceden el máximo");
                    out.write(buf,0,n);
                }
                return out.toByteArray();
            }
        }finally{c.disconnect();}
    }

    private static HttpURLConnection openHttps(String url)throws Exception {
        URL current=new URL(url);
        for(int attempt=0;attempt<7;attempt++){
            if(!"https".equalsIgnoreCase(current.getProtocol())||
               !allowedHost(current.getHost()))
                throw new SecurityException("La descarga salió del canal HTTPS autorizado");
            HttpURLConnection c=(HttpURLConnection)current.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(14000);
            c.setReadTimeout(70000);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control","no-cache");
            c.setRequestProperty("User-Agent","DriveLab-Auto-Updater/0.3.27");
            if("api.github.com".equalsIgnoreCase(current.getHost())){
                c.setRequestProperty("Accept",current.getPath().contains("/assets/")?
                    "application/octet-stream":"application/vnd.github+json");
                c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            }
            int status=c.getResponseCode();
            if(status==200)return c;
            if(status==301||status==302||status==303||status==307||status==308){
                String location=c.getHeaderField("Location");
                if(location==null||location.isEmpty()){
                    c.disconnect();throw new IOException("Redirección de actualización vacía");
                }
                URL next=new URL(current,location);
                c.disconnect();current=next;continue;
            }
            c.disconnect();
            throw new IOException("Error HTTP "+status+" al descargar actualización");
        }
        throw new IOException("Demasiadas redirecciones de actualización");
    }
    private static boolean allowedHost(String host){
        if(host==null)return false;
        String normalized=host.toLowerCase(Locale.ROOT);
        return "github.com".equals(normalized)||
            "api.github.com".equals(normalized)||
            normalized.endsWith(".githubusercontent.com");
    }
    private static String hex(byte[] bytes){
        StringBuilder result=new StringBuilder(bytes.length*2);
        for(byte b:bytes)result.append(String.format(Locale.US,"%02x",b&255));
        return result.toString();
    }
    private static void notify(Activity a,Reporter reporter,String message){
        a.runOnUiThread(()->{
            if(!a.isFinishing()&&!a.isDestroyed())reporter.report(message);
        });
    }
}
