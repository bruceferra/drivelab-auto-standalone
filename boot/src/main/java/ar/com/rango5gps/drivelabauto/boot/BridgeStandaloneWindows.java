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
        String selected=prefs(c).getString(TAB_MAP,BridgeWindowsPolicy.WAZE);
        return BridgeDualRoutePolicy.BOTH.equals(selected)||
            BridgeWindowsPolicy.validMap(selected)?
                selected:BridgeWindowsPolicy.WAZE;
    }
    static boolean tableroEnabled(Context c){
        SharedPreferences p=prefs(c);
        return p.getBoolean(TAB_CONFIRMED,false)&&
            p.getBoolean(TAB_ROUTE,false);
    }
    static boolean routeOnTablero(Context c,String pkg){
        return tableroEnabled(c)&&
            BridgeDualRoutePolicy.selectedForTablero(pkg,tabMap(c));
    }
    static void disableTablero(Context c){
        prefs(c).edit()
            .putBoolean(TAB_CONFIRMED,false)
            .putBoolean(TAB_ROUTE,false)
            .putString(LAST,"Display 1 DESACTIVADO: próximos destinos "+
                "van a Display 0. Android puede conservar un mapa "+
                "abierto en el tablero; no se borran ni detienen tareas.").apply();
    }
    static boolean display1Available(Context c){
        try{display(c,1);return true;}
        catch(Exception unavailable){return false;}
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
    /** Manual parked-only FULLSCREEN launch on real Android Display 1.
     * Does not use Freeform/hidden APIs, bounds, scaling, ADB, OEM writes,
     * keyboard/wheel capture or boot-time activity launch. Start is only
     * a request; owner confirms actual visibility before Sender routing.
     * Both maps can receive routes on the same display even though Android
     * can show only one fullscreen activity on top at a time.
     */
    static Result tablero(Activity activity){
        String selected=tabMap(activity);
        String[] apps=BridgeDualRoutePolicy.BOTH.equals(selected)?
            new String[]{BridgeWindowsPolicy.WAZE,BridgeWindowsPolicy.MAPS}:
            new String[]{selected};
        try{
            Display target=display(activity,1);
            Point point=pixels(target);
            if(point.x<400||point.y<200)
                throw new IllegalArgumentException(
                    "Display 1 no tiene resolución válida");
            // Preflight ALL apps before opening any; never open something
            // unintentionally on Display 0 as a fallback.
            List<Intent> intents=new ArrayList<>();
            PackageManager pm=activity.getPackageManager();
            for(String pkg:apps){
                if(!BridgeWindowsPolicy.validMap(pkg))
                    throw new SecurityException("Sólo Waze/Google Maps");
                Intent intent=pm.getLaunchIntentForPackage(pkg);
                if(intent==null||intent.getComponent()==null||
                   !pkg.equals(intent.getComponent().getPackageName()))
                    throw new IllegalStateException(pkg+" no está instalado");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intents.add(intent);
            }
            int sent=0;
            ActivityOptions options=ActivityOptions.makeBasic();
            options.setLaunchDisplayId(1);
            for(Intent intent:intents){
                activity.startActivity(intent,options.toBundle());
                sent++;
            }
            String message="Display 1: "+sent+" solicitudes a pantalla "+
                "completa (100%, sin Freeform). "+
                "Android aceptó abrir "+selected+
                ", pero la ubicación y visibilidad requieren "+
                "confirmación visual. No se alteraron tareas Jetour.";
            prefs(activity).edit().putString(LAST,message).apply();
            return new Result(true,message);
        }catch(Exception failed){
            return reject(activity,"Display 1: solicitud NO completada: "+
                failed.getClass().getSimpleName()+": "+
                String.valueOf(failed.getMessage())+
                ". No hubo sustitución automática por Display 0.");
        }
    }
    private static Result reject(Context c,String reason){
        prefs(c).edit().putString(LAST,reason).apply();
        return new Result(false,reason);
    }
}
