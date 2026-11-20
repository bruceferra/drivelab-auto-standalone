package ar.com.rango5gps.drivelabauto.sender;

import java.nio.file.Files;
import java.nio.file.Path;

/** The QR is a one-time authenticated identity, not a static LAN IP.
 * Pure source contract; physical Wi-Fi/hotspot behavior needs device QA.
 */
public final class SenderQrPersistenceSourceTest {
    static String read(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    static void ok(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        String sender="sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/";
        String boot="boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/";
        String client=read(sender+"SenderClient.java");
        String discovery=read(sender+"SenderBridgeDiscovery.java");
        String senderUi=read(sender+"SenderActivity.java");
        String bridge=read(boot+"BootLinkService.java");
        String config=read(boot+"BootConfig.java");
        String crypto=read(
            "common/src/main/java/ar/com/rango5gps/drivelabauto/common/BridgeDiscoveryProof.java");
        ok(client.contains("putString(\"receiver_key\",BridgeCrypto.packKey(session.key))")&&
           client.contains("putString(\"receiver_ip\",found).commit()")&&
           client.contains("SenderBridgeDiscovery.locate(key,ip)")&&
           client.contains("remoteClockOffset(found,key)")&&
           client.contains("Endpoint target=endpoint(c)")&&
           client.contains("static Delivery reconnect(Context c)")&&
           !client.contains("unpair(c);")&&
           !client.contains("unpair(context);"),
           "Prior QR key persists and only newly authenticated IP may change");
        ok(discovery.contains("BridgeDiscoveryProof.newNonce()")&&
           discovery.contains("BridgeDiscoveryProof.request(pairedKey,nonce)")&&
           discovery.contains("BridgeDiscoveryProof.acceptsResponse(")&&
           discovery.contains("getInterfaceAddresses()")&&
           discovery.contains("getBroadcast()")&&
           discovery.contains("SenderClient.localIpv4(ip)")&&
           !discovery.contains("192.168.1.1")&&
           !discovery.contains("Scan")&&
           !discovery.contains("8.8.8.8"),
           "Sender searches current connected subnet with fresh MAC, no fixed-IP guesses");
        ok(bridge.contains("new Thread(this::listenDiscovery")&&
           bridge.contains("BridgeDiscoveryProof.acceptsRequest(")&&
           bridge.contains("BridgeDiscoveryProof.response(key,")&&
           bridge.contains("BridgeCrypto.unpackKey(")&&
           bridge.contains(".putBoolean(\"enabled\",true).commit()")&&
           config.contains("if(p.contains(\"enabled\"))return p.getBoolean(\"enabled\",false)")&&
           config.contains("BootLinkService.prefs(context).getString(\"secret\",\"\")"),
           "Bridge answers only paired-key devices; auto listener survives ignition");
        ok(senderUi.contains("RECONECTAR BRIDGE SIN QR · CLAVE GUARDADA")&&
           senderUi.contains("SenderClient.reconnect(this)")&&
           senderUi.contains("Sender-Bridge-QR-Persistent-Reconnect")&&
           crypto.contains("HmacSHA256")&&
           crypto.contains("MessageDigest.isEqual"),
           "Sender tries automatic link recovery on open and exposes manual status");
        ok(!client.contains("getSharedPreferences(\"drivelab_receiver")&&
           !bridge.contains("com.jetour")&&
           !bridge.contains("pm clear")&&
           !bridge.contains("am force-stop"),
           "No Receiver/OEM/vehicle system dependency or destructive reset");
        System.out.println("PASS persistent Bridge QR identity, verified LAN IP rediscovery, no re-pair");
    }
}
