package ar.com.rango5gps.drivelabauto.common;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated local broadcast discovery for an ALREADY paired Sender.
 * Nothing is announced to unpaired devices. Does not rotate/change keys.
 * A MAC proves possession of the same AES key that protects directions.
 */
public final class BridgeDiscoveryProof {
    public static final int PORT=48393;
    public static final int NONCE=16, SIZE=4+NONCE+32;
    private static final byte[] REQ={'D','L','B','1'};
    private static final byte[] ACK={'D','L','B','2'};
    private static final byte[] DOMAIN_REQ={
        'D','L','B','-','q','r','-','v','1',':'};
    private static final byte[] DOMAIN_ACK={
        'D','L','B','-','a','c','k','-','v','1',':'};
    private BridgeDiscoveryProof(){}
    public static byte[] newNonce(){
        byte[] bytes=new byte[NONCE];new SecureRandom().nextBytes(bytes);
        return bytes;
    }
    private static byte[] sign(byte[] key,byte[] domain,byte[] nonce)throws Exception{
        if(key==null||key.length!=16||nonce==null||nonce.length!=NONCE)
            throw new SecurityException("Vínculo/no nonce inválidos");
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key,"HmacSHA256"));
        mac.update(domain);return mac.doFinal(nonce);
    }
    private static byte[] packet(byte[] magic,byte[] key,byte[] nonce,
                                 byte[] domain)throws Exception{
        return ByteBuffer.allocate(SIZE).put(magic).put(nonce)
            .put(sign(key,domain,nonce)).array();
    }
    public static byte[] request(byte[] key,byte[] nonce)throws Exception{
        return packet(REQ,key,nonce,DOMAIN_REQ);
    }
    public static boolean acceptsRequest(byte[] key,byte[] raw,int length){
        return valid(raw,length,REQ,key,DOMAIN_REQ,null);
    }
    public static byte[] response(byte[] key,byte[] request)throws Exception{
        if(!acceptsRequest(key,request,request==null?0:request.length))
            throw new SecurityException("Descubrimiento no autenticado");
        return packet(ACK,key,Arrays.copyOfRange(request,4,4+NONCE),DOMAIN_ACK);
    }
    public static boolean acceptsResponse(byte[] key,byte[] nonce,
                                           byte[] raw,int length){
        return valid(raw,length,ACK,key,DOMAIN_ACK,nonce);
    }
    private static boolean valid(byte[] raw,int length,byte[] magic,
                                 byte[] key,byte[] domain,byte[] expect){
        try{
            if(raw==null||length!=SIZE||raw.length<length||
               !MessageDigest.isEqual(magic,Arrays.copyOfRange(raw,0,4)))
                return false;
            byte[] nonce=Arrays.copyOfRange(raw,4,4+NONCE);
            if(expect!=null&&!MessageDigest.isEqual(expect,nonce))return false;
            return MessageDigest.isEqual(
                sign(key,domain,nonce),Arrays.copyOfRange(raw,4+NONCE,SIZE));
        }catch(Exception invalid){return false;}
    }
}
