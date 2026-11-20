package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Real Activity running in the private VirtualDisplay. No map or OEM control. */
public final class BridgeVirtualSceneActivity extends Activity {
    static final String EXTRA_EXPECTED_DISPLAY="bridge_probe_expected_display";
    static final String EXTRA_SESSION="bridge_probe_session";
    static final String EXTRA_EXPECT_PUBLIC="bridge_probe_expected_public";
    interface Listener {
        void onAttached(long token,int actualDisplay,boolean virtualPrivate);
    }
    private static volatile Listener listener;
    private static volatile WeakReference<BridgeVirtualSceneActivity> current=
        new WeakReference<>(null);
    static void setListener(Listener next){listener=next;}
    static void clearListener(Listener old){
        if(listener==old)listener=null;
    }
    static void finishSession(long token){
        BridgeVirtualSceneActivity a=current.get();
        if(a!=null&&a.sessionToken==token){
            a.runOnUiThread(()->{
                if(!a.isFinishing())a.finishAndRemoveTask();
            });
        }
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private long sessionToken;
    private int actualId;
    private TextView marker;
    private int ticks;
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(isFinishing()||marker==null)return;
            ticks++;
            marker.setText("BRIDGE · ACTIVITY REAL\n"+
                "VIRTUAL DISPLAY "+actualId+"\n"+
                "CONTADOR EN VIVO: "+ticks+"\n"+
                "NO ES WAZE · NO ES UNA IMAGEN");
            marker.setBackgroundColor(ticks%2==0?0xFF136778:0xFF267B54);
            main.postDelayed(this,700L);
        }
    };
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        sessionToken=getIntent().getLongExtra(EXTRA_SESSION,-1L);
        int expected=getIntent().getIntExtra(EXTRA_EXPECTED_DISPLAY,
            Display.INVALID_DISPLAY);
        boolean expectedPublic=getIntent().getBooleanExtra(EXTRA_EXPECT_PUBLIC,false);
        Display actual=getDisplay();
        actualId=actual==null?Display.INVALID_DISPLAY:actual.getDisplayId();
        boolean owned=actual!=null&&actualId==expected&&expected>1&&
            (((actual.getFlags()&Display.FLAG_PRIVATE)==0)==expectedPublic);
        current=new WeakReference<>(this);
        if(!owned||sessionToken<=0){
            Listener l=listener;
            if(l!=null)l.onAttached(sessionToken,actualId,false);
            finishAndRemoveTask();
            return;
        }
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF104356);
        marker=new TextView(this);
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(27f);
        marker.setGravity(Gravity.CENTER);
        marker.setPadding(24,20,24,20);
        root.addView(marker,new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        main.post(tick);
        getWindow().getDecorView().post(()->{
            Listener l=listener;
            if(l!=null&&!isFinishing())
                l.onAttached(sessionToken,actualId,true);
        });
    }
    @Override protected void onDestroy(){
        main.removeCallbacks(tick);
        if(current.get()==this)current=new WeakReference<>(null);
        super.onDestroy();
    }
}
