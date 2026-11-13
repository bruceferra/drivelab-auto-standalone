package ar.com.rango5gps.drivelabauto.receiver;
public final class T1TopRowEnergyStatusTest {
    private static void eq(String got,String expected){
        if(!expected.equals(got))throw new AssertionError(got+" != "+expected);
    }
    public static void main(String[] argv){
        eq(T1TopRowEnergyStatus.regeneration(1),"REGEN · BAJA");
        eq(T1TopRowEnergyStatus.regeneration(2),"REGEN · MEDIA");
        eq(T1TopRowEnergyStatus.regeneration(3),"REGEN · ALTA");
        eq(T1TopRowEnergyStatus.regeneration(-1),"REGEN · SIN LECTURA");
        eq(T1TopRowEnergyStatus.battery(2),"BATERÍA · SMART");
        eq(T1TopRowEnergyStatus.battery(4),"BATERÍA · FORZADO");
        eq(T1TopRowEnergyStatus.battery(0),"BATERÍA · SIN LECTURA");
        eq(T1TopRowEnergyStatus.targetSoc(4,56),"SOC OBJ. · 50%");
        eq(T1TopRowEnergyStatus.targetSoc(4,48),"SOC OBJ. · 40%");
        eq(T1TopRowEnergyStatus.targetSoc(4,30),"SOC OBJ. · 20%");
        eq(T1TopRowEnergyStatus.targetSoc(4,40),"SOC OBJ. · 30%");
        eq(T1TopRowEnergyStatus.targetSoc(4,70),"SOC OBJ. · ?");
        eq(T1TopRowEnergyStatus.targetSoc(2,56),"SOC OBJ. · —");
        eq(T1TopRowEnergyStatus.targetSoc(-1,56),"SOC OBJ. · S/L");
        if(!T1TopRowEnergyStatus.description(4,70).contains("sin mapeo"))
            throw new AssertionError("unknown 60/70 must never look verified");
        System.out.println("PASS fixed-row energy OEM 1040/1041/1046 statuses");
    }
}
