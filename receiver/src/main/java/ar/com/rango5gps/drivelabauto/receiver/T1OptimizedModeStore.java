package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;

/** Local user preferences. No OEM writes, no GPS history uploaded. */
final class T1OptimizedModeStore {
    static final String NAME="t1_optimized_mode_profiles_v1";
    private static SharedPreferences preferences(Context c){
        return c.getApplicationContext().getSharedPreferences(NAME,Context.MODE_PRIVATE);
    }
    static T1OptimizedModePolicy.Rule get(Context c,T1OptimizedModePolicy.Road r){
        SharedPreferences p=preferences(c);
        T1OptimizedModePolicy.Rule d=T1OptimizedModePolicy.defaultRule(r);
        String k="road_"+r.name()+"_";
        // Migration: an old single threshold is kept with its original
        // comparator. After the first save, both bounds become independent.
        boolean legacyEnabled=p.getBoolean(
            k+"threshold_enabled",d.thresholdEnabled);
        T1OptimizedModePolicy.Comparison legacyComparison=
            enumOr(T1OptimizedModePolicy.Comparison.class,
                p.getString(k+"comparison",d.comparison.name()),d.comparison);
        int legacyPct=p.getInt(k+"min_pct",d.minBattery);
        boolean v2=p.contains(k+"min_enabled_v2")||
            p.contains(k+"max_enabled_v2");
        boolean minOn=v2?p.getBoolean(k+"min_enabled_v2",false):
            legacyEnabled&&legacyComparison==
                T1OptimizedModePolicy.Comparison.GREATER_OR_EQUAL;
        boolean maxOn=v2?p.getBoolean(k+"max_enabled_v2",false):
            legacyEnabled&&legacyComparison==
                T1OptimizedModePolicy.Comparison.LESS_OR_EQUAL;
        int minimum=v2?legacyPct:
            legacyComparison==T1OptimizedModePolicy.Comparison.LESS_OR_EQUAL?
                5:legacyPct;
        int maximum=v2?p.getInt(k+"max_pct_v2",100):
            legacyComparison==T1OptimizedModePolicy.Comparison.LESS_OR_EQUAL?
                legacyPct:100;
        return new T1OptimizedModePolicy.Rule(
            enumOr(T1OptimizedModePolicy.Power.class,
                p.getString(k+"power",d.power.name()),d.power),
            enumOr(T1OptimizedModePolicy.Strategy.class,
                p.getString(k+"strategy",d.strategy.name()),d.strategy),
            p.getInt(k+"soc",d.forcedSoc),
            minOn,minimum,maxOn,maximum,
            enumOr(T1OptimizedModePolicy.Regen.class,
                p.getString(k+"regen",d.regeneration.name()),d.regeneration));
    }
    /** Entire-road kill switch. All four blocks default to enabled to
     * preserve existing user profiles across OTA upgrades. Toggling this
     * is persisted immediately; editing other settings still needs GUARDAR.
     */
    static boolean isEnabled(Context c,T1OptimizedModePolicy.Road r){
        if(r==null)return false;
        return preferences(c).getBoolean("road_"+r.name()+"_enabled",true);
    }
    static void setEnabled(Context c,T1OptimizedModePolicy.Road r,boolean enabled){
        if(r==null)throw new IllegalArgumentException("Road required");
        preferences(c).edit()
            .putBoolean("road_"+r.name()+"_enabled",enabled)
            .commit();
    }
    static void save(Context c,T1OptimizedModePolicy.Road r,
                     T1OptimizedModePolicy.Rule v){
        String k="road_"+r.name()+"_";
        preferences(c).edit()
            .putString(k+"power",v.power.name())
            .putString(k+"strategy",v.strategy.name())
            .putInt(k+"soc",v.forcedSoc)
            .putBoolean(k+"min_enabled_v2",v.minEnabled)
            .putBoolean(k+"max_enabled_v2",v.maxEnabled)
            .putInt(k+"max_pct_v2",v.maxBattery)
            // Keep old keys for a safe on-disk migration; new code
            // always prefers both v2 flags.
            .putBoolean(k+"threshold_enabled",v.thresholdEnabled)
            .putInt(k+"min_pct",v.minEnabled?v.minBattery:v.maxBattery)
            .putString(k+"comparison",
                v.minEnabled?
                    T1OptimizedModePolicy.Comparison.GREATER_OR_EQUAL.name():
                    T1OptimizedModePolicy.Comparison.LESS_OR_EQUAL.name())
            .putString(k+"regen",v.regeneration.name())
            .apply();
    }
    static void markReading(Context c,T1OptimizedModePolicy.Road road,
                            String tag,String details){
        preferences(c).edit()
            .putString("last_road",road.name())
            .putString("last_tag",tag==null?"":tag)
            .putString("last_info",details==null?"":details)
            .putLong("last_ts",System.currentTimeMillis())
            .apply();
    }
    static T1OptimizedModePolicy.Road lastRoad(Context c){
        SharedPreferences p=preferences(c);
        // Stale route state is UNKNOWN, never trust the old motorway location.
        long age=System.currentTimeMillis()-p.getLong("last_ts",0);
        if(age<0||age>45000L)return T1OptimizedModePolicy.Road.UNKNOWN;
        return enumOr(T1OptimizedModePolicy.Road.class,
            p.getString("last_road","UNKNOWN"),
            T1OptimizedModePolicy.Road.UNKNOWN);
    }
    static String lastInfo(Context c){
        SharedPreferences p=preferences(c);
        return "OSM "+p.getString("last_tag","")+" · "+
            p.getString("last_info","Sin lectura");
    }
    private static <E extends Enum<E>> E enumOr(Class<E> type,String name,E fallback){
        try{return Enum.valueOf(type,name);}catch(Exception ignored){return fallback;}
    }
    private T1OptimizedModeStore(){}
}
