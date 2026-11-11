package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Owner-opt-in telemetria sync. Reads only locally-recorded TRIP CSV files,
 * uploads a restricted whitelist (never GPS, VIN, phone, or full CSV), and
 * advances a durable per-file cursor ONLY after a server acknowledgement.
 *
 * Endpoint stores private batches with RLS; it reuses the independent T1
 * diagnostic owner PIN but never embeds a PIN or server secret in this APK.
 */
final class T1TripSync {
    static final String PREFS="t1_trip_private_sync";
    static final String ENABLED="enabled",STATUS="status",SENT="rows_sent",
        LAST="last_success_ms",SECRET="encrypted_owner_pin";
    private static final String ENDPOINT=
        "https://wgqbvbzhdnkkrpkrohej.supabase.co/functions/v1/drivelab-auto-trip-upload";
    private static final String KEY_ALIAS="drivelab_receiver_trip_sync_pin_v1";
    private static final String CURSOR="cursor_";
    private static final AtomicBoolean ACTIVE=new AtomicBoolean(false);
    private static volatile long lastAttempt=0;
    private static final int BATCH=40,MAX_PER_PASS=12;

    private T1TripSync(){}
    static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry existing=store.getEntry(KEY_ALIAS,null);
        if(existing instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry)existing).getSecretKey();
        KeyGenerator g=KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
        return g.generateKey();
    }
    static void configure(Context c,String pin)throws Exception{
        if(pin==null||!pin.matches("[0-9]{8}"))
            throw new IllegalArgumentException("PIN T1 de 8 dígitos requerido");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        String encoded=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+
            Base64.encodeToString(cipher.doFinal(pin.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
        prefs(c).edit().putString(SECRET,encoded).putBoolean(ENABLED,true)
            .putString(STATUS,"Sincronización activada; pendiente de primer envío")
            .apply();
    }
    static void disable(Context c){
        prefs(c).edit().putBoolean(ENABLED,false)
            .putString(STATUS,"Sincronización pausada; CSV locales conservados").apply();
    }
    private static String readPin(Context c)throws Exception{
        String stored=prefs(c).getString(SECRET,"");
        if(stored==null||!stored.contains(":"))throw new IllegalStateException("PIN no configurado");
        String[] parts=stored.split(":",2);
        byte[] iv=Base64.decode(parts[0],Base64.NO_WRAP);
        if(iv.length!=12)throw new IllegalStateException("IV inválido");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        String pin=new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),
            StandardCharsets.UTF_8);
        if(!pin.matches("[0-9]{8}"))throw new IllegalStateException("PIN inválido");
        return pin;
    }
    static void trigger(Context c,boolean force){
        Context app=c.getApplicationContext();
        if(!prefs(app).getBoolean(ENABLED,false))return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(!force && now-lastAttempt<55000L)return;
        if(!ACTIVE.compareAndSet(false,true))return;
        lastAttempt=now;
        new Thread(()->{
            try{sendPending(app);}
            catch(Exception ex){
                prefs(app).edit().putString(STATUS,
                    "Sin conexión o envío fallido: "+ex.getClass().getSimpleName()+
                    " (se conserva todo para reintentar)").apply();
            }finally{ACTIVE.set(false);}
        },"T1-Private-Trip-Sync").start();
    }
    private static void putNumber(JSONObject obj,String key,String[] fields,int at)
        throws Exception{
        if(at>=fields.length||fields[at].isEmpty()){
            obj.put(key,JSONObject.NULL);return;
        }
        try{
            double number=Double.parseDouble(fields[at]);
            obj.put(key,Double.isFinite(number)?number:JSONObject.NULL);
        }catch(NumberFormatException ignored){obj.put(key,JSONObject.NULL);}
    }
    private static JSONObject parseRow(String csv)throws Exception{
        String[] f=csv.split(",",-1);
        if(f.length<23||!f[0].matches("[0-9]{12,15}"))
            throw new IllegalArgumentException("Fila de TRIP incompleta");
        JSONObject s=new JSONObject();
        s.put("ts",Long.parseLong(f[0]));
        putNumber(s,"gear",f,1);
        putNumber(s,"speed",f,2);
        putNumber(s,"fuel",f,3);
        putNumber(s,"socInternal",f,4);
        putNumber(s,"socDisplay",f,5);
        putNumber(s,"voltage",f,6);
        putNumber(s,"current",f,7);
        putNumber(s,"tripKm",f,16);
        putNumber(s,"hvOut",f,17);
        putNumber(s,"hvIn",f,18);
        putNumber(s,"gasProxy",f,19);
        // Added after the original 23 columns. Older TRIP logs are valid too.
        putNumber(s,"rpm",f,23);
        putNumber(s,"motorOn",f,24);
        putNumber(s,"oemElectricRolling50",f,25);
        putNumber(s,"oemFuelRolling50",f,26);
        putNumber(s,"oemElectricAverage500m",f,27);
        putNumber(s,"oemFuelAverage500m",f,28);
        putNumber(s,"rpmValid1034",f,29);
        return s;
    }
    private static JSONArray readBatch(File file,int offset)throws Exception{
        JSONArray arr=new JSONArray();
        try(BufferedReader r=new BufferedReader(new FileReader(file))){
            String line=r.readLine(); // CSV header
            if(line==null)return arr;
            for(int i=0;i<offset;i++)if(r.readLine()==null)return arr;
            while(arr.length()<BATCH&&(line=r.readLine())!=null){
                if(line.isEmpty())continue;
                arr.put(parseRow(line));
            }
        }
        return arr;
    }
    private static void post(String pin,String id,int cursor,JSONArray batch)
        throws Exception{
        JSONObject body=new JSONObject();
        body.put("tripId",id).put("rowOffset",cursor).put("samples",batch);
        byte[] raw=body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        try{
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("X-Drivelab-T1-Key",pin);
            connection.setFixedLengthStreamingMode(raw.length);
            try(OutputStream out=connection.getOutputStream()){out.write(raw);}
            int code=connection.getResponseCode();
            if(code!=200)throw new IllegalStateException("HTTP "+code);
            try(InputStream in=connection.getInputStream()){
                byte[] bytes=new byte[4096];
                int n=in.read(bytes);
                JSONObject result=new JSONObject(n>0?
                    new String(bytes,0,n,StandardCharsets.UTF_8):"{}");
                if(!result.optBoolean("ok",false)||result.optInt("rowOffset",-1)!=cursor
                        ||result.optInt("count",-1)!=batch.length())
                    throw new IllegalStateException("Confirmación incompleta");
            }
        }finally{connection.disconnect();}
    }
    private static void sendPending(Context c)throws Exception{
        final String pin=readPin(c);
        File[] files=T1TripRecorderService.tripDir(c).listFiles(
            f->f.isFile()&&f.getName().matches("trip_[0-9]{8}_[0-9]{6}\\.csv"));
        if(files==null||files.length==0){
            prefs(c).edit().putString(STATUS,"No hay viajes CSV para subir").apply();return;
        }
        Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
        int batches=0,uploaded=0;
        for(File file:files){
            String tripId=file.getName().substring(5,20);
            String cursorKey=CURSOR+tripId;
            int cursor=prefs(c).getInt(cursorKey,0);
            while(batches<MAX_PER_PASS){
                JSONArray batch=readBatch(file,cursor);
                if(batch.length()==0)break;
                post(pin,tripId,cursor,batch);
                cursor+=batch.length();uploaded+=batch.length();batches++;
                prefs(c).edit().putInt(cursorKey,cursor)
                    .putInt(SENT,prefs(c).getInt(SENT,0)+batch.length())
                    .putLong(LAST,System.currentTimeMillis())
                    .putString(STATUS,"Sincronizado "+tripId+" · "+cursor+
                        " muestras · último envío OK").commit();
            }
            if(batches>=MAX_PER_PASS)break;
        }
        if(uploaded==0)
            prefs(c).edit().putString(STATUS,"Al día: no hay nuevas muestras").apply();
    }
}
