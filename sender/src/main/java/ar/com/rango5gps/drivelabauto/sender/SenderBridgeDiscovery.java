package ar.com.rango5gps.drivelabauto.sender;

import ar.com.rango5gps.drivelabauto.common.BridgeDiscoveryProof;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.LinkedHashSet;

/** After the owner pairs ONCE by QR, recover Bridge's new DHCP address
 * without another QR. The existing 128-bit AES pairing key authenticates
 * every discovery request and response with a fresh random nonce.
 *
 * No LAN-wide port scanning, no multicast groups, no remote Internet.
 * Only private IPv4 broadcast on active interfaces plus the previously
 * confirmed address. A cryptographic navigation ping is mandatory
 * before Sender stores or uses any returned address.
 */
final class SenderBridgeDiscovery {
    private SenderBridgeDiscovery(){}
    static String locate(byte[] pairedKey,String knownIp)throws Exception{
        if(pairedKey==null||pairedKey.length!=16)
            throw new SecurityException("Sin clave de vinculación anterior");
        byte[] nonce=BridgeDiscoveryProof.newNonce();
        byte[] challenge=BridgeDiscoveryProof.request(pairedKey,nonce);
        LinkedHashSet<InetAddress> candidates=new LinkedHashSet<>();
        if(SenderClient.localIpv4(knownIp))
            candidates.add(InetAddress.getByName(knownIp));
        // Mobile hotspots and Wi-Fi APs have different DHCP subnets.
        // Use the broadcast address from the actual connected interface.
        try{
            Enumeration<NetworkInterface> interfaces=
                NetworkInterface.getNetworkInterfaces();
            while(interfaces!=null&&interfaces.hasMoreElements()){
                NetworkInterface nic=interfaces.nextElement();
                if(!nic.isUp()||nic.isLoopback()||nic.isVirtual())continue;
                for(InterfaceAddress address:nic.getInterfaceAddresses()){
                    InetAddress own=address.getAddress();
                    InetAddress broadcast=address.getBroadcast();
                    if(!(own instanceof Inet4Address)||
                       !(broadcast instanceof Inet4Address))continue;
                    if(!own.isSiteLocalAddress()||
                       !broadcast.isSiteLocalAddress())continue;
                    candidates.add(broadcast);
                }
            }
        }catch(Exception blocked){
            // Some vehicle-tethered Android builds hide interfaces.
        }
        candidates.add(InetAddress.getByName("255.255.255.255"));
        if(candidates.size()>20)
            throw new SecurityException("Demasiadas interfaces; sin búsqueda");
        try(DatagramSocket socket=new DatagramSocket()){
            socket.setBroadcast(true);
            socket.setSoTimeout(550);
            byte[] answer=new byte[BridgeDiscoveryProof.SIZE+1];
            for(int round=0;round<4;round++){
                for(InetAddress destination:candidates){
                    try{
                        socket.send(new DatagramPacket(
                            challenge,challenge.length,
                            destination,BridgeDiscoveryProof.PORT));
                    }catch(Exception unavailable){
                        // An individual Wi-Fi/hotspot broadcast may be blocked.
                    }
                }
                long until=System.nanoTime()+550_000_000L;
                while(System.nanoTime()<until){
                    DatagramPacket reply=new DatagramPacket(answer,answer.length);
                    try{socket.receive(reply);}
                    catch(SocketTimeoutException noReply){break;}
                    InetAddress source=reply.getAddress();
                    String ip=source==null?"":source.getHostAddress();
                    if(!SenderClient.localIpv4(ip))continue;
                    if(BridgeDiscoveryProof.acceptsResponse(
                        pairedKey,nonce,reply.getData(),reply.getLength()))
                        return ip;
                }
            }
        }
        throw new java.io.IOException(
            "Bridge no respondió por Wi-Fi (puerto 48393). "+
            "La clave QR se conserva; comprobá que ambos estén en la misma red.");
    }
}
