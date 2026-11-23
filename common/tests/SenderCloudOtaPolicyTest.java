package ar.com.rango5gps.drivelabauto.common;

/** Sender-only Cloud OTA policy regression, pure JDK 17 and no credentials. */
public final class SenderCloudOtaPolicyTest {
    private static int checks;
    private static void verify(boolean ok,String description) {
        checks++;
        if(!ok) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        String good="https://promanager.com.ar/drivelab/updates/nav-sender/DriveLab-nav-sender-v0.2.26.apk";
        String hash="a".repeat(64),signer="b".repeat(64);
        verify(SenderCloudOtaPolicy.PACKAGE.equals("ar.com.rango5gps.drivelabauto.sender"),"Sender package");
        verify(SenderCloudOtaPolicy.LOOKUP_URL.equals(
            "https://promanager.com.ar/drivelab/api/v1/ota?channel=nav-sender"),"Cloud lookup");
        verify(SenderCloudOtaPolicy.allowedDownloadUrl(good,"0.2.26"),"exact URL");
        verify(SenderCloudOtaPolicy.allowedAnyDownloadUrl(good),"safe download URL");
        verify(SenderCloudOtaPolicy.isValidOffer(SenderCloudOtaPolicy.PACKAGE,
            "0.2.26",31,1_270_000,hash,signer,good,30),"versionCode 31 over 30");
        verify(!SenderCloudOtaPolicy.isValidOffer(SenderCloudOtaPolicy.PACKAGE,
            "0.2.26",30,1_270_000,hash,signer,good,30),"duplicate code rejected");
        verify(!SenderCloudOtaPolicy.isValidOffer(SenderCloudOtaPolicy.PACKAGE,
            "0.2.26",29,1_270_000,hash,signer,good,30),"downgrade rejected");
        verify(!SenderCloudOtaPolicy.isValidOffer("ar.com.rango5gps.drivelabauto.receiver",
            "0.2.26",31,1_270_000,hash,signer,good,30),"AUTO package rejected");
        verify(!SenderCloudOtaPolicy.isValidOffer(SenderCloudOtaPolicy.PACKAGE,
            "0.2.26",31,1_270_000,"BAD",signer,good,30),"bad hash rejected");
        verify(!SenderCloudOtaPolicy.isValidOffer(SenderCloudOtaPolicy.PACKAGE,
            "0.2.26",31,1_270_000,hash,"BAD",good,30),"bad signer rejected");
        verify(!SenderCloudOtaPolicy.isValidOffer(SenderCloudOtaPolicy.PACKAGE,
            "0.2.26",31,200_000_000,hash,signer,good,30),"large APK rejected");
        for(String url:new String[]{
            "http://promanager.com.ar/drivelab/updates/nav-sender/DriveLab-nav-sender-v0.2.26.apk",
            "https://promanager.com.ar.evil.com/drivelab/updates/nav-sender/DriveLab-nav-sender-v0.2.26.apk",
            "https://promanager.com.ar/drivelab/updates/auto/DriveLab-auto-v0.2.26.apk",
            "https://promanager.com.ar/drivelab/updates/nav-sender/DriveLab-nav-sender-v0.2.26.apk#bad",
            "https://promanager.com.ar/drivelab/updates/nav-sender/DriveLab-nav-sender-v0.2.26.apk?bad=1",
            "https://promanager.com.ar:443/drivelab/updates/nav-sender/DriveLab-nav-sender-v0.2.26.apk",
            "https://user@promanager.com.ar/drivelab/updates/nav-sender/DriveLab-nav-sender-v0.2.26.apk",
            "https://promanager.com.ar/drivelab/updates/nav-sender/DriveLab-nav-sender-v0.2.25.apk"
        }) verify(!SenderCloudOtaPolicy.allowedDownloadUrl(url,"0.2.26"),
            "forbid unexpected download URL: "+url);
        verify(SenderCloudOtaPolicy.isValidToken("x".repeat(48)),"valid 48-char token");
        verify(!SenderCloudOtaPolicy.isValidToken("short"),"short token rejected");
        verify(!SenderCloudOtaPolicy.isValidToken("x".repeat(47)+":"),"unsafe token rejected");
        System.out.println("PASS SENDER_CLOUD_OTA_POLICY "+checks+" checks");
    }
}
