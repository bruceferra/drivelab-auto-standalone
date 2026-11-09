package ar.com.rango5gps.drivelabauto.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Update checker without APK-install permissions.
 *
 * The browser / Android Downloads app owns the download and installation.
 * This app never installs packages, requests untrusted-source privileges,
 * or silently downloads an executable. The verified manifest is hosted
 * by the owner's GitHub release channel and the Android package installer
 * enforces the original app-signing certificate on upgrades.
 */
public final class Updater {
    private Updater(){}
    public interface Reporter { void report(String text); }

    private static final String OWNER=
        "https://github.com/bruceferra/drivelab-auto-standalone/";
    public static final String BASE=OWNER+"releases/latest/download/";

    public static void check(Activity activity,String app,Reporter reporter){
        if(!"sender".equals(app)&&!"receiver".equals(app)){
            reporter.report("Canal de actualización no reconocido");
            return;
        }
        final String manifest=BASE+app+".json";
        new Thread(()->{
            try{
                byte[] raw=readHttps(manifest,24000);
                JSONObject data=new JSONObject(new String(raw,StandardCharsets.UTF_8));
                String pkg=data.getString("packageName");
                long code=data.getLong("versionCode");
                String version=data.getString("versionName");
                String url=data.getString("apkUrl");
                String hash=data.getString("sha256").toLowerCase(Locale.US);
                String proper="sender".equals(app)?"Sender":"Receiver";
                String expected=OWNER+"releases/download/v"+version+
                    "/DriveLab-Auto-"+proper+"-v"+version+".apk";

                if(!pkg.equals(activity.getPackageName())||
                   !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||
                   !hash.matches("[0-9a-f]{64}")||
                   !url.equals(expected))
                    throw new SecurityException("Invalid update manifest");

                PackageInfo current=activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(),0);
                long installed=Build.VERSION.SDK_INT>=28?
                    current.getLongVersionCode():current.versionCode;
                if(code<=installed){
                    post(activity,()->reporter.report("Ya tenés la última versión"));
                    return;
                }
                post(activity,()->new AlertDialog.Builder(activity)
                    .setTitle("DriveLab Auto · Actualización "+version)
                    .setMessage("Se abrirá GitHub en el navegador para descargar "+
                        "la APK oficial. El navegador y Android gestionan "+
                        "la instalación con tu confirmación.\n\n"+
                        "SHA-256 publicado: "+hash+"\n\n"+
                        "No hace falta desactivar Play Protect.")
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("ABRIR DESCARGA",(d,w)->
                        openBrowser(activity,url,reporter))
                    .show());
            }catch(Exception error){
                post(activity,()->reporter.report(
                    "No se pudo consultar el canal HTTPS ("+
                    error.getClass().getSimpleName()+"). "+
                    "La versión instalada permanece intacta."));
            }
        },"DriveLab-Update-Check").start();
    }

    private static void openBrowser(Activity activity,String url,Reporter reporter){
        Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        try{
            activity.startActivity(browser);
            reporter.report("Abrí la descarga oficial en el navegador; "+
                "Android solicitará confirmación antes de instalar.");
        }catch(ActivityNotFoundException|SecurityException error){
            reporter.report("No se encontró un navegador habilitado para "+
                "descargar desde GitHub.");
        }
    }

    private static byte[] readHttps(String url,int max) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(9000);
        c.setReadTimeout(12000);
        c.setRequestProperty("Accept","application/json");
        try(InputStream in=c.getInputStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){
                out.write(buf,0,n);
                if(out.size()>max)throw new java.io.IOException("Manifest too large");
            }
            return out.toByteArray();
        }finally{c.disconnect();}
    }
    private static void post(Activity a,Runnable action){
        a.runOnUiThread(()->{
            if(!a.isFinishing()&&!a.isDestroyed())action.run();
        });
    }
}
