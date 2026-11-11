package ar.com.rango5gps.drivelabauto.receiver;

import android.app.*;
import android.content.*;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import ar.com.rango5gps.drivelabauto.common.BridgeCrypto;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

/** Isolated navigation-only foreground LAN server. No vehicle OEM or CAN APIs. */
public final class ReceiverService extends Service {
    public static final String PREFS="drivelab_receiver";
    public static final String ACTION_START="drivelab.receiver.START";
    public static final String ACTION_STOP="drivelab.receiver.STOP";
    public static final String ACTION_PAIR="drivelab.receiver.ARM_PAIR";
    public static final String ACTION_PAIR_QR="drivelab.receiver.ARM_PAIR_QR";
    public static final String FROM_BOOT="from_boot";
    private static final String CHANNEL="drivelab_receiver_background",ROUTES="drivelab_receiver_routes";
    private volatile boolean running=false;
    private ServerSocket navServer,pairServer;
    private Thread navigationThread,pairingThread;
    private final LinkedHashSet<String> recentNonces=new LinkedHashSet<>();
    private volatile Pending pending;
    private static final class Pending {
        final byte[] secret;
        final String code;
        final long id,expiry;
        Pending(byte[] key,String sas,long session,long expires){
            secret=key;code=sas;id=session;expiry=expires;
        }
    }
    public static String localIp(){
        String fallback="";
        try{
            Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();
            while(networks.hasMoreElements()){
                NetworkInterface network=networks.nextElement();
                if(!network.isUp()||network.isLoopback())continue;
                Enumeration<InetAddress> addresses=network.getInetAddresses();
                while(addresses.hasMoreElements()){
                    InetAddress address=addresses.nextElement();
                    if(!(address instanceof Inet4Address)||!address.isSiteLocalAddress())continue;
                    String n=network.getName().toLowerCase(Locale.US);
                    if(n.startsWith("wlan")||n.startsWith("ap")||n.startsWith("eth"))
                        return address.getHostAddress();
                    if(fallback.isEmpty())fallback=address.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return fallback;
    }
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    @Override public void onCreate(){super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
            "DriveLab Auto · receptor activo",NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(ROUTES,
            "DriveLab Auto · destinos",NotificationManager.IMPORTANCE_DEFAULT));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)){
            prefs().edit().putBoolean("keep_alive",false)
                .putBoolean("auto_boot",false).apply();
            shutdown();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            return START_NOT_STICKY;
        }
        boolean qr=ACTION_PAIR_QR.equals(action);
        boolean arm=qr||ACTION_PAIR.equals(action);
        boolean start=arm||ACTION_START.equals(action)||
            (intent==null&&prefs().getBoolean("keep_alive",false));
        if(!start)return START_NOT_STICKY;
        if(!running){
            prefs().edit().putBoolean("keep_alive",true)
                .putString("pair_port_status","Iniciando puerto 48392…").apply();
            startForeground(7001,ongoing());
            running=true;
            prefs().edit().putString("status","Servidor iniciándose…")
                .putBoolean("running",true).apply();
            navigationThread=new Thread(this::listenNav,"Receiver-Navigation");
            pairingThread=new Thread(this::listenPair,"Receiver-Pairing");
            navigationThread.start();pairingThread.start();
            if(intent!=null&&intent.getBooleanExtra(FROM_BOOT,false))scheduleBootApp();
        }
        if(arm){
            pending=null;
            SharedPreferences.Editor e=prefs().edit()
                .putLong("pair_until",System.currentTimeMillis()+120000)
                .remove("pair_code").remove("pair_id").remove("approved_id")
                .remove("pair_qr_ticket");
            if(qr){
                if(localIp().isEmpty()){
                    e.putString("pair_status",
                        "SIN IP Wi-Fi · conectá T1 y Xiaomi a la misma red");
                }else{
                    byte[] random=new byte[24];new SecureRandom().nextBytes(random);
                    StringBuilder t=new StringBuilder(48);
                    for(byte b:random)
                        t.append(String.format(Locale.US,"%02x",b&255));
                    e.putString("pair_qr_ticket",t.toString())
                        .putString("pair_status",
                            "QR ACTIVO · escaneá desde Sender · 2 MINUTOS");
                }
            }else e.putString("pair_status","ESPERANDO XIAOMI · 2 MINUTOS");
            e.apply();
        }
        return START_STICKY;
    }
    private Notification ongoing(){
        Intent activity=new Intent(this,ReceiverActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,100,activity,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,ReceiverService.class).setAction(ACTION_STOP);
        PendingIntent cancel=PendingIntent.getService(this,101,stop,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("DriveLab Auto Receiver · activo")
            .setContentText("Recibiendo destinos cifrados sin ocupar la pantalla")
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_delete,"Detener",cancel).build();
    }
    private void listenNav(){
        try(ServerSocket server=new ServerSocket(BridgeCrypto.NAV_PORT,5)){
            navServer=server;server.setSoTimeout(1000);
            String ip=localIp();prefs().edit().putString("status","ESCUCHANDO "+ip+":48391").apply();
            while(running){
                String actual=localIp();
                if(!actual.equals(ip)){
                    ip=actual;prefs().edit().putString("status",ip.isEmpty()?"SIN IP Wi-Fi · receptor esperando":
                        "ESCUCHANDO "+ip+":48391").apply();
                }
                try(Socket client=server.accept()){
                    client.setSoTimeout(4000);
                    if(!isPrivate(client.getInetAddress()))continue;
                    DataInputStream in=new DataInputStream(client.getInputStream());
                    DataOutputStream out=new DataOutputStream(client.getOutputStream());
                    byte[] packet=BridgeCrypto.read(in);
                    String nonce=BridgeCrypto.nonce(packet);
                    if(recentNonces.contains(nonce))continue;
                    byte[] key=BridgeCrypto.unpackKey(prefs().getString("secret",""));
                    BridgeCrypto.Packet message;
                    try{
                        message=BridgeCrypto.decrypt(key,packet);
                    }catch(GeneralSecurityException clockOrCryptoError){
                        // An authenticated ping is allowed even when the T1
                        // and Xiaomi clocks differ. Navigation packets remain
                        // timestamp-protected and are never eligible here.
                        message=BridgeCrypto.decryptPairing(key,packet);
                        if(!"ping".equals(message.kind))throw clockOrCryptoError;
                    }
                    recentNonces.add(nonce);
                    if(recentNonces.size()>64)recentNonces.remove(recentNonces.iterator().next());
                    boolean ready="ping".equals(message.kind)||dispatchNavigation(message);
                    BridgeCrypto.write(out,BridgeCrypto.encrypt(key,ready?"ack":"not_ready",nonce));
                }catch(SocketTimeoutException ignored){}catch(Exception ex){
                    // Never store plaintext, address, secret or route in logs.
                    try{Thread.sleep(100);}catch(InterruptedException ignored){}
                }
            }
        }catch(Exception ex){
            if(running)prefs().edit().putString("status","ERROR EN PUERTO 48391").apply();
        }finally{navServer=null;running=false;
            prefs().edit().putBoolean("running",false).apply();
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    }
    private static boolean isPrivate(InetAddress address){
        return address!=null&&(address.isSiteLocalAddress()||address.isLinkLocalAddress());
    }
    private boolean dispatchNavigation(BridgeCrypto.Packet msg){
        if(!"coord".equals(msg.kind)&&!"search".equals(msg.kind))return false;
        Uri target=Uri.parse("waze://?"+("coord".equals(msg.kind)?"ll=":"q=")+
            Uri.encode(msg.value,"coord".equals(msg.kind)?",":"")+"&navigate=yes");
        Intent launch=new Intent(Intent.ACTION_VIEW,target).setPackage("com.waze");
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean attempted=false;
        if(prefs().getBoolean("open_waze",true)&&
            (ReceiverActivity.isVisible()||Settings.canDrawOverlays(this))){
            try{startActivity(launch);attempted=true;}
            catch(RuntimeException error){
                prefs().edit().putString("last_open","WAZE BLOQUEADO: "+
                    error.getClass().getSimpleName()).apply();
            }
        }
        // Always retain user-tappable notification if Android/OEM disallows launch.
        PendingIntent tap=PendingIntent.getActivity(this,9002,launch,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notif=new Notification.Builder(this,ROUTES)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Nuevo destino del Xiaomi")
            .setContentText("Tocá para abrir en Waze si no apareció en el clúster")
            .setAutoCancel(true).setContentIntent(tap).build();
        getSystemService(NotificationManager.class).notify(9002,notif);
        prefs().edit().putInt("received",prefs().getInt("received",0)+1)
            .putString("last_open",attempted?"Waze solicitado en T1 / clúster":
                "Ruta recibida; Waze requiere permiso o notificación").apply();
        return attempted;
    }
    private void listenPair(){
        try(ServerSocket server=new ServerSocket(BridgeCrypto.PAIR_PORT,3)){
            pairServer=server;server.setSoTimeout(1000);
            prefs().edit().putString("pair_port_status",
                "PUERTO 48392 ABIERTO · esperando Xiaomi").apply();
            while(running){
                try(Socket client=server.accept()){
                    client.setSoTimeout(4500);
                    if(!isPrivate(client.getInetAddress()))continue;
                    DataInputStream in=new DataInputStream(client.getInputStream());
                    DataOutputStream out=new DataOutputStream(client.getOutputStream());
                    if(in.readInt()!=BridgeCrypto.PAIR_MAGIC)continue;
                    int action=in.readUnsignedByte();
                    if(action==1)beginPair(in,out,false);
                    else if(action==2)finishPair(in,out);
                    else if(action==3)beginPair(in,out,true);
                }catch(SocketTimeoutException ignored){}catch(Exception error){
                    // Only failure category, never the code, key, IP or payload.
                    prefs().edit().putString("pair_status",
                        "VINCULACIÓN FALLIDA · "+error.getClass().getSimpleName()+
                        " · reiniciá el código y repetí las dos confirmaciones").apply();
                }
            }
        }catch(Exception error){
            prefs().edit()
                .putString("pair_port_status",
                    "ERROR PUERTO 48392 · "+error.getClass().getSimpleName())
                .putString("pair_status",
                    "NO SE PUEDE VINCULAR: puerto 48392 no disponible")
                .apply();
        }finally{
            pairServer=null;pending=null;
            // Preserve a concrete bind error if one was shown.
            if(!prefs().getString("pair_port_status","").startsWith("ERROR"))
                prefs().edit().putString("pair_port_status",
                    "PUERTO 48392 CERRADO").apply();
        }
    }
    private void reject(DataOutputStream out) throws Exception{
        out.writeInt(BridgeCrypto.PAIR_MAGIC);out.writeByte(13);out.flush();
    }
    private void beginPair(DataInputStream in,DataOutputStream out,boolean qr)
        throws Exception{
        long now=System.currentTimeMillis();
        if(now>prefs().getLong("pair_until",0)||pending!=null){reject(out);return;}
        int len=in.readUnsignedShort();
        if(len<70||len>200){reject(out);return;}
        byte[] phone=new byte[len];in.readFully(phone);
        if(qr){
            String ticket=prefs().getString("pair_qr_ticket","");
            if(ticket.length()!=48){reject(out);return;}
            byte[] proof=new byte[32];in.readFully(proof);
            byte[] expected=BridgeCrypto.qrPairProof(ticket,phone);
            if(!MessageDigest.isEqual(expected,proof)){reject(out);return;}
        }
        KeyPair local=BridgeCrypto.newKeyPair();
        byte[] secret=BridgeCrypto.derive(local.getPrivate(),phone);
        long id=new SecureRandom().nextLong();
        String code=BridgeCrypto.sas(secret);
        pending=new Pending(secret,code,id,Math.min(now+120000,prefs().getLong("pair_until",0)));
        SharedPreferences.Editor edit=prefs().edit()
            .putLong("pair_id",id).putString("pair_code",code);
        if(qr)edit.putLong("approved_id",id)
            .putString("pair_status",
                "QR VERIFICADO · completando vinculación cifrada Xiaomi");
        else edit.putString("pair_status","COMPARÁ LOS 6 DÍGITOS CON XIAOMI")
            .remove("approved_id");
        edit.apply();
        byte[] pub=BridgeCrypto.publicBytes(local);
        out.writeInt(BridgeCrypto.PAIR_MAGIC);out.writeByte(11);out.writeLong(id);
        out.writeShort(pub.length);out.write(pub);out.flush();
    }
    private void finishPair(DataInputStream in,DataOutputStream out) throws Exception{
        long id=in.readLong();byte[] proof=BridgeCrypto.read(in);
        Pending session=pending;
        if(session==null||id!=session.id||System.currentTimeMillis()>session.expiry){
            prefs().edit().putString("pair_status",
                "CÓDIGO CADUCADO O SESIÓN DISTINTA · empezá otra vinculación").apply();
            reject(out);return;
        }
        if(prefs().getLong("approved_id",0)!=id){
            prefs().edit().putString("pair_status",
                "FALTA AUTORIZAR EN T1 · tocá SÍ, COINCIDEN antes que Xiaomi").apply();
            reject(out);return;
        }
        // Two device clocks may differ. This AES proof is valid only inside
        // the physical two-minute, one-time SAS-authorized pairing session.
        BridgeCrypto.Packet request=BridgeCrypto.decryptPairing(session.secret,proof);
        if(!"ping".equals(request.kind)||!"".equals(request.value)){reject(out);return;}
        if(!prefs().edit().putString("secret",BridgeCrypto.packKey(session.secret))
            .putString("pair_status","XIAOMI VINCULADO · CIFRADO ACTIVO")
            .remove("pair_code").remove("pair_id").remove("approved_id")
            .remove("pair_qr_ticket")
            .remove("pair_until").commit()){reject(out);return;}
        pending=null;
        out.writeInt(BridgeCrypto.PAIR_MAGIC);out.writeByte(12);
        BridgeCrypto.write(out,BridgeCrypto.encrypt(session.secret,"ack",BridgeCrypto.nonce(proof)));
    }
    private void scheduleBootApp(){
        if(!prefs().getBoolean("launch_app_on_boot",false))return;
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            if(!running||!prefs().getBoolean("launch_app_on_boot",false))return;
            String pkg=prefs().getString("launch_package","");
            if(pkg.isEmpty()||!Settings.canDrawOverlays(this)){
                prefs().edit().putString("boot_status","App de inicio sin permiso o sin selección").apply();return;
            }
            Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch==null){prefs().edit().putString("boot_status","App elegida no disponible").apply();return;}
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try{startActivity(launch);prefs().edit().putString("boot_status",
                "Apertura solicitada: "+pkg).apply();}
            catch(RuntimeException error){prefs().edit().putString("boot_status",
                "Android bloqueó abrir la app seleccionada").apply();}
        },12000);
    }
    private void shutdown(){
        running=false;
        try{if(navServer!=null)navServer.close();}catch(Exception ignored){}
        try{if(pairServer!=null)pairServer.close();}catch(Exception ignored){}
        pending=null;
        prefs().edit().putBoolean("running",false)
            .putString("status","DETENIDO")
            .putString("pair_port_status","PUERTO 48392 CERRADO").apply();
    }
    @Override public void onDestroy(){shutdown();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
