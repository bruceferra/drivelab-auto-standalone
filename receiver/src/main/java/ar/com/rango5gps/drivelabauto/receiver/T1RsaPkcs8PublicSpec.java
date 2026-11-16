package ar.com.rango5gps.drivelabauto.receiver;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

/** Restore the public half of an ADB RSA host key persisted as PKCS#8.
 *
 * Android/Conscrypt may load the private half as OpenSSLRSAPrivateKey
 * instead of RSAPrivateCrtKey. Never cast the loaded key to CRT, never
 * regenerate an already approved host key, and never use a guessed exponent.
 * Parse only the RSA PKCS#1 public fields within the private PKCS#8 wrapper.
 */
final class T1RsaPkcs8PublicSpec {
    private static final byte[] RSA_ENCRYPTION_OID={
        0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,0x01,0x01,0x01
    };

    private T1RsaPkcs8PublicSpec(){}

    static RSAPublicKeySpec recover(byte[] pkcs8)throws InvalidKeySpecException {
        Der outer=new Der(pkcs8);
        Der info=new Der(outer.read(0x30));
        outer.end();
        if(!info.integer().equals(BigInteger.ZERO))
            throw new InvalidKeySpecException("PKCS#8 RSA version unsupported");
        Der algorithm=new Der(info.read(0x30));
        if(!Arrays.equals(algorithm.read(0x06),RSA_ENCRYPTION_OID))
            throw new InvalidKeySpecException("Not an RSA PKCS#8 key");
        if(algorithm.hasMore()){
            if(algorithm.read(0x05).length!=0)
                throw new InvalidKeySpecException("Invalid RSA NULL parameters");
        }
        algorithm.end();
        byte[] pkcs1=info.read(0x04);
        info.end();
        Der wrapper=new Der(pkcs1);
        Der rsa=new Der(wrapper.read(0x30));
        wrapper.end();
        if(!rsa.integer().equals(BigInteger.ZERO))
            throw new InvalidKeySpecException("RSA multi-prime unsupported");
        BigInteger modulus=rsa.integer();
        BigInteger exponent=rsa.integer();
        if(modulus.bitLength()!=2048 || !modulus.testBit(0) ||
           exponent.compareTo(BigInteger.valueOf(3))<0 ||
           !exponent.testBit(0) || exponent.bitLength()>32)
            throw new InvalidKeySpecException("Invalid 2048-bit RSA host key");
        // Remaining PKCS#1 private fields are validated when KeyFactory
        // imports the full PKCS#8 key. Only the public half is needed here.
        return new RSAPublicKeySpec(modulus,exponent);
    }

    private static final class Der {
        private final byte[] bytes;
        private int offset;
        Der(byte[] bytes)throws InvalidKeySpecException {
            if(bytes==null||bytes.length==0||bytes.length>8192)
                throw new InvalidKeySpecException("RSA key size invalid");
            this.bytes=bytes;
        }
        boolean hasMore(){return offset<bytes.length;}
        void end()throws InvalidKeySpecException {
            if(hasMore())throw new InvalidKeySpecException("Trailing RSA DER data");
        }
        BigInteger integer()throws InvalidKeySpecException {
            byte[] v=read(0x02);
            if(v.length==0||v.length>257||v[0]<0)
                throw new InvalidKeySpecException("Invalid RSA DER integer");
            return new BigInteger(1,v);
        }
        byte[] read(int wanted)throws InvalidKeySpecException {
            if(offset+2>bytes.length||
               (bytes[offset++]&0xff)!=wanted)
                throw new InvalidKeySpecException("Unexpected RSA DER tag");
            int first=bytes[offset++]&0xff;
            long count;
            if(first<128)count=first;
            else {
                int size=first&0x7f;
                if(size==0||size>3||offset+size>bytes.length)
                    throw new InvalidKeySpecException("Invalid RSA DER length");
                count=0;
                for(int i=0;i<size;i++)
                    count=(count<<8)|(bytes[offset++]&0xff);
            }
            if(count<0||count>bytes.length-offset)
                throw new InvalidKeySpecException("Truncated RSA DER field");
            int next=offset+(int)count;
            byte[] v=Arrays.copyOfRange(bytes,offset,next);
            offset=next;
            return v;
        }
    }
}
