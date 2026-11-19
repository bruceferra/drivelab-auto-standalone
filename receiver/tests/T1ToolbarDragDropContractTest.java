package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Guardrails for user-customizable toolbar ordering. */
public final class T1ToolbarDragDropContractTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        String s=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1GlobalToolbar.java"));
        check(s.contains("enableRowReorder(leading,\"first\")")&&
            s.contains("enableRowReorder(actions,\"second\")"),
            "Both button rows must support reordering");
        check(s.contains("T1DragCue.attach(")&&
            s.contains("DragEvent.ACTION_DROP")&&
            s.contains("DragEvent.ACTION_DRAG_ENTERED"),
            "Reorder must be long-press drag/drop");
        check(s.contains("getParent()==row")&&
            s.contains("source.getParent()!=row"),
            "Drag/drop must be restricted to original row");
        check(s.contains("ORDER_PREFS")&&s.contains("editor.apply()")&&
            s.contains("Collections.sort(ordered"),
            "Order must persist and restore");
        check(s.contains("if(row.getChildAt(i) instanceof Button)")&&
            s.contains("int insert=firstIndex")&&
            s.contains("IdentityHashMap<Button,Integer>"),
            "Do not move clock/status text or rely on unstable button titles");
        check(s.contains("T1DragCue.attach(b,key)")&&
            s.contains("b.setOnDragListener("),
            "Read-only OEM chips must be draggable without click actions");
        System.out.println("Toolbar same-row drag/drop contract: OK");
    }
}
