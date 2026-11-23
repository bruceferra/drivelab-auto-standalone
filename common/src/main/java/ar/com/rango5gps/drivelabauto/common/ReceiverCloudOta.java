package ar.com.rango5gps.drivelabauto.common;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Optional Cloud v2 lookup for DriveLab AUTO.
 *
 * Fails closed on an invalid release. NEVER replaces the original OTA source:
 * the legacy signed GitHub updater remains a fallback until a signed bridge
 * has been installed and the public Cloud proxy has been verified.
 *
 * Device enrollment uses a separate Cloud v2 Bearer token provisioned
 * explicitly to this device and wrapped with AndroidKeyStore AES-GCM.
 * Never use a GitHub credential or the unrelated T1 telemetry/QR token.
 */
public final class ReceiverCloudOta {
    private static final String PREFS="drivelab_auto_cloud_ota_v1";
    private static final String KEY_ALIAS="drivelab-auto-ota-device-v1";
    private static final String ENCRYPTED="encrypted_token";
    private static final String IV="encrypted_iv";
    private static final int MAX_JSON=16*1024;
    private ReceiverCloudOta(){}

    public static final class Release {
        public final String versionName,url,sha256,signerSha256;
        public final long versionCode,bytes;
        private Release(String version,String url,String hash,String signer,
                long code,long size){
            versionName=version;this.url=url;sha256=hash;
            signerSha256=signer;versionCode=code;bytes=size;
        }
    }

    private static SharedPreferences prefs(Context a){
        return a.getApplicationContext().getSharedPreferences(
            PREFS,Context.MODE_PRIVATE);
    }

    private static SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if(store.containsAlias(KEY_ALIAS))
            return (SecretKey)store.getKey(KEY_ALIAS,null);
        if(!create)return null;
        KeyGenerator generator=KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }

    /** Called only by a future explicit authenticated device-enrollment UI.
     * Do not store an OTA token or a device credential in app source code. */
    public static void enroll(Activity a,String token)throws Exception{
        if(!ReceiverCloudOtaPolicy.PACKAGE.equals(a.getPackageName())||
            !ReceiverCloudOtaPolicy.isValidToken(token))
            throw new SecurityException("Invalid AUTO Cloud enrollment");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key(true));
        byte[] iv=cipher.getIV();
        byte[] sealed=cipher.doFinal(token.getBytes(StandardCharsets.US_ASCII));
        if(!prefs(a).edit()
            .putString(IV,Base64.getEncoder().encodeToString(iv))
            .putString(ENCRYPTED,Base64.getEncoder().encodeToString(sealed))
            .commit())throw new IOException("Could not persist Cloud enrollment");
    }

    public static boolean isEnrolled(Activity a){
        return prefs(a).contains(IV)&&prefs(a).contains(ENCRYPTED);
    }

    public static void revoke(Activity a){
        prefs(a).edit().remove(IV).remove(ENCRYPTED).apply();
    }

    private static String token(Activity a)throws Exception{
        if(!isEnrolled(a))return null;
        SecretKey secret=key(false);
        if(secret==null)throw new SecurityException("Cloud device key unavailable");
        byte[] iv=Base64.getDecoder().decode(prefs(a).getString(IV,""));
        byte[] ciphertext=Base64.getDecoder().decode(
            prefs(a).getString(ENCRYPTED,""));
        if(iv.length!=12||ciphertext.length<17||ciphertext.length>1024)
            throw new SecurityException("Cloud credential metadata invalid");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,secret,new GCMParameterSpec(128,iv));
        String value=new String(cipher.doFinal(ciphertext),
            StandardCharsets.US_ASCII);
        if(!ReceiverCloudOtaPolicy.isValidToken(value))
            throw new SecurityException("Cloud device token invalid");
        return value;
    }

    /** Returns null when Cloud isn't enrolled or no release was published.
     * Network outages throw IOException so Updater can retain GitHub fallback.
     * Invalid releases throw SecurityException and MUST NOT be installed.
     */
    public static Release lookup(Activity a)throws Exception{
        if(!ReceiverCloudOtaPolicy.PACKAGE.equals(a.getPackageName()))
            throw new SecurityException("Wrong package for DriveLab Cloud AUTO");
        String bearer=token(a);
        if(bearer==null)return null;
        URL api=new URL(ReceiverCloudOtaPolicy.LOOKUP_URL);
        HttpURLConnection conn=(HttpURLConnection)api.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(9000);
        conn.setUseCaches(false);
        conn.setRequestProperty("Cache-Control","no-store");
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Authorization","Bearer "+bearer);
        final byte[] bytes;
        try{
            if(conn.getResponseCode()!=200)
                throw new IOException("Cloud OTA HTTP "+conn.getResponseCode());
            if(conn.getContentLengthLong()>MAX_JSON)
                throw new SecurityException("Cloud OTA catalog too large");
            try(InputStream in=conn.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[2048];int n;
                while((n=in.read(buf))!=-1){
                    if(out.size()+n>MAX_JSON)
                        throw new SecurityException("Cloud OTA catalog too large");
                    out.write(buf,0,n);
                }
                bytes=out.toByteArray();
            }
        }finally{conn.disconnect();}
        JSONObject payload=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!payload.getBoolean("ok")||
            !"auto".equals(payload.getString("channel"))||
            !ReceiverCloudOtaPolicy.PACKAGE.equals(
                payload.getString("packageName")))
            throw new SecurityException("Cloud OTA channel identity mismatch");
        if(!payload.getBoolean("available")){
            if(!"not_published".equals(payload.getString("reason"))||
                payload.has("release"))
                throw new SecurityException("Cloud OTA unpublished state invalid");
            return null;
        }
        JSONObject release=payload.getJSONObject("release");
        if(release.length()!=7)
            throw new SecurityException("Cloud OTA release metadata invalid");
        String pkg=release.getString("packageName");
        String version=release.getString("versionName");
        long code=release.getLong("versionCode");
        String hash=release.getString("sha256");
        String signer=release.getString("signerSha256");
        String url=release.getString("url");
        long size=release.getLong("bytes");
        PackageInfo installed=a.getPackageManager().getPackageInfo(
            a.getPackageName(),0);
        if(code<=installed.getLongVersionCode())return null;
        if(!ReceiverCloudOtaPolicy.isValidOffer(pkg,version,code,size,
             hash,signer,url,installed.getLongVersionCode()))
            throw new SecurityException("Cloud OTA release failed package/signature policy");
        return new Release(version,url,hash,signer,code,size);
    }
}
