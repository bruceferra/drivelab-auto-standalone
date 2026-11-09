package ar.com.rango5gps.drivelabauto.receiver;

import java.util.Locale;

/** Only three pre-identified float getters; never allows arbitrary CAN/OEM IDs. */
public final class EnergySignalPolicy {
    private static final int[] IDS={1042,1062,1064};
    public static int[] ids(){return IDS.clone();}
    public static boolean allowed(int id){
        return id==1042||id==1062||id==1064;
    }
    public static boolean usable(int id,float value){
        if(!allowed(id)||Float.isNaN(value)||Float.isInfinite(value)||value==-1.0f)
            return false;
        // SOC is a percent value; current units/sign still under investigation.
        if(id==1042||id==1062)return value>=0.0f&&value<=100.0f;
        return value>=-10000.0f&&value<=10000.0f;
    }
    public static String display(float value){
        return String.format(Locale.forLanguageTag("es-AR"),"%.2f",value);
    }
    private EnergySignalPolicy(){}
}
