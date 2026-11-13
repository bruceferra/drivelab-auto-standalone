package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * Immersive Android 11 activity for the T1's center display. The voice
 * assistant ("Hola Jetour" with three spheres) remains an OEM-owned overlay.
 *
 * Keep an EMPTY, PINNED, non-scrolling strip above each Receiver screen:
 * when the user scrolls, labels and buttons must never slide behind the
 * always-visible OEM voice assistant. We do NOT hide or intercept the overlay.
 * The strip can be adjusted (including zero) without root or system settings.
 */
public abstract class T1ImmersiveActivity extends Activity {
    private static final java.util.concurrent.atomic.AtomicBoolean AUTO_INITIALIZED=
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final String CLEAR_PREFS="t1_voice_overlay_clearance";
    private static final String CLEAR_HEIGHT="clear_height_dp";
    private static final int DEFAULT_CLEAR_HEIGHT_DP=0;
    private static final int[] HEIGHTS_DP={0,80,112,144,176};
    private View clearStrip;
    private T1GlobalToolbar globalToolbar;
    private boolean activeScreen;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        // MainthorCar no longer places HolaJetour over this T1 screen:
        // always use full usable height and immersive system bars.
        getSharedPreferences(CLEAR_PREFS,Context.MODE_PRIVATE)
            .edit().putInt(CLEAR_HEIGHT,0).apply();
        T1ImmersiveMode.setEnabled(this,true);
        // Owner request: both cabin INTERIOR and reverse-mirror AUTO are the
        // defaults ONCE per fresh Receiver app process. A manual OFF tap is
        // respected within this process and while navigating Receiver screens;
        // reopening after process exit restores the requested AUTO default.
        // Neither initializer bypasses an OEM safety pause/unknown mirror pose.
        if(AUTO_INITIALIZED.compareAndSet(false,true)){
            RecirculationGuardService.activateOnAppOpen(this);
            MirrorAutomationService.activateOnAppOpen(this);
        }
    }
    @Override protected void onResume(){
        super.onResume();
        activeScreen=true;
        T1ImmersiveMode.apply(this);
        RecirculationGuardService.ensureRunning(this);
        MirrorAutomationService.ensureRunning(this);
        if(globalToolbar!=null)globalToolbar.onResume();
    }
    @Override protected void onPause(){
        activeScreen=false;
        if(globalToolbar!=null)globalToolbar.onPause();
        super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)T1ImmersiveMode.apply(this);
    }
    /**
     * This app uses setContentView(View) for its screens; wrap the content
     * with a permanent top-safe strip OUTSIDE the ScrollView.
     */
    @Override public void setContentView(View content){
        if(globalToolbar!=null){
            globalToolbar.onPause();
            globalToolbar=null;
        }
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(overlayClearBackgroundColor());

        View blank=new View(this);
        blank.setContentDescription(null);
        blank.setClickable(false);
        blank.setFocusable(false);
        blank.setBackgroundColor(overlayClearBackgroundColor());
        clearStrip=blank;
        shell.addView(blank,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,px(clearanceDp())));

        globalToolbar=new T1GlobalToolbar(this);
        shell.addView(globalToolbar.view(),new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(content,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        super.setContentView(shell);
        if(activeScreen)globalToolbar.onResume();
    }
    protected int overlayClearBackgroundColor(){return Color.WHITE;}
    protected final int clearanceDp(){
        SharedPreferences p=getSharedPreferences(CLEAR_PREFS,Context.MODE_PRIVATE);
        int value=p.getInt(CLEAR_HEIGHT,DEFAULT_CLEAR_HEIGHT_DP);
        for(int allowed:HEIGHTS_DP)if(allowed==value)return allowed;
        return DEFAULT_CLEAR_HEIGHT_DP;
    }
    protected final int cycleClearanceDp(){
        int current=clearanceDp(),next=DEFAULT_CLEAR_HEIGHT_DP;
        for(int i=0;i<HEIGHTS_DP.length;i++){
            if(HEIGHTS_DP[i]==current){
                next=HEIGHTS_DP[(i+1)%HEIGHTS_DP.length];break;
            }
        }
        setClearanceDp(next);return next;
    }
    protected final void setClearanceDp(int value){
        boolean valid=false;
        for(int v:HEIGHTS_DP)if(v==value)valid=true;
        if(!valid)throw new IllegalArgumentException("Altura libre no soportada");
        getSharedPreferences(CLEAR_PREFS,Context.MODE_PRIVATE)
            .edit().putInt(CLEAR_HEIGHT,value).apply();
        if(clearStrip!=null){
            ViewGroup.LayoutParams params=clearStrip.getLayoutParams();
            params.height=px(value);
            clearStrip.setLayoutParams(params);
        }
    }
    private int px(int dp){
        return Math.round(dp*getResources().getDisplayMetrics().density);
    }
}
