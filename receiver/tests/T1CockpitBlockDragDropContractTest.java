package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1CockpitBlockDragDropContractTest {
    static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        String s=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1PremiumCockpitActivity.java"));
        for(String row:new String[]{"trip_controls","trip_overview","temperatures","energy_metrics"})
            yes(s.contains("reorderCockpitRow("+(
                row.equals("trip_controls")?"controls":row.equals("trip_overview")?
                "overview":row.equals("temperatures")?"powertrain":"metrics")+
                ",\""+row+"\")"),"Missing row "+row);
        yes(s.contains("T1DragCue.attach(")&&s.contains("ACTION_DROP")&&
            s.contains("source.getParent()!=row"),"Drag must remain within same row");
        yes(s.contains("drivelab_cockpit_blocks_order_v1")&&
            s.contains("java.util.Collections.sort(sorted")&&
            s.contains("edit.apply()"),"Order must persist");
        System.out.println("Cockpit block same-row drag/drop contract: OK");
    }
}
