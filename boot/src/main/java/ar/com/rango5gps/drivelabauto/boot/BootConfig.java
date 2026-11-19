package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ComponentName;

final class BootConfig {
    static final String PREFS="drivelab_boot_mosaic_config_v1";
    static final String RECEIVER="ar.com.rango5gps.drivelabauto.receiver";
    static final String ACTION="ar.com.rango5gps.drivelabauto.receiver.action.APPLY_BOOT_MOSAIC";
    static final String BRIDGE=
        "ar.com.rango5gps.drivelabauto.receiver.T1BootMosaicBridgeReceiver";
    static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean enabled(Context context){
        return prefs(context).getBoolean("enabled",false);
    }
    /** One explicit, signature-protected broadcast. No generic ADB/shell,
     * no HOME or vehicle processes. Display 1 is delegated to Receiver's
     * existing guarded Waze/Maps router, never a raw shell.
     */
    static void assertReceiver(Context context){
        if(context.getPackageManager().checkSignatures(
            context.getPackageName(),RECEIVER)!=
                android.content.pm.PackageManager.SIGNATURE_MATCH)
            throw new SecurityException(
                "Instalá Receiver 0.3.94 o posterior para configurar ambas pantallas");
        try{
            long receiverCode=context.getPackageManager()
                .getPackageInfo(RECEIVER,0).getLongVersionCode();
            if(receiverCode<195L)
                throw new SecurityException(
                    "Actualizá primero Receiver 0.3.94 (build 195)");
        }catch(android.content.pm.PackageManager.NameNotFoundException ex){
            throw new SecurityException("DriveLab Receiver no está instalado");
        }
    }
    /** User explicitly elected to delegate boot UI opening to Boot app.
     * This only disables ReceiverService.launch_app_on_boot, not its FGS
     * or cluster. T1 Launcher HOME is never changed.
     */
    static void takeOver(Context context){
        takeOver(context,true);
    }
    static void takeOver(Context context,boolean enabled){
        assertReceiver(context);
        Intent request=new Intent(
            "ar.com.rango5gps.drivelabauto.receiver.action.BOOT_MOSAIC_TAKEOVER");
        request.setComponent(new ComponentName(RECEIVER,BRIDGE));
        request.putExtra("boot_enabled",enabled);
        request.putExtra("cluster_enabled",
            enabled&&prefs(context).getBoolean("cluster_enabled",false));
        context.sendBroadcast(request);
    }
    static void send(Context context){
        send(context,false);
    }
    static void send(Context context,boolean fromBoot){
        assertReceiver(context);
        SharedPreferences p=prefs(context);
        int mode=p.getInt("mode",3),percent=p.getInt("percent",50);
        if(mode<0||mode>4||percent<1||percent>99)
            throw new IllegalArgumentException("Mosaico inválido");
        int count=mode==4?1:mode==3?2:3;
        Intent request=new Intent(ACTION);
        request.setComponent(new ComponentName(RECEIVER,BRIDGE));
        request.putExtra("mode",mode);
        request.putExtra("percent",percent);
        for(int i=0;i<count;i++){
            String pkg=p.getString("pkg"+i,"");
            if(pkg==null||pkg.isEmpty())
                throw new IllegalStateException("Elegí la app del panel "+(i+1));
            request.putExtra("pkg"+i,pkg);
        }
        // Display 1 uses existing Receiver layout/routing, not a new
        // shell executor. Disabled by default and never controls OEM dials.
        boolean cluster=p.getBoolean("cluster_enabled",false);
        request.putExtra("cluster_enabled",cluster);
        request.putExtra("from_boot",fromBoot);
        request.putExtra("cluster_confirmed",
            p.getBoolean("cluster_confirmed",false));
        if(cluster){
            int panels=p.getInt("cluster_count",2);
            int ratio=p.getInt("cluster_percent",50);
            if((panels!=1&&panels!=2)||ratio<30||ratio>70)
                throw new IllegalArgumentException("Configuración Display 1 inválida");
            request.putExtra("cluster_count",panels);
            request.putExtra("cluster_percent",ratio);
            for(int i=0;i<panels;i++){
                String pkg=p.getString("cluster_pkg"+i,
                    i==0?"com.waze":"com.google.android.apps.maps");
                if(!"com.waze".equals(pkg)&&
                    !"com.google.android.apps.maps".equals(pkg))
                    throw new SecurityException(
                        "Display 1 sólo admite Waze y Google Maps");
                if(i==1&&pkg.equals(p.getString("cluster_pkg0","com.waze")))
                    throw new IllegalArgumentException(
                        "Elegí dos navegadores distintos para Display 1");
                request.putExtra("cluster_pkg"+i,pkg);
            }
        }
        int caption=p.getInt("caption_safe_dp",44);
        if(caption<0||caption>88)
            throw new IllegalArgumentException("Margen superior inválido");
        request.putExtra("caption_safe_dp",caption);
        context.sendBroadcast(request);
        p.edit().putString("last_status",
            "Solicitud enviada al Receiver · esperando ADB y Freeform")
            .putLong("last_request",System.currentTimeMillis()).apply();
    }
}
