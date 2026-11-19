package ar.com.rango5gps.drivelabauto.boot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jetour MODE visual obstruction: compare WINDOW and SurfaceFlinger
 * snapshots in two driver-observed states (MODE covering vs maps visible).
 *
 * READ-ONLY. No layer, OEM, launch or app changes. Summaries contain no
 * raw window titles, routes, coordinates, tokens, contacts or other apps.
 */
final class BridgeModeLayerDiff {
    private static final Pattern WINDOW=Pattern.compile(
        "^\\s*Window\\s+#\\d+\\s+Window\\{([^}]*)\\}\\s*:?.*$");
    private static final Pattern DISPLAY=Pattern.compile(
        "(?:mDisplayId|displayId|display)\\s*[=:]\\s*(\\d+)");
    private static final Pattern LAYER=Pattern.compile(
        "\\b(?:mBaseLayer|mLayer|mSubLayer|layer|z)\\s*[=:]\\s*(-?\\d+)");
    private static final Pattern WINDOW_TYPE=Pattern.compile(
        "\\b(?:type|ty)\\s*[=:]\\s*([A-Za-z_0-9-]+)");
    private static final Pattern SF_NAME=Pattern.compile(
        "(?:name\\s*=\\s*|\\+\\s*Layer\\s+|Layer\\s+0x[0-9a-fA-F]+\\s*\\()([^\\n\\r]{1,200})");
    private static final Pattern SF_Z=Pattern.compile(
        "(?:\\bz\\s*=\\s*|\\bz-order\\s*=\\s*)(-?\\d+)");
    private BridgeModeLayerDiff(){}

    private static String category(String text){
        String s=text.toLowerCase(Locale.ROOT);
        if(s.contains("com.waze")||s.contains("waze"))return "WAZE";
        if(s.contains("com.google.android.apps.maps")||
           s.contains("google maps"))return "GOOGLE_MAPS";
        if(s.contains("spotify"))return "SPOTIFY";
        if(s.contains("mode")||s.contains("media")||
           s.contains("music"))return "MODE_MEDIA_JETOUR_CANDIDATE";
        if(s.contains("systemui"))return "SYSTEM_UI";
        if(s.contains("statusbar")||s.contains("navigationbar"))
            return "SYSTEM_BARS";
        return "OTHER_"+digest(s);
    }
    private static String digest(String s){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] d=md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(8);
            for(int i=0;i<4;i++)
                out.append(String.format(Locale.ROOT,"%02x",d[i]&255));
            return out.toString();
        }catch(Exception ignored){return "unavailable";}
    }
    private static Integer number(Pattern pattern,String s){
        Matcher m=pattern.matcher(s);
        if(!m.find())return null;
        try{return Integer.valueOf(m.group(1));}
        catch(Exception ignored){return null;}
    }
    private static String type(String raw){
        Matcher m=WINDOW_TYPE.matcher(raw);
        return m.find()?m.group(1):"?";
    }
    private static final class Win{
        String kind;
        int display=-1;
        Integer z;
        String type="?";
        boolean visible;
        String signature;
        String row(){
            return kind+" display="+display+
                " layer="+(z==null?"?":z)+
                " type="+type+" visible="+visible+
                " id="+signature;
        }
    }
    static final class Snapshot{
        final String name;
        final List<String> lines=new ArrayList<>();
        final Map<String,String> windowState=new LinkedHashMap<>();
        int waze,maps,spotify,mode,unknownDisplay;
        Snapshot(String n){name=n;}
        String report(){
            StringBuilder b=new StringBuilder("ESTADO "+name+
                " · WINDOW / SUPERFICIES (lectura)");
            b.append("\nMapas detectados: Waze=").append(waze)
                .append(" Maps=").append(maps)
                .append(" Spotify=").append(spotify)
                .append(" MODE/candidatas=").append(mode)
                .append(" display desconocido=").append(unknownDisplay);
            for(String s:lines)b.append("\n").append(s);
            return b.toString();
        }
    }
    static Snapshot summarize(String stage,String windowDump,
                              String activityDump,String sfDump){
        if(!"TAPADO".equals(stage)&&!"VISIBLE".equals(stage))
            throw new IllegalArgumentException("Estado desconocido");
        Snapshot result=new Snapshot(stage);
        if(windowDump==null||windowDump.isEmpty()){
            result.lines.add("WindowManager: sin datos");
        }else{
            List<Win> windows=parseWindows(windowDump);
            windows.sort(Comparator.comparingInt(
                (Win w)->w.display).thenComparingInt(
                w->w.z==null?Integer.MIN_VALUE:w.z).reversed());
            for(Win w:windows){
                if(w.display!=1){
                    if(w.display==-1&&
                       !w.kind.startsWith("OTHER_"))
                        result.unknownDisplay++;
                    continue;
                }
                count(result,w.kind);
                result.windowState.put(w.signature,w.row());
                if(result.lines.size()<38)result.lines.add("WM: "+w.row());
            }
            if(windows.isEmpty())
                result.lines.add("WindowManager: formato sin ventanas parseables");
        }
        if(activityDump!=null){
            int wazeTasks=0,mapsTasks=0;
            for(String line:activityDump.split("\\r?\\n")){
                String low=line.toLowerCase(Locale.ROOT);
                if(!low.contains("task{")&&!low.contains("taskrecord{")&&
                   !low.contains("task #"))continue;
                if(low.contains("com.waze"))wazeTasks++;
                if(low.contains("com.google.android.apps.maps"))mapsTasks++;
            }
            result.lines.add("TASKS sin certeza de pantalla: Waze="+wazeTasks+
                " Maps="+mapsTasks+" (no confirma píxeles visibles)");
        }
        if(sfDump==null||sfDump.isEmpty()){
            result.lines.add("SurfaceFlinger: sin permiso / sin datos");
        }else{
            parseSurface(sfDump,result);
        }
        return result;
    }
    private static void count(Snapshot s,String category){
        if(category.equals("WAZE"))s.waze++;
        else if(category.equals("GOOGLE_MAPS"))s.maps++;
        else if(category.equals("SPOTIFY"))s.spotify++;
        else if(category.equals("MODE_MEDIA_JETOUR_CANDIDATE"))s.mode++;
    }
    private static List<Win> parseWindows(String raw){
        List<Win> result=new ArrayList<>();
        Win current=null;
        for(String line:raw.split("\\r?\\n")){
            Matcher header=WINDOW.matcher(line);
            if(header.find()){
                current=new Win();
                current.kind=category(header.group(1));
                current.signature=digest(
                    header.group(1).replaceAll("(?i)\\b[0-9a-f]{7,}\\b",
                        "WINDOW_ID"));
                Integer headerDisplay=number(DISPLAY,line);
                if(headerDisplay!=null)current.display=headerDisplay;
                result.add(current);
            }else if(current!=null){
                Integer display=number(DISPLAY,line);
                if(display!=null)current.display=display;
                Integer layer=number(LAYER,line);
                if(layer!=null)current.z=layer;
                if(!"?".equals(type(line)))current.type=type(line);
                if(line.contains("isOnScreen=true")||
                   line.contains("isVisible=true")||
                   line.contains("mHasSurface=true"))
                    current.visible=true;
            }
        }
        return result;
    }
    private static void parseSurface(String sf,Snapshot result){
        // SurfaceFlinger dumps vary wildly between Android 11 vendor ROMs.
        // Do NOT infer a global Z order from the order of --list names.
        int candidates=0,withZ=0;
        for(String line:sf.split("\\r?\\n")){
            Matcher named=SF_NAME.matcher(line);
            if(!named.find())continue;
            String kind=category(named.group(1));
            if(kind.startsWith("OTHER_"))continue;
            candidates++;
            Integer z=number(SF_Z,line);
            if(z!=null)withZ++;
            if(result.lines.size()<60)
                result.lines.add("SF: "+kind+" z="+(z==null?"?":z)+
                    " (display no garantizado)");
        }
        result.lines.add("SurfaceFlinger: superficies de interés="+candidates+
            ", con z explícito="+withZ+
            ". El z global sólo es comparable si padre/display coinciden.");
    }
    static String compare(Snapshot hidden,Snapshot shown){
        if(hidden==null||shown==null)
            return "Necesitamos dos capturas: TAPADO y VISIBLE";
        if(!"TAPADO".equals(hidden.name)||!"VISIBLE".equals(shown.name))
            return "Secuencia inválida: primero TAPADO y después VISIBLE";
        StringBuilder out=new StringBuilder(
            "COMPARACIÓN JETOUR MODE · NINGUNA CAPA MODIFICADA");
        out.append("\nTAPADO: Waze=").append(hidden.waze)
            .append(" Maps=").append(hidden.maps)
            .append(" MODE=").append(hidden.mode)
            .append(" Spotify=").append(hidden.spotify);
        out.append("\nVISIBLE: Waze=").append(shown.waze)
            .append(" Maps=").append(shown.maps)
            .append(" MODE=").append(shown.mode)
            .append(" Spotify=").append(shown.spotify);
        if(hidden.mode>shown.mode)
            out.append("\nINDICIO: el firmware MODE ocultó o retiró "+
                "una ventana capturable por WindowManager.");
        else if(hidden.mode==shown.mode&&hidden.mode>0)
            out.append("\nINDICIO: MODE permanece registrada; "+
                "investigar foco, capa z o SurfaceFlinger.");
        else out.append("\nNo hay capa MODE identificable en WindowManager. "+
            "Comparar SurfaceFlinger y tareas; puede ser superficie OEM.");
        int absent=0,changed=0;
        for(Map.Entry<String,String> earlier:hidden.windowState.entrySet()){
            String after=shown.windowState.get(earlier.getKey());
            if(after==null){
                absent++;
                if(absent<=8)
                    out.append("\nVENTANA RETIRADA DEL DISPLAY 1: "+
                        earlier.getValue());
            }else if(!earlier.getValue().equals(after)){
                changed++;
                if(changed<=8)
                    out.append("\nVENTANA CON CAMBIO DE CAPA/ESTADO: "+
                        earlier.getValue()+" => "+after);
            }
        }
        for(Map.Entry<String,String> later:shown.windowState.entrySet()){
            if(!hidden.windowState.containsKey(later.getKey())){
                if(absent++<8)
                    out.append("\nVENTANA NUEVA EN DISPLAY 1: "+
                        later.getValue());
            }
        }
        if(absent==0&&changed==0)
            out.append("\nWindowManager conserva las mismas ventanas "+
                "y capas parseables: probablemente hubo cambios en "+
                "SurfaceFlinger o una superficie del firmware.");
        if(shown.waze+shown.maps<2)
            out.append("\nNO CONFIRMADO: WindowManager no muestra ambos "+
                "mapas a la vez en Display 1.");
        out.append("\nNo ejecutar force-stop, pm clear, toques MODE "+
            "sintéticos ni overlays que oculten avisos críticos.");
        return out.toString();
    }
}