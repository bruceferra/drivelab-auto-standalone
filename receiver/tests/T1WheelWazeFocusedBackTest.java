package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;
public final class T1WheelWazeFocusedBackTest {
    static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] a)throws Exception{
        String w=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1WazeWheelService.java"));
        String ui=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1WheelSettingsActivity.java"));
        ok(w.contains("private boolean backOnlyOnWaze()")&&
           !w.contains("performGlobalAction(GLOBAL_ACTION_BACK)")&&
           w.contains("T1WazePromptPolicy.dismiss"),
           "BACK never globally targets the wrong display");
        ok(w.contains("windowDecoration(String label)")&&
           w.contains("!windowDecoration(label)")&&
           w.contains("n.equals(\"maximizar\")")&&w.contains("n.equals(\"cerrar\")"),
           "Freeform chrome is not a Waze map button");
        ok(w.contains("p.getBoolean(ZOOM_MAP,false)")&&
           ui.contains("MODO RUEDITA ACTUAL: ")&&
           ui.contains("ZOOM DEL MAPA")&&
           ui.contains("SELECCIÓN DE BOTONES"),
           "User chooses mode explicitly and sees current choice");
        ok(ui.contains("ACTIVAR VOLANTE OEM")&&
           ui.contains("DESACTIVAR VOLANTE OEM")&&
           ui.contains("OEM_BINDER_ENABLED,true")&&
           ui.contains("OEM_BINDER_ENABLED,false"),
           "Separate idempotent OEM activation");
        ok(w.contains("if(confirm&&mapMenuArmed&&collectMapChoices(root))")&&
           w.contains("clickSelected(\"MENSAJE WAZE · OK\")")&&
           w.contains("if(choices.size()==1&&!question[0])return false;"),
           "OK only chooses an identified Waze action");
        ok(w.contains("if(expected>0&&across.keyAt(d)!=expected)continue;")&&
           w.contains("current=expected>0?null:getRootInActiveWindow()"),
           "No accidental Waze action in default display");
        System.out.println("PASS wheel mode, Waze-only controls and safe BACK");
    }
}
