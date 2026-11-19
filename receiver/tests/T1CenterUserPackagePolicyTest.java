package ar.com.rango5gps.drivelabauto.receiver;

public final class T1CenterUserPackagePolicyTest {
    static void ck(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        ck(T1CenterUserPackagePolicy.allowed("ar.com.promanager.nativeapp"),"ProManager valid");
        ck(T1CenterUserPackagePolicy.allowed("ar.com.rango5gps.uberbeta"),"Uber valid");
        ck(T1CenterUserPackagePolicy.allowed("com.spotify.music"),"Spotify user app valid");
        ck(T1CenterUserPackagePolicy.allowed("com.waze"),"Waze user app valid");
        ck(!T1CenterUserPackagePolicy.allowed("ar.com.rango5gps.drivelabauto.receiver"),"Do not reposition own Receiver");
        ck(!T1CenterUserPackagePolicy.allowed("com.autolink.bluetooth"),"OEM Bluetooth forbidden");
        ck(!T1CenterUserPackagePolicy.allowed("com.jetour.systemhome"),"OEM Home forbidden");
        ck(!T1CenterUserPackagePolicy.allowed("com.android.settings"),"System settings forbidden");
        ck(!T1CenterUserPackagePolicy.allowed("android"),"Android package forbidden");
        ck(!T1CenterUserPackagePolicy.allowed("com.chery.system"),"Automaker service forbidden");
        ck(!T1CenterUserPackagePolicy.allowed("com.spotify.music;rm"),"Shell metacharacters rejected");
        ck(!T1CenterUserPackagePolicy.allowed("com.spotify.music /"),"Spaces rejected");
        ck(!T1CenterUserPackagePolicy.allowed(null),"Null rejected");
        System.out.println("PASS user-selected mosaic package policy");
    }
}
