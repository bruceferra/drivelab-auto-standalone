package ar.com.rango5gps.drivelabauto.common;

import java.net.URI;
import java.util.Locale;

/** Strict Cloud OTA policy for the Sender package. Pure Java and testable
 * without an Android device. Does not grant trust to a download host alone.
 */
public final class SenderCloudOtaPolicy {
    public static final String PACKAGE="ar.com.rango5gps.drivelabauto.sender";
    public static final String CHANNEL="nav-sender";
    public static final String LOOKUP_URL=
        "https://promanager.com.ar/drivelab/api/v1/ota?channel=nav-sender";
    public static final long MAX_APK_BYTES=50L*1024L*1024L;
    private SenderCloudOtaPolicy(){}

    public static boolean isValidToken(String token){
        if(token==null||token.length()<32||token.length()>256)return false;
        for(int i=0;i<token.length();i++){
            char c=token.charAt(i);
            if(c<=32||c>=127||c==':')return false;
        }
        return true;
    }

    public static boolean isValidHash(String hex){
        return hex!=null&&hex.matches("[0-9a-f]{64}");
    }

    public static boolean isValidVersion(String version){
        return version!=null&&version.matches("[0-9]+\\.[0-9]+\\.[0-9]+");
    }

    public static boolean isValidOffer(String pkg,String version,
            long code,long bytes,String hash,String signer,String url,
            long installedCode){
        return PACKAGE.equals(pkg)&&isValidVersion(version)&&
            code>installedCode&&code>0&&bytes>=1024&&
            bytes<=MAX_APK_BYTES&&isValidHash(hash)&&
            isValidHash(signer)&&allowedDownloadUrl(url,version);
    }

    public static boolean allowedDownloadUrl(String rawUrl,String version){
        if(rawUrl==null||!isValidVersion(version))return false;
        try{
            URI u=new URI(rawUrl);
            return "https".equals(u.getScheme())&&
                "promanager.com.ar".equals(u.getHost())&&
                u.getPort()==-1&&u.getRawUserInfo()==null&&
                u.getRawQuery()==null&&u.getRawFragment()==null&&
                ("/drivelab/updates/nav-sender/DriveLab-nav-sender-v"+version+".apk")
                    .equals(u.getRawPath());
        }catch(Exception invalid){return false;}
    }

    /** Redirect checks cannot depend on a manifest version value. */
    public static boolean allowedAnyDownloadUrl(String rawUrl){
        if(rawUrl==null)return false;
        try{
            URI u=new URI(rawUrl);
            String prefix="/drivelab/updates/nav-sender/DriveLab-nav-sender-v";
            String path=u.getRawPath();
            if(path==null||!path.startsWith(prefix)||!path.endsWith(".apk"))
                return false;
            String version=path.substring(prefix.length(),path.length()-4);
            return allowedDownloadUrl(rawUrl,version);
        }catch(Exception invalid){return false;}
    }
}
