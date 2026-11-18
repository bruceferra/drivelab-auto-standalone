package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Read-only forensic snapshot of DriveLab-owned Display 1 navigation tasks.
 * Does NOT inspect/stop the T1Launcher cluster or mutate Android tasks.
 * Task listing and process IDs alone cannot prove the topmost OEM layer.
 */
final class T1ClusterTaskSnapshot {
    private static final String RESULT="cluster_task_snapshot_result_v1";
    private static final String AT="cluster_task_snapshot_at_v1";
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";
    private T1ClusterTaskSnapshot(){}

    static String last(Context c){
        SharedPreferences p=T1ClusterWazeRouter.prefs(c);
        long when=p.getLong(AT,0L);
        if(when<=0)return "Sin diagnóstico de tareas todavía";
        return new SimpleDateFormat("dd/MM HH:mm:ss",Locale.ROOT)
            .format(new Date(when))+" · "+
            p.getString(RESULT,"Sin resultados");
    }
    static String lastTime(Context c){
        long when=T1ClusterWazeRouter.prefs(c).getLong(AT,0L);
        return when<=0?"Aún no leído":
            "Última lectura "+new SimpleDateFormat(
                "dd/MM HH:mm:ss",Locale.ROOT).format(new Date(when));
    }
    private static String shortValue(String value){
        if(value==null)return "(sin respuesta)";
        String trimmed=value.trim().replace('\n',' ');
        return trimmed.length()>110?trimmed.substring(0,110):trimmed;
    }
    private static boolean invalid(String raw){
        if(raw==null||raw.trim().isEmpty())return true;
        String l=raw.toLowerCase(Locale.ROOT);
        return l.contains("permission denial")||
            l.contains("securityexception")||l.startsWith("error:")||
            l.startsWith("unknown command")||
            l.startsWith("exception:");
    }
    private static void describe(StringBuilder out,
                                 List<T1AdbTaskParser.Task> tasks,
                                 String pkg){
        int found=0;
        for(T1AdbTaskParser.Task task:tasks){
            if(!pkg.equals(task.app))continue;
            found++;
            out.append("\n  tarea ").append(task.id)
                .append(" · DISPLAY ").append(task.display)
                .append(" · límites ").append(task.bounds==null?
                    "no informados":task.bounds.toString());
        }
        if(found==0)out.append("\n  ninguna tarea parseable");
    }
    private static String pid(Context c,String pkg){
        try{
            String s=T1LocalAdbClient.shell(c,"pidof "+pkg);
            if(invalid(s))return "sin proceso confirmado / sin lectura";
            // Only numeric PID tokens: no routes, titles, or untrusted logs.
            String clean=s.trim();
            return clean.matches("[0-9 ]{1,110}")?
                clean:"respuesta no interpretable";
        }catch(Exception ex){
            return "lectura no disponible ("+
                ex.getClass().getSimpleName()+")";
        }
    }
    private static String focus(Context c){
        try{
            String raw=T1LocalAdbClient.shell(c,"dumpsys window windows");
            if(invalid(raw))return "Sin permiso o sin informe de ventanas";
            // WindowManager may expose OEM Spotify as a separate surface.
            // We deliberately never copy raw titles or unrelated apps.
            int spotify=0,waze=0,maps=0,focus=0;
            for(String line:raw.split("\\r?\\n")){
                String lower=line.toLowerCase(Locale.ROOT);
                if(lower.contains("spotify"))spotify++;
                if(lower.contains("com.waze"))waze++;
                if(lower.contains("com.google.android.apps.maps"))maps++;
                if(lower.contains("mcurrentfocus")||lower.contains("mfocusedapp"))focus++;
            }
            return "menciones en WindowManager: Spotify="+spotify+
                ", Waze="+waze+", Maps="+maps+
                ", líneas foco="+focus+
                ". NO prueban orden visual ni pantalla superior.";
        }catch(Exception ex){
            return "WindowManager no disponible ("+
                ex.getClass().getSimpleName()+")";
        }
    }
    static String capture(Context ctx){
        Context c=ctx.getApplicationContext();
        StringBuilder out=new StringBuilder();
        out.append("DRIVELAB · DIAGNÓSTICO DE PANELES (SOLO LECTURA)");
        out.append("\nClúster nuestro: ").append(
            T1ClusterWazeRouter.enabled(c)?"ON":"OFF");
        out.append(" · Display elegido: ").append(
            T1ClusterWazeRouter.prefs(c).getInt(
                T1ClusterWazeRouter.DISPLAY_ID,-1));
        out.append("\nT1Launcher: no se consulta ni se modifica.");
        if(!T1ClusterAdbResize.enabled(c)){
            out.append("\nADB local desactivado: no se puede listar "+
                "la tarea real de Waze/Maps.");
            return save(c,out.toString());
        }
        try{
            String raw=T1LocalAdbClient.shell(c,"am stack list");
            List<T1AdbTaskParser.Task> tasks=T1AdbTaskParser.scan(raw);
            if(invalid(raw)||tasks.isEmpty()){
                raw=T1LocalAdbClient.shell(c,"dumpsys activity activities");
                tasks=T1AdbTaskParser.scan(raw);
            }
            out.append("\n\nWAZE:");
            describe(out,tasks,WAZE);
            out.append("\nProceso Waze: ").append(pid(c,WAZE));
            out.append("\n\nGOOGLE MAPS:");
            describe(out,tasks,MAPS);
            out.append("\nProceso Maps: ").append(pid(c,MAPS));
            out.append("\n\nSPOTIFY / CAPAS: ").append(focus(c));
            if(tasks.isEmpty())
                out.append("\nADB no permitió identificar tareas "+
                    "y pantallas. No se interpretará como mapa cerrado.");
        }catch(Exception ex){
            out.append("\nADB NO DISPONIBLE: ")
                .append(ex.getClass().getSimpleName())
                .append(" · ").append(shortValue(ex.getMessage()));
        }
        out.append("\n\nNada se cerró, movió, reinició o escribió. "+
            "Si Maps aparece en Display 0 y Waze en Display 1, "+
            "ahí está la divergencia a investigar.");
        return save(c,out.toString());
    }
    private static String save(Context c,String result){
        T1ClusterWazeRouter.prefs(c).edit()
            .putLong(AT,System.currentTimeMillis())
            .putString(RESULT,result).commit();
        return result;
    }
}
