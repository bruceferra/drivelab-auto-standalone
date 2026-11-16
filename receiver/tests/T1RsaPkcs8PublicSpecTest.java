package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

/** Exercise actual persisted key recovery and an Android CRT-free provider. */
public final class T1RsaPkcs8PublicSpecTest {
    static void check(boolean yes,String message) {
        if(!yes)throw new AssertionError(message);
    }
    static void rejected(byte[] bad) {
        try {
            T1RsaPkcs8PublicSpec.recover(bad);
            throw new AssertionError("Malformed RSA key was accepted");
        }catch(java.security.spec.InvalidKeySpecException expected){}
    }
    public static void main(String[] args)throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair original=generator.generateKeyPair();
        byte[] bytes=original.getPrivate().getEncoded();
        RSAPublicKeySpec spec=T1RsaPkcs8PublicSpec.recover(bytes);
        RSAPublicKey fromGeneration=(RSAPublicKey)original.getPublic();
        check(spec.getModulus().equals(fromGeneration.getModulus())&&
              spec.getPublicExponent().equals(fromGeneration.getPublicExponent()),
              "PKCS#8 n/e must match original public key");
        KeyFactory factory=KeyFactory.getInstance("RSA");
        java.security.spec.RSAPrivateCrtKeySpec crt=T1RsaPkcs8PublicSpec.recoverPrivate(bytes);
        check(crt.getModulus().equals(fromGeneration.getModulus())&&
              crt.getPublicExponent().equals(fromGeneration.getPublicExponent())&&
              crt.getPrimeP().multiply(crt.getPrimeQ()).equals(crt.getModulus()),
              "Restored CRT params must match original approved RSA identity");
        PrivateKey imported=factory.generatePrivate(crt);
        Signature sign=Signature.getInstance("SHA256withRSA");
        sign.initSign(imported);
        sign.update(new byte[]{11,12,13,14});
        byte[] sig=sign.sign();
        Signature verify=Signature.getInstance("SHA256withRSA");
        verify.initVerify(factory.generatePublic(spec));
        verify.update(new byte[]{11,12,13,14});
        check(verify.verify(sig),
              "Previously authorized private key signs against recovered public key");
        byte[] prefix={0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,
            0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        byte[] adbChallenge=new byte[prefix.length+20];
        System.arraycopy(prefix,0,adbChallenge,0,prefix.length);
        Signature raw=Signature.getInstance("NONEwithRSA");
        raw.initSign(imported);
        raw.update(adbChallenge);
        byte[] rawSignature=raw.sign();
        Signature rawVerify=Signature.getInstance("NONEwithRSA");
        rawVerify.initVerify(original.getPublic());
        rawVerify.update(adbChallenge);
        check(rawVerify.verify(rawSignature),"Restored key must sign raw ADB authorization");
        rejected(Arrays.copyOf(bytes,7));
        byte[] corrupted=bytes.clone();
        corrupted[0]=0x31;
        rejected(corrupted);
        String src=Files.readString(Path.of(
           "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1LocalAdbClient.java"));
        check(src.contains("T1RsaPkcs8PublicSpec.recoverPrivate(encoded)")&&
              !src.contains("(RSAPrivateCrtKey)")&&
              src.contains("factory.generatePrivate(crt)")&&
              src.contains("KEY_FILE=\"t1_drivelab_local_adb_rsa.pk8\""),
              "Host key recovery must not cast Android Conscrypt key or change key filename");
        System.out.println("PASS legacy RSA host key survives Android Conscrypt reload");
    }
}
