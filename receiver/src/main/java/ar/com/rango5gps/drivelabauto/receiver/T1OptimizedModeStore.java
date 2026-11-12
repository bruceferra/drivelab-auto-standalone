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
        return new T1OptimizedModePolicy.Rule(
            enumOr(T1OptimizedModePolicy.Power.class,
                p.getString(k+"power",d.power.name()),d.power),
            enumOr(T1OptimizedModePolicy.Strategy.class,
                p.getString(k+"strategy",d.strategy.name()),d.strategy),
            p.getInt(k+"soc",d.forcedSoc),
            p.getBoolean(k+"threshold_enabled",d.thresholdEnabled),
            p.getInt(k+"min_pct",d.minBattery),
            enumOr(T1OptimizedModePolicy.Regen.class,
                p.getString(k+"regen",d.regeneration.name()),d.regeneration));
    }
    static void save(Context c,T1OptimizedModePolicy.Road r,
                     T1OptimizedModePolicy.Rule v){
        String k="road_"+r.name()+"_";
        preferences(c).edit()
            .putString(k+"power",v.power.name())
            .putString(k+"strategy",v.strategy.name())
            .putInt(k+"soc",v.forcedSoc)
            .putBoolean(k+"threshold_enabled",v.thresholdEnabled)
            .putInt(k+"min_pct",v.minBattery)
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
