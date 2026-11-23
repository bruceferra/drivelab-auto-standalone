package ar.com.rango5gps.drivelabauto.common;

/** Run with javac/java on JDK 17. No Android SDK, network or production APK. */
public final class ReceiverCloudOtaPolicyTest {
    private static int checks;
    private static void expect(boolean condition,String description){
        checks++;
        if(!condition)throw new AssertionError(description);
    }
    public static void main(String[] args){
        String ok="https://promanager.com.ar/drivelab/updates/auto/DriveLab-auto-v0.3.99.apk";
        String sha="a".repeat(64),signer="b".repeat(64);
        expect(ReceiverCloudOtaPolicy.allowedDownloadUrl(ok,"0.3.99"),"exact Cloud URL");
        expect(ReceiverCloudOtaPolicy.allowedAnyDownloadUrl(ok),"redirect pin");
        expect(ReceiverCloudOtaPolicy.isValidOffer(
            ReceiverCloudOtaPolicy.PACKAGE,"0.3.99",200,6_000_000,sha,signer,ok,199),
            "legitimate code 200 over code 199");
        expect(!ReceiverCloudOtaPolicy.isValidOffer(
            ReceiverCloudOtaPolicy.PACKAGE,"0.3.99",199,6_000_000,sha,signer,ok,199),
            "reject same versionCode");
        expect(!ReceiverCloudOtaPolicy.isValidOffer(
            ReceiverCloudOtaPolicy.PACKAGE,"0.3.99",198,6_000_000,sha,signer,ok,199),
            "reject downgrade");
        expect(!ReceiverCloudOtaPolicy.isValidOffer(
            "ar.com.rango5gps.drivelabauto.boot.lab","0.3.99",
            200,6_000_000,sha,signer,ok,199),"reject NAV package");
        expect(!ReceiverCloudOtaPolicy.isValidOffer(
            ReceiverCloudOtaPolicy.PACKAGE,"0.3.99",200,6_000_000,
            sha,"not-a-cert",ok,199),"reject corrupt signing digest");
        expect(!ReceiverCloudOtaPolicy.isValidOffer(
            ReceiverCloudOtaPolicy.PACKAGE,"0.3.99",200,6_000_000,
            "000",signer,ok,199),"reject corrupt APK hash");
        expect(!ReceiverCloudOtaPolicy.isValidOffer(
            ReceiverCloudOtaPolicy.PACKAGE,"0.3.99",200,60_000_000,
            sha,signer,ok,199),"reject overlarge APK");
        for(String unsafe:new String[]{
            "http://promanager.com.ar/drivelab/updates/auto/DriveLab-auto-v0.3.99.apk",
            "https://promanager.com.ar.evil.tld/drivelab/updates/auto/DriveLab-auto-v0.3.99.apk",
            "https://promanager.com.ar:443/drivelab/updates/auto/DriveLab-auto-v0.3.99.apk",
            "https://promanager.com.ar/drivelab/updates/nav/DriveLab-nav-v0.3.99.apk",
            "https://promanager.com.ar/drivelab/updates/auto/DriveLab-auto-v0.3.99.apk?x=1",
            "https://user@promanager.com.ar/drivelab/updates/auto/DriveLab-auto-v0.3.99.apk",
            "https://promanager.com.ar/drivelab/updates/auto/DriveLab-auto-v0.3.99.apk#anchor",
            "https://promanager.com.ar/drivelab/updates/auto/DriveLab-auto-v0.3.98.apk"
        }){
            expect(!ReceiverCloudOtaPolicy.allowedDownloadUrl(unsafe,"0.3.99"),
                   "reject untrusted CDN/path/URL: "+unsafe);
        }
        expect(ReceiverCloudOtaPolicy.isValidToken("a".repeat(48)),"token length");
        expect(!ReceiverCloudOtaPolicy.isValidToken("short"),"short token");
        expect(!ReceiverCloudOtaPolicy.isValidToken("a".repeat(47)+":"),"invalid token");
        expect(ReceiverCloudOtaPolicy.PACKAGE.equals(
            "ar.com.rango5gps.drivelabauto.receiver"),"AUTO exact identity");
        System.out.println("PASS AUTO Cloud OTA pure Java policy: "+checks+" checks");
    }
}
