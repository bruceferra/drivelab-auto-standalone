package ar.com.rango5gps.drivelabauto.receiver;

import java.util.Locale;

/**
 * Explicitly allowlisted read-only VehicleService samples. No arbitrary IDs,
 * no setter transactions, no attempt to enumerate 1..9999.
 */
public final class ChangeDetectorPolicy {
    public static final int DURATION_MS=45000;
    public static final int INTERVAL_MS=750;
    public static final int MAX_EVENTS=120;

    public static final class Signal {
        public final int id;
        public final String name;
        public final boolean isFloat;
        public final double minimumDelta;
        public Signal(int id,String name,boolean isFloat,double minimumDelta){
            this.id=id;this.name=name;this.isFloat=isFloat;this.minimumDelta=minimumDelta;
        }
        public String label(){return id+" · "+name;}
    }
    private static final Signal[] SIGNALS={
        new Signal(2004,"ventanilla delantera izquierda (candidato)",false,1.0),
        new Signal(2054,"ventanilla · segundo estado sin identificar",false,1.0),
        new Signal(2505,"señal acompañante de maniobra, sin identificar",true,0.10),
        new Signal(1042,"SOC interno",true,0.10),
        new Signal(1062,"SOC mostrado",true,0.10),
        new Signal(1064,"indicador corriente batería",true,0.10)
    };
    public static Signal[] signals(){return SIGNALS.clone();}
    public static boolean allowed(int id,boolean isFloat){
        for(Signal s:SIGNALS)if(s.id==id&&s.isFloat==isFloat)return true;
        return false;
    }
    public static boolean usable(Signal s,double value){
        if(!allowed(s.id,s.isFloat)||!Double.isFinite(value)||value==-1)return false;
        if(s.id==1042||s.id==1062)return value>=0&&value<=100;
        if(s.id==1064||s.id==2505)return value>=-10000&&value<=10000;
        return value>=0&&value<=1000;
    }
    public static boolean changed(Signal s,double before,double after){
        return usable(s,before)&&usable(s,after)&&
            Math.abs(after-before)+0.00001>=s.minimumDelta;
    }
    public static String fmt(Signal s,double value){
        if(!usable(s,value))return "Sin valor";
        if(!s.isFloat)return String.format(Locale.ROOT,"%d",(long)value);
        return String.format(Locale.forLanguageTag("es-AR"),"%.2f",value);
    }
    private ChangeDetectorPolicy(){}
}
