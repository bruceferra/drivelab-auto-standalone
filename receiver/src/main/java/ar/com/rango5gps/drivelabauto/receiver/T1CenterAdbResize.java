package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

/** Reuses the verified cluster ADB task-resize mechanism on Display 0.
 * Only known user apps; never OEM, HOME or cluster tasks.
 */
final class T1CenterAdbResize {
    interface Done {void completed(String result);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private T1CenterAdbResize(){}
    static boolean allowed(String pkg){
        return pkg!=null&&pkg.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")&&
            !pkg.startsWith("com.android.")&&!pkg.startsWith("android.")&&
            !pkg.startsWith("com.jetour.")&&!pkg.startsWith("com.chery.")&&
            !pkg.startsWith("com.autolink.");
    }
    static void apply(Context context,String[] packages,
                      T1CenterMosaicGeometry.Tile[] tiles,int count,Done done){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String result;
            try{
                if(count<2||count>3)throw new IllegalArgumentException("Paneles inválidos");
                java.util.HashSet<String> used=new java.util.HashSet<>();
                for(int i=0;i<count;i++){
                    if(!allowed(packages[i])||!used.add(packages[i]))
                        throw new IllegalStateException(
                            "Elegí tres aplicaciones distintas y válidas para Display 0");
                }
                // This is layout configuration, not normal climate/map use.
                // Never resize unknown-speed moving tasks.
                String before=tasks(app);
                String beforeDump=readOptional(app,"dumpsys activity activities");
                String split=readOptional(app,"am supports-split-screen-multi-window");
                boolean freeform=app.getPackageManager().hasSystemFeature(
                    "android.software.freeform_window_management");
                StringBuilder trace=new StringBuilder();
                trace.append("\nFreeform declarado: ").append(freeform?"SÍ":"NO")
                    .append(" · Split-screen ADB: ").append(shortOutput(split,80));
                T1AdbTaskParser.Task[] selected=new T1AdbTaskParser.Task[count];
                for(int i=0;i<count;i++){
                    selected[i]=findTask(before,beforeDump,packages[i]);
                    if(selected[i]==null)throw new IllegalStateException(
                        "Tarea única no encontrada en Display 0: "+packages[i]+
                        ". Abrí primero las aplicaciones.");
                    trace.append("\nPanel ").append(i+1).append(" ANTES: ")
                        .append(selected[i]);
                }
                for(int i=0;i<count;i++){
                    Rect rect=new Rect(tiles[i].left,tiles[i].top,
                        tiles[i].right,tiles[i].bottom);
                    if(rect.left<0||rect.top<0||rect.width()<80||
                        rect.height()<80||rect.right>8192||rect.bottom>8192)
                        throw new IllegalStateException("Geometría inválida");
                    String response=T1LocalAdbClient.shell(app,
                        "am task resize "+selected[i].id+" "+
                        rect.left+" "+rect.top+" "+rect.right+" "+rect.bottom);
                    trace.append("\nPanel ").append(i+1)
                        .append(" ADB: ").append(response==null||
                            response.trim().isEmpty()?"sin texto":shortOutput(response,160));
                    if(response!=null&&(response.toLowerCase().contains("error")||
                        response.toLowerCase().contains("exception")||
                        response.toLowerCase().contains("failed")))
                        throw new IllegalStateException("ADB rechazó panel "+
                            (i+1)+": "+shortOutput(response,180));
                }
                Thread.sleep(500);
                String after=tasks(app);
                String afterDump=readOptional(app,"dumpsys activity activities");
                int verified=0;
                for(int i=0;i<count;i++){
                    T1AdbTaskParser.Task t=findTask(after,afterDump,packages[i]);
                    T1AdbTaskParser.Box expected=new T1AdbTaskParser.Box(
                        tiles[i].left,tiles[i].top,tiles[i].right,tiles[i].bottom);
                    boolean ok=t!=null&&t.id==selected[i].id&&
                        t.bounds!=null&&t.bounds.near(expected,20);
                    if(ok)verified++;
                    trace.append("\nPanel ").append(i+1)
                        .append(" ESPERADO: ").append(expected)
                        .append(" · REAL: ").append(t==null?"no identificado":t)
                        .append(" · ").append(ok?"VERIFICADO":
                            t==null?"tarea no identificada":
                            t.id!=selected[i].id?"ID de tarea cambió":
                            t.bounds==null?"sin geometría informada":
                            "geometría diferente");
                    if(t!=null)trace.append("\n  dumpsys: ")
                        .append(taskLine(afterDump,t.id));
                }
                result=(verified==count?
                    "ADB VERIFICÓ "+verified+"/"+count+
                    " geometrías en Display 0. Confirmá visualmente.":
                    "ADB verificó "+verified+"/"+count+" paneles en Display 0. "+
                    "Diagnóstico por panel abajo; NO se confirma mosaico.")
                    +trace.toString();
            }catch(Exception ex){
                result="MOSAICO ADB NO CONFIRMADO: "+
                    T1LocalAdbClient.connectivityHint(ex);
            }
            final String message=result;
            MAIN.post(()->done.completed(message));
        },"T1-Center-ADB-Resize").start();
    }
    /** Read-only diagnostics; never launches, moves or resizes a task. */
    static void diagnose(Context context,String[] packages,int count,Done done){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String result;
            try{
                String list=tasks(app);
                String dump=readOptional(app,"dumpsys activity activities");
                String split=readOptional(app,"am supports-split-screen-multi-window");
                boolean freeform=app.getPackageManager().hasSystemFeature(
                    "android.software.freeform_window_management");
                StringBuilder out=new StringBuilder("DIAGNÓSTICO DISPLAY 0 · SOLO LECTURA");
                out.append("\nFreeform declarado: ").append(freeform?"SÍ":"NO")
                    .append("\nSplit-screen ADB: ").append(shortOutput(split,120))
                    .append("\nPuerto ADB: ").append(T1LocalAdbClient.connectedPort());
                for(int i=0;i<count;i++){
                    T1AdbTaskParser.Task t=findTask(list,dump,packages[i]);
                    out.append("\nPanel ").append(i+1).append(" · ")
                        .append(packages[i]).append("\n  ")
                        .append(t==null?"Tarea no encontrada o ambigua":t.toString());
                    if(t!=null)out.append("\n  dumpsys: ")
                        .append(taskLine(dump,t.id));
                }
                result=out.toString();
            }catch(Exception ex){
                result="Diagnóstico ADB: "+T1LocalAdbClient.connectivityHint(ex);
            }
            String doneResult=result;
            MAIN.post(()->done.completed(doneResult));
        },"T1-Center-ADB-Diagnose").start();
    }
    private static String readOptional(Context c,String command){
        try{return T1LocalAdbClient.shell(c,command);}
        catch(Exception ex){return "Error al leer "+command+": "+ex.getMessage();}
    }
    private static T1AdbTaskParser.Task findTask(
            String primary,String fallback,String pkg){
        T1AdbTaskParser.Task one=T1AdbTaskParser.uniqueCenter(primary,pkg);
        T1AdbTaskParser.Task two=T1AdbTaskParser.uniqueCenter(fallback,pkg);
        if(one==null)return two;
        if(two!=null&&two.id==one.id&&one.bounds==null)return two;
        return one;
    }
    private static String shortOutput(String s,int limit){
        if(s==null||s.trim().isEmpty())return "sin respuesta";
        String clean=s.replace('\r',' ').replace('\n',' ').trim();
        return clean.substring(0,Math.min(limit,clean.length()));
    }
    /** A raw Task line may expose fullscreen/freeform mode on vendor builds. */
    private static String taskLine(String dump,int id){
        if(dump==null||dump.isEmpty())return "sin datos";
        java.util.regex.Pattern p=java.util.regex.Pattern.compile(
            "(?:taskId[=:]\\s*|Task\\{[^}]*?#)"+id+"(?![0-9])");
        for(String raw:dump.split("\\r?\\n")){
            if(p.matcher(raw).find())return shortOutput(raw,230);
        }
        return "línea de tarea no expuesta por dumpsys";
    }
    private static String tasks(Context c)throws Exception{
        String s=T1LocalAdbClient.shell(c,"am stack list");
        if(s==null||s.trim().isEmpty()||
            s.toLowerCase().contains("unknown command"))
            return T1LocalAdbClient.shell(c,"dumpsys activity activities");
        return s;
    }
}
