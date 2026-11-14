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
    static final String STATUS="status";
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
            .putBoolean(AUTO_BOOT,false)
            .putString(STATUS,"Display elegido: "+describe(d)+
                ". Primero probá Waze detenido, después activá clúster.")
            .apply();
    }
    static String launchWaze(Context c,Intent intent,
                             boolean manualTest,boolean fromBoot){
        if(!manualTest&&!enabled(c)){
            return "Clúster independiente desactivado";
        }
        if(!WAZE.equals(intent.getPackage())){
            return "Rechazado: solo está autorizado Waze";
        }
        if(!multiDisplay(c)){
            return "Android no habilita actividades en pantallas secundarias";
        }
        Display d=selected(c);
        if(d==null){
            return "Display secundario cambió/no está visible. "+
                "Reconfigurá en CLÚSTER; no abriré Waze sobre la pantalla central.";
        }
        if(fromBoot&&!Settings.canDrawOverlays(c)&&
            !ReceiverActivity.isVisible()){
            return "Android bloquea abrir Waze en segundo plano: "+
                "falta permiso «Mostrar sobre otras apps».";
        }
        Intent launch=new Intent(intent);
        launch.setPackage(WAZE);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
            Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{
            ActivityOptions options=ActivityOptions.makeBasic()
                .setLaunchDisplayId(d.getDisplayId());
            // Android may reject a private/restricted cluster even if visible.
            c.startActivity(launch,options.toBundle());
            prefs(c).edit().putLong(LAST_REQUEST_MS,
                System.currentTimeMillis()).apply();
            return "Waze SOLICITADO para "+describe(d)+
                ". Verificá físicamente el clúster; no es confirmación visual.";
        }catch(RuntimeException e){
            return "Android/Jetour rechazó abrir Waze en "+
                describe(d)+": "+e.getClass().getSimpleName()+
                ". No se abrió ventana de respaldo en pantalla central.";
        }
    }
    static boolean routeDestination(Context c,Intent waze){
        String result=launchWaze(c,waze,false,false);
        status(c,result);
        return result.startsWith("Waze SOLICITADO");
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
    static void onReceiverBoot(Context c){
        if(!autoBoot(c))return;
        if(selected(c)==null){
            status(c,"Arranque: display no coincide con el elegido. "+
                "Se preservan la pantalla central y el launcher OEM.");
            return;
        }
        Intent waze=c.getPackageManager().getLaunchIntentForPackage(WAZE);
        if(waze==null){
            status(c,"Arranque: Waze no disponible en Jetour");
            return;
        }
        status(c,launchWaze(c,waze,false,true));
    }
}