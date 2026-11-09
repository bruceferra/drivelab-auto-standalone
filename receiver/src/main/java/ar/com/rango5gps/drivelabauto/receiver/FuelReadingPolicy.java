package ar.com.rango5gps.drivelabauto.receiver;

import java.util.Locale;

/**
 * Sanity checks and display format for validated CI 186 OEM liters.
 * 70 L is the nominal tank capacity; a 5 L margin is reserved for
 * OEM measurement noise, not a claim of usable volume.
 */
public final class FuelReadingPolicy {
    public static final float MAX_PLAUSIBLE_LITERS=75.0f;
    public static boolean usable(float value){
        return !Float.isNaN(value)&&!Float.isInfinite(value)
            &&value>=0.0f&&value<=MAX_PLAUSIBLE_LITERS;
    }
    public static String display(float value){
        if(!usable(value))throw new IllegalArgumentException("Lectura OEM inválida");
        return String.format(Locale.forLanguageTag("es-AR"),"%.1f L",value);
    }
    private FuelReadingPolicy(){}
}
