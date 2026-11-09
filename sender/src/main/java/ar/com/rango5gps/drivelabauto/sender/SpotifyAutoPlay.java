package ar.com.rango5gps.drivelabauto.sender;

import android.Manifest;
import android.app.NotificationManager;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.session.*;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.*;

/**
 * Opt-in, phone-side Spotify resume. Never starts the local T1 Spotify or opens an Activity.
 * Requires Bluetooth Connect and enabled Notification Listener access. Does not inspect
 * notification contents and never controls a media session from another package.
 */
public final class SpotifyAutoPlay {
    private SpotifyAutoPlay() {}
    static final String SPOTIFY = "com.spotify.music";
    static final String PREFS = "drivelab_spotify";
    private static final String ENABLED="enabled", ADDRESS="bt_address", LABEL="bt_label";
    private static final String LAST="last_status", LAST_ATTEMPT="last_attempt";
    private static final long DEBOUNCE_MS=90000L;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return prefs(c).getBoolean(ENABLED,false);}
    static String label(Context c){return prefs(c).getString(LABEL,"Sin seleccionar");}
    static String status(Context c){return prefs(c).getString(LAST,"Sin pruebas");}
    static void report(Context c,String status){prefs(c).edit().putString(LAST,status).apply();}
    static void setEnabled(Context c,boolean active){
        prefs(c).edit().putBoolean(ENABLED,active).apply();
        report(c,active?"Activado · esperando Bluetooth Music de T1":"Desactivado");
        if(active)checkConnected(c,"activación",false,null);
    }
    static void select(Context c,BluetoothDevice d){
        try{
            String name=d.getName();
            String address=d.getAddress();
            if(address==null||address.isEmpty())throw new IllegalStateException("Sin identificador");
            prefs(c).edit().putString(ADDRESS,address)
                .putString(LABEL,name==null||name.trim().isEmpty()?address:name)
                .remove(LAST_ATTEMPT).apply();
            report(c,"Dispositivo seleccionado · "+label(c));
        }catch(SecurityException ex){report(c,"Concedé el permiso Dispositivos cercanos");}
    }
    static boolean hasBluetoothPermission(Context c){
        return Build.VERSION.SDK_INT<31 ||
            c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    }
    static boolean hasListenerAccess(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        try{return nm!=null&&nm.isNotificationListenerAccessGranted(
            new ComponentName(c,SpotifySessionListener.class));
        }catch(RuntimeException ex){return false;}
    }
    static List<BluetoothDevice> bonded(Context c){
        if(!hasBluetoothPermission(c))return Collections.emptyList();
        try{
            BluetoothManager bm=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=bm==null?null:bm.getAdapter();
            if(adapter==null||adapter.getBondedDevices()==null)return Collections.emptyList();
            ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
            devices.sort((a,b)->{
                String x=a.getName(),y=b.getName();
                return (x==null?"":x).compareToIgnoreCase(y==null?"":y);
            });
            return devices;
        }catch(Exception ex){return Collections.emptyList();}
    }
    static void onBluetoothEvent(Context c,Intent intent,Runnable finished){
        try{
            if(!enabled(c)||!hasBluetoothPermission(c)){if(finished!=null)finished.run();return;}
            BluetoothDevice d;
            if(Build.VERSION.SDK_INT>=33)
                d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
            else d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String selected=prefs(c).getString(ADDRESS,"");
            if(d==null||selected.isEmpty()||!selected.equalsIgnoreCase(d.getAddress())){
                if(finished!=null)finished.run();return;
            }
            String action=intent.getAction();
            if(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)){
                int state=intent.getIntExtra(BluetoothProfile.EXTRA_STATE,-1);
                if(state==BluetoothProfile.STATE_DISCONNECTED){
                    prefs(c).edit().remove(LAST_ATTEMPT).apply();
                    report(c,"Bluetooth Music desconectado de T1");
                    if(finished!=null)finished.run();
                }else if(state==BluetoothProfile.STATE_CONNECTED){
                    checkConnected(c,"Bluetooth Music conectado",false,finished);
                }else if(finished!=null)finished.run();
            }else if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                // ACL is not audio. Never send PLAY until this device is confirmed via A2DP.
                checkConnected(c,"enlace Bluetooth conectado",false,finished);
            }else if(finished!=null)finished.run();
        }catch(Exception ex){
            report(c,"Error detectando Bluetooth · "+ex.getClass().getSimpleName());
            if(finished!=null)finished.run();
        }
    }
    /** Asynchronous A2DP device check avoids confusing BT calls with BT music. */
    static void checkConnected(Context context,String reason,boolean manual,Runnable finished){
        Context c=context.getApplicationContext();
        if(!manual&&!enabled(c)){if(finished!=null)finished.run();return;}
        if(!hasBluetoothPermission(c)){
            report(c,"Falta autorizar Dispositivos cercanos");
            if(finished!=null)finished.run();return;
        }
        String address=prefs(c).getString(ADDRESS,"");
        if(address.isEmpty()){
            report(c,"Elegí primero el Bluetooth de T1");
            if(finished!=null)finished.run();return;
        }
        BluetoothManager bm=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=bm==null?null:bm.getAdapter();
        if(adapter==null){
            report(c,"Bluetooth no disponible");
            if(finished!=null)finished.run();return;
        }
        try{
            boolean accepted=adapter.getProfileProxy(c,new BluetoothProfile.ServiceListener(){
                @Override public void onServiceConnected(int profile,BluetoothProfile proxy){
                    boolean targetConnected=false;
                    try{
                        for(BluetoothDevice d:proxy.getConnectedDevices())
                            if(address.equalsIgnoreCase(d.getAddress()))targetConnected=true;
                    }catch(Exception ex){report(c,"No se pudo comprobar audio Bluetooth");}
                    finally{try{adapter.closeProfileProxy(profile,proxy);}catch(Exception ignored){}}
                    if(targetConnected)attempt(c,reason,manual);
                    else report(c,"T1 vinculada, pero Bluetooth Music (A2DP) todavía no está conectado");
                    if(finished!=null)finished.run();
                }
                @Override public void onServiceDisconnected(int profile){
                    report(c,"Bluetooth Music se desconectó");
                    if(finished!=null)finished.run();
                }
            },BluetoothProfile.A2DP);
            if(!accepted){
                report(c,"No fue posible consultar Bluetooth Music");
                if(finished!=null)finished.run();
            }
        }catch(Exception ex){
            report(c,"No se puede verificar A2DP · "+ex.getClass().getSimpleName());
            if(finished!=null)finished.run();
        }
    }
    private static void attempt(Context c,String reason,boolean manual){
        if(!hasListenerAccess(c)){
            report(c,"Falta habilitar Acceso a notificaciones para controlar Spotify");
            return;
        }
        long now=System.currentTimeMillis();
        long previous=prefs(c).getLong(LAST_ATTEMPT,0);
        if(!manual&&now>=previous&&now-previous<DEBOUNCE_MS)return;
        try{
            MediaSessionManager manager=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if(manager==null){report(c,"Servicio multimedia no disponible");return;}
            List<MediaController> sessions=manager.getActiveSessions(
                new ComponentName(c,SpotifySessionListener.class));
            for(MediaController session:sessions){
                if(!SPOTIFY.equals(session.getPackageName()))continue;
                PlaybackState state=session.getPlaybackState();
                if(state!=null&&state.getState()==PlaybackState.STATE_PLAYING){
                    prefs(c).edit().putLong(LAST_ATTEMPT,now).apply();
                    report(c,"Spotify ya estaba reproduciendo en Xiaomi · sin cambios");
                    return;
                }
                session.getTransportControls().play();
                prefs(c).edit().putLong(LAST_ATTEMPT,now).apply();
                report(c,"Play enviado a Spotify del Xiaomi · "+reason+
                    " · comprobá audio en T1 (no modifica la fuente MODE)");
                return;
            }
            report(c,"Spotify no tiene una sesión activa en Xiaomi. Abrilo una vez para inicializarlo.");
        }catch(SecurityException ex){
            report(c,"Acceso multimedia denegado · verificá Acceso a notificaciones");
        }catch(Exception ex){
            report(c,"No se pudo reanudar Spotify · "+ex.getClass().getSimpleName());
        }
    }
}
