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
        yes(fade.contains("DEFAULT_PX=60")&&
            fade.contains("MAX_PX=300")&&
            fade.contains("Math.min(MAX_PX,stored)")&&
            fade.contains("Math.min(MAX_PX,pixels)")&&
            settings.contains("DESVANECIDO AMBOS PANELES")&&
            settings.contains("fadeSeek.setMax(T1ClusterEdgeFade.MAX_PX)")&&
            settings.contains("T1ClusterEdgeFade.setWidth("),
            "Owner can adjust fade from 0 to 300px for both freeform panels");
        yes(fade.contains("createWindowContext(d,")&&
            fade.contains("leftWidth=Math.round(physicalWidth*ratio/100f)")&&
            fade.contains("rightWidth=physicalWidth-leftWidth")&&
            fade.contains("wm.addView(leftView,params(leftWidth,physicalHeight,0,")&&
            fade.contains("wm.addView(rightView,params(rightWidth,physicalHeight,leftWidth,")&&
            fade.contains("removeViewImmediate(leftView)")&&
            fade.contains("removeViewImmediate(rightView)")&&
            fade.contains("int e=Math.min(widthPx,Math.max(0,panelWidth/2))")&&
            !fade.contains("Math.max(0,w/8)"),
            "Separate physical-display overlays must draw in both panels, no 240px cap");
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
        yes(router.contains("T1ClusterEdgeFade.refresh(c)")&&
            toolbar.contains("T1ClusterEdgeFade.hide(owner)")&&
            toolbar.contains("T1ClusterEdgeFade.refresh(owner)")&&
            reboot.contains("T1ClusterEdgeFade.refresh(owner)"),
            "Fade present after both maps launch, removed on CLUSTER OFF");
        yes(fade.contains("NOT alpha control")||
            fade.contains("NOT true transparency")||fade.contains("not true transparency"),
            "Document that edge mask does not reveal underlying OEM surface");
        System.out.println("PASS 0..300px dual freeform overlays, touch-through, cluster scoped");
    }
}
