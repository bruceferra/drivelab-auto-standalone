package ar.com.rango5gps.drivelabauto.receiver;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import java.util.ArrayList;
import java.util.List;

/**
 * Own native display routing, NOT a copied T1Launcher ClusterService:
 * the OEM/third-party cluster may use Presentation or MediaProjection,
 * neither of which grants this app the right to display another app.
 *
 * Only a user-chosen, public NON-DEFAULT display can receive Waze. Fail
 * closed if Android forbids it; never fall back to launching Waze as an
 * unexpected window over the center display while cluster mode is enabled.
 * No overlay windows, MediaProjection, shell/ADB, OEM writes or HOME role.
 */
final class T1ClusterWazeRouter {
    static final String PREFS="t1_cluster_native_waze_v1";
    static final String ENABLED="enabled";
    static final String AUTO_BOOT="auto_boot";
    static final String DISPLAY_ID="display_id";
    static final String DISPLAY_NAME="display_name";
    static final String DISPLAY_WIDTH="display_width";
    static final String DISPLAY_HEIGHT="display_height";
    static final String APP_PACKAGE="app_package";
    static final String APP_LABEL="app_label";
    static final String STATUS="status";
    static final String CLOSE_PENDING="cluster_close_pending";
    static final String VISUAL_OEM_CONFIRMED="cluster_oem_visually_confirmed";
    static final String LAST_REQUEST_MS="last_request_ms";
    private static final String WAZE="com.waze";
    private T1ClusterWazeRouter(){}

    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean enabled(Context c){
        return prefs(c).getBoolean(ENABLED,false);
    }
    static boolean autoBoot(Context c){
        return enabled(c)&&prefs(c).getBoolean(AUTO_BOOT,false);
    }
    static void status(Context c,String value){
        prefs(c).edit().putString(STATUS,value).apply();
    }
    static boolean multiDisplay(Context c){
        return c.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
    }
    static List<Display> candidates(Context c){
        ArrayList<Display> found=new ArrayList<>();
        DisplayManager manager=(DisplayManager)c.getSystemService(
            Context.DISPLAY_SERVICE);
        if(manager==null)return found;
        for(Display d:manager.getDisplays()){
            if(d==null||d.getDisplayId()==Display.DEFAULT_DISPLAY||
                d.getDisplayId()==Display.INVALID_DISPLAY)continue;
            // Android forbids launching other packages on private displays.
            if((d.getFlags()&Display.FLAG_PRIVATE)!=0)continue;
            if(d.getState()==Display.STATE_OFF)continue;
            found.add(d);
        }
        return found;
    }
    static String describe(Display d){
        return "Display "+d.getDisplayId()+" · "+d.getName()+
            " · "+d.getMode().getPhysicalWidth()+"×"+
            d.getMode().getPhysicalHeight();
    }
    static boolean selectionExists(Context c){
        return prefs(c).getInt(DISPLAY_ID,Display.INVALID_DISPLAY)>0&&
            !prefs(c).getString(DISPLAY_NAME,"").isEmpty();
    }
    static Display selected(Context c){
        if(!selectionExists(c))return null;
        SharedPreferences p=prefs(c);
        int id=p.getInt(DISPLAY_ID,Display.INVALID_DISPLAY);
        String name=p.getString(DISPLAY_NAME,"");
        int width=p.getInt(DISPLAY_WIDTH,0);
        int height=p.getInt(DISPLAY_HEIGHT,0);
        List<Display> available=candidates(c);
        for(Display d:available)
            if(d.getDisplayId()==id&&name.equals(d.getName())&&
                (width==0||d.getMode().getPhysicalWidth()==width)&&
                (height==0||d.getMode().getPhysicalHeight()==height))
                return d;
        // IDs can change after boot. Reidentify ONLY if the name and
        // resolution uniquely match one public non-primary display.
        Display only=null;
        for(Display d:available){
            if(!name.equals(d.getName())||width<=0||height<=0||
                d.getMode().getPhysicalWidth()!=width||
                d.getMode().getPhysicalHeight()!=height)continue;
            if(only!=null)return null;
            only=d;
        }
        if(only!=null)p.edit().putInt(DISPLAY_ID,only.getDisplayId()).apply();
        return only;
    }
    static void choose(Context c,Display d){
        if(d==null||d.getDisplayId()==Display.DEFAULT_DISPLAY||
           !candidates(c).stream().anyMatch(x->
               x.getDisplayId()==d.getDisplayId()&&
               x.getName().equals(d.getName())))
            throw new IllegalArgumentException("Display no apto");
        prefs(c).edit()
            .putInt(DISPLAY_ID,d.getDisplayId())
            .putString(DISPLAY_NAME,d.getName())
            .putInt(DISPLAY_WIDTH,d.getMode().getPhysicalWidth())
            .putInt(DISPLAY_HEIGHT,d.getMode().getPhysicalHeight())
            .putBoolean(ENABLED,false)
            .putBoolean(T1ClusterLayoutPolicy.ACTIVE,false)
            .putBoolean(AUTO_BOOT,false)
            .putString(STATUS,"Display elegido: "+describe(d)+
                ". Primero probá Waze detenido, después activá clúster.")
            .apply();
    }
    /** Selected launchable app for the cluster. Defaults to the known-working
     * Waze. This preference does NOT change where the Sender/voice routes go:
     * explicit navigations always open Waze, even if another app is on screen.
     */
    static String selectedAppPackage(Context c){
        return prefs(c).getString(APP_PACKAGE,WAZE);
    }
    static String selectedAppLabel(Context c){
        return prefs(c).getString(APP_LABEL,"Waze");
    }
    static boolean isSelectablePackage(Context c,String packageName){
        if(packageName==null||packageName.isEmpty()||
            packageName.equals(c.getPackageName())||
            packageName.equals("com.t1launcher")||
            packageName.equals("com.android.settings")||
            packageName.equals("com.android.systemui")||
            packageName.startsWith("com.autolink."))return false;
        // Only visible launchable applications: no implicit intents, component
        // injection, overlays, OEM modules or arbitrary package commands.
        return c.getPackageManager().getLaunchIntentForPackage(packageName)!=null;
    }
    static void chooseApp(Context c,String packageName,String label){
        if(!isSelectablePackage(c,packageName))
            throw new IllegalArgumentException("No es una app pública iniciable");
        prefs(c).edit()
            .putString(APP_PACKAGE,packageName)
            .putString(APP_LABEL,label)
            .putBoolean(T1ClusterLayoutPolicy.ACTIVE,false)
            // Never silently replace what auto-launches on the next boot.
            .putBoolean(AUTO_BOOT,false)
            .putString(STATUS,"Aplicación elegida: "+label+
                ". Probala en P y 0 km/h antes de reactivar el arranque.")
            .apply();
    }
    private static String launchOnDisplay(Context c,Intent launch,
                                          boolean manualTest,boolean fromBoot,
                                          String label,boolean waze){
        if(!manualTest&&!enabled(c))
            return "Clúster independiente desactivado";
        if(!multiDisplay(c))
            return "Android no habilita actividades en pantallas secundarias";
        Display d=selected(c);
        if(d==null)
            return "Display secundario cambió/no está visible. "+
                "Reconfigurá en CLÚSTER; no abriré apps sobre la pantalla central.";
        if(fromBoot&&!Settings.canDrawOverlays(c)&&
           !ReceiverActivity.isVisible())
            return "Android bloquea abrir una app en segundo plano: "+
                "falta permiso «Mostrar sobre otras apps».";
        Intent intent=new Intent(launch);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
            Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{
            ActivityOptions options=ActivityOptions.makeBasic()
                .setLaunchDisplayId(d.getDisplayId());
            c.startActivity(intent,options.toBundle());
            prefs(c).edit().putLong(LAST_REQUEST_MS,
                System.currentTimeMillis()).apply();
            return (waze?"Waze SOLICITADO":"APP SOLICITADA: "+label)+
                " para "+describe(d)+
                ". Verificá físicamente el clúster; no es confirmación visual.";
        }catch(RuntimeException e){
            return "Android/Jetour rechazó abrir "+label+" en "+
                describe(d)+": "+e.getClass().getSimpleName()+
                ". No se abrió ventana de respaldo en pantalla central.";
        }
    }
    /** Deliberate app launch for a stopped vehicle, or the user-enabled
     * auto-boot option. Never called for an incoming navigation intent.
     */
    static String launchSelectedApp(Context c,boolean manualTest,
                                    boolean fromBoot){
        String pkg=selectedAppPackage(c);
        if(!isSelectablePackage(c,pkg))
            return "Aplicación elegida no disponible: "+pkg+
                ". Elegí otra en CLÚSTER.";
        Intent app=c.getPackageManager().getLaunchIntentForPackage(pkg);
        if(app==null)
            return "La app no expone una pantalla de inicio: "+pkg;
        app.setPackage(pkg);
        return launchOnDisplay(c,app,manualTest,fromBoot,
            selectedAppLabel(c),WAZE.equals(pkg));
    }
    static boolean openSelectedAppTest(Context c){
        String result=launchSelectedApp(c,true,false);
        status(c,result);
        return result.startsWith("Waze SOLICITADO")||
            result.startsWith("APP SOLICITADA:");
    }
    static String launchWaze(Context c,Intent intent,
                             boolean manualTest,boolean fromBoot){
        if(!WAZE.equals(intent.getPackage()))
            return "Rechazado: solo está autorizado Waze para destinos";
        Intent launch=new Intent(intent);
        launch.setPackage(WAZE);
        return launchOnDisplay(c,launch,manualTest,fromBoot,"Waze",true);
    }
    /** Google Maps destination, only on the chosen secondary display. */
    static String routeGoogleMaps(Context c,Intent maps){
        if(!"com.google.android.apps.maps".equals(maps.getPackage()))
            return "Rechazado: el destino no es de Google Maps";
        if(!enabled(c))return "CLÚSTER OFF: no enviar al Display 1";
        if(T1ClusterLayoutPolicy.active(c)&&
            T1ClusterLayoutPolicy.MODE_SPLIT.equals(
                T1ClusterLayoutPolicy.mode(c))){
            if(!"com.google.android.apps.maps".equals(
                T1ClusterLayoutPolicy.secondPackage(c)))
                return "La ventana derecha no está configurada para Google Maps";
            Display display=selected(c);
            if(display==null)return "Display 1 no disponible";
            return T1ClusterLayoutPolicy.start(c,display,maps,
                T1ClusterLayoutPolicy.geometry(c,display,
                    !T1ClusterLayoutPolicy.inverted(c)),"Google Maps");
        }
        return launchOnDisplay(c,maps,false,false,"Google Maps",false);
    }
    static boolean routeDestination(Context c,Intent waze){
        // Never silently replace a user-selected unverified 50/50 with FULL
        // after receiving an Uber or spoken navigation destination.
        if(enabled(c)&&T1ClusterLayoutPolicy.pendingUnverified(c)){
            status(c,T1ClusterLayoutPolicy.pendingMessage(c));
            return false;
        }
        String shaped=enabled(c)?
            T1ClusterLayoutPolicy.routeWazeIfActive(c,waze):null;
        String result=shaped==null?
            launchWaze(c,waze,false,false):shaped;
        status(c,result);
        return result.startsWith("Waze SOLICITADO")||
            result.startsWith("APP SOLICITADA EN VENTANA:");
    }
    static boolean openManualTest(Context c){
        Intent waze=c.getPackageManager().getLaunchIntentForPackage(WAZE);
        if(waze==null){
            status(c,"Waze no está instalada/no tiene actividad visible");
            return false;
        }
        String result=launchWaze(c,waze,true,false);
        status(c,result);
        return result.startsWith("Waze SOLICITADO");
    }
    static String onReceiverBoot(Context c){
        if(!autoBoot(c))return "Arranque del clúster desactivado";
        if(selected(c)==null){
            String reason="Arranque: display no coincide con el elegido. "+
                "Se preservan la pantalla central y el launcher OEM.";
            status(c,reason);
            return reason;
        }
        // Delayed ignition auto-boot must not clobber an unverified 50/50.
        if(T1ClusterLayoutPolicy.pendingUnverified(c)){
            String reason=T1ClusterLayoutPolicy.pendingMessage(c);
            status(c,reason);
            return reason;
        }
        String opened=T1ClusterLayoutPolicy.active(c)?
            T1ClusterLayoutPolicy.boot(c):
            launchSelectedApp(c,false,true);
        status(c,opened);
        return opened;
    }
}