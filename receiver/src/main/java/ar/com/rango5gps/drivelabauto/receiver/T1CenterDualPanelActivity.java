package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Self-removing test panes. Only the explicitly launched DriveLab-owned
 * tasks may have their bounds changed. No OEM or third-party task is used.
 */
abstract class T1CenterDualPanelActivity extends Activity {
    static final String PREFS="drivelab_center_mosaic_v1";
    static final String L="dual_left",R="dual_right";
    abstract String side();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable periodic=new Runnable(){
        @Override public void run(){
            capture();
            main.postDelayed(this,1300L);
        }
    };
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        TextView label=new TextView(this);
        label.setText("DRIVELAB · PANEL "+(side().equals(L)?"IZQUIERDO":"DERECHO")+
            "\nPrueba Freeform: dos ventanas propias");
        label.setGravity(Gravity.CENTER);
        label.setTextSize(23f);
        label.setTextColor(Color.WHITE);
        label.setBackgroundColor(side().equals(L)?
            Color.rgb(16,73,92):Color.rgb(45,59,105));
        setContentView(label);
        Display d=getDisplay();
        if(d==null||d.getDisplayId()!=Display.DEFAULT_DISPLAY){
            record("ERROR: no se abrió en Display 0");
            finishAndRemoveTask();
            return;
        }
        record("INICIADA");
        main.postDelayed(periodic,500L);
        main.postDelayed(()->{
            capture();
            finishAndRemoveTask();
        },30000L);
    }
    private void capture(){
        if(isFinishing())return;
        View view=getWindow().getDecorView();
        Rect visible=new Rect();
        view.getWindowVisibleDisplayFrame(visible);
        Display d=getDisplay();
        record("Display="+(d==null?-1:d.getDisplayId())+
            " · isInMultiWindowMode="+isInMultiWindowMode()+
            " · decor="+view.getWidth()+"x"+view.getHeight()+
            " · frame="+visible.flattenToString());
    }
    private void record(String observation){
        getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putInt(side()+"_task",getTaskId())
            .putLong(side()+"_at",System.currentTimeMillis())
            .putString(side()+"_ui",observation)
            .apply();
    }
    @Override protected void onDestroy(){
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
