package ar.com.rango5gps.drivelabauto.boot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone owner of manual CENTER and TABLERO window requests.
 * No dependency on DriveLab Auto / Receiver, no ADB shell mutation,
 * task kills, data clearing or OEM hardware control.
 *
 * Freeform request uses the Android 11 framework's non-SDK launch
 * windowing API. If blocked by OEM hidden-API enforcement, fails closed:
 * it must NEVER silently open an unwanted full-screen application.
 *
 * startActivity is only an accepted REQUEST, NOT evidence the window
 * became visible or that the Jetour firmware's MODE layer moved behind.
 */
final class BridgeStandaloneWindows {
    static final String PREFS="drivelab_bridge_windows_v1";
    static final String FIRST="center_first",SECOND="center_second",
        RATIO="center_ratio",TAB_MAP="tab_map",LAST="window_request_result",
        TAB_CONFIRMED="tab_confirmed",TAB_ROUTE="tab_route";
    private static final int FREEFORM=5;
    private BridgeStandaloneWindows(){}
    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static String first(Context c){
        return prefs(c).getString(FIRST,BridgeWindowsPolicy.MAPS);
    }
    static String second(Context c){
        return prefs(c).getString(SECOND,BridgeWindowsPolicy.SPOTIFY);
    }
    static int ratio(Context c){
        return prefs(c).getInt(RATIO,65);
    }
    static String tabMap(Context c){
        return prefs(c).getString(TAB_MAP,BridgeWindowsPolicy.MAPS);
    }
    static boolean routeOnTablero(Context c,String pkg){
        SharedPreferences p=prefs(c);
        return BridgeWindowsPolicy.validMap(pkg)&&
            pkg.equals(tabMap(c))&&
            p.getBoolean(TAB_CONFIRMED,false)&&
            p.getBoolean(TAB_ROUTE,false);
    }
    static final class Result{
        final boolean submitted;
        final String message;
        Result(boolean yes,String text){submitted=yes;message=text;}
    }
    private static final class Prepared{
        final Intent intent;
        final ActivityOptions options;
        Prepared(Intent intent,ActivityOptions options){
            this.intent=intent;this.options=options;
        }
    }
    private static Display display(Context c,int id){
        DisplayManager manager=(DisplayManager)c.getSystemService(
            Context.DISPLAY_SERVICE);
        Display d=manager==null?null:manager.getDisplay(id);
        if(d==null||d.getState()==Display.STATE_OFF)
            throw new IllegalStateException(
                "Display "+id+" desconectado o apagado");
        if(id!=0&&(d.getFlags()&Display.FLAG_PRIVATE)!=0)
            throw new SecurityException(
                "Display 1 es PRIVADO del sistema; Bridge no lo invadirá");
        return d;
    }
    private static Point pixels(Display display){
        Point p=new Point();
        display.getRealSize(p);
        if(p.x<400||p.y<200)
            throw new IllegalStateException("Pantalla sin geometría usable");
        return p;
    }
    // Only the manually confirmed, parked-only Freeform probe uses this
    // Android 11 OEM API. If Jetour blocks it, NO activity is launched.
    // Narrow lint suppression; all other fatal Android Lint rules stay on.
    @SuppressLint("BlockedPrivateApi")
    private static Method freeformMethod()throws Exception{
        // API hidden on stock Android 11. Fail before starting any app
        // if OEM policy blocks it, rather than opening fullscreen.
        Method setter=ActivityOptions.class.getDeclaredMethod(
            "setLaunchWindowingMode",int.class);
        setter.setAccessible(true);
        return setter;
    }
    private static Prepared prepare(Activity activity,Method method,
                                    int displayId,String pkg,
                                    BridgeWindowMath.Frame frame)throws Exception{
        if(!BridgeWindowsPolicy.allowed(pkg))
            throw new SecurityException("Aplicación no permitida: "+pkg);
        PackageManager manager=activity.getPackageManager();
        Intent intent=manager.getLaunchIntentForPackage(pkg);
        if(intent==null||intent.getComponent()==null||
           !pkg.equals(intent.getComponent().getPackageName()))
            throw new IllegalStateException(
                "No hay entrada LAUNCHER instalada: "+pkg);
        // Do not CLEAR_TOP, CLEAR_TASK, MULTIPLE_TASK or force-stop:
        // preserve user's route, Spotify session and existing app data.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
            Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ActivityOptions options=ActivityOptions.makeBasic();
        method.invoke(options,FREEFORM);
        options.setLaunchDisplayId(displayId);
        options.setLaunchBounds(new Rect(frame.left,frame.top,
            frame.right,frame.bottom));
        return new Prepared(intent,options);
    }
    private static Result launch(Activity activity,List<Prepared> jobs,
                                 int displayId){
        int done=0;
        try{
            for(Prepared p:jobs){
                activity.startActivity(p.intent,p.options.toBundle());
                done++;
            }
        }catch(Exception e){
            String error="Display "+displayId+
                ": "+done+" de "+jobs.size()+" solicitudes enviadas; "+
                e.getClass().getSimpleName()+
                ". NO se forzó cierre ni se borraron mapas.";
            prefs(activity).edit().putString(LAST,error).apply();
            return new Result(false,error);
        }
        String state="Display "+displayId+": "+done+
            " solicitudes Freeform enviadas desde BRIDGE."+
            " VENTANAS NO VERIFICADAS FÍSICAMENTE. "+
            "Sin Receiver; sin detener ni borrar aplicaciones.";
        prefs(activity).edit().putString(LAST,state).apply();
        return new Result(true,state);
    }
    static Result center(Activity activity){
        String first=first(activity),second=second(activity);
        int ratio=ratio(activity);
        if(!BridgeWindowsPolicy.validCenter(first,second,ratio))
            return reject(activity,"Elegí dos apps distintas; DriveLab Auto "+
                "y apps OEM están excluidas");
        try{
            Display d=display(activity,Display.DEFAULT_DISPLAY);
            Point point=pixels(d);
            BridgeWindowMath.Frame[] frames=
                BridgeWindowMath.center(point.x,point.y,ratio,0);
            if(!BridgeWindowMath.disjoint(frames[0],frames[1]))
                throw new IllegalArgumentException(
                    "Las ventanas de Display 0 se superponen");
            Method method=freeformMethod();
            List<Prepared> all=new ArrayList<>(2);
            all.add(prepare(activity,method,0,first,frames[0]));
            all.add(prepare(activity,method,0,second,frames[1]));
            return launch(activity,all,0);
        }catch(Exception e){
            return reject(activity,
                "Display 0: NO SE ABRIERON APPS. "+
                e.getClass().getSimpleName()+": "+
                String.valueOf(e.getMessage())+
                ". Freeform bloqueado o no disponible en este Android.");
        }
    }
    /** Explicit parked-only experiment. No boot autolaunch and never
     * touches SystemUI, MODE, wheel keys or the T1 OEM task.
     * Bounded map area to leave the instrument regions intact as far
     * as the vendor display permits. Must be physically inspected.
     */
    static Result tablero(Activity activity){
        // A new request is not proof that the previous map is still
        // visible. Invalidate Sender routing before attempting it.
        prefs(activity).edit()
            .putBoolean(TAB_CONFIRMED,false)
            .putBoolean(TAB_ROUTE,false).apply();
        String map=tabMap(activity);
        if(!BridgeWindowsPolicy.validMap(map))
            return reject(activity,"Display 1 sólo admite Waze o Google Maps");
        try{
            Display d=display(activity,1);
            Point p=pixels(d);
            BridgeWindowMath.Frame area=
                BridgeWindowMath.display1(p.x,p.y,8,68,62);
            Method method=freeformMethod();
            List<Prepared> jobs=new ArrayList<>(1);
            jobs.add(prepare(activity,method,1,map,area));
            return launch(activity,jobs,1);
        }catch(Exception e){
            return reject(activity,
                "Display 1: NO SE ABRIÓ el mapa. "+
                e.getClass().getSimpleName()+": "+
                String.valueOf(e.getMessage())+
                ". No se modificó la interfaz OEM.");
        }
    }
    private static Result reject(Context c,String reason){
        prefs(c).edit().putString(LAST,reason).apply();
        return new Result(false,reason);
    }
}
