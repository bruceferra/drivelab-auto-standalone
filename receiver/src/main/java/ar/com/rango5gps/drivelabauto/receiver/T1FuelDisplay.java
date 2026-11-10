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
    public static String liters(double raw) {
        if (!Double.isFinite(raw) || raw < 0 || raw > 70) return "--";
        // Two decimals = 0.01 L = 10 mL; DOWN avoids inventing extra fuel.
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.DOWN)
            .toPlainString().replace('.', ',');
    }
}
