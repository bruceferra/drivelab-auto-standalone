package ar.com.rango5gps.drivelabauto.common;

/** Signed-route destination policy. Legacy coord/search remain Waze-compatible. */
public final class BridgeRouteTarget {
    public static final String WAZE="waze",MAPS="maps",BOTH="both";
    private BridgeRouteTarget(){}
    public static String normalize(String mode){
        return MAPS.equals(mode)?MAPS:BOTH.equals(mode)?BOTH:WAZE;
    }
    /** Null is an invalid wire kind, not an implicit Waze command. */
    public static String baseKind(String kind){
        if("coord".equals(kind)||"search".equals(kind))return kind;
        if("coord_maps".equals(kind)||"coord_both".equals(kind))return "coord";
        if("search_maps".equals(kind)||"search_both".equals(kind))return "search";
        return null;
    }
    public static String target(String kind){
        if("coord".equals(kind)||"search".equals(kind))return WAZE;
        if("coord_maps".equals(kind)||"search_maps".equals(kind))return MAPS;
        if("coord_both".equals(kind)||"search_both".equals(kind))return BOTH;
        return null;
    }
    public static String sendKind(String baseKind,String target){
        if(!"coord".equals(baseKind)&&!"search".equals(baseKind))
            throw new IllegalArgumentException("Unsupported destination kind");
        String mode=normalize(target);
        return WAZE.equals(mode)?baseKind:baseKind+"_"+mode;
    }
}
