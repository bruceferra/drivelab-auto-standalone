package ar.com.rango5gps.drivelabauto.boot;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

/** Rebuild the SAME approved ADB host identity from its existing RSA PKCS8.
 * Avoid importing the key as a provider-specific non-CRT RSA private key.
 */
final class BridgeAdbRsaSpec {
    private static final BigInteger ONE=BigInteger.ONE;
    private static final byte[] RSA_ENCRYPTION_OID={
        0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,0x01,0x01,0x01
    };
    private BridgeAdbRsaSpec(){}

    static RSAPublicKeySpec recover(byte[] pkcs8)throws InvalidKeySpecException {
        RSAPrivateCrtKeySpec key=recoverPrivate(pkcs8);
        return new RSAPublicKeySpec(key.getModulus(),key.getPublicExponent());
    }

    static RSAPrivateCrtKeySpec recoverPrivate(byte[] pkcs8)
            throws InvalidKeySpecException {
        Der outer=new Der(pkcs8);
        Der info=new Der(outer.read(0x30));
        outer.end();
        if(!info.integer().equals(BigInteger.ZERO))
            throw new InvalidKeySpecException("PKCS8 RSA version unsupported");
        Der algorithm=new Der(info.read(0x30));
        if(!Arrays.equals(algorithm.read(0x06),RSA_ENCRYPTION_OID))
            throw new InvalidKeySpecException("Not an RSA PKCS8 key");
        if(algorithm.hasMore()&&algorithm.read(0x05).length!=0)
            throw new InvalidKeySpecException("Invalid RSA NULL params");
        algorithm.end();
        byte[] pkcs1=info.read(0x04);
        info.end();
        Der wrapper=new Der(pkcs1);
        Der rsa=new Der(wrapper.read(0x30));
        wrapper.end();
        if(!rsa.integer().equals(BigInteger.ZERO))
            throw new InvalidKeySpecException("Multiprime RSA unsupported");
        BigInteger n=rsa.integer(),e=rsa.integer(),d=rsa.integer();
        BigInteger p=rsa.integer(),q=rsa.integer();
        BigInteger dP=rsa.integer(),dQ=rsa.integer(),qInv=rsa.integer();
        rsa.end();
        if(n.bitLength()!=2048||!n.testBit(0)||
           e.compareTo(BigInteger.valueOf(3))<0||!e.testBit(0)||
           e.bitLength()>32||d.signum()<=0||
           p.compareTo(ONE)<=0||q.compareTo(ONE)<=0||
           !p.multiply(q).equals(n)||
           !d.mod(p.subtract(ONE)).equals(dP)||
           !d.mod(q.subtract(ONE)).equals(dQ)||
           !q.modInverse(p).equals(qInv))
            throw new InvalidKeySpecException("Invalid RSA CRT host key");
        return new RSAPrivateCrtKeySpec(n,e,d,p,q,dP,dQ,qInv);
    }
    private static final class Der {
        private final byte[] bytes;
        private int offset;
        Der(byte[] b)throws InvalidKeySpecException {
            if(b==null||b.length==0||b.length>8192)
                throw new InvalidKeySpecException("Invalid RSA key length");
            bytes=b;
        }
        boolean hasMore(){return offset<bytes.length;}
        void end()throws InvalidKeySpecException {
            if(hasMore())throw new InvalidKeySpecException("Trailing DER data");
        }
        BigInteger integer()throws InvalidKeySpecException {
            byte[] value=read(0x02);
            if(value.length==0||value.length>257||value[0]<0)
                throw new InvalidKeySpecException("Invalid RSA integer");
            return new BigInteger(1,value);
        }
        byte[] read(int tag)throws InvalidKeySpecException {
            if(offset+2>bytes.length||(bytes[offset++]&255)!=tag)
                throw new InvalidKeySpecException("Unexpected DER tag");
            int first=bytes[offset++]&255;long length;
            if(first<128)length=first;
            else {
                int count=first&127;
                if(count==0||count>3||offset+count>bytes.length)
                    throw new InvalidKeySpecException("Invalid DER length");
                length=0;
                for(int i=0;i<count;i++)
                    length=(length<<8)|(bytes[offset++]&255);
            }
            if(length>bytes.length-offset)
                throw new InvalidKeySpecException("DER truncated");
            int next=offset+(int)length;
            byte[] result=Arrays.copyOfRange(bytes,offset,next);
            offset=next;
            return result;
        }
    }
}
