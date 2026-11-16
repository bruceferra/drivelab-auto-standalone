package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1ClusterInvertedPanelsAndWheelSourceTest {
    static String src(String filename)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",
            filename));
    }
    static void check(boolean x,String reason){
        if(!x)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        T1ClusterLayoutMath.Frame google=T1ClusterLayoutMath.left(1920,720,40);
        T1ClusterLayoutMath.Frame waze=T1ClusterLayoutMath.right(1920,720,40);
        check(google.left==0&&google.right==768&&google.width()==768&&
            waze.left==768&&waze.right==1920&&waze.width()==1152,
            "Maps 40 percent left, Waze 60 percent right without gaps");
        String p=src("T1ClusterLayoutPolicy.java"),
            ui=src("T1ClusterLayoutSettingsActivity.java"),
            preview=src("T1ClusterLayoutPreview.java"),
            resize=src("T1ClusterAdbResize.java"),
            router=src("T1ClusterWazeRouter.java"),
            wheel=src("T1WazeWheelService.java");
        check(p.contains("PANELS_INVERTED")&&
            p.contains("flip?40:50")&&
            p.contains("if(flip)edit.putString(SECOND_APP,MAPS)")&&
            p.contains("MODE_SPLIT.equals(mode(c))&&inverted(c)")&&
            p.contains("geometry(c,display,!reversed)"),
            "Inverted layout persists Google-left/Waze-right and routes Waze correctly");
        check(router.contains("!T1ClusterLayoutPolicy.inverted(c)")&&
            resize.contains("boolean inverted=split&&T1ClusterLayoutPolicy.inverted(app)")&&
            resize.contains("app,selected,!inverted")&&
            resize.contains("if(!parked(c))"),
            "Actual task bounds and Google route adapt only when P/0");
        String target=src("T1WheelTarget.java");
        check(!target.contains("T1ClusterLayoutPolicy.active(c)")&&
            target.contains("T1ClusterWazeRouter.enabled(c)")&&
            target.contains("T1ClusterWazeRouter.selected(c)!=null"),
            "Do not gate read-only map discovery on pending ADB layout approval");
        check(ui.contains("INVERTIR PANELES")&&
            ui.contains("this::invertPanels")&&ui.contains("testLayout()")&&
            ui.contains("originalInvert==T1ClusterLayoutPolicy.inverted(this)")&&
            preview.contains("inverted?\"Waze\":second"),
            "Single inverter button previews changes and re-verifies orientation");
        check(wheel.contains("findMapNode(")&&
            wheel.contains("recentEventRoot(")&&
            wheel.contains("sourceWindow.getDisplayId()!=display.getDisplayId()")&&
            wheel.contains("window.getDisplayId()!=displayId")&&
            wheel.contains("ventana ")&&
            !wheel.contains("performGlobalAction(GLOBAL_ACTION_BACK)"),
            "Wheel uses only selected-display map-owned nodes and reports actual windows");
        System.out.println("PASS Google 40 left/Waze 60 right and safe multi-display wheel");
    }
}
