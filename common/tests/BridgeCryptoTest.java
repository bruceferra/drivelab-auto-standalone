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
  System.out.println("OK: ECDH, six-digit SAS, AES-GCM, route, authenticated NACK, tamper rejection");
 }
}
