package ar.com.rango5gps.drivelabauto.boot;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Display;
import ar.com.rango5gps.drivelabauto.common.BridgeCrypto;
import ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Independent LAN/QR Receiver owned BY Boot, NOT the legacy Receiver app.
 * Uses the exact existing Sender ECDH/AES-GCM protocol and 48391/48392
 * ports. Never imports the legacy Receiver's private key, app database,
 * shell, maps cache or vehicle interfaces.
 *
 * On a conflict, fails with PORT OCUPADO instead of stopping other apps.
 * Users can disable the old Receiver's auto-start separately.
 */
public final class BootLinkService extends Service {
    static final String PREFS="boot_receiver_lan_v1";
    static final String START="boot.receiver.START";
    static final String PAIR_QR="boot.receiver.PAIR_QR";
    static final String STOP="boot.receiver.STOP";
    private static final String CHANNEL="boot_receiver_lan";
    private volatile boolean running;
    private ServerSocket navServer,pairServer;
    private volatile Pending pairing;
    private final LinkedHashSet<String> recent=new LinkedHashSet<>();
    private static final class Pending {
        final byte[] secret;
        final long id,expiry;
        Pending(byte[] k,long i,long e){secret=k;id=i;expiry=e;}
    }
    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,MODE_PRIVATE);
    }
    static String localIp(){
        String alternate="";
        try{
            Enumeration<NetworkInterface> items=NetworkInterface.getNetworkInterfaces();
            while(items.hasMoreElements()){
                NetworkInterface iface=items.nextElement();
                if(!iface.isUp()||iface.isLoopback())continue;
                Enumeration<InetAddress> addrs=iface.getInetAddresses();
                while(addrs.hasMoreElements()){
                    InetAddress addr=addrs.nextElement();
                    if(!(addr instanceof Inet4Address)||!addr.isSiteLocalAddress())
                        continue;
                    String name=iface.getName().toLowerCase(Locale.ROOT);
                    if(name.startsWith("wlan")||name.startsWith("ap")||
                       name.startsWith("eth"))return addr.getHostAddress();
                    if(alternate.isEmpty())alternate=addr.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return alternate;
    }
    static String qr(Context c){
        SharedPreferences p=prefs(c);
        String ip=localIp();
        String ticket=p.getString("qr_ticket","");
        if(!ip.matches("[0-9.]{7,15}")||
           !ticket.matches("[0-9a-f]{48}")||
           p.getLong("qr_until",0)<System.currentTimeMillis())
            return "";
        return "drivelabauto://pair?ip="+ip+"&ticket="+ticket;
    }
    private void update(String key,String value){
        prefs(this).edit().putString(key,value).apply();
    }
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager n=getSystemService(NotificationManager.class);
        if(n!=null)n.createNotificationChannel(new NotificationChannel(
            CHANNEL,"DriveLab Boot & Receiver · Xiaomi",
            NotificationManager.IMPORTANCE_LOW));
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        // Android may restart a START_STICKY service with null Intent.
        // Never restart the listener if the owner has disabled Bridge AUTO.
        if(intent==null&&!BootConfig.enabled(this)){
            shutdown();stopSelf(id);return START_NOT_STICKY;
        }
        String action=intent==null?START:intent.getAction();
        if(STOP.equals(action)){
            prefs(this).edit().putBoolean("auto_receive",false).apply();
            shutdown();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            return START_NOT_STICKY;
        }
        if(!PAIR_QR.equals(action)&&!START.equals(action))
            return START_NOT_STICKY;
        try{
            PendingIntent open=PendingIntent.getActivity(this,701,
                new Intent(this,BootActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            startForeground(7701,new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("DriveLab Boot & Receiver")
                .setContentText("Receptor Xiaomi cifrado · independiente")
                .setContentIntent(open).setOngoing(true).build());
        }catch(Exception ex){
            update("status","Android bloqueó iniciar receptor: "+
                ex.getClass().getSimpleName());
            stopSelf(id);return START_NOT_STICKY;
        }
        if(!running){
            running=true;
            update("status","Iniciando puertos 48391/48392…");
            new Thread(this::listenNav,"BootReceiver-Nav").start();
            new Thread(this::listenPair,"BootReceiver-QR").start();
        }
        if(PAIR_QR.equals(action)){
            pairing=null;
            SharedPreferences.Editor e=prefs(this).edit()
                .putLong("qr_until",System.currentTimeMillis()+120000L)
                .remove("pair_id").remove("approved_id")
                .remove("pair_code");
            if(localIp().isEmpty()){
                e.remove("qr_ticket").putString("pair_status",
                    "SIN RED: conectá T1 y Xiaomi al mismo Wi-Fi");
            }else{
                byte[] token=new byte[24];
                new SecureRandom().nextBytes(token);
                StringBuilder hex=new StringBuilder(48);
                for(byte b:token)hex.append(String.format(Locale.ROOT,"%02x",b&255));
                e.putString("qr_ticket",hex.toString())
                    .putString("pair_status",
                        "QR ACTIVO POR 2 MINUTOS · leelo desde Sender Xiaomi");
            }
            e.apply();
        }
        // A manually started QR/listener is NEVER persistent unless the
        // owner explicitly opted into Bridge's independent auto-start.
        return BootConfig.enabled(this)?START_STICKY:START_NOT_STICKY;
    }
    private static boolean privatePeer(InetAddress a){
        return a!=null&&(a.isSiteLocalAddress()||a.isLinkLocalAddress());
    }
    private void listenNav(){
        try(ServerSocket server=new ServerSocket(BridgeCrypto.NAV_PORT,5)){
            navServer=server;server.setSoTimeout(1000);
            update("status","ESCUCHANDO "+localIp()+":48391");
            while(running){
                try(Socket client=server.accept()){
                    if(!privatePeer(client.getInetAddress()))continue;
                    client.setSoTimeout(4000);
                    DataInputStream in=new DataInputStream(client.getInputStream());
                    DataOutputStream out=new DataOutputStream(client.getOutputStream());
                    byte[] raw=BridgeCrypto.read(in);
                    String nonce=BridgeCrypto.nonce(raw);
                    if(recent.contains(nonce))continue;
                    byte[] key=BridgeCrypto.unpackKey(
                        prefs(this).getString("secret",""));
                    BridgeCrypto.Packet message;
                    try{message=BridgeCrypto.decrypt(key,raw);}
                    catch(GeneralSecurityException time){
                        message=BridgeCrypto.decryptPairing(key,raw);
                        if(!"ping".equals(message.kind))throw time;
                    }
                    recent.add(nonce);
                    if(recent.size()>64)recent.remove(recent.iterator().next());
                    boolean accepted="ping".equals(message.kind)||
                        dispatch(message);
                    BridgeCrypto.write(out,BridgeCrypto.encrypt(key,
                        accepted?"ack":"not_ready",nonce));
                }catch(SocketTimeoutException ignored){}
                catch(Exception ignored){
                    // Route data/secrets/tickets are never written to logs.
                }
            }
        }catch(Exception problem){
            update("status","ERROR PUERTO 48391 · "+
                problem.getClass().getSimpleName()+
                ". Puede estar ocupado por el Receiver anterior.");
        }finally{
            navServer=null;shutdown();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    private boolean dispatch(BridgeCrypto.Packet packet){
        String kind=BridgeRouteTarget.baseKind(packet.kind);
        String target=BridgeRouteTarget.target(packet.kind);
        if(kind==null||target==null)return false;
        if(!Settings.canDrawOverlays(this)&&
            !BootActivity.isVisible()){
            update("last_route","DESTINO RECIBIDO · Android bloquea "+
                "abrir mapas desde segundo plano; habilitá el permiso");
            return false;
        }
        String[] apps=BridgeRouteTarget.BOTH.equals(target)?
            new String[]{"com.waze","com.google.android.apps.maps"}:
            new String[]{BridgeRouteTarget.MAPS.equals(target)?
                "com.google.android.apps.maps":"com.waze"};
        int accepted=0;
        for(String pkg:apps)
            if(openMap(pkg,kind,packet.value))accepted++;
        boolean all=accepted==apps.length;
        prefs(this).edit().putString("last_route",
            all?"DESTINO ENVIADO A "+accepted+" MAPA(S) · comprobar tablero":
            "DESTINO PARCIAL: "+accepted+"/"+apps.length+
                " mapa(s) recibieron solicitud; sin confirmación visual")
            .putInt("received",prefs(this).getInt("received",0)+(all?1:0))
            .apply();
        // Crucial: Sender ACK means all intents submitted, not visible.
        return all;
    }
    private boolean openMap(String pkg,String base,String destination){
        try{
            Intent map;
            Uri uri;
            if("com.waze".equals(pkg)){
                uri=Uri.parse("waze://?"+("coord".equals(base)?"ll=":"q=")+
                    Uri.encode(destination,"coord".equals(base)?",":"")+
                    "&navigate=yes");
                map=new Intent(Intent.ACTION_VIEW,uri).setPackage(pkg);
            }else if("com.google.android.apps.maps".equals(pkg)){
                uri=Uri.parse("google.navigation:q="+
                    Uri.encode(destination,"coord".equals(base)?",":"")+"&mode=d");
                map=new Intent(Intent.ACTION_VIEW,uri).setPackage(pkg);
            }else return false;
            if(getPackageManager().resolveActivity(map,0)==null)return false;
            map.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
            // Bridge's own, user-confirmed destination. NEVER consult the
            // legacy Receiver's cluster_enabled / cluster_display_id flags.
            boolean tablero=BridgeStandaloneWindows.routeOnTablero(this,pkg);
            if(tablero){
                DisplayManager dm=(DisplayManager)getSystemService(DISPLAY_SERVICE);
                Display display=dm==null?null:dm.getDisplay(1);
                if(display==null||display.getState()==Display.STATE_OFF||
                   (display.getFlags()&Display.FLAG_PRIVATE)!=0){
                    update("last_route","DISPLAY 1 NO DISPONIBLE; "+
                        "no se cambió de pantalla ni se modificó el mapa");
                    return false;
                }
                ActivityOptions opts=ActivityOptions.makeBasic()
                    .setLaunchDisplayId(1);
                // Sender routing NEVER changes Freeform bounds or stops tasks.
                // It reuses Android's normal task/startActivity handling.
                startActivity(map,opts.toBundle());
            }else{
                // Destinations requested for other apps stay on Display 0.
                // If a specific map was confirmed on Display 1, only that
                // app goes there; no silent fallback or duplicate layers.
                startActivity(map);
            }
            return true;
        }catch(Exception ignored){return false;}
    }
    private void listenPair(){
        try(ServerSocket server=new ServerSocket(BridgeCrypto.PAIR_PORT,3)){
            pairServer=server;server.setSoTimeout(1000);
            update("pair_port_status","PUERTO 48392 · LISTO");
            while(running){
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(4500);
                    if(!privatePeer(socket.getInetAddress()))continue;
                    DataInputStream in=new DataInputStream(socket.getInputStream());
                    DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                    if(in.readInt()!=BridgeCrypto.PAIR_MAGIC)continue;
                    int action=in.readUnsignedByte();
                    if(action==3)begin(in,out,true);
                    else if(action==1)begin(in,out,false);
                    else if(action==2)finish(in,out);
                }catch(SocketTimeoutException ignored){}
                catch(Exception ex){
                    update("pair_status","VINCULACIÓN NO CONFIRMADA: "+
                        ex.getClass().getSimpleName());
                }
            }
        }catch(Exception error){
            update("pair_port_status","ERROR PUERTO 48392 · "+
                error.getClass().getSimpleName()+
                " (comprobá que DriveLab T1 esté apagado)");
        }finally{
            pairServer=null;
        }
    }
    private static void deny(DataOutputStream out)throws Exception{
        out.writeInt(BridgeCrypto.PAIR_MAGIC);
        out.writeByte(13);
        out.flush();
    }
    private void begin(DataInputStream in,DataOutputStream out,
                       boolean qr)throws Exception{
        SharedPreferences p=prefs(this);
        long now=System.currentTimeMillis();
        if(now>p.getLong("qr_until",0)||pairing!=null){
            deny(out);return;
        }
        int len=in.readUnsignedShort();
        if(len<70||len>200){deny(out);return;}
        byte[] remote=new byte[len];
        in.readFully(remote);
        if(qr){
            String ticket=p.getString("qr_ticket","");
            if(!ticket.matches("[0-9a-f]{48}")){deny(out);return;}
            byte[] proof=new byte[32];in.readFully(proof);
            if(!MessageDigest.isEqual(proof,
                BridgeCrypto.qrPairProof(ticket,remote))){
                deny(out);return;
            }
        }
        KeyPair key=BridgeCrypto.newKeyPair();
        byte[] secret=BridgeCrypto.derive(key.getPrivate(),remote);
        long id=new SecureRandom().nextLong();
        pairing=new Pending(secret,id,
            Math.min(now+120000L,p.getLong("qr_until",0)));
        SharedPreferences.Editor editor=p.edit().putLong("pair_id",id)
            .putString("pair_code",BridgeCrypto.sas(secret));
        if(qr){
            editor.putLong("approved_id",id)
                .putString("pair_status","QR VALIDADO · cifrado Xiaomi pendiente");
        }else{
            editor.putString("pair_status",
                "COMPARÁ CÓDIGO Y CONFIRMÁ EN BOOT")
                .remove("approved_id");
        }
        editor.apply();
        byte[] publicKey=BridgeCrypto.publicBytes(key);
        out.writeInt(BridgeCrypto.PAIR_MAGIC);out.writeByte(11);
        out.writeLong(id);out.writeShort(publicKey.length);
        out.write(publicKey);out.flush();
    }
    private void finish(DataInputStream in,DataOutputStream out)throws Exception{
        long id=in.readLong();
        byte[] proof=BridgeCrypto.read(in);
        Pending p=pairing;
        if(p==null||id!=p.id||System.currentTimeMillis()>p.expiry){
            update("pair_status","SESIÓN CADUCADA · escaneá otro QR");
            deny(out);return;
        }
        if(prefs(this).getLong("approved_id",0)!=id){
            update("pair_status","FALTA AUTORIZAR EN BOOT");
            deny(out);return;
        }
        BridgeCrypto.Packet message=BridgeCrypto.decryptPairing(p.secret,proof);
        if(!"ping".equals(message.kind)||!"".equals(message.value)){
            deny(out);return;
        }
        if(!prefs(this).edit()
                .putString("secret",BridgeCrypto.packKey(p.secret))
                .putString("pair_status","XIAOMI VINCULADO A BRIDGE · AES-GCM")
                .remove("qr_ticket").remove("qr_until")
                .remove("pair_code").remove("pair_id").remove("approved_id")
                .commit()){
            deny(out);return;
        }
        pairing=null;
        out.writeInt(BridgeCrypto.PAIR_MAGIC);out.writeByte(12);
        BridgeCrypto.write(out,BridgeCrypto.encrypt(
            p.secret,"ack",BridgeCrypto.nonce(proof)));
    }
    private void shutdown(){
        running=false;
        try{if(navServer!=null)navServer.close();}catch(Exception ignored){}
        try{if(pairServer!=null)pairServer.close();}catch(Exception ignored){}
        update("status","RECEPTOR BOOT DETENIDO");
    }
    @Override public void onDestroy(){
        shutdown();
        super.onDestroy();
    }
}
