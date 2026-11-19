package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

public final class T1ClusterEdgeFadeContractTest {
    static String read(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    static void yes(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String fade=read("T1ClusterEdgeFade.java");
        String settings=read("T1ClusterLayoutSettingsActivity.java");
        String router=read("T1ClusterWazeRouter.java");
        String toolbar=read("T1GlobalToolbar.java");
        String reboot=read("T1ClusterManualReboot.java");
        String wheel=read("T1WazeWheelService.java");
        String immersive=read("T1ImmersiveActivity.java");
        yes(fade.contains("DEFAULT_PX=60")&&
            fade.contains("MAX_PX=300")&&
            fade.contains("Math.min(MAX_PX,stored)")&&
            fade.contains("Math.min(MAX_PX,px)")&&
            settings.contains("DESVANECIDO AMBOS PANELES")&&
            settings.contains("fadeSeek.setMax(T1ClusterEdgeFade.MAX_PX)")&&
            settings.contains("T1ClusterEdgeFade.setWidth("),
            "Owner can adjust fade from 0 to 300px for both freeform panels");
        int start=fade.indexOf("static void setWidth(Context c,int px)");
        int end=fade.indexOf("static String last(Context c)",start);
        yes(start>0&&end>start,
            "SeekBar setter remains available without altering navigation");
        String slider=fade.substring(start,end);
        yes(!slider.contains("addView(")&&
            !slider.contains("removeView")&&
            !slider.contains("refresh(")&&
            !slider.contains("apply(app)")&&
            !slider.contains("postDelayed")&&
            slider.contains("view.update(safe,"),
            "Dragging slider must NEVER create or remove overlay windows");
        int add=fade.indexOf("wm.addView(");
        yes(add>0&&fade.indexOf("wm.addView(",add+1)==-1&&
            fade.contains("createDisplayContext(d)")&&
            !fade.contains("createWindowContext(d,")&&
            fade.contains("physicalWidth,physicalHeight,")&&
            fade.contains("float seam=w*")&&
            fade.contains("strip(canvas,0,left,VEIL,clear)")&&
            fade.contains("strip(canvas,seam-left,seam,clear,VEIL)")&&
            fade.contains("strip(canvas,seam,seam+right,VEIL,clear)")&&
            fade.contains("strip(canvas,w-right,w,clear,VEIL)"),
            "One physically full-width overlay paints all four side gradients");
        yes(fade.contains("d.getDisplayId()!=1")&&
            fade.contains("TYPE_APPLICATION_OVERLAY")&&
            fade.contains("FLAG_NOT_FOCUSABLE")&&
            fade.contains("FLAG_NOT_TOUCHABLE")&&
            fade.contains("IMPORTANT_FOR_ACCESSIBILITY_NO")&&
            fade.contains("PixelFormat.TRANSLUCENT")&&
            fade.contains("new LinearGradient")&&
            !fade.contains("dispatchGesture")&&
            !fade.contains("am force-stop")&&
            !fade.contains("CarPropertyManager"),
            "Fade is decorative touch-through Display 1 overlay, not OEM or steering command");
        yes(toolbar.contains("T1ClusterEdgeFade.hide(owner)")&&
            toolbar.contains("T1ClusterEdgeFade.refresh(owner)")&&
            reboot.contains("REPARAR MAPAS DESACTIVADO POR SEGURIDAD")&&
            !reboot.contains("T1ClusterEdgeFade.refresh(owner)")&&
            immersive.contains("T1ClusterEdgeFade.refresh(this)"),
            "Fade stays available through explicit CLUSTER and UI resume; destructive REBOOT is disabled");
        yes(fade.contains(".putInt(WIDTH_PX,safe).commit()")&&
            fade.contains("view.isAttachedToWindow()")&&
            fade.contains("static void navigationWindowChanged(Context c)")&&
            fade.contains("apply(app,true)")&&
            fade.contains("MAIN.postDelayed(navigationRebind,NAV_REBIND_DELAY_MS)")&&
            fade.contains("NAV_REBIND_GUARD_MS=2500L")&&
            fade.contains("if(navigationRebind!=null)")&&
            immersive.contains("T1ClusterEdgeFade.refresh(this)")&&
            wheel.contains("T1ClusterEdgeFade.navigationWindowChanged(this)")&&
            wheel.contains("sourceWindow.getDisplayId()!=display.getDisplayId()")&&
            wheel.contains("type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED")&&
            wheel.contains("type==AccessibilityEvent.TYPE_WINDOWS_CHANGED"),
            "Persisted width restores on activity resume and selected cluster map window transitions");
        yes(fade.contains("not transparency")||
            fade.contains("NOT true transparency")||fade.contains("not true transparency"),
            "Document that edge mask does not reveal underlying OEM surface");
        System.out.println("PASS single full-width four-edge gradient, no WindowManager side effects on slider");
    }
}
