package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Display;

/**
 * Only the signed DriveLab Boot may delegate its OPTIONAL Display 1
 * navigation design here. Reuse the Receiver's existing Waze/Maps router:
 * no alternate cluster launcher, no raw shell, no OEM overlay manipulation.
 * Changing the display layout requires a fresh 0 km/h OEM readback.
 */
final class T1BootClusterLayout {
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";
    private T1BootClusterLayout(){}

    private static boolean stopped(Context context){
        MirrorAutomationOem oem=null;
        try{
            oem=new MirrorAutomationOem(context);
            double kmh=oem.speedKmh();
            return Double.isFinite(kmh)&&kmh>=0&&kmh<=0.5;
        }catch(Exception ignored){return false;}
        finally{if(oem!=null)oem.close();}
    }
    private static String name(String pkg){
        return WAZE.equals(pkg)?"Waze":"Google Maps";
    }
    static String apply(Context c,int count,int percent,String[] packages,
            boolean fromBoot,boolean visuallyConfirmed){
        if(count!=1&&count!=2)return "CLÚSTER NO ABIERTO: 1–2 paneles solamente";
        if(percent<30||percent>70)return "CLÚSTER NO ABIERTO: porcentaje inválido";
        if(packages==null||packages.length<count)
            return "CLÚSTER NO ABIERTO: faltan apps";
        for(int i=0;i<count;i++){
            String pkg=packages[i];
            if((!WAZE.equals(pkg)&&!MAPS.equals(pkg))||
               !T1ClusterWazeRouter.isSelectablePackage(c,pkg))
                return "CLÚSTER NO ABIERTO: navegador no autorizado o no instalado";
        }
        if(count==2&&packages[0].equals(packages[1]))
            return "CLÚSTER NO ABIERTO: elegí dos navegadores distintos";
        if(fromBoot&&!visuallyConfirmed)
            return "CLÚSTER NO ABIERTO: primero probá el diseño manualmente "+
                "y confirmá en DriveLab Boot que se ve correctamente";
        Display cluster=T1ClusterWazeRouter.selected(c);
        if(cluster==null||cluster.getDisplayId()!=1)
            return "CLÚSTER NO ABIERTO: seleccioná primero el Display 1 "+
                "en Receiver → CLÚSTER, sin alterar la pantalla central";
        if(!stopped(c))
            return "CLÚSTER NO ABIERTO: velocidad 0 km/h no confirmada "+
                "por VehicleService; no se modifican ventanas del tablero";
        if(fromBoot&&!Settings.canDrawOverlays(c)&&
           !ReceiverActivity.isVisible())
            return "CLÚSTER NO ABIERTO: Android exige permiso "+
                "«Mostrar sobre otras apps» para iniciar desde segundo plano";

        // Single controlling owner: do not let Receiver's old independent
        // cluster autostart race with Boot's chosen layout at ignition.
        SharedPreferences prefs=T1ClusterWazeRouter.prefs(c);
        boolean saved=prefs.edit()
            .putBoolean(T1ClusterWazeRouter.ENABLED,true)
            .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false)
            .commit();
        if(!saved)return "CLÚSTER NO ABIERTO: no se guardó control de autoinicio";

        String a=packages[0];
        String b=count==2?packages[1]:(WAZE.equals(a)?MAPS:WAZE);
        try{
            T1ClusterLayoutPolicy.configurePanels(c,true,a,name(a),
                count==2,b,name(b),percent);
            String sent=count==2?
                T1ClusterLayoutPolicy.reopenSavedPanelsStopped(c):
                T1ClusterWazeRouter.launchApp(c,a,name(a),!fromBoot,fromBoot);
            boolean requested=sent.startsWith("Waze SOLICITADO")||
                sent.startsWith("APP SOLICITADA:")||
                sent.contains("Reapertura manual ");
            String result=(requested?
                "DISPLAY 1 · DISEÑO SOLICITADO: ":
                "DISPLAY 1 · NO CONFIRMADO: ")+sent;
            if(requested)
                result+="\nNo se cerraron apps OEM, ni se alteraron capas, "+
                    "tareas de T1 Launcher, Display 0 o indicadores del vehículo. "+
                    "Debe verificarse visualmente la superposición y el foco.";
            int gradient=T1ClusterEdgeFade.width(c);
            if(gradient>0)
                result+="\nAviso: el desvanecido propio de DriveLab "+
                    "sigue configurado en "+gradient+"px; puede ser "+
                    "otra capa sobre los mapas. No se cambió.";
            T1ClusterWazeRouter.status(c,result);
            return result;
        }catch(Exception e){
            String error="DISPLAY 1 · NO CONFIRMADO: "+
                e.getClass().getSimpleName()+": "+e.getMessage();
            T1ClusterWazeRouter.status(c,error);
            return error;
        }
    }
}
