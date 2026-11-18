package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.math.BigInteger;

/** ADB HOST protocol over the CAR's LOCAL LOOPBACK only.
 *
 * NOT a privileged shell. The owner first enables TCP ADB from an existing,
 * physically authorized USB/WebADB host. Jetour may persist port 5556
 * while an explicitly started session listens on 5555. Android then asks to
 * trust this app's NEW 2048-bit host key. No root, no Shizuku, no commands
 * against remote devices and no arbitrary user-input shell execution.
 *
 * This is intentionally a short-lived host connection per whitelisted command;
 * it never exposes an inbound socket or starts adbd by itself.
 */
final class T1LocalAdbClient {
    private static final int MAX_PACKET=1024*1024;
    private static final int PERSISTENT_PORT=5556, MANUAL_PORT=5555;
    private static volatile int lastLocalPort=PERSISTENT_PORT;
    private static final int PROTOCOL=0x01000000;
    private static final String LOOPBACK="127.0.0.1";
    private static final String KEY_FILE="t1_drivelab_local_adb_rsa.pk8";
    private static final byte[] SHA1_DIGEST_INFO={
        0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,
        0x1a,0x05,0x00,0x04,0x14
    };
    private T1LocalAdbClient(){}

    /** Read-only socket discovery: only the two user-observed loopback ports.
     * Never starts ADB, sets properties, roots the car or opens a listener.
     */
    private static Socket openLocal()throws Exception{
        int first=lastLocalPort==MANUAL_PORT?MANUAL_PORT:PERSISTENT_PORT;
        int second=first==PERSISTENT_PORT?MANUAL_PORT:PERSISTENT_PORT;
        java.net.ConnectException failure=null;
        for(int port:new int[]{first,second}){
            Socket socket=new Socket();
            try{
                socket.connect(new InetSocketAddress(LOOPBACK,port),1500);
                socket.setSoTimeout(9000);
                lastLocalPort=port;
                return socket;
            }catch(java.net.ConnectException e){
                failure=e;
                try{socket.close();}catch(Exception ignored){}
            }catch(java.net.SocketTimeoutException e){
                try{socket.close();}catch(Exception ignored){}
            }catch(Exception e){
                try{socket.close();}catch(Exception ignored){}
                throw e;
            }
        }
        throw new java.net.ConnectException(
            "ADB no escucha en 127.0.0.1:5556 ni :5555"+
            (failure==null?"":" · "+failure.getMessage()));
    }
    static int connectedPort(){return lastLocalPort;}
    static String shell(Context context,String command)throws Exception{
        // STRICT command whitelist, no shell metacharacters or package names.
        // Only known Waze/Maps force-stop + process checks, task/geometry
        // management, and exact accessibility-list recovery are authorized.
        // No steering wheel key injection or arbitrary ADB commands.
        // No global BACK, tap, display 0, arbitrary shell, or OEM writes.
        if(!command.equals("am supports-split-screen-multi-window")&&
           !command.matches("am start --display 1 --windowingMode 3 -n com\\.waze/[A-Za-z0-9_.$/]{1,220}")&&
           !command.matches("am start --display 1 --windowingMode 4 -n com\\.google\\.android\\.apps\\.maps/[A-Za-z0-9_.$/]{1,220}")&&
           !command.equals("am force-stop com.waze")&&
           !command.equals("am force-stop com.google.android.apps.maps")&&
           !command.equals("pidof com.waze")&&
           !command.equals("pidof com.google.android.apps.maps")&&
           !command.equals("am stack list")&&
           !command.equals("dumpsys activity activities")&&
           !command.equals("dumpsys window windows")&&
           !command.equals("dumpsys input")&&
           !command.matches(
               "settings put secure enabled_accessibility_services "+
               "[A-Za-z0-9_.$/:]{1,1024}")&&
           !command.equals("settings put secure accessibility_enabled 1")&&
           !command.equals("settings get secure enabled_accessibility_services")&&
           !command.equals("settings get secure accessibility_enabled")&&
           !command.matches(
              "am task resize [1-9][0-9]{0,6}( [0-9]{1,5}){4}")&&
           !command.matches("am stack remove [1-9][0-9]{0,6}"))
            throw new SecurityException("Comando ADB no permitido");
        String phase="abrir TCP 127.0.0.1:5556/5555";
        try(Socket sock=openLocal()){
            DataInputStream in=new DataInputStream(sock.getInputStream());
            phase="recuperar clave RSA autorizada";
            KeyPair pair=hostKey(context.getApplicationContext());
            phase="negociar autorización ADB";
            send(sock,"CNXN",PROTOCOL,4096,
                ascii("host::\0"));
            boolean signatureSent=false,publicKeySent=false,ready=false;
            for(int i=0;i<8;i++){
                Message msg=read(in);
                if(msg.is("CNXN")){ready=true;break;}
                if(!msg.is("AUTH")||msg.arg0!=1||msg.payload.length!=20)
                    throw new IllegalStateException(
                        "Respuesta ADB incompatible durante autorización");
                if(!signatureSent){
                    phase="firmar desafío ADB";
                    send(sock,"AUTH",2,0,signChallenge(
                        pair.getPrivate(),msg.payload));
                    phase="esperar autorización ADB";
                    signatureSent=true;
                }else if(!publicKeySent){
                    send(sock,"AUTH",3,0,adbPublicKey(
                        (RSAPublicKey)pair.getPublic()));
                    publicKeySent=true;
                    // This may trigger Android's USB debugging authorization
                    // prompt. NEVER claim success until CNXN arrives.
                }else throw new SecurityException(
                    "Android no autorizó la clave ADB de DriveLab");
            }
            if(!ready)throw new SecurityException(
                "ADB local no autorizó a DriveLab (revisar pantalla de T1)");
            phase="leer tareas por shell ADB";
            send(sock,"OPEN",1,0,ascii("shell:"+command+"\0"));
            int remote=0;
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            for(int i=0;i<150;i++){
                Message msg=read(in);
                if(msg.is("OKAY")){
                    if(remote==0)remote=msg.arg0;
                }else if(msg.is("WRTE")){
                    if(remote==0)remote=msg.arg0;
                    if(output.size()+msg.payload.length>96*1024)
                        throw new IllegalStateException("Salida ADB excesiva");
                    output.write(msg.payload);
                    send(sock,"OKAY",1,remote,new byte[0]);
                }else if(msg.is("CLSE")){
                    send(sock,"CLSE",1,msg.arg0,new byte[0]);
                    // Android 11 lacks ByteArrayOutputStream.toString(Charset).
                    // Decode bytes with the API 1 String(byte[], Charset) instead.
                    return new String(output.toByteArray(),StandardCharsets.UTF_8);
                }else if(msg.is("AUTH"))
                    throw new SecurityException("ADB perdió autorización");
                else if(msg.is("CNXN")||msg.is("OPEN"))
                    throw new IllegalStateException("Respuesta shell ADB inválida");
            }
            throw new IllegalStateException("ADB no cerró la sesión shell");
        }catch(java.net.ConnectException ex){throw ex;
        }catch(java.net.SocketTimeoutException ex){throw ex;
        }catch(Exception ex){
            throw new IllegalStateException("fase: "+phase+" · "+
                ex.getClass().getSimpleName()+": "+
                String.valueOf(ex.getMessage()),ex);
        }
    }
    static String connectivityHint(Throwable error){
        String name=error==null?"":error.getClass().getSimpleName();
        if(error instanceof java.net.ConnectException)
            return "ADB no responde en los puertos locales 5556 y 5555. "+
                "Que 5556 esté configurado como persistente no asegura "+
                "que el servicio ADB escuche tras reiniciar el vehículo.";
        if(error instanceof java.net.SocketTimeoutException)
            return "ADB no respondió o espera autorización. Revisá en la T1 "+
                "el diálogo «Permitir depuración USB» y aceptalo SOLO si "+
                "reconocés esta prueba.";
        return "ADB local no disponible: "+name+
            " · "+String.valueOf(error==null?"":error.getMessage());
    }
    private static KeyPair hostKey(Context c)throws Exception{
        File file=new File(c.getFilesDir(),KEY_FILE);
        KeyFactory factory=KeyFactory.getInstance("RSA");
        if(file.exists()){
            // Importing PKCS8 into Android Conscrypt can produce a non-CRT
            // OpenSSLRSAPrivateKey, triggering a cast failure during signing.
            // Restore ALL CRT parameters without rotating the existing key.
            byte[] encoded=Files.readAllBytes(file.toPath());
            java.security.spec.RSAPrivateCrtKeySpec crt=
                T1RsaPkcs8PublicSpec.recoverPrivate(encoded);
            PrivateKey priv=factory.generatePrivate(crt);
            return new KeyPair(factory.generatePublic(
                new java.security.spec.RSAPublicKeySpec(
                    crt.getModulus(),crt.getPublicExponent())),priv);
        }
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair=generator.generateKeyPair();
        try(FileOutputStream out=c.openFileOutput(KEY_FILE,
                Context.MODE_PRIVATE)){
            out.write(pair.getPrivate().getEncoded());
            out.getFD().sync();
        }
        return pair;
    }
    private static byte[] signChallenge(PrivateKey key,byte[] token)
            throws Exception{
        byte[] digestInfo=new byte[SHA1_DIGEST_INFO.length+20];
        System.arraycopy(SHA1_DIGEST_INFO,0,digestInfo,0,
            SHA1_DIGEST_INFO.length);
        System.arraycopy(token,0,digestInfo,SHA1_DIGEST_INFO.length,20);
        Signature sign=Signature.getInstance("NONEwithRSA");
        sign.initSign(key);
        sign.update(digestInfo);
        return sign.sign();
    }
    private static byte[] little(BigInteger value,int bytes){
        byte[] big=value.toByteArray(),result=new byte[bytes];
        for(int i=0;i<bytes;i++){
            int from=big.length-1-i;
            result[i]=from<0?0:big[from];
        }
        return result;
    }
    private static byte[] adbPublicKey(RSAPublicKey key){
        BigInteger mod=key.getModulus(),two32=BigInteger.ONE.shiftLeft(32);
        BigInteger n0=mod.mod(two32);
        long inv=two32.subtract(n0.modInverse(two32))
            .mod(two32).longValue();
        ByteBuffer b=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);b.putInt((int)inv);
        b.put(little(mod,256));
        b.put(little(BigInteger.ONE.shiftLeft(4096).mod(mod),256));
        b.putInt(key.getPublicExponent().intValue());
        String pub=Base64.encodeToString(b.array(),Base64.NO_WRAP)+
            " drivelab-t1@localhost\0";
        return ascii(pub);
    }
    private static byte[] ascii(String s){
        return s.getBytes(StandardCharsets.UTF_8);
    }
    private static int id(String name){
        byte[] raw=name.getBytes(StandardCharsets.US_ASCII);
        return (raw[0]&255)|((raw[1]&255)<<8)|
            ((raw[2]&255)<<16)|((raw[3]&255)<<24);
    }
    private static void send(Socket sock,String code,int a,int b,byte[] payload)
            throws Exception{
        if(payload.length>MAX_PACKET)throw new IllegalArgumentException(
            "ADB packet exceeds limit");
        int sum=0;
        for(byte x:payload)sum+=(x&255);
        int cmd=id(code);
        ByteBuffer header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(cmd).putInt(a).putInt(b).putInt(payload.length)
            .putInt(sum).putInt(cmd^0xffffffff);
        sock.getOutputStream().write(header.array());
        sock.getOutputStream().write(payload);
        sock.getOutputStream().flush();
    }
    private static final class Message {
        final int code,arg0,arg1;
        final byte[] payload;
        Message(int c,int a,int b,byte[] p){
            code=c;arg0=a;arg1=b;payload=p;
        }
        boolean is(String name){return code==id(name);}
    }
    private static Message read(DataInputStream in)throws Exception{
        byte[] header=new byte[24];
        in.readFully(header);
        ByteBuffer b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int code=b.getInt(),arg0=b.getInt(),arg1=b.getInt();
        int len=b.getInt(),checksum=b.getInt(),magic=b.getInt();
        if(magic!=(code^0xffffffff)||len<0||len>MAX_PACKET)
            throw new IllegalStateException("ADB packet inválido");
        byte[] payload=new byte[len];
        in.readFully(payload);
        int actual=0;
        for(byte x:payload)actual+=(x&255);
        if(checksum!=actual&&checksum!=0)
            throw new IllegalStateException("Checksum ADB incorrecto");
        return new Message(code,arg0,arg1,payload);
    }
}
