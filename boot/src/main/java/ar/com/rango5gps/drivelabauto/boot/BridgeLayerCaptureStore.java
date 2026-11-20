package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

/**
 * Two durable, local, REDACTED forensic snapshots of Display 1.
 *
 * Raw dumpsys output is NEVER saved: only BridgeModeLayerDiff's hashed
 * window IDs, sanitized types/categories and counts. Each state has its
 * own preference key so leaving the diagnostic Activity, opening Waze,
 * switching apps, or restarting Bridge cannot erase the first capture.
 * No network, screenshots, OEM or ADB writes.
 */
final class BridgeLayerCaptureStore {
    private static final String PREFS="bridge_layers_diagnostics_v1";
    private static final int SCHEMA=1,MAX_LINES=80,MAX_WINDOWS=90;
    private BridgeLayerCaptureStore(){}

    static boolean save(Context context,BridgeModeLayerDiff.Snapshot snapshot){
        if(snapshot==null||!valid(snapshot.name))
            throw new IllegalArgumentException("Captura inválida");
        try{
            JSONObject json=new JSONObject();
            json.put("schema",SCHEMA);
            json.put("phase",snapshot.name);
            json.put("at",System.currentTimeMillis());
            json.put("waze",snapshot.waze);
            json.put("maps",snapshot.maps);
            json.put("spotify",snapshot.spotify);
            json.put("mode",snapshot.mode);
            json.put("unknownDisplay",snapshot.unknownDisplay);
            JSONArray lines=new JSONArray();
            for(String line:snapshot.lines){
                if(lines.length()>=MAX_LINES)break;
                lines.put(line);
            }
            json.put("lines",lines);
            JSONObject windows=new JSONObject();
            int n=0;
            for(Map.Entry<String,String> entry:snapshot.windowState.entrySet()){
                if(n++>=MAX_WINDOWS)break;
                windows.put(entry.getKey(),entry.getValue());
            }
            json.put("windows",windows);
            return prefs(context).edit().putString(snapshot.name,
                json.toString()).commit();
        }catch(Exception broken){
            return false;
        }
    }

    static BridgeModeLayerDiff.Snapshot load(Context context,String phase){
        if(!valid(phase))return null;
        String saved=prefs(context).getString(phase,null);
        if(saved==null||saved.length()>32000)return null;
        try{
            JSONObject json=new JSONObject(saved);
            if(json.getInt("schema")!=SCHEMA||
               !phase.equals(json.getString("phase")))return null;
            BridgeModeLayerDiff.Snapshot snapshot=
                new BridgeModeLayerDiff.Snapshot(phase);
            snapshot.waze=json.getInt("waze");
            snapshot.maps=json.getInt("maps");
            snapshot.spotify=json.getInt("spotify");
            snapshot.mode=json.getInt("mode");
            snapshot.unknownDisplay=json.getInt("unknownDisplay");
            JSONArray lines=json.getJSONArray("lines");
            if(lines.length()>MAX_LINES)return null;
            for(int i=0;i<lines.length();i++)
                snapshot.lines.add(lines.getString(i));
            JSONObject windows=json.getJSONObject("windows");
            if(windows.length()>MAX_WINDOWS)return null;
            java.util.Iterator<String> keys=windows.keys();
            while(keys.hasNext()){
                String key=keys.next();
                snapshot.windowState.put(key,windows.getString(key));
            }
            return snapshot;
        }catch(Exception unreadable){
            return null;
        }
    }

    static long timestamp(Context context,String phase){
        if(!valid(phase))return 0;
        String saved=prefs(context).getString(phase,null);
        if(saved==null)return 0;
        try{return new JSONObject(saved).optLong("at",0);}
        catch(Exception ignored){return 0;}
    }

    static boolean clear(Context context){
        return prefs(context).edit().remove("TAPADO").remove("VISIBLE").commit();
    }

    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static boolean valid(String phase){
        return "TAPADO".equals(phase)||"VISIBLE".equals(phase);
    }
}
