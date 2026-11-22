package ar.com.rango5gps.drivelabauto.boot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/**
 * NAV virtual-display touch adapter. Requires deliberate Android
 * accessibility consent AND separate in-app opt-in, every session.
 *
 * Never listens to wheel/Binder/vehicle keys, HOME, global BACK, OEM input or
 * default/physical display 0/1. No arbitrary gesture entry points: one
 * visible top APPLICATION window whose package equals our chosen map/Spotify
 * is required on NAV's EXACT currently owned public virtual display.
 */
public final class NavMosaicTouchService extends AccessibilityService {
    private static volatile NavMosaicTouchService instance;
    private static final String SPOTIFY="com.spotify.music";
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean ready;
    private boolean busy;
    static boolean connected(){return instance!=null&&instance.ready;}
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        ready=true;instance=this;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        // Intentional NO-OP. Does not capture user or OEM events.
    }
    @Override public void onInterrupt(){busy=false;}
    @Override public boolean onKeyEvent(android.view.KeyEvent event){
        return false;
    }
    @Override public void onDestroy(){
        ready=false;busy=false;
        if(instance==this)instance=null;
        super.onDestroy();
    }

    /**
     * Normalized coordinates are derived from the owner's touch inside
     * TextureView, not raw screen coordinates or an external requester.
     * Gestures are never sent to display 0/1, another app's display, or
     * arbitrary accessible system windows.
     */
    static boolean forward(Context context,long token,int panel,
            float fromX,float fromY,float toX,float toY,boolean swipe){
        NavMosaicTouchService current=instance;
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(context);
        if(current==null||!current.ready||current.busy||
           live==null||live.token!=token||panel<0||panel>1||
           !valid(fromX)||!valid(fromY)||!valid(toX)||!valid(toY))
            return false;
        int display=panel==0?live.displayId:live.mediaDisplayId;
        String pkg=panel==0?live.mapPackage:SPOTIFY;
        if(display<2||display>99||!current.topApplicationIs(display,pkg))
            return false;
        int width=panel==0?1280:480;
        int height=panel==0?720:800;
        float x1=bound(fromX*width,width),y1=bound(fromY*height,height);
        float x2=bound(toX*width,width),y2=bound(toY*height,height);
        Path path=new Path();
        path.moveTo(x1,y1);
        if(swipe)path.lineTo(x2,y2);
        GestureDescription gesture=new GestureDescription.Builder()
            .setDisplayId(display)
            .addStroke(new GestureDescription.StrokeDescription(
                path,0,swipe?340L:85L)).build();
        current.busy=true;
        try{
            boolean submitted=current.dispatchGesture(
                gesture,new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription g){
                        current.busy=false;
                    }
                    @Override public void onCancelled(GestureDescription g){
                        current.busy=false;
                    }
                },current.main);
            if(!submitted)current.busy=false;
            return submitted; // only request accepted; click not confirmed
        }catch(RuntimeException denied){
            current.busy=false;
            return false;
        }
    }
    private static boolean valid(float x){
        return !Float.isNaN(x)&&!Float.isInfinite(x)&&x>=0f&&x<=1f;
    }
    private static float bound(float pixel,int dimension){
        return Math.max(1f,Math.min(dimension-2f,pixel));
    }
    private boolean topApplicationIs(int display,String exactPackage){
        try{
            SparseArray<List<AccessibilityWindowInfo>> all=
                getWindowsOnAllDisplays();
            List<AccessibilityWindowInfo> windows=
                all==null?null:all.get(display);
            if(windows==null)return false;
            for(AccessibilityWindowInfo window:windows){
                if(window==null||window.getDisplayId()!=display||
                   window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)
                    continue;
                AccessibilityNodeInfo root=window.getRoot();
                if(root==null)return false; // visible app not inspectable
                CharSequence name=root.getPackageName();
                return name!=null&&exactPackage.contentEquals(name)&&
                    root.isVisibleToUser();
            }
        }catch(RuntimeException ignored){}
        return false;
    }
}
