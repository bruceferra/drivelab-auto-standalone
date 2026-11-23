package ar.com.rango5gps.drivelabauto.common;
import java.util.Arrays;

/** Offline cryptographic contract, no Android, no network. */
public final class BridgeDiscoveryProofTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        byte[] key=new byte[16],wrong=new byte[16];
        for(int i=0;i<16;i++){key[i]=(byte)(i+1);wrong[i]=(byte)(i+2);}
        byte[] nonce=BridgeDiscoveryProof.newNonce();
        byte[] req=BridgeDiscoveryProof.request(key,nonce);
        check(req.length==BridgeDiscoveryProof.SIZE,"Fixed request");
        check(BridgeDiscoveryProof.acceptsRequest(key,req,req.length),"Valid pairing proof");
        check(!BridgeDiscoveryProof.acceptsRequest(wrong,req,req.length),
            "Wrong sender cannot discover paired Bridge");
        byte[] ack=BridgeDiscoveryProof.response(key,req);
        check(BridgeDiscoveryProof.acceptsResponse(key,nonce,ack,ack.length),
            "Exactly matching nonce and paired key required");
        check(!BridgeDiscoveryProof.acceptsResponse(wrong,nonce,ack,ack.length),
            "Wrong key cannot impersonate Jetour");
        check(!BridgeDiscoveryProof.acceptsResponse(key,
            BridgeDiscoveryProof.newNonce(),ack,ack.length),
            "Replayed response cannot authenticate another nonce");
        byte[] corrupted=Arrays.copyOf(ack,ack.length);
        corrupted[8]^=0x5;
        check(!BridgeDiscoveryProof.acceptsResponse(key,nonce,corrupted,
            corrupted.length),"Corrupted proof invalid");
        check(!BridgeDiscoveryProof.acceptsRequest(key,req,req.length-1),
            "Truncated request invalid");
        check(!BridgeDiscoveryProof.acceptsResponse(key,nonce,req,req.length),
            "Echoed request cannot be accepted as response");
        boolean denied=false;
        try{BridgeDiscoveryProof.response(wrong,req);}
        catch(SecurityException expected){denied=true;}
        check(denied,"Bridge must never sign a request without old key");
        System.out.println("PASS paired-key authenticated LAN rediscovery, replay and spoof rejection");
    }
}
