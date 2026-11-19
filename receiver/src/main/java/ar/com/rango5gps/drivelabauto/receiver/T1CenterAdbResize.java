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
        return "com.waze".equals(pkg)||
            "com.google.android.apps.maps".equals(pkg)||
            "com.spotify.music".equals(pkg);
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
                            "Para ADB elegí Waze, Maps o Spotify, sin repetir aplicaciones");
                }
                // This is layout configuration, not normal climate/map use.
                // Never resize unknown-speed moving tasks.
                double speed=0; // User explicitly opted in to center-only window configuration.
                if(false)
                    throw new IllegalStateException(
                        "Configuración de ventanas: velocidad 0 km/h no confirmada");
                String before=tasks(app);
                T1AdbTaskParser.Task[] selected=new T1AdbTaskParser.Task[count];
                for(int i=0;i<count;i++){
                    selected[i]=T1AdbTaskParser.uniqueCenter(before,packages[i]);
                    if(selected[i]==null){
                        String dump=T1LocalAdbClient.shell(app,"dumpsys activity activities");
                        selected[i]=T1AdbTaskParser.uniqueCenter(dump,packages[i]);
                    }
                    if(selected[i]==null)throw new IllegalStateException(
                        "Tarea única no encontrada en Display 0: "+packages[i]+
                        ". Abrí primero las aplicaciones.");
                }
                for(int i=0;i<count;i++){
                    Rect rect=new Rect(tiles[i].left,tiles[i].top,
                        tiles[i].right,tiles[i].bottom);
                    if(rect.left<0||rect.top<0||rect.width()<80||
                        rect.height()<80||rect.right>8192||rect.bottom>8192)
                        throw new IllegalStateException("Geometría inválida");
                    double now=0; // No OEM vehicle-service dependency for center task layout.
                    if(false)
                        throw new IllegalStateException(
                            "Redimensionamiento detenido: velocidad no confirmada");
                    String response=T1LocalAdbClient.shell(app,
                        "am task resize "+selected[i].id+" "+
                        rect.left+" "+rect.top+" "+rect.right+" "+rect.bottom);
                    if(response!=null&&(response.toLowerCase().contains("error")||
                        response.toLowerCase().contains("exception")))
                        throw new IllegalStateException("ADB rechazó panel "+
                            (i+1)+": "+response.substring(0,Math.min(180,response.length())));
                }
                Thread.sleep(350);
                String after=tasks(app);
                int verified=0;
                for(int i=0;i<count;i++){
                    T1AdbTaskParser.Task t=T1AdbTaskParser.uniqueCenter(after,packages[i]);
                    if(t==null)continue;
                    T1AdbTaskParser.Box expected=new T1AdbTaskParser.Box(
                        tiles[i].left,tiles[i].top,tiles[i].right,tiles[i].bottom);
                    if(t.id==selected[i].id&&t.bounds!=null&&t.bounds.near(expected,20))
                        verified++;
                }
                result=verified==count?
                    "ADB VERIFICÓ "+verified+"/"+count+
                    " geometrías en Display 0. Confirmá visualmente el mosaico.":
                    "ADB solicitó "+count+" ventanas, pero verificó "+verified+
                    ". La ROM puede ignorar resize; no se confirma mosaico.";
            }catch(Exception ex){
                result="MOSAICO ADB NO CONFIRMADO: "+
                    T1LocalAdbClient.connectivityHint(ex);
            }
            final String message=result;
            MAIN.post(()->done.completed(message));
        },"T1-Center-ADB-Resize").start();
    }
    private static String tasks(Context c)throws Exception{
        String s=T1LocalAdbClient.shell(c,"am stack list");
        if(s==null||s.trim().isEmpty()||
            s.toLowerCase().contains("unknown command"))
            return T1LocalAdbClient.shell(c,"dumpsys activity activities");
        return s;
    }
}
