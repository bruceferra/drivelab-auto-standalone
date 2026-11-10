import ar.com.rango5gps.drivelabauto.receiver.T1FuelDisplay;

public final class T1FuelDisplayTest {
    private static void eq(String expected,String actual){
        if(!expected.equals(actual))
            throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        eq("63,50",T1FuelDisplay.liters(63.5));
        eq("63,50",T1FuelDisplay.liters(63.50999));
        eq("63,99",T1FuelDisplay.liters(63.999));
        eq("64,00",T1FuelDisplay.liters(64));
        eq("67,00",T1FuelDisplay.liters(67));
        eq("0,01",T1FuelDisplay.liters(0.01));
        eq("--",T1FuelDisplay.liters(Double.NaN));
        eq("--",T1FuelDisplay.liters(-1));
        eq("--",T1FuelDisplay.liters(71));
        System.out.println("PASS: CI186 0.01 L non-integer display with no rounding up");
    }
}
