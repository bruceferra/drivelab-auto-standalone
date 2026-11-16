package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1WheelWazeFocusedBackTest {
    static void ok(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String wheel=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1WazeWheelService.java"));
        String ui=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1WheelSettingsActivity.java"));
        int a=wheel.indexOf("private boolean backOnlyIfWazeFocused()");
        int b=wheel.indexOf("private void refreshOemBridge()",a);
        ok(a>0&&b>a,"Guarded global back function required");
        String guarded=wheel.substring(a,b);
        ok(guarded.contains("T1ClusterWazeRouter.selected(this)")&&
           guarded.contains("d.getDisplayId()<=0")&&
           guarded.contains("all.get(target)")&&
           guarded.contains("win.isFocused()")&&
           guarded.contains("root.isVisibleToUser()")&&
           guarded.contains("WAZE.equals(String.valueOf(root.getPackageName()))")&&
           guarded.contains("if(!focused)")&&
           guarded.contains("performGlobalAction(GLOBAL_ACTION_BACK)"),
           "BACK only with explicit Waze cluster focus, not OEM or center apps");
        ok(wheel.contains("p.getBoolean(ZOOM_MAP,false)")&&
           wheel.contains("zoomVisibleWazeMap(root,forward)")&&
           ui.contains("p.getBoolean(T1WazeWheelService.ZOOM_MAP,false)"),
           "Wheel selects Waze actions by default, zoom remains optional");
        ok(wheel.contains("if(confirm&&!mapMenuArmed)")&&
           wheel.contains("if(confirm&&mapMenuArmed&&collectMapChoices(root))")&&
           wheel.contains("clickSelected(\"MENSAJE WAZE · OK\")"),
           "OK never guesses an action; requires selected Waze UI button");
        ok(wheel.contains("if(choices.size()==1&&!question[0])return false;")&&
           wheel.contains("backOnlyIfWazeFocused()"),
           "Single-button pass prompt can be selected, unsafe back is prevented");
        ok(wheel.contains("if(expected>0&&across.keyAt(d)!=expected)continue;")&&
           wheel.contains("current=expected>0?null:getRootInActiveWindow()"),
           "Never control a Waze task on wrong/center display");
        ok(!wheel.contains("injectInputEvent(")&&
           !wheel.contains("Runtime.getRuntime().exec"),
           "Never use raw input shell or privileged OEM writes");
        System.out.println("PASS Waze wheel select, OK, focus-guarded BACK, optional zoom");
    }
}
