package ar.com.rango5gps.drivelabauto.receiver;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.provider.Settings;
import android.view.Display;
import java.lang.reflect.Method;

/**
 * Layout interoperability based on verified T1Launcher-T1N134.apk behavior:
 * cluster/LanzarApp.lanzar invokes setLaunchDisplayId + setLaunchBounds and
 * reflects ActivityOptions.setLaunchWindowingMode(5) = FREEFORM.
 *
 * Public Android API alone does not guarantee multiwindow. Fail explicitly
 * if hidden/windowing support is denied by ROM. Never show a fake success.
 * Experimental geometry is opt-in, always OFF until physically confirmed.
 */
final class T1ClusterLayoutPolicy {
    static final String MODE="layout_mode";
    static final String MODE_FULL="FULL";
    static final String MODE_WINDOW="WINDOW";
    static final String MODE_SPLIT="SPLIT";
    static final String ACTIVE="layout_active";
    static final String TEST_AT="layout_test_at";
    static final String TEST_MODE="layout_test_mode";
    static final String SPLIT_PERCENT="layout_split_pct";
    static final String PANELS_INVERTED="layout_panels_inverted";
    static final String WIDTH="layout_width_pct";
    static final String HEIGHT="layout_height_pct";
    static final String X="layout_x_pct";
    static final String Y="layout_y_pct";
    static final String SECOND_APP="layout_second_app";
    static final String PANEL_A_ON="panel_a_on";
    static final String PANEL_B_ON="panel_b_on";
    static final String SECOND_LABEL="layout_second_label";
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";

    private T1ClusterLayoutPolicy(){}
    private static SharedPreferences prefs(Context c){
        return T1ClusterWazeRouter.prefs(c);
    }
    static String mode(Context c){
        String chosen=prefs(c).getString(MODE,MODE_FULL);
        return MODE_WINDOW.equals(chosen)||MODE_SPLIT.equals(chosen)?
            chosen:MODE_FULL;
    }
    static int number(Context c,String key,int defaultValue,int min,int max){
        return T1ClusterLayoutMath.bounded(
            prefs(c).getInt(key,defaultValue),min,max);
    }
    static boolean inverted(Context c){
        return prefs(c).getBoolean(PANELS_INVERTED,false);
    }
    static void invertPanels(Context c){
        if(!MODE_SPLIT.equals(mode(c)))return;
        boolean flip=!inverted(c);
        SharedPreferences.Editor edit=prefs(c).edit()
            .putBoolean(PANELS_INVERTED,flip)
            .putInt(SPLIT_PERCENT,flip?40:50)
            .putBoolean(ACTIVE,false)
            .remove(TEST_AT).remove(TEST_MODE);
        if(flip)edit.putString(SECOND_APP,MAPS)
            .putString(SECOND_LABEL,"Google Maps");
        edit.apply();
    }
    static boolean active(Context c){
        return prefs(c).getBoolean(ACTIVE,false)&&
            !MODE_FULL.equals(mode(c));
    }
    /** Selected split/window must not silently fall back to fullscreen Waze
     * when a geometry change or new ADB test cleared active approval.
     */
    static boolean pendingUnverified(Context c){
        return !MODE_FULL.equals(mode(c))&&!active(c);
    }
    static String pendingMessage(Context c){
        return "DISEÑO "+(MODE_SPLIT.equals(mode(c))?"DIVIDIDO":"VENTANA")+
            " PENDIENTE: falta verificar tamaños por ADB en P/0 y "+
            "CONFIRMAR DISEÑO VISIBLE. No abriré Waze a pantalla completa.";
    }
    static void setMode(Context c,String desired){
        if(!MODE_FULL.equals(desired)&&!MODE_WINDOW.equals(desired)&&
           !MODE_SPLIT.equals(desired))return;
        prefs(c).edit().putString(MODE,desired)
            .putBoolean(ACTIVE,false)
            .remove(TEST_AT).remove(TEST_MODE)
            
            .apply();
    }
    static void setPercent(Context c,String key,int value,int min,int max){
        prefs(c).edit().putInt(key,
            T1ClusterLayoutMath.bounded(value,min,max))
            .putBoolean(ACTIVE,false)
            .remove(TEST_AT).remove(TEST_MODE)
            .apply();
    }
    static void secondApp(Context c,String pkg,String label){
        if(!T1ClusterWazeRouter.isSelectablePackage(c,pkg))
            throw new IllegalArgumentException("App no disponible");
        prefs(c).edit().putString(SECOND_APP,pkg)
            .putString(SECOND_LABEL,label)
            .putBoolean(ACTIVE,false)
            .remove(TEST_AT).remove(TEST_MODE)
            .apply();
    }
    static String secondPackage(Context c){
        return prefs(c).getString(SECOND_APP,MAPS);
    }
    static String secondLabel(Context c){
        return prefs(c).getString(SECOND_LABEL,"Google Maps");
    }
    static String firstPackage(Context c){
        return T1ClusterWazeRouter.selectedAppPackage(c);
    }
    static String firstLabel(Context c){
        return T1ClusterWazeRouter.selectedAppLabel(c);
    }
    static boolean panelAOn(Context c){
        return prefs(c).getBoolean(PANEL_A_ON,true);
    }
    static boolean panelBOn(Context c){
        return prefs(c).getBoolean(PANEL_B_ON,MODE_SPLIT.equals(mode(c)));
    }
    static String appInPanel(Context c,boolean panelB){
        return panelB?secondPackage(c):firstPackage(c);
    }
    static String labelInPanel(Context c,boolean panelB){
        return panelB?secondLabel(c):firstLabel(c);
    }
    static String singlePackage(Context c){
        return !panelAOn(c)&&panelBOn(c)?secondPackage(c):firstPackage(c);
    }
    static String singleLabel(Context c){
        return !panelAOn(c)&&panelBOn(c)?secondLabel(c):firstLabel(c);
    }
    /** Atomic single-Apply UX: no half-applied selections or invalidations
     * while moving the slider or choosing packages in the UI. */
    static void configurePanels(Context c,boolean aOn,String a,String aLabel,
                                boolean bOn,String b,String bLabel,int percent){
        if(!aOn&&!bOn)throw new IllegalArgumentException("Activá al menos un panel");
        if((aOn&&!T1ClusterWazeRouter.isSelectablePackage(c,a))||
           (bOn&&!T1ClusterWazeRouter.isSelectablePackage(c,b)))
            throw new IllegalArgumentException("La aplicación elegida no está disponible");
        if(aOn&&bOn&&a.equals(b))
            throw new IllegalArgumentException("Elegí aplicaciones diferentes");
        prefs(c).edit()
            .putBoolean(PANEL_A_ON,aOn).putBoolean(PANEL_B_ON,bOn)
            .putString(T1ClusterWazeRouter.APP_PACKAGE,a)
            .putString(T1ClusterWazeRouter.APP_LABEL,aLabel)
            .putString(SECOND_APP,b).putString(SECOND_LABEL,bLabel)
            .putString(MODE,aOn&&bOn?MODE_SPLIT:MODE_FULL)
            .putInt(SPLIT_PERCENT,T1ClusterLayoutMath.bounded(percent,30,70))
            .putBoolean(PANELS_INVERTED,false)
            .putBoolean(ACTIVE,false)
            .remove(TEST_AT).remove(TEST_MODE)
            .apply();
    }
    private static Rect asRect(T1ClusterLayoutMath.Frame f){
        return new Rect(f.left,f.top,f.right,f.bottom);
    }
    static Rect geometry(Context c,Display d,boolean right){
        int width=d.getMode().getPhysicalWidth();
        int height=d.getMode().getPhysicalHeight();
        if(MODE_SPLIT.equals(mode(c))){
            int ratio=number(c,SPLIT_PERCENT,50,30,70);
            return asRect(right?
                T1ClusterLayoutMath.right(width,height,ratio):
                T1ClusterLayoutMath.left(width,height,ratio));
        }
        return asRect(T1ClusterLayoutMath.window(width,height,
            number(c,WIDTH,70,30,100),
            number(c,HEIGHT,75,35,100),
            number(c,X,0,0,100),
            number(c,Y,0,0,100)));
    }
    private static void setFreeform(ActivityOptions options)throws Exception{
        // OEM APK invokes this method reflectively with value 5.
        Method freeform=ActivityOptions.class.getMethod(
            "setLaunchWindowingMode",int.class);
        freeform.invoke(options,5);
    }
    static String start(Context c,Display display,Intent target,
                        Rect bounds,String label){
        if(display==null||display.getDisplayId()==Display.DEFAULT_DISPLAY)
            return "Pantalla de clúster inválida; no se abrió la app";
        if(target==null||target.getPackage()==null||
            !T1ClusterWazeRouter.isSelectablePackage(c,target.getPackage()))
            return "Paquete no iniciable en el clúster";
        try{
            ActivityOptions options=ActivityOptions.makeBasic();
            options.setLaunchDisplayId(display.getDisplayId());
            setFreeform(options);
            options.setLaunchBounds(bounds);
            Intent actual=new Intent(target);
            actual.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                Intent.FLAG_ACTIVITY_SINGLE_TOP|
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
            c.startActivity(actual,options.toBundle());
            return "APP SOLICITADA EN VENTANA: "+label+" · "+
                bounds.toShortString()+
                " · display "+display.getDisplayId()+
                ". Verificar tamaño y posición físicamente.";
        }catch(Exception ex){
            return "VENTANA NO COMPATIBLE: "+label+" · "+
                ex.getClass().getSimpleName()+
                ". No se abrió pantalla completa como alternativa.";
        }
    }
    /** Always manual, require parked check in the caller. */
    static String test(Context c){
        Display display=T1ClusterWazeRouter.selected(c);
        if(display==null)return "Elegí primero el display real del tablero";
        // Panel A/Panel B are logical user-chosen applications, never
        // hardcoded Waze/Maps. Legacy inverted layouts remain supported.
        String first=MODE_SPLIT.equals(mode(c))?firstPackage(c):
            singlePackage(c);
        Intent app=c.getPackageManager().getLaunchIntentForPackage(first);
        if(app==null)return "App principal no disponible: "+first;
        app.setPackage(first);
        if(MODE_FULL.equals(mode(c)))
            return T1ClusterWazeRouter.launchApp(c,first,
                singleLabel(c),true,false);
        if(!T1ClusterAdbResize.enabled(c))
            return "Para mover ventanas reales activá PUENTE ADB LOCAL "+
                "y autorizá su clave antes de PROBAR DISEÑO.";
        String second=secondPackage(c);
        Intent other=null;
        if(MODE_SPLIT.equals(mode(c))){
            if(second.equals(first))return "Elegí dos apps diferentes para dividir";
            other=c.getPackageManager().getLaunchIntentForPackage(second);
            if(other==null)return "Segunda app no disponible: "+second;
            other.setPackage(second);
        }
        boolean reversed=MODE_SPLIT.equals(mode(c))&&inverted(c);
        String left=start(c,display,app,geometry(c,display,reversed),
            MODE_SPLIT.equals(mode(c))?firstLabel(c):
                singleLabel(c));
        if(!left.startsWith("APP SOLICITADA"))return left;
        if(MODE_SPLIT.equals(mode(c))){
            String right=start(c,display,other,geometry(c,display,!reversed),
                secondLabel(c));
            if(!right.startsWith("APP SOLICITADA"))return left+"\n"+right;
            return left+"\n"+right+
                "\nSolicitudes enviadas, NO confirmadas. "+
                "DriveLab comprobará luego los límites reales mediante ADB.";
        }
        return left+"\nVentana solicitada, pendiente de "+
            "verificación de tamaño real por ADB.";
    }
    /** A previously verified user-defined layout on next ignition. */
    static String boot(Context c){
        if(!active(c))return "Layout libre aún no confirmado";
        if(!T1ClusterWazeRouter.autoBoot(c))
            return "Autoarranque de clúster desactivado";
        if(!Settings.canDrawOverlays(c)&&!ReceiverActivity.isVisible())
            return "Android bloquea ventanas libres desde segundo plano";
        String launched=test(c);
        if(launched.startsWith("APP SOLICITADA")&&
           T1ClusterAdbResize.enabled(c)){
            // Reuse ONLY the user's approved layout. ADB measurement is
            // performed asynchronously and rechecks P/0 before moving
            // any task; after a cold start ADB TCP may need re-enabling.
            T1ClusterAdbResize.apply(c,
                MODE_SPLIT.equals(mode(c)),1800L,null);
            return launched+" · ADB comprobará límites si está autorizado y P/0.";
        }
        return launched;
    }
    static boolean enableAfterTest(Context c){
        if(MODE_FULL.equals(mode(c))){
            prefs(c).edit().putBoolean(ACTIVE,false).apply();return true;
        }
        if(T1ClusterWazeRouter.selected(c)==null||
           !T1ClusterWazeRouter.enabled(c)||
           !T1ClusterAdbResize.enabled(c))return false;
        long time=prefs(c).getLong(TEST_AT,0L);
        long verified=prefs(c).getLong(
            T1ClusterAdbResize.VERIFIED_AT,0L);
        if(verified==0||verified<time)return false;
        if(time<=0||System.currentTimeMillis()-time>600000L||
            !mode(c).equals(prefs(c).getString(TEST_MODE,"")))return false;
        prefs(c).edit().putBoolean(ACTIVE,true).apply();
        return true;
    }
    static String routeWazeIfActive(Context c,Intent waze){
        if(!active(c))return null;
        Display display=T1ClusterWazeRouter.selected(c);
        if(display==null)return "Layout clúster no disponible";
        if(!WAZE.equals(waze.getPackage()))
            return "Solo Waze puede recibir rutas";
        // Never steal a panel configured for Spotify/YouTube. If Waze
        // occupies either panel, retain precisely its approved geometry.
        boolean split=MODE_SPLIT.equals(mode(c));
        boolean wazeInA=WAZE.equals(firstPackage(c));
        boolean wazeInB=WAZE.equals(secondPackage(c));
        if(split&&!wazeInA&&!wazeInB)
            return "Waze no está elegido en ninguno de los paneles";
        if(!split&&!WAZE.equals(singlePackage(c)))
            return "El panel único no está configurado para Waze";
        boolean right=split&&(wazeInA?inverted(c):!inverted(c));
        return start(c,display,waze,geometry(c,display,right),"Waze");
    }
}