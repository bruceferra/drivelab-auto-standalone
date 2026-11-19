package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1DrivingMode2053Test {
    private static void ck(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception {
        ck(T1DrivingMode2053.label(0).equals("ECO"),"0 -> ECO");
        ck(T1DrivingMode2053.label(1).equals("NORMAL"),"1 -> NORMAL");
        ck(T1DrivingMode2053.label(2).equals("SPORT"),"2 -> SPORT");
        ck(T1DrivingMode2053.label(3).equals("SNOW"),"3 -> SNOW");
        ck(T1DrivingMode2053.label(-1).equals("SIN DATO"),"unknown sentinel");
        ck(T1DrivingMode2053.label(Integer.MIN_VALUE).equals("SIN DATO"),"no read");
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String header=Files.readString(Path.of(root,"T1GlobalToolbar.java"));
        String cockpit=Files.readString(Path.of(root,"T1PremiumCockpitActivity.java"));
        String telemetry=Files.readString(Path.of(root,"T1TripTelemetry.java"));
        int ev=header.indexOf("modes[2]=button(leading,\"EV MAX\"");
        int driving=header.indexOf("drivingModeIndicator=readOnlyIndicator(");
        int road=header.indexOf("roadModeButton=button(");
        ck(ev>=0&&driving>ev&&road>driving,"one read-only control after EV MAX");
        ck(header.contains("drivingModeIndicator=readOnlyIndicator(leading,\"SIN DATO\",85)"),
           "same width as HEV");
        ck(header.contains("refreshDrivingMode(snapshot.driveMode2053)")&&
           telemetry.contains("s.driveMode2053=diagnosticInt(()->intValue(b,2053))"),
           "real OEM reading from signal 2053");
        ck(header.contains("case 0:tint=T1JetourTheme.GREEN_BUTTON")&&
           header.contains("case 1:tint=T1JetourTheme.TURQUOISE")&&
           header.contains("case 2:tint=T1JetourTheme.RED_BUTTON")&&
           header.contains("case 3:tint=T1JetourTheme.WHITE")&&
           header.contains("if(raw==3)drivingModeIndicator.setTextColor(T1JetourTheme.NIGHT)"),
           "required palette/contrast");
        ck(header.contains("drivingModeIndicator.setOnClickListener(null)")&&
           header.contains("drivingModeIndicator.setClickable(false)")&&
           cockpit.contains("return T1DrivingMode2053.label(raw)"),
           "indicator is passive and cockpit uses same mapping");
        System.out.println("PASS OEM 2053 driving indicator labels, location, read-only and palette");
    }
}
