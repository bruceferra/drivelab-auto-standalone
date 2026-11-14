package ar.com.rango5gps.drivelabauto.sender;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.SystemClock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in, once-per-T1-A2DP-connection media wakeup experiment.
 * A short, low-volume PCM tone with AVRCP track metadata is emitted on the
 * Xiaomi, not on the Receiver. Does not play/pause Spotify or change
 * Bluetooth pairing, routes, radio modes or vehicle settings.
 *
 * This is an investigation of the owner's observed "Not assigned" car sink.
 * A2DP and AVRCP decisions are implemented by Android and the car OEM and
 * cannot be guaranteed or forced by an unprivileged Sender.
 */
final class SenderAudioBootstrap {
    static final String PREFS="sender_t1_audio_bootstrap_v1";
    static final String ENABLED="enabled";
    static final String ADDRESS="device_address";
    static final String LABEL="device_label";
    static final String STATUS="last_status";
    static final String ATTEMPT_MS="last_attempt_ms";
    private static final long COOLDOWN_MS=60000L;
    private static final int RATE=48000;
    private static final int PLAY_MS=1400;
    private SenderAudioBootstrap(){}

    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean permission(Context c){
        return Build.VERSION.SDK_INT<31||
            c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==
                PackageManager.PERMISSION_GRANTED;
    }
    static boolean configured(Context c){
        SharedPreferences p=prefs(c);
        return p.getBoolean(ENABLED,false)&&
            !p.getString(ADDRESS,"").isEmpty();
    }
    static boolean sameDevice(Context c,BluetoothDevice d){
        if(d==null||!permission(c)||!configured(c))return false;
        try{
            return prefs(c).getString(ADDRESS,"").equalsIgnoreCase(
                d.getAddress());
        }catch(SecurityException error){return false;}
    }
    static void report(Context c,String message){
        prefs(c).edit().putString(STATUS,message).apply();
    }
    /** Called from either a short goAsync A2DP receiver or the foreground UI. */
    static void prime(Context context,boolean fromVerifiedConnection){
        Context c=context.getApplicationContext();
        SharedPreferences p=prefs(c);
        if(!configured(c)||!permission(c)){
            report(c,"NO ejecutado: primero elegí T1 y autorizá Bluetooth");
            return;
        }
        long now=System.currentTimeMillis();
        long last=p.getLong(ATTEMPT_MS,0L);
        if(now-last>=0&&now-last<COOLDOWN_MS){
            report(c,"Omitido: ya se intentó hace "+
                ((now-last)/1000L)+" segundos; sin repetición automática");
            return;
        }
        p.edit().putLong(ATTEMPT_MS,now).apply();

        AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        if(am==null){
            report(c,"No hay gestor de audio Android");return;
        }
        if(am.isMusicActive()){
            report(c,"Omitido: otra reproducción multimedia ya está activa");
            return;
        }
        // A manual test may happen with the device already connected.
        // Android hides route MAC on some devices: verify the exact connected
        // A2DP profile first instead of accidentally playing to headphones.
        boolean deviceVerified=fromVerifiedConnection ||
            exactConnectedA2dp(c,p.getString(ADDRESS,""));
        if(!deviceVerified){
            report(c,"La T1 elegida no está conectada en perfil A2DP. "+
                "Comprobá que Audio multimedia esté activo en Bluetooth.");
            return;
        }
        boolean ready=false;
        // BT profile connection broadcast can arrive before the Android
        // media route is ready. Total bounded well below goAsync 10s budget.
        for(int attempt=0;attempt<5;attempt++){
            if(routeMatches(am,p.getString(ADDRESS,""),
                    deviceVerified)){ready=true;break;}
            SystemClock.sleep(450L);
        }
        if(!ready){
            report(c,"La T1 no aparece como salida A2DP verificada en Android. "+
                "No se reprodujo nada en otro dispositivo.");
            return;
        }
        AudioAttributes attrs=new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        AudioFocusRequest focus=new AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs).build();
        AudioTrack track=null;
        MediaSession session=null;
        boolean acquired=false;
        try{
            int result=am.requestAudioFocus(focus);
            acquired=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if(!acquired){
                report(c,"Otra app o Android denegó foco multimedia; sin tono");
                return;
            }
            if(am.isMusicActive()){
                report(c,"Otra reproducción ya empezó: no la interrumpo");
                return;
            }
            byte[] pcm=shortPulse();
            AudioFormat fmt=new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            track=new AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(fmt)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length).build();
            if(track.getState()!=AudioTrack.STATE_INITIALIZED)
                throw new IllegalStateException("AudioTrack no inicializado");
            int written=0;
            while(written<pcm.length){
                int n=track.write(pcm,written,pcm.length-written);
                if(n<=0)throw new IllegalStateException(
                    "AudioTrack no aceptó PCM ("+n+")");
                written+=n;
            }
            session=new MediaSession(c,"DriveLab-T1-A2DP-Wakeup");
            session.setPlaybackToLocal(attrs);
            session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE,"DriveLab · Audio T1")
                .putString(MediaMetadata.METADATA_KEY_ARTIST,"Prueba de conexión")
                .putLong(MediaMetadata.METADATA_KEY_DURATION,PLAY_MS)
                .build());
            session.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING,0L,1f)
                .build());
            session.setActive(true);
            track.play();
            SystemClock.sleep(PLAY_MS+150L);
            report(c,"Prueba A2DP enviada a "+p.getString(LABEL,"T1")+
                ": 1,4 s de audio con datos de pista. "+
                "Verificá si desapareció 'Not assigned'. "+
                "No implica confirmación del vehículo.");
        }catch(Exception ex){
            report(c,"No se pudo iniciar multimedia: "+
                ex.getClass().getSimpleName()+" · "+ex.getMessage());
        }finally{
            if(session!=null){
                try{session.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_STOPPED,0L,0f).build());}
                catch(RuntimeException ignored){}
                try{session.setActive(false);session.release();}
                catch(RuntimeException ignored){}
            }
            if(track!=null){
                try{track.pause();track.flush();track.release();}
                catch(RuntimeException ignored){}
            }
            if(acquired){
                try{am.abandonAudioFocusRequest(focus);}
                catch(RuntimeException ignored){}
            }
        }
    }
    /** Strictly verify the chosen MAC in A2DP, not merely BT phone link.
     * Bluetooth's profile proxy is asynchronous; this runs off the UI thread.
     */
    private static boolean exactConnectedA2dp(Context context,String mac){
        if(mac==null||mac.isEmpty()||!permission(context))return false;
        BluetoothManager manager=(BluetoothManager)
            context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null)return false;
        CountDownLatch latch=new CountDownLatch(1);
        AtomicBoolean match=new AtomicBoolean(false);
        BluetoothProfile.ServiceListener listener=
            new BluetoothProfile.ServiceListener(){
                @Override public void onServiceConnected(int profile,
                                                         BluetoothProfile proxy){
                    try{
                        if(profile==BluetoothProfile.A2DP){
                            for(BluetoothDevice device:proxy.getConnectedDevices())
                                if(mac.equalsIgnoreCase(device.getAddress()))
                                    match.set(true);
                        }
                    }catch(SecurityException ignored){}
                    finally{
                        try{adapter.closeProfileProxy(profile,proxy);}
                        catch(RuntimeException ignored){}
                        latch.countDown();
                    }
                }
                @Override public void onServiceDisconnected(int profile){
                    latch.countDown();
                }
            };
        try{
            if(!adapter.getProfileProxy(context,listener,BluetoothProfile.A2DP))
                return false;
            latch.await(1800L,TimeUnit.MILLISECONDS);
            return match.get();
        }catch(SecurityException | InterruptedException ex){
            return false;
        }
    }

    /** No output to unknown A2DP headphones; a user-triggered test requires
     * the actual device address, broadcasts already identify its device. */
    private static boolean routeMatches(AudioManager am,String mac,
                                        boolean fromVerifiedConnection){
        if(mac==null||mac.isEmpty())return false;
        for(AudioDeviceInfo route:
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
            if(route.getType()!=AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)continue;
            String address=route.getAddress();
            if(address!=null&&!address.isEmpty()&&
                    address.equalsIgnoreCase(mac))return true;
            if((address==null||address.isEmpty())&&fromVerifiedConnection)
                return true; // only after an A2DP event from this exact MAC
        }
        return false;
    }
    /** Audible, low-level sinusoid; silence may never activate the sink.
     * Fade edges to avoid clicks. No microphone recording or network calls. */
    private static byte[] shortPulse(){
        final int count=RATE*PLAY_MS/1000;
        byte[] data=new byte[count*2];
        for(int i=0;i<count;i++){
            double fade=Math.min(1.0,Math.min(i/(RATE*0.04),
                (count-1-i)/(RATE*0.04)));
            double sample=Math.sin(2.0*Math.PI*440*i/RATE)
                *0.022*Math.max(0,fade);
            short v=(short)Math.round(sample*32767);
            data[2*i]=(byte)(v&0xff);
            data[2*i+1]=(byte)((v>>8)&0xff);
        }
        return data;
    }
}
