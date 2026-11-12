package ar.com.rango5gps.drivelabauto.receiver;

/**
 * Pure JVM, fail-closed gate for explicitly opt-in OEM 1043 changes.
 * No actuation from UNKNOWN roads, one-off GPS matches or stale battery
 * snapshots. Once a write is rejected, the caller disables automation.
 */
final class T1OptimizedAutoGate {
    enum Reason { WAIT_STABLE, UNKNOWN, NO_BATTERY, NO_OEM, SAME_MODE,
        COOLDOWN, READY, NO_LOCATION, UNSUPPORTED_PROFILE }
    static final class Decision {
        final Reason reason;
        final int target;
        final String text;
        Decision(Reason r,int target,String t){
            reason=r;this.target=target;text=t;
        }
        boolean ready(){return reason==Reason.READY;}
    }
    private T1OptimizedModePolicy.Road candidate;
    private int votes;
    private long lastCommandAt=-1;
    private int lastTarget=-1;
    static final long MIN_COMMAND_GAP_MS=90000;
    static final int REQUIRED_MATCHES=3;
    Decision examine(T1OptimizedModePolicy.Road road,
                     T1OptimizedModePolicy.Rule rule,
                     double battery,double speed,int gear,int currentMode,
                     long now,boolean freshGps,boolean freshTelemetry){
        if(!freshGps||road==null){
            candidate=null;votes=0;
            return new Decision(Reason.NO_LOCATION,0,
                "GPS ausente o impreciso; sin cambio OEM");
        }
        if(road==T1OptimizedModePolicy.Road.UNKNOWN){
            candidate=null;votes=0;
            return new Decision(Reason.UNKNOWN,0,
                "Vía desconocida: se conserva modo actual");
        }
        if(candidate!=road){
            candidate=road;votes=1;
        }else votes=Math.min(100,votes+1);
        if(votes<REQUIRED_MATCHES)
            return new Decision(Reason.WAIT_STABLE,0,
                "Verificando vía "+T1OptimizedModePolicy.roadName(road)+
                " ("+votes+"/"+REQUIRED_MATCHES+")");
        if(rule==null)return new Decision(Reason.UNSUPPORTED_PROFILE,0,
            "Perfil no disponible");
        if(!freshTelemetry||!T1DriveModeGate.allowed(gear,speed)||
            currentMode<1||currentMode>3)
            return new Decision(Reason.NO_OEM,0,
                "Marcha/velocidad/modo OEM no confiables; sin orden");
        if(!Double.isFinite(battery)||battery<0||battery>100)
            return new Decision(Reason.NO_BATTERY,0,
                "SOC sin lectura fiable; conservar modo");
        T1OptimizedModePolicy.Decision evaluated=
            T1OptimizedModePolicy.evaluate(road,rule,battery);
        int target;
        if(!evaluated.conditionSatisfied)
            target=2; // condition failed: user-requested HEV fallback
        else target=rule.power==T1OptimizedModePolicy.Power.EV?1:
            rule.power==T1OptimizedModePolicy.Power.HEV?2:3;
        if(target==currentMode)
            return new Decision(Reason.SAME_MODE,target,
                "1043="+target+" ya coincide con perfil");
        if(lastCommandAt>=0 && now-lastCommandAt<MIN_COMMAND_GAP_MS)
            return new Decision(Reason.COOLDOWN,target,
                "Evitando cambios repetidos; espera de estabilidad");
        return new Decision(Reason.READY,target,
            evaluated.conditionSatisfied?"Aplicar propulsión 1043="+target:
                "Condición SOC no cumplida; respaldo HEV 1043=2");
    }
    void recordCommand(long time,int target){
        lastCommandAt=time;
        lastTarget=target;
        votes=0;
        candidate=null;
    }
}
