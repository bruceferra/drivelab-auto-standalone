package ar.com.rango5gps.drivelabauto.boot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.graphics.Rect;
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
    private boolean routeScheduled;
    private final Runnable routeCheck=new Runnable(){
        @Override public void run(){
            routeScheduled=false;
            if(!ready)return;
            checkMapsRoute();
            if(NavMosaicMapsRoute.snapshot(NavMosaicTouchService.this)!=null)
                scheduleRouteCheck(550L);
        }
    };
    static void navigationIntentArrived(Context context){
        NavMosaicTouchService service=instance;
        if(service!=null&&service.ready)service.scheduleRouteCheck();
    }
    private void scheduleRouteCheck(){
        scheduleRouteCheck(180L);
    }
    private void scheduleRouteCheck(long delay){
        if(routeScheduled)return;
        routeScheduled=true;
        main.postDelayed(routeCheck,delay);
    }
    static boolean connected(){return instance!=null&&instance.ready;}
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        ready=true;instance=this;
        if(NavMosaicMapsRoute.snapshot(this)!=null)scheduleRouteCheck();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        // No general event listener. Only re-check a short-lived explicit
        // encrypted Sender route delivered to this NAV-owned Maps display.
        if(event!=null&&NavMosaicMapsRoute.snapshot(this)!=null){
            int type=event.getEventType();
            if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||
               type==AccessibilityEvent.TYPE_WINDOWS_CHANGED||
               type==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                scheduleRouteCheck();
        }
    }
    @Override public void onInterrupt(){busy=false;}
    @Override public boolean onKeyEvent(android.view.KeyEvent event){
        return false;
    }
    @Override public void onDestroy(){
        ready=false;busy=false;
        main.removeCallbacks(routeCheck);
        routeScheduled=false;
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
        // Display geometry belongs to the currently verified NAV VD.
        // LAB47's fixed 1280/480 pixel mapping was wrong after zooming
        // panel surfaces; Android got taps shifted away from real icons.
        DisplayManager dm=(DisplayManager)
            context.getSystemService(Context.DISPLAY_SERVICE);
        if(dm==null)return false;
        Display owned=dm.getDisplay(display);
        if(owned==null||owned.getDisplayId()!=display)return false;
        String expectedName=panel==0?"DriveLabNAVMapa":"DriveLabNAVSpotify";
        if(!expectedName.equals(owned.getName()))return false;
        Point dimensions=new Point();
        owned.getRealSize(dimensions);
        final NavMosaicGeometry.Size actual;
        try{
            actual=NavMosaicGeometry.fromSurface(
                dimensions.x,dimensions.y);
        }catch(SecurityException invalid){
            return false;
        }
        int width=actual.w,height=actual.h;
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

    /** A NAV map-route confirmation is NOT a touch gesture and never
     * targets physical 0/1 or any task/display from another launcher.
     * The verified display/session is armed by an authenticated Sender
     * delivery to the existing Maps TASK, not by the window event itself.
     */
    private AccessibilityNodeInfo topMapsRouteWindow(int display){
        if(display<2||display>99)return null;
        try{
            SparseArray<List<AccessibilityWindowInfo>> all=
                getWindowsOnAllDisplays();
            List<AccessibilityWindowInfo> windows=
                all==null?null:all.get(display);
            if(windows==null)return null;
            for(AccessibilityWindowInfo window:windows){
                if(window==null||window.getDisplayId()!=display)continue;
                // A system alert above Maps must block route automation.
                if(window.getType()==AccessibilityWindowInfo.TYPE_SYSTEM)
                    return null;
                if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)
                    continue;
                AccessibilityNodeInfo root=window.getRoot();
                if(root==null||!root.isVisibleToUser()||
                   !NavMosaicSessionRegistry.MAPS.equals(
                       String.valueOf(root.getPackageName())))
                    return null;
                return root; // top visible Maps application (including dialog)
            }
        }catch(RuntimeException ignored){}
        return null;
    }
    private static String nodeLabel(AccessibilityNodeInfo node){
        if(node==null)return "";
        CharSequence description=node.getContentDescription();
        if(description!=null&&description.length()>0)
            return description.toString();
        CharSequence value=node.getText();
        return value==null?"":value.toString();
    }
    private static final class RoutePrompt{
        boolean exactExit,stopLike;
        int exitCount,startCount;
        Rect lastExit,lastStart;
        AccessibilityNodeInfo exit,start;
    }
    private void inspectRouteControls(AccessibilityNodeInfo node,
            RoutePrompt found,int depth,int[] scanned){
        if(node==null||depth>18||scanned[0]++>500||
           !node.isVisibleToUser())return;
        String label=nodeLabel(node);
        if(BridgeGoogleMapsReplacement.exitQuestion(label))
            found.exactExit=true;
        if(BridgeGoogleMapsReplacement.stopQuestion(label))
            found.stopLike=true;
        boolean exit=BridgeGoogleMapsReplacement.stopAction(label)||
            BridgeGoogleMapsReplacement.genericExitAction(label);
        boolean start=BridgeGoogleMapsReplacement.startAction(label);
        if(exit||start){
            AccessibilityNodeInfo clickable=node.isClickable()?node:null;
            if(clickable==null){
                AccessibilityNodeInfo parent=node.getParent();
                if(parent!=null&&parent.isClickable())clickable=parent;
            }
            if(clickable!=null&&clickable.isEnabled()&&
               clickable.isVisibleToUser()&&
               NavMosaicSessionRegistry.MAPS.equals(
                   String.valueOf(clickable.getPackageName()))){
                Rect bounds=new Rect();
                clickable.getBoundsInScreen(bounds);
                if(bounds.width()>25&&bounds.height()>18&&
                   bounds.width()<900&&bounds.height()<400){
                    if(exit&&(found.lastExit==null||
                              !found.lastExit.equals(bounds))){
                        found.exitCount++;
                        found.lastExit=bounds;
                        found.exit=clickable;
                    }
                    if(start&&(found.lastStart==null||
                               !found.lastStart.equals(bounds))){
                        found.startCount++;
                        found.lastStart=bounds;
                        found.start=clickable;
                    }
                }
            }
        }
        for(int i=0;i<node.getChildCount()&&scanned[0]<500;i++)
            inspectRouteControls(node.getChild(i),found,depth+1,scanned);
    }
    private void checkMapsRoute(){
        if(!ready||busy)return;
        NavMosaicMapsRoute.Snapshot request=
            NavMosaicMapsRoute.snapshot(this);
        if(request==null)return;
        AccessibilityNodeInfo root=topMapsRouteWindow(request.display);
        if(root==null)return;
        RoutePrompt dialog=new RoutePrompt();
        inspectRouteControls(root,dialog,0,new int[]{0});
        if(dialog.exactExit){
            // Only the real Maps question "¿Quieres salir de la
            // navegación?" may authorize a single exit/yes action.
            if(!request.exitPending)return;
            if(dialog.exitCount!=1||dialog.exit==null){
                NavMosaicMapsRoute.status(this,
                    "Maps NAV: aviso detectado, pero botón Salir/Sí ambiguo.");
                return; // Never guess a pixel or press generic Back.
            }
            if(!NavMosaicMapsRoute.consumeExit(this,request))return;
            boolean clicked=false;
            try{
                clicked=dialog.exit.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK);
            }catch(RuntimeException ignored){}
            if(!clicked)NavMosaicMapsRoute.abort(this,request,
                "Maps NAV rechazó confirmar la sustitución de ruta.");
            else NavMosaicMapsRoute.status(this,
                "Maps NAV: salida de ruta anterior solicitada.");
            return;
        }
        // Another stop/navigation/permission question is never clicked.
        if(dialog.stopLike)return;
        if(!request.startPending||dialog.startCount!=1||
           dialog.start==null)return;
        if(!NavMosaicMapsRoute.consumeStart(this,request))return;
        boolean clicked=false;
        try{
            clicked=dialog.start.performAction(
                AccessibilityNodeInfo.ACTION_CLICK);
        }catch(RuntimeException ignored){}
        NavMosaicMapsRoute.complete(this,request,clicked?
            "Maps NAV: Iniciar nueva ruta solicitado. Confirmar navegación visualmente.":
            "Maps NAV: botón Iniciar detectado, clic rechazado por Android.");
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
