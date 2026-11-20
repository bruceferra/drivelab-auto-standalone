package ar.com.rango5gps.drivelabauto.boot;
public final class BridgeWindowsPolicyTest {
    static void yes(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    public static void main(String[] args){
        yes(BridgeWindowsPolicy.allowed("com.spotify.music"),"Spotify");
        yes(BridgeWindowsPolicy.allowed("com.google.android.apps.maps"),"Maps");
        yes(BridgeWindowsPolicy.allowed("com.waze"),"Waze");
        yes(BridgeWindowsPolicy.allowed("ar.com.rango5gps.promanager"),"ProManager");
        yes(!BridgeWindowsPolicy.allowed(BridgeWindowsPolicy.AUTO),"DriveLab Auto forbidden");
        yes(!BridgeWindowsPolicy.allowed(BridgeWindowsPolicy.BRIDGE),"Bridge not own tile");
        yes(!BridgeWindowsPolicy.allowed("com.autolink.carservice"),"No OEM");
        yes(!BridgeWindowsPolicy.allowed("com.jetour.cluster"),"No Jetour");
        yes(!BridgeWindowsPolicy.allowed("android.settings"),"No Android");
        yes(!BridgeWindowsPolicy.allowed("com.t1.launcher"),"No other launcher");
        yes(BridgeWindowsPolicy.validCenter("com.spotify.music",
            "ar.com.rango5gps.promanager",65),"safe parked trial Spotify+ProManager");
        yes(!BridgeWindowsPolicy.validCenter("com.spotify.music",
            "com.spotify.music",65),"no duplicate tasks");
        yes(!BridgeWindowsPolicy.validCenter("com.spotify.music",
            BridgeWindowsPolicy.AUTO,65),"cannot launch Receiver");
        yes(!BridgeWindowsPolicy.validCenter("com.spotify.music",
            "com.waze",95),"ratio must be bounded");
        yes(BridgeWindowsPolicy.validMap("com.waze"),"Waze tab");
        yes(BridgeWindowsPolicy.validMap(BridgeWindowsPolicy.MAPS),"Maps tab");
        yes(!BridgeWindowsPolicy.validMap("com.spotify.music"),"no Spotify tab");
        System.out.println("PASS Bridge app allowlist independent of Auto/OEM/other launcher");
    }
}
