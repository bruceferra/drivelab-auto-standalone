package ar.com.rango5gps.drivelabauto.common;

import java.security.GeneralSecurityException;

/**
 * Regression: offline car clock may be hours apart from the phone.
 * Proof and receipt are limited by a physical 2-minute SAS pairing session
 * enforced by ReceiverService; navigation always retains timestamp freshness.
 */
public final class BridgeCryptoClockSkewTest {
    private interface Work {void run() throws Exception;}
    private static void failAsExpected(Work action,String name)throws Exception {
        try{action.run();throw new AssertionError(name+" unexpectedly accepted");}
        catch(GeneralSecurityException expected){}
    }
    public static void main(String[] args) throws Exception {
        byte[] key=BridgeCrypto.derive(
            BridgeCrypto.newKeyPair().getPrivate(),
            BridgeCrypto.publicBytes(BridgeCrypto.newKeyPair()));
        long t=System.currentTimeMillis();
        byte[] proof=BridgeCrypto.encryptAt(key,"ping","",t-3600_000L);
        failAsExpected(()->BridgeCrypto.decrypt(key,proof),"stale generic proof");
        BridgeCrypto.Packet parsed=BridgeCrypto.decryptPairing(key,proof);
        if(!"ping".equals(parsed.kind)||!"".equals(parsed.value))
            throw new AssertionError("Offline T1 clock proof was not accepted");
        byte[] ack=BridgeCrypto.encryptAt(key,"ack",BridgeCrypto.nonce(proof),t+7200_000L);
        failAsExpected(()->BridgeCrypto.decrypt(key,ack),"future generic ACK");
        BridgeCrypto.Packet verified=BridgeCrypto.decryptPairing(key,ack);
        if(!"ack".equals(verified.kind)||
           !BridgeCrypto.nonce(proof).equals(verified.value))
            throw new AssertionError("Pairing receipt authentication failed");
        byte[] route=BridgeCrypto.encryptAt(key,"coord","-34.9214,-57.9545",t-3600_000L);
        failAsExpected(()->BridgeCrypto.decrypt(key,route),"stale route");
        failAsExpected(()->BridgeCrypto.decryptPairing(key,route),"route disguised as proof");
        ack[ack.length-1]^=0x01;
        failAsExpected(()->BridgeCrypto.decryptPairing(key,ack),"tampered pairing ACK");
        System.out.println("OK: clock-independent physical SAS pairing, strict route age, no route-as-proof, GCM integrity");
    }
}
