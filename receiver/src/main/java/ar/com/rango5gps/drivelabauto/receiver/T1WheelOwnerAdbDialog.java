package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

/** Explicit owner confirmation before a previously approved local ADB
 * session changes Android secure Accessibility settings.
 * This UI never grants Accessibility as an ordinary app permission. */
final class T1WheelOwnerAdbDialog {
    private T1WheelOwnerAdbDialog(){}
    static void show(Activity owner){
        final String state=T1WheelAccessibilityRecovery.permissionState(owner);
        new AlertDialog.Builder(owner)
            .setTitle("Reautorizar ruedita · ADB LOCAL T1")
            .setMessage(state+
                "\n\nEste procedimiento usa DriveLab directamente en "+
                "la pantalla del vehículo: ADB en 127.0.0.1:5556 o :5555, "+
                "sin celular, internet ni Termux."+
                "\n\nSólo si autorizaste antes el servicio DriveLab y "+
                "la clave ADB de esta instalación, intentará añadir "+
                "DriveLab a la lista de accesibilidad y activar esa "+
                "función de Android. Conserva los otros servicios."+
                "\n\nSi no está escuchando ADB o su clave no está "+
                "autorizada, no hará cambios. Probá con el vehículo "+
                "detenido y confirmá el resultado abajo.")
            .setNegativeButton("CANCELAR",null)
            .setNeutralButton("AJUSTES ANDROID",(dialog,which)->{
                try{owner.startActivity(
                    new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }catch(Exception ex){
                    Toast.makeText(owner,
                        "Esta ROM no abre Ajustes. Usar ADB autorizado por USB "+
                        "para inicializar la conexión si está caída.",
                        Toast.LENGTH_LONG).show();
                }
            })
            .setPositiveButton("AUTORIZO RESTAURAR POR ADB",
                (dialog,which)->{
                    String message=T1WheelAccessibilityRecovery
                        .reauthorizeWithOwnerApproval(owner);
                    Toast.makeText(owner,message,Toast.LENGTH_LONG).show();
                })
            .show();
    }
}
