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
        yes(fade.contains("DEFAULT_PX=60")&&fade.contains("Math.min(120,saved)")&&
            fade.contains("Math.min(120,px)")&&
            settings.contains("DESVANECIDO LATERAL")&&
            settings.contains("fadeSeek.setMax(120)")&&
            settings.contains("T1ClusterEdgeFade.setWidth("),
            "Owner can choose edge fade from 0 to 120 px on split panel screen");
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
        yes(fade.contains("not true transparency")||
            fade.contains("NOT true transparency")||
            fade.contains("NOT true transparency of foreign app surfaces"),
            "Document that edge mask does not reveal underlying OEM surface");
        System.out.println("PASS 0..120px touch-through edge fade, cluster scoped");
    }
}
