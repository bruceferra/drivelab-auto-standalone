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

/** Tiny isolated, temporary task. Invoked solely by explicit ADB command.
 * It neither launches any third-party app nor changes the OEM launcher.
 * Saves physical task/window observations and removes only its own task.
 */
public final class T1CenterFreeformTestActivity extends Activity {
    static final String PREFS="drivelab_center_mosaic_v1";
    static final String KEY="own_freeform_test_result";
    static final String TASK="own_freeform_test_task";
    static final String TIME="own_freeform_test_at";
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        TextView label=new TextView(this);
        label.setText("DRIVELAB · PRUEBA TEMPORAL FREEFORM\\n"+
            "Solo Display 0. Se cierra automáticamente.");
        label.setTextSize(20f);
        label.setGravity(Gravity.CENTER);
        label.setBackgroundColor(Color.rgb(14,32,47));
        label.setTextColor(Color.WHITE);
        setContentView(label);
        // Only this disposable task is involved. Bail out on any other display.
        Display display=getDisplay();
        if(display==null||display.getDisplayId()!=Display.DEFAULT_DISPLAY){
            record("ERROR: actividad no inició en Display 0",getTaskId());
            finishAndRemoveTask();
            return;
        }
        record("INICIADA · esperando geometría",getTaskId());
        main.postDelayed(this::capture,850L);
        main.postDelayed(this::capture,1600L);
        main.postDelayed(()->{
            capture();
            finishAndRemoveTask();
        },3800L);
    }
    private void capture(){
        if(isFinishing())return;
        View decor=getWindow().getDecorView();
        Rect frame=new Rect();
        decor.getWindowVisibleDisplayFrame(frame);
        Display display=getDisplay();
        int displayId=display==null?-1:display.getDisplayId();
        boolean multi=isInMultiWindowMode();
        record("Display="+displayId+
            " · isInMultiWindowMode="+multi+
            " · decor="+decor.getWidth()+"x"+decor.getHeight()+
            " · frame="+frame.flattenToString(),getTaskId());
    }
    private void record(String value,int taskId){
        getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(KEY,value)
            .putInt(TASK,taskId)
            .putLong(TIME,System.currentTimeMillis())
            .apply();
    }
    @Override protected void onDestroy(){
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
