package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
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

/**
 * READ ONLY local ADB bridge. New independent RSA identity, approved
 * explicitly by the vehicle owner via Android's normal ADB dialog.
 * The only commands are dumpsys queries. No shell writes / killing
 * processes / changing application state or vehicle firmware.
 */
final class BridgeAdbReadOnly {
    private static final int MAX_PACKET=1024*1024;
    private static final int PROTOCOL=0x01000000;
    private static final int MAX_RESPONSE=8*1024*1024;
    private static final String KEY_FILE="bridge_adb_readonly_key.pk8";
    private static final byte[] SHA1_INFO={
        0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,
        0x1a,0x05,0x00,0x04,0x14
    };
    private BridgeAdbReadOnly(){}
    static String read(Context context,String command)throws Exception{
        if(!"dumpsys window windows".equals(command)&&
           !"dumpsys activity activities".equals(command)&&
           !"dumpsys SurfaceFlinger --list".equals(command)&&
           !"dumpsys SurfaceFlinger".equals(command))
            throw new SecurityException("Solo diagnóstico sin modificaciones");
        try(Socket socket=openLoopback()){
            DataInputStream input=new DataInputStream(socket.getInputStream());
            KeyPair key=key(context.getApplicationContext());
            send(socket,"CNXN",PROTOCOL,4096,ascii("host::\0"));
            boolean authorized=false,signed=false,advertised=false;
            for(int i=0;i<8;i++){
                Packet packet=receive(input);
                if(packet.is("CNXN")){authorized=true;break;}
                if(!packet.is("AUTH")||packet.arg0!=1||
                   packet.bytes.length!=20)
                    throw new SecurityException("Respuesta ADB inesperada");
                if(!signed){
                    send(socket,"AUTH",2,0,
                        sign(key.getPrivate(),packet.bytes));
                    signed=true;
                }else if(!advertised){
                    send(socket,"AUTH",3,0,
                        androidKey((RSAPublicKey)key.getPublic()));
                    advertised=true;
                }else throw new SecurityException(
                    "Android no autorizó la nueva clave Bridge");
            }
            if(!authorized)
                throw new SecurityException("Falta autorizar ADB Bridge en Android");
            send(socket,"OPEN",1,0,ascii("shell:"+command+"\0"));
            int remote=0;
            ByteArrayOutputStream raw=new ByteArrayOutputStream();
            for(int i=0;i<32768;i++){
                Packet p=receive(input);
                if(p.is("OKAY")){
                    if(remote==0)remote=p.arg0;
                }else if(p.is("WRTE")){
                    if(remote==0)remote=p.arg0;
                    if(raw.size()+p.bytes.length>MAX_RESPONSE)
                        throw new SecurityException(
                            "Informe supera 8 MB; lectura descartada");
                    raw.write(p.bytes);
                    send(socket,"OKAY",1,remote,new byte[0]);
                }else if(p.is("CLSE")){
                    send(socket,"CLSE",1,p.arg0,new byte[0]);
                    return new String(raw.toByteArray(),
                        StandardCharsets.UTF_8);
                }else if(p.is("AUTH")){
                    throw new SecurityException("Autorización ADB revocada");
                }
            }
            throw new IllegalStateException("ADB no finalizó el informe");
        }
    }
    private static Socket openLoopback()throws Exception{
        for(int port:new int[]{5556,5555}){
            Socket result=new Socket();
            try{
                result.connect(new InetSocketAddress("127.0.0.1",port),1600);
                result.setSoTimeout(12000);
                return result;
            }catch(Exception ex){
                try{result.close();}catch(Exception ignored){}
            }
        }
        throw new java.net.ConnectException(
            "ADB local no escucha en 127.0.0.1:5556 / :5555");
    }
    private static KeyPair key(Context c)throws Exception{
        File file=new File(c.getFilesDir(),KEY_FILE);
        KeyFactory factory=KeyFactory.getInstance("RSA");
        if(file.exists()){
            java.security.spec.RSAPrivateCrtKeySpec crt=
                BridgeRsaKeySpec.recoverPrivate(
                    Files.readAllBytes(file.toPath()));
            return new KeyPair(factory.generatePublic(
                new java.security.spec.RSAPublicKeySpec(
                    crt.getModulus(),crt.getPublicExponent())),
                factory.generatePrivate(crt));
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
    private static byte[] sign(PrivateKey key,byte[] nonce)throws Exception{
        byte[] digest=new byte[SHA1_INFO.length+20];
        System.arraycopy(SHA1_INFO,0,digest,0,SHA1_INFO.length);
        System.arraycopy(nonce,0,digest,SHA1_INFO.length,20);
        Signature signature=Signature.getInstance("NONEwithRSA");
        signature.initSign(key);
        signature.update(digest);
        return signature.sign();
    }
    private static byte[] le(BigInteger value,int count){
        byte[] big=value.toByteArray(),out=new byte[count];
        for(int i=0;i<count;i++)
            out[i]=i<big.length?big[big.length-1-i]:0;
        return out;
    }
    private static byte[] androidKey(RSAPublicKey key){
        BigInteger modulus=key.getModulus(),pow=BigInteger.ONE.shiftLeft(32);
        long inverse=pow.subtract(modulus.mod(pow).modInverse(pow))
            .mod(pow).longValue();
        ByteBuffer data=ByteBuffer.allocate(524)
            .order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(64).putInt((int)inverse)
            .put(le(modulus,256))
            .put(le(BigInteger.ONE.shiftLeft(4096).mod(modulus),256))
            .putInt(key.getPublicExponent().intValue());
        return ascii(Base64.encodeToString(data.array(),Base64.NO_WRAP)+
            " drivelab-bridge@localhost\0");
    }
    private static byte[] ascii(String s){
        return s.getBytes(StandardCharsets.UTF_8);
    }
    private static int token(String name){
        byte[] raw=name.getBytes(StandardCharsets.US_ASCII);
        return (raw[0]&255)|((raw[1]&255)<<8)|
            ((raw[2]&255)<<16)|((raw[3]&255)<<24);
    }
    private static void send(Socket socket,String command,int a,int b,
                             byte[] payload)throws Exception{
        if(payload.length>MAX_PACKET)
            throw new SecurityException("Paquete ADB demasiado grande");
        int sum=0;
        for(byte part:payload)sum+=part&255;
        int code=token(command);
        ByteBuffer header=ByteBuffer.allocate(24)
            .order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(code).putInt(a).putInt(b).putInt(payload.length)
            .putInt(sum).putInt(code^0xffffffff);
        socket.getOutputStream().write(header.array());
        socket.getOutputStream().write(payload);
        socket.getOutputStream().flush();
    }
    private static final class Packet{
        final int code,arg0;
        final byte[] bytes;
        Packet(int c,int a,byte[] b){code=c;arg0=a;bytes=b;}
        boolean is(String expected){return code==token(expected);}
    }
    private static Packet receive(DataInputStream input)throws Exception{
        byte[] header=new byte[24];
        input.readFully(header);
        ByteBuffer data=ByteBuffer.wrap(header)
            .order(ByteOrder.LITTLE_ENDIAN);
        int code=data.getInt(),a=data.getInt();
        data.getInt();
        int length=data.getInt(),checksum=data.getInt(),magic=data.getInt();
        if(magic!=(code^0xffffffff)||length<0||length>MAX_PACKET)
            throw new SecurityException("Encabezado ADB inválido");
        byte[] body=new byte[length];
        input.readFully(body);
        int sum=0;
        for(byte byteValue:body)sum+=byteValue&255;
        if(checksum!=sum&&checksum!=0)
            throw new SecurityException("Checksum ADB incorrecto");
        return new Packet(code,a,body);
    }
}