package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;

/** User-opt-in pinned shortcut. Does not add another MAIN/LAUNCHER icon
 * to the manifest (some Jetour/T1 launchers hide the primary app if so).
 */
final class T1MosaicLauncherShortcut {
    private T1MosaicLauncherShortcut(){}
    static String request(Activity owner){
        ShortcutManager shortcuts=owner.getSystemService(
            ShortcutManager.class);
        if(shortcuts==null||!shortcuts.isRequestPinShortcutSupported())
            return "Este launcher no admite accesos directos fijados. "+
                "Abrí DriveLab desde su icono y tocá ▦ en "+
                "la barra superior para volver al mosaico.";
        try{
            Intent target=new Intent(owner,
                T1MosaicShortcutActivity.class);
            target.setAction(Intent.ACTION_VIEW);
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            ShortcutInfo shortcut=new ShortcutInfo.Builder(owner,
                    "drivelab_t1_restore_mosaic")
                .setShortLabel("Mosaico")
                .setLongLabel("DriveLab · Volver al mosaico")
                .setIcon(Icon.createWithResource(owner,
                    android.R.drawable.ic_menu_view))
                .setIntent(target)
                .build();
            boolean requested=shortcuts.requestPinShortcut(
                shortcut,null);
            return requested?
                "Pedido enviado al launcher. Si muestra un "+
                "diálogo, aceptá para agregar «Mosaico». "+
                "La presencia final del icono depende del T1 Launcher.":
                "El launcher rechazó agregar el acceso. "+
                "Usá el icono DriveLab y el botón ▦ fijo.";
        }catch(Exception ex){
            return "No se agregó el acceso: "+
                ex.getClass().getSimpleName()+
                ". Usá el botón ▦ de DriveLab.";
        }
    }
}
