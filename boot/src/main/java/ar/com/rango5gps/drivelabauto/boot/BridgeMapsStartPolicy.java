package ar.com.rango5gps.drivelabauto.boot;
import java.text.Normalizer;
import java.util.Locale;

/** Exact user-facing Maps route-preview buttons only, never generic confirmations. */
final class BridgeMapsStartPolicy {
    private BridgeMapsStartPolicy(){}
    static String norm(String label){
        if(label==null)return "";
        return Normalizer.normalize(label,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","")
            .toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }
    static boolean explicitStart(String label){
        String s=norm(label);
        return s.equals("iniciar")||s.equals("iniciar navegacion")||
            s.equals("comenzar navegacion")||
            s.equals("start navigation")||
            s.equals("start driving");
    }
}
