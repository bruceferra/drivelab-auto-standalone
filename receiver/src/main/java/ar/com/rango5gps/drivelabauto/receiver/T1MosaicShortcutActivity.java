package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Dedicated explicit target for a user-pinned launcher shortcut.
 * Not a second MAIN/LAUNCHER entry: T1 Launcher can hide Receiver when an
 * APK has two launcher icons. Only runs after a user taps this shortcut.
 */
public final class T1MosaicShortcutActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        TextView status=new TextView(this);
        status.setText("DriveLab · recuperando mosaico guardado…");
        status.setTextSize(19f);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xFF101A23);
        status.setGravity(Gravity.CENTER);
        setContentView(status);
        T1CenterMosaicRecovery.restore(this,(requested,result)->{
            if(isFinishing()||isDestroyed())return;
            if(requested){
                // This isolated shortcut task is not one of the Freeform
                // tiles. Remove only this transient task from recents.
                finishAndRemoveTask();
            }else{
                new AlertDialog.Builder(this)
                    .setTitle("MOSAICO NO DISPONIBLE")
                    .setMessage(result)
                    .setNegativeButton("CERRAR",(d,w)->finish())
                    .setPositiveButton("IR A MOSAICO",(d,w)->{
                        startActivity(new Intent(this,
                            T1CenterMosaicActivity.class));
                        finish();
                    }).show();
            }
        });
    }
}
