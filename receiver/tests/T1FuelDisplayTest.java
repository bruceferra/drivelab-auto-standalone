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
        // Owner's repeated physical cutoff CI186=5.3 L: the main
        // dashboard must show AVAILABLE, not the OEM 30.10 raw figure.
        eq("24,80",T1FuelDisplay.usableLitersText(30.10));
        eq("30,10",T1FuelDisplay.liters(30.10)); // diagnostics unchanged
        eq("24,80",T1FuelDisplay.usableLitersText(30.10f));
        eq("0,00",T1FuelDisplay.usableLitersText(5.30));
        eq("0,00",T1FuelDisplay.usableLitersText(4.9));
        eq("0,00",T1FuelDisplay.usableLitersText(0));
        eq("61,70",T1FuelDisplay.usableLitersText(67.0));
        eq("--",T1FuelDisplay.usableLitersText(Double.NaN));
        eq("--",T1FuelDisplay.usableLitersText(-1));
        eq("--",T1FuelDisplay.usableLitersText(71));
        if(Math.abs(T1FuelDisplay.usableLiters(30.10)-24.80)>0.00001)
            throw new AssertionError("CI186=30.10 should yield 24.80 usable L");
        if(T1FuelDisplay.FULL_USABLE_REFILL_L!=67.0 ||
           T1FuelDisplay.CUTOFF_CI186_L!=5.3)
            throw new AssertionError("Tank baseline 67, cutoff 5.3");
        if(!T1FuelDisplay.topUncalibrated(60.5))
            throw new AssertionError("top saturated OEM must remain flagged");
        System.out.println("PASS: raw CI186 preserved; usable liters 30.10 -> 24.80; cutoff 5.3; 67L refill baseline");
    }
}
