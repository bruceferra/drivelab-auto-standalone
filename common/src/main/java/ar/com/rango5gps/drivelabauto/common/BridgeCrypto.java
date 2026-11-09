package ar.com.rango5gps.drivelabauto.common;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Independent DriveLab Auto protocol v2. LAN only, ECDH P-256 + AES-128-GCM. */
public final class BridgeCrypto {
    private BridgeCrypto() {}
    public static final int NAV_PORT=48391, PAIR_PORT=48392, PAIR_MAGIC=0x44534132;
    private static final int MAGIC=0x444C4232, MAX_PACKET=1024;
    private static final long MAX_AGE_MS=300000L;
    private static final SecureRandom RANDOM=new SecureRandom();
    public static final class Packet {
        public final String kind,value;
        public final long createdAt;
        public Packet(String k,String v,long created){kind=k;value=v;createdAt=created;}
    }
    public static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator gen=KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"),RANDOM);
        return gen.generateKeyPair();
    }
    public static byte[] publicBytes(KeyPair k){return k.getPublic().getEncoded();}
    public static byte[] derive(PrivateKey own,byte[] remote) throws Exception {
        if(remote==null||remote.length<70||remote.length>200)throw new GeneralSecurityException("Invalid peer key");
        PublicKey peer=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(remote));
        KeyAgreement exchange=KeyAgreement.getInstance("ECDH");
        exchange.init(own);exchange.doPhase(peer,true);
        byte[] shared=exchange.generateSecret();
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        sha.update("DriveLabAuto-v2-AES".getBytes(StandardCharsets.UTF_8));
        sha.update(shared);
        return Arrays.copyOf(sha.digest(),16);
    }
    public static String sas(byte[] key) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        sha.update("DriveLabAuto-v2-SAS".getBytes(StandardCharsets.UTF_8));
        byte[] h=sha.digest(key);
        long v=((h[0]&255L)<<24)|((h[1]&255L)<<16)|((h[2]&255L)<<8)|(h[3]&255L);
        return String.format(Locale.US,"%06d",v%1000000L);
    }
    public static String packKey(byte[] key){return Base64.getUrlEncoder().withoutPadding().encodeToString(key);}
    public static byte[] unpackKey(String s) throws Exception {
        if(s==null||!s.matches("[A-Za-z0-9_-]{22}"))throw new GeneralSecurityException("Invalid key");
        byte[] k=Base64.getUrlDecoder().decode(s);
        if(k.length!=16)throw new GeneralSecurityException("Invalid key length");
        return k;
    }
    public static String nonce(byte[] packet){
        return Base64.getEncoder().encodeToString(Arrays.copyOfRange(packet,0,12));
    }
    private static void validate(String kind,String value) throws GeneralSecurityException {
        if("ping".equals(kind)&&"".equals(value))return;
        if(("ack".equals(kind)||"not_ready".equals(kind))&&value!=null&&value.matches("[A-Za-z0-9+/]{16}"))return;
        if("coord".equals(kind)&&value!=null&&value.matches("-?[0-9]{1,2}(?:\\.[0-9]{1,9})?,-?[0-9]{1,3}(?:\\.[0-9]{1,9})?")){
            String[] a=value.split(",");
            try {double lat=Double.parseDouble(a[0]),lon=Double.parseDouble(a[1]);
                if(Math.abs(lat)<=90&&Math.abs(lon)<=180&&!(lat==0&&lon==0))return;
            }catch(NumberFormatException ignored){}
        }
        if("search".equals(kind)&&value!=null&&value.length()>=3&&value.length()<=180&&
           !value.contains("\n")&&!value.contains("\r")&&value.matches("(?s).*\\p{L}.*"))return;
        throw new GeneralSecurityException("Invalid navigation payload");
    }
    public static byte[] encrypt(byte[] key,String kind,String value) throws Exception {
        return encryptAt(key,kind,value,System.currentTimeMillis());
    }
    // Package-private time injection for cryptographic clock-skew tests.
    static byte[] encryptAt(byte[] key,String kind,String value,long createdAt) throws Exception {
        validate(kind,value);
        ByteArrayOutputStream raw=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(raw);
        out.writeInt(MAGIC);out.writeLong(createdAt);
        out.writeUTF(kind);out.writeUTF(value);out.flush();
        byte[] iv=new byte[12];RANDOM.nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        byte[] enc=cipher.doFinal(raw.toByteArray());
        byte[] combined=new byte[iv.length+enc.length];
        System.arraycopy(iv,0,combined,0,iv.length);
        System.arraycopy(enc,0,combined,iv.length,enc.length);
        if(combined.length>MAX_PACKET)throw new GeneralSecurityException("Oversized packet");
        return combined;
    }
    /**
     * Pairing proof/ACK uses the explicit two-minute, on-device pairing window,
     * six-digit SAS confirmation and one-time ECDH session ID, not the
     * absolute wall-clock time of a second (possibly offline) Android device.
     * ONLY ping/ack are valid in this context. Routes retain strict expiry.
     */
    public static Packet decryptPairing(byte[] key,byte[] payload) throws Exception {
        Packet p=decryptInternal(key,payload,false);
        if(!"ping".equals(p.kind)&&!"ack".equals(p.kind))
            throw new GeneralSecurityException("Invalid pairing packet type");
        return p;
    }
    public static Packet decrypt(byte[] key,byte[] payload) throws Exception {
        return decryptInternal(key,payload,true,System.currentTimeMillis());
    }

    /**
     * Only used by a previously authenticated Sender after an authenticated
     * LAN ping. The ACK timestamp supplies a fresh remote clock offset.
     * Route expiration continues to be checked on both devices.
     */
    public static Packet decryptWithClockOffset(byte[] key,byte[] payload,long offsetMillis)
            throws Exception {
        return decryptInternal(key,payload,true,System.currentTimeMillis()-offsetMillis);
    }
    public static byte[] encryptWithClockOffset(byte[] key,String kind,String value,long offsetMillis)
            throws Exception {
        return encryptAt(key,kind,value,System.currentTimeMillis()-offsetMillis);
    }
    private static Packet decryptInternal(byte[] key,byte[] payload,boolean checkClock) throws Exception {
        return decryptInternal(key,payload,checkClock,System.currentTimeMillis());
    }
    private static Packet decryptInternal(byte[] key,byte[] payload,boolean checkClock,long now)
            throws Exception {
        if(payload==null||payload.length<28||payload.length>MAX_PACKET)
            throw new GeneralSecurityException("Invalid packet length");
        byte[] iv=Arrays.copyOfRange(payload,0,12);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        byte[] dec=cipher.doFinal(payload,12,payload.length-12);
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(dec));
        if(input.readInt()!=MAGIC)throw new GeneralSecurityException("Bad packet magic");
        long created=input.readLong();
        if(checkClock){
            // Overflow-safe age check (malformed extremes must not wrap).
            if(created>now+MAX_AGE_MS||created<now-MAX_AGE_MS)
                throw new GeneralSecurityException("Expired message");
        }
        String kind=input.readUTF(),value=input.readUTF();
        validate(kind,value);
        if(input.available()!=0)throw new GeneralSecurityException("Trailing data");
        return new Packet(kind,value,created);
    }
    public static byte[] read(DataInputStream in) throws Exception {
        int size=in.readInt();
        if(size<28||size>MAX_PACKET)throw new IOException("Invalid packet size");
        byte[] b=new byte[size];in.readFully(b);return b;
    }
    public static void write(DataOutputStream out,byte[] data) throws Exception {
        if(data.length>MAX_PACKET)throw new IOException("Packet too big");
        out.writeInt(data.length);out.write(data);out.flush();
    }
}
