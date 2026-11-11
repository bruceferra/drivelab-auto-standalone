import ar.com.rango5gps.drivelabauto.common.BridgeCrypto;
import java.security.KeyPair;
import java.util.Arrays;
public class BridgeCryptoTest {
 public static void main(String[] args)throws Exception{
  KeyPair a=BridgeCrypto.newKeyPair(),b=BridgeCrypto.newKeyPair();
  byte[] ak=BridgeCrypto.derive(a.getPrivate(),BridgeCrypto.publicBytes(b));
  byte[] bk=BridgeCrypto.derive(b.getPrivate(),BridgeCrypto.publicBytes(a));
  if(!Arrays.equals(ak,bk)||!BridgeCrypto.sas(ak).equals(BridgeCrypto.sas(bk)))throw new AssertionError("ECDH");
  byte[] payload=BridgeCrypto.encrypt(ak,"coord","-34.9214,-57.9545");
  BridgeCrypto.Packet m=BridgeCrypto.decrypt(bk,payload);
  if(!m.kind.equals("coord")||!m.value.equals("-34.9214,-57.9545"))throw new AssertionError("route");
  byte[] ack=BridgeCrypto.encrypt(bk,"not_ready",BridgeCrypto.nonce(payload));
  if(!BridgeCrypto.decrypt(ak,ack).kind.equals("not_ready"))throw new AssertionError("nack");
  payload[13]^=1;
  try{BridgeCrypto.decrypt(bk,payload);throw new AssertionError("Tampered payload passed");}catch(javax.crypto.AEADBadTagException expected){}
  // QR pairing replaces manual SAS approval with a 192-bit token
  // captured optically. The proof is bound to an ephemeral ECDH public key.
  String ticket="7a".repeat(24);
  byte[] pa=BridgeCrypto.publicBytes(a);
  byte[] pb=BridgeCrypto.publicBytes(b);
  byte[] qa=BridgeCrypto.qrPairProof(ticket,pa);
  byte[] qaAgain=BridgeCrypto.qrPairProof(ticket,pa);
  if(qa.length!=32||!Arrays.equals(qa,qaAgain))
    throw new AssertionError("QR proof not deterministic");
  if(Arrays.equals(qa,BridgeCrypto.qrPairProof(ticket,pb)))
    throw new AssertionError("QR proof not bound to sender ephemeral public key");
  try{
    BridgeCrypto.qrPairProof("000000",pa);
    throw new AssertionError("Weak/manual code wrongly accepted as QR ticket");
  }catch(java.security.GeneralSecurityException expected){}
  System.out.println("OK: ECDH, six-digit SAS, AES-GCM, route, authenticated NACK, tamper rejection, QR HMAC public-key binding");
 }
}
