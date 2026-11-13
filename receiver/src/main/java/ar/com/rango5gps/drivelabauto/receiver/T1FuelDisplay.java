package ar.com.rango5gps.drivelabauto.receiver;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Display-only formatting of OEM CI186 (not physical tank liters).
 * Preserve incoming Float/Double values and CSV raw precision; never round
 * the visible value UP or to an integer when diagnosing 0.01 L steps.
 */
public final class T1FuelDisplay {
    private T1FuelDisplay() {}
    /** Fuel remaining before the observed thermal-engine cutoff.
     * CI186 is an OEM indicated reading, not a certified physical meter.
     * Keep all raw OEM data unchanged for export and diagnosis.
     * Maximum 67 L is the owner's rounded observed REFILL usable capacity.
     */
    public static final double CUTOFF_CI186_L=5.3;
    public static final double FULL_USABLE_REFILL_L=67.0;
    public static double usableLiters(double ci186) {
        if (!Double.isFinite(ci186) || ci186 < 0 || ci186 > 70)
            return Double.NaN;
        return Math.min(FULL_USABLE_REFILL_L,
            Math.max(0,ci186-CUTOFF_CI186_L));
    }
    public static String usableLitersText(double ci186) {
        double useful=usableLiters(ci186);
        if (!Double.isFinite(useful)) return "--";
        // Avoid rounding up 0.01 L increments: mirrors raw CI186.
        return BigDecimal.valueOf(ci186)
            .subtract(BigDecimal.valueOf(CUTOFF_CI186_L))
            .max(BigDecimal.ZERO)
            .min(BigDecimal.valueOf(FULL_USABLE_REFILL_L))
            .setScale(2,RoundingMode.DOWN).toPlainString().replace('.',',');
    }
    public static boolean topUncalibrated(double ci186){
        return Double.isFinite(ci186) && ci186>=60.5 && ci186<=70;
    }
    public static String liters(double raw) {
        if (!Double.isFinite(raw) || raw < 0 || raw > 70) return "--";
        // Two decimals = 0.01 L = 10 mL; DOWN avoids inventing extra fuel.
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.DOWN)
            .toPlainString().replace('.', ',');
    }
}
