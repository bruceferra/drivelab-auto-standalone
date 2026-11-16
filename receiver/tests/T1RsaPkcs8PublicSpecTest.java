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
        PrivateKey imported=factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        Signature sign=Signature.getInstance("SHA256withRSA");
        sign.initSign(imported);
        sign.update(new byte[]{11,12,13,14});
        byte[] sig=sign.sign();
        Signature verify=Signature.getInstance("SHA256withRSA");
        verify.initVerify(factory.generatePublic(spec));
        verify.update(new byte[]{11,12,13,14});
        check(verify.verify(sig),
              "Previously authorized private key signs against recovered public key");
        rejected(Arrays.copyOf(bytes,7));
        byte[] corrupted=bytes.clone();
        corrupted[0]=0x31;
        rejected(corrupted);
        String src=Files.readString(Path.of(
           "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1LocalAdbClient.java"));
        check(src.contains("T1RsaPkcs8PublicSpec.recover(encoded)")&&
              !src.contains("(RSAPrivateCrtKey)")&&
              src.contains("new PKCS8EncodedKeySpec(encoded)")&&
              src.contains("KEY_FILE=\"t1_drivelab_local_adb_rsa.pk8\""),
              "Host key recovery must not cast Android Conscrypt key or change key filename");
        System.out.println("PASS legacy RSA host key survives Android Conscrypt reload");
    }
}
