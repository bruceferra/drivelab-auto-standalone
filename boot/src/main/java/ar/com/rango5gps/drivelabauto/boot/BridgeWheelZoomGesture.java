package ar.com.rango5gps.drivelabauto.boot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;

/**
 * Original, display-scoped Google Maps two-finger zoom for Android 11.
 * The caller MUST have matched a visible, topmost Maps application window
 * and obtained explicit opt-in for the steering-wheel mode.
 * Never targets SystemUI/OEM, Waze, or a display inferred from a mirror.
 */
final class BridgeWheelZoomGesture {
    private static final long DURATION_MS=210L;
    private BridgeWheelZoomGesture(){}
    static boolean send(AccessibilityService service,Rect bounds,int display,
                        boolean closer,Handler handler,
                        AccessibilityService.GestureResultCallback cb){
        if(service==null||bounds==null||handler==null||
            (display!=0&&display!=1)||bounds.width()<440||
            bounds.height()<285)return false;
        // Both strokes remain strictly inside the verified Maps window.
        float cx=bounds.centerX(),cy=bounds.centerY();
        float far=Math.min(bounds.width()*.19f,bounds.height()*.29f);
        if(far<65f)return false;
        float near=Math.max(24f,far*.42f);
        float start=closer?near:far,end=closer?far:near;
        Path left=new Path(),right=new Path();
        left.moveTo(cx-start,cy);
        left.lineTo(cx-end,cy);
        right.moveTo(cx+start,cy);
        right.lineTo(cx+end,cy);
        GestureDescription.Builder gesture=new GestureDescription.Builder();
        gesture.setDisplayId(display); // Public API 30, not OEM reflection.
        gesture.addStroke(new GestureDescription.StrokeDescription(
            left,0,DURATION_MS));
        gesture.addStroke(new GestureDescription.StrokeDescription(
            right,0,DURATION_MS));
        return service.dispatchGesture(gesture.build(),cb,handler);
    }
}
