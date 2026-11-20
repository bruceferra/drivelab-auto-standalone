package ar.com.rango5gps.drivelabauto.sender;

import android.content.Context;
import android.net.Uri;
import android.content.SharedPreferences;
import ar.com.rango5gps.drivelabauto.common.BridgeCrypto;
import java.io.*;
import java.net.*;
import java.security.KeyPair;
import java.util.*;

/** Sender side only. Pairing never sends the AES key over the LAN. */
public final class SenderClient {
    private SenderClient(){}
    public static final String PREFS="drivelab_sender";
    public static final class Session {
        public final String ip,code;
        public final long id,created;
        public final byte[] key;
        Session(String address,String sas,long sessionId,long at,byte[] secret){
            ip=address;code=sas;id=sessionId;created=at;key=secret;
        }
    }
    public static final class Delivery {
        public final boolean accepted;
        public final String status;
        Delivery(boolean ok,String s){accepted=ok;status=s;}
    }
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static boolean configured(Context c){
        SharedPreferences p=prefs(c);
        try{return localIpv4(p.getString("receiver_ip",""))&&
            BridgeCrypto.unpackKey(p.getString("receiver_key","")).length==16;}
        catch(Exception e){return false;}
    }
    public static boolean localIpv4(String ip){
        if(ip==null||!ip.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}"))return false;
        try{
            String[] a=ip.split("\\.");int[] b=new int[4];
            for(int i=0;i<4;i++){b[i]=Integer.parseInt(a[i]);if(b[i]>255)return false;}
            return b[0]==10||(b[0]==172&&b[1]>=16&&b[1]<=31)||
                (b[0]==192&&b[1]==168);
        }catch(Exception e){return false;}
    }
    private static Socket connect(String ip,int port) throws Exception{
        Socket s=new Socket();s.connect(new InetSocketAddress(ip,port),3500);
        s.setSoTimeout(4500);return s;
    }
    public static Session beginPairing(String ip) throws Exception{
        return beginPairing(ip,null);
    }
    public static Session beginPairingQr(String raw)throws Exception{
        if(raw==null||raw.length()>250)
            throw new IOException("QR de vinculación inválido");
        Uri link=Uri.parse(raw);
        if(!"drivelabauto".equals(link.getScheme())||
            !"pair".equals(link.getHost()))
            throw new IOException("QR no pertenece a DriveLab Auto");
        String ip=link.getQueryParameter("ip");
        String token=link.getQueryParameter("ticket");
        if(token==null||!token.matches("[0-9a-f]{48}"))
            throw new IOException("QR vencido o mal formado");
        return beginPairing(ip,token);
    }
    private static Session beginPairing(String ip,String ticket) throws Exception{
        if(!localIpv4(ip))throw new IOException("Dirección IP privada inválida");
        KeyPair mine=BridgeCrypto.newKeyPair();
        try(Socket socket=connect(ip,BridgeCrypto.PAIR_PORT)){
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            DataInputStream in=new DataInputStream(socket.getInputStream());
            byte[] pub=BridgeCrypto.publicBytes(mine);
            out.writeInt(BridgeCrypto.PAIR_MAGIC);
            out.writeByte(ticket==null?1:3);
            out.writeShort(pub.length);out.write(pub);
            if(ticket!=null)out.write(BridgeCrypto.qrPairProof(ticket,pub));
            out.flush();
            if(in.readInt()!=BridgeCrypto.PAIR_MAGIC||in.readUnsignedByte()!=11)
                throw new IOException(ticket!=null?
                    "QR no aceptado: generá uno nuevo en la T1, misma red Wi-Fi":
                    "Primero activá VINCULAR en el Receiver");
            long id=in.readLong();int size=in.readUnsignedShort();
            if(size<70||size>200)throw new IOException("Clave pública T1 inválida");
            byte[] received=new byte[size];in.readFully(received);
            byte[] key=BridgeCrypto.derive(mine.getPrivate(),received);
            return new Session(ip,BridgeCrypto.sas(key),id,System.currentTimeMillis(),key);
        }
    }
    public static void finishPairing(Context context,Session session) throws Exception{
        if(session==null||System.currentTimeMillis()-session.created>120000)
            throw new IOException("El código de vinculación venció");
        try(Socket s=connect(session.ip,BridgeCrypto.PAIR_PORT)){
            DataOutputStream out=new DataOutputStream(s.getOutputStream());
            DataInputStream in=new DataInputStream(s.getInputStream());
            byte[] proof=BridgeCrypto.encrypt(session.key,"ping","");
            out.writeInt(BridgeCrypto.PAIR_MAGIC);out.writeByte(2);
            out.writeLong(session.id);BridgeCrypto.write(out,proof);
            if(in.readInt()!=BridgeCrypto.PAIR_MAGIC||in.readUnsignedByte()!=12)
                throw new IOException("T1 rechazó la confirmación. En Receiver, tocá "+
                    "AUTORIZAR CÓDIGO COINCIDENTE → SÍ, COINCIDEN; "+
                    "después confirmá en Xiaomi. Si venció, iniciá un código nuevo.");
            byte[] ack=BridgeCrypto.read(in);
            // The two screens can have different wall clocks while the
            // vehicle is offline. Physical SAS approval authenticates pairing.
            BridgeCrypto.Packet result=BridgeCrypto.decryptPairing(session.key,ack);
            if(!"ack".equals(result.kind)||!BridgeCrypto.nonce(proof).equals(result.value))
                throw new IOException("Confirmación de T1 no autenticada");
            if(!prefs(context).edit().putString("receiver_ip",session.ip)
                .putString("receiver_key",BridgeCrypto.packKey(session.key))
                .putString("last_status","VINCULADA · cifrado autenticado")
                .commit())throw new IOException("No se pudo guardar el vínculo");
        }
    }
    public static void unpair(Context c){
        prefs(c).edit().remove("receiver_ip").remove("receiver_key")
            .putString("last_status","SIN VINCULAR").apply();
    }
    /**
     * The T1 may boot offline with a different system date. Obtain the
     * authenticated T1 clock from an AES-GCM ping before each destination.
     * Its response must match the fresh nonce so stale traffic cannot
     * impersonate a clock sync. The route still expires on both ends.
     */
    private static long remoteClockOffset(String ip,byte[] key)throws Exception{
        long before=System.currentTimeMillis();
        byte[] ping=BridgeCrypto.encrypt(key,"ping","");
        try(Socket socket=connect(ip,BridgeCrypto.NAV_PORT)){
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            DataInputStream in=new DataInputStream(socket.getInputStream());
            BridgeCrypto.write(out,ping);
            BridgeCrypto.Packet ack=BridgeCrypto.decryptPairing(
                key,BridgeCrypto.read(in));
            if(!"ack".equals(ack.kind)||
               !BridgeCrypto.nonce(ping).equals(ack.value))
                throw new IOException("Confirmación de hora T1 no autenticada");
            long after=System.currentTimeMillis();
            long midpoint=before+(after-before)/2;
            return midpoint-ack.createdAt;
        }
    }

    private static final class Endpoint {
        final String ip;
        final long offset;
        Endpoint(String address,long clock){ip=address;offset=clock;}
    }
    private static Endpoint endpoint(Context c)throws Exception{
        SharedPreferences p=prefs(c);
        String ip=p.getString("receiver_ip","");
        byte[] key=BridgeCrypto.unpackKey(p.getString("receiver_key",""));
        if(!localIpv4(ip))throw new IOException("IP no configurada");
        try{
            // Reuse persisted ECDH/AES key and prior address if it answers.
            return new Endpoint(ip,remoteClockOffset(ip,key));
        }catch(Exception staleIp){
            // Authenticated broadcast, NOT a new QR pairing.
            String found=SenderBridgeDiscovery.locate(key,ip);
            long offset=remoteClockOffset(found,key); // verify before commit
            if(!p.edit().putString("receiver_ip",found).commit())
                throw new IOException("No se pudo guardar nueva IP autenticada");
            p.edit().putString("last_status",
                "BRIDGE RECONECTADO · vínculo QR anterior conservado").apply();
            return new Endpoint(found,offset);
        }
    }
    /** Explicit, user-friendly connectivity check; never replaces a key.
     * Sender automatically does this same discovery before each route.
     */
    public static Delivery reconnect(Context c){
        try{
            if(!configured(c))
                return new Delivery(false,
                    "NO VINCULADO · el QR sólo es necesario la primera vez");
            Endpoint target=endpoint(c);
            String status="BRIDGE CONECTADO · "+target.ip+
                " · QR persistente; no hace falta volver a escanear";
            prefs(c).edit().putString("last_status",status).apply();
            return new Delivery(true,status);
        }catch(Exception error){
            String details=String.valueOf(error.getMessage());
            if(details.length()>160)details=details.substring(0,160);
            String status="BRIDGE NO LOCALIZADO · "+details+
                " · clave QR guardada; no desvincules";
            prefs(c).edit().putString("last_status",status).apply();
            return new Delivery(false,status);
        }
    }

    public static Delivery send(Context c,String kind,String value){
        try{
            SharedPreferences p=prefs(c);
            Endpoint target=endpoint(c);
            String ip=target.ip;
            long offset=target.offset;
            byte[] key=BridgeCrypto.unpackKey(p.getString("receiver_key",""));
            byte[] packet=BridgeCrypto.encryptWithClockOffset(key,kind,value,offset);
            try(Socket socket=connect(ip,BridgeCrypto.NAV_PORT)){
                DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                DataInputStream in=new DataInputStream(socket.getInputStream());
                BridgeCrypto.write(out,packet);
                BridgeCrypto.Packet reply=BridgeCrypto.decryptWithClockOffset(
                    key,BridgeCrypto.read(in),offset);
                if(!BridgeCrypto.nonce(packet).equals(reply.value))
                    throw new IOException("Respuesta no autenticada");
                boolean accepted="ack".equals(reply.kind);
                if(!accepted&&!"not_ready".equals(reply.kind))
                    throw new IOException("Tipo de respuesta inválido");
                boolean t1Trial=kind!=null&&kind.endsWith("_t1");
                String status=t1Trial?
                    (accepted?
                        "T1LAUNCHER: Android reportó entrega a la instancia existente de Maps; verificá ruta en pantalla":
                        "T1LAUNCHER: NO se verificó entrega a Maps virtual; sin relanzamiento automático"):
                    (accepted?"T1 CONFIRMÓ RECEPCIÓN CIFRADA":
                        "T1 recibió el destino, pero la navegación elegida no se inició");
                SharedPreferences.Editor edit=p.edit().putString("last_status",status);
                if(accepted)edit.putInt("sent",p.getInt("sent",0)+1);
                edit.apply();return new Delivery(accepted,status);
            }
        }catch(Exception e){
            String detail=String.valueOf(e.getMessage());
            if(detail.length()>160)detail=detail.substring(0,160);
            String status="SIN CONEXIÓN BRIDGE · "+e.getClass().getSimpleName()+
                " · "+detail+
                " · vínculo QR conservado, no hace falta desvincular";
            prefs(c).edit().putString("last_status",status).apply();
            return new Delivery(false,status);
        }
    }
}
