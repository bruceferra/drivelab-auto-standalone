package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The whole-day TRIP keeps recording OEM samples. A passenger trip is a LOCAL
 * checkpoint pair inside that one TRIP; it does not restart callbacks, maps
 * or independent trips. No location or identifying passenger data is uploaded.
 */
final class T1JourneyStore {
    private static final String PREFS="t1_passenger_journeys";
    private static final String ACTIVE="active",PAUSED="client_paused",LABEL="label",TIME="start_time",
        TRIP="trip_id",KM="km",FUEL="fuel",ELEC="electric",N_FUEL="nf",N_ELEC="ne",
        PENDING_LABEL="pending_label",PENDING_TIME="pending_time",
        PENDING_TRIP="pending_trip",RECOVERY_NOTE="last_interruption_note",
        CARRY_KM="carry_km",CARRY_FUEL="carry_fuel",
        CARRY_ELEC="carry_electric",CARRY_NF="carry_nf",
        CARRY_NE="carry_ne",CHAIN="trip_chain",
        ORIGINAL_TIME="original_client_start",
        START_PARKED="parked_at_client_start",
        START_OFFLINE="offline_at_client_start",
        CARRY_UNCERTAIN="carry_has_missing_samples",
        PAUSE_KM="pause_total_km",PAUSE_FUEL="pause_total_fuel",
        PAUSE_ELEC="pause_total_electric",PAUSE_NF="pause_total_nf",
        PAUSE_NE="pause_total_ne",PAUSE_SINCE="pause_since_epoch_ms",
        PAUSED_SUM="paused_total_ms",
        CLIENT_EV_KM="client_ev_km_1035",CLIENT_ICE_KM="client_ice_km_1035",
        CLIENT_UNKNOWN_KM="client_unknown_km_1035";
    private static final String CSV_HEADER=
        "id;inicio;fin;etiqueta;trip_id;km_est_oem_velocidad;eventos_nafta;"+
        "nafta_raw;litros_experimentales;eventos_electricidad;electricidad_raw;"+
        "kwh_neto_experimental;costo_nafta_experimental;precio_nafta\n";
    private static final String DRIVE_KM_HEADER=
        "id;km_electricos_motor_apagado_1035;km_termicos_motor_prendido_1035;"+
        "km_estado_motor_desconocido_1035\n";
    private static final String CHARGES_HEADER=
        "fecha_epoch_ms;kwh_red_medidos;precio_kwh;importe_ars;nota\n";

    static final class Entry {
        final String id,label,tripId;
        final long started,ended;
        final double km,liters,electricNet,fuelCost;
        double evKm=Double.NaN,iceKm=Double.NaN,unknownKm=Double.NaN;
        final int fuelEvents,electricEvents;
        Entry(String id,long started,long ended,String label,String tripId,
              double km,int fuelEvents,double liters,int electricEvents,
              double electricNet,double fuelCost){
            this.id=id;this.started=started;this.ended=ended;
            this.label=label;this.tripId=tripId;this.km=km;
            this.fuelEvents=fuelEvents;this.electricEvents=electricEvents;
            this.liters=liters;this.electricNet=electricNet;this.fuelCost=fuelCost;
        }
    }
    static final class Totals {
        int trips,charges,fuelKnownTrips,electricKnownTrips;
        double km,liters,electricNet,fuelCost,gridKwh,gridCost;
        void add(Entry e){
            trips++;km+=e.km;
            if(Double.isFinite(e.liters)){liters+=e.liters;fuelKnownTrips++;}
            if(Double.isFinite(e.electricNet)){
                electricNet+=e.electricNet;electricKnownTrips++;
            }
            if(Double.isFinite(e.fuelCost))fuelCost+=e.fuelCost;
        }
    }
    private static SharedPreferences p(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static SharedPreferences master(Context c){
        return c.getApplicationContext().getSharedPreferences(
            T1TripRecorderService.PREFS,Context.MODE_PRIVATE);
    }
    static String cleanLabel(String value) {
        String v=value==null?"":value.trim().replaceAll("[\\r\\n;\\t]"," ");
        if(v.isEmpty())v="Trayecto";
        if(v.length()>45)v=v.substring(0,45);
        if("=+-@".indexOf(v.charAt(0))>=0)v="'"+v; // spreadsheet formula neutralization
        return v;
    }
    static T1JourneyMetrics.Checkpoint now(Context c) {
        SharedPreferences m=master(c);
        String trip=m.getString(T1TripRecorderService.ID,"");
        long lastSample=m.getLong(T1TripRecorderService.LAST_SAMPLE_TIME,0L);
        long age=System.currentTimeMillis()-lastSample;
        if(!m.getBoolean(T1TripRecorderService.RUNNING,false)||
           !trip.matches("[0-9]{8}_[0-9]{6}")||
           m.getInt(T1TripRecorderService.SAMPLES,0)<1||
           lastSample<=0||age<0||age>15000)
            throw new IllegalStateException("Esperando muestra OEM nueva de la jornada");
        return new T1JourneyMetrics.Checkpoint(trip,
            System.currentTimeMillis(),m.getFloat(T1TripRecorderService.DISTANCE,0f),
            m.getFloat(T1TripRecorderService.SEG_FUEL_RAW_SUM,0f),
            m.getFloat(T1TripRecorderService.SEG_ELEC_RAW_SUM,0f),
            m.getInt(T1TripRecorderService.SEG_FUEL_COUNT,0),
            m.getInt(T1TripRecorderService.SEG_ELEC_COUNT,0));
    }
    static boolean active(Context c){return p(c).getBoolean(ACTIVE,false);}
    /** The recorder calls this after each new OEM sample. Pause excludes
     * the interval and finishing prevents accidental attribution to the
     * next passenger. Unknown RPM never becomes "electric". */
    static synchronized void recordMovement(Context c,int engineOn,double km){
        if(!Double.isFinite(km)||km<=0||km>1.0||!active(c)||paused(c))return;
        SharedPreferences z=p(c);
        String key=engineOn==0?CLIENT_EV_KM:
            engineOn==1?CLIENT_ICE_KM:CLIENT_UNKNOWN_KM;
        z.edit().putFloat(key,z.getFloat(key,0f)+(float)km).apply();
    }
    static double electricKm(Context c){
        return p(c).getFloat(CLIENT_EV_KM,0f);
    }
    static double thermalKm(Context c){
        return p(c).getFloat(CLIENT_ICE_KM,0f);
    }
    static double unknownKm(Context c){
        return p(c).getFloat(CLIENT_UNKNOWN_KM,0f);
    }
    static boolean paused(Context c){return active(c)&&p(c).getBoolean(PAUSED,false);}
    static String activeLabel(Context c){return p(c).getString(LABEL,"");}
    static boolean pending(Context c){return p(c).contains(PENDING_TIME);}
    static String pendingLabel(Context c){return p(c).getString(PENDING_LABEL,"");}

    /** PAUSE captures accumulated values; RESUME starts a new checkpoint in
     * the SAME master without counting distance/energy during the pause. */
    static synchronized String pause(Context c){
        if(!active(c))return "No hay cliente activo para pausar.";
        if(paused(c))return "Cliente ya pausado.";
        T1JourneyMetrics.Result result=running(c);
        if(result==null)return "Esperando muestra OEM fresca; no se pausó.";
        boolean saved=p(c).edit().putBoolean(PAUSED,true)
            .putFloat(PAUSE_KM,(float)result.km)
            .putFloat(PAUSE_FUEL,(float)result.rawFuel)
            .putFloat(PAUSE_ELEC,(float)result.rawElectric)
            .putInt(PAUSE_NF,result.fuelEvents)
            .putInt(PAUSE_NE,result.electricEvents)
            .putLong(PAUSE_SINCE,System.currentTimeMillis()).commit();
        return saved?"CLIENTE PAUSADO · km y energía conservados; jornada sigue grabando.":
            "No se pudo guardar la pausa del cliente.";
    }
    static synchronized String resume(Context c){
        if(!paused(c))return "Cliente no está pausado.";
        T1JourneyMetrics.Checkpoint mark;
        try{mark=now(c);}
        catch(Exception ex){return "Aún sin muestra OEM fresca para reanudar cliente.";}
        SharedPreferences z=p(c);
        long start=z.getLong(PAUSE_SINCE,0);
        long duration=start>0?Math.max(0,System.currentTimeMillis()-start):0;
        boolean saved=z.edit()
            .putString(TRIP,mark.tripId).putLong(TIME,mark.time)
            .putFloat(KM,(float)mark.km)
            .putFloat(FUEL,(float)mark.rawFuel)
            .putFloat(ELEC,(float)mark.rawElectric)
            .putInt(N_FUEL,mark.fuelCount).putInt(N_ELEC,mark.electricCount)
            .putFloat(CARRY_KM,z.getFloat(PAUSE_KM,0f))
            .putFloat(CARRY_FUEL,z.getFloat(PAUSE_FUEL,0f))
            .putFloat(CARRY_ELEC,z.getFloat(PAUSE_ELEC,0f))
            .putInt(CARRY_NF,z.getInt(PAUSE_NF,0))
            .putInt(CARRY_NE,z.getInt(PAUSE_NE,0))
            .putLong(PAUSED_SUM,z.getLong(PAUSED_SUM,0)+duration)
            .putBoolean(PAUSED,false).remove(PAUSE_SINCE).commit();
        return saved?"CLIENTE REANUDADO · mismo trayecto, sin sumar tramo pausado.":
            "Error al reanudar; cliente sigue pausado.";
    }
    static synchronized T1JourneyMetrics.Result pausedResult(Context c){
        if(!paused(c))return null;
        SharedPreferences z=p(c);
        return new T1JourneyMetrics.Result(
            z.getFloat(PAUSE_KM,0f),z.getFloat(PAUSE_FUEL,0f),
            z.getFloat(PAUSE_ELEC,0f),z.getInt(PAUSE_NF,0),
            z.getInt(PAUSE_NE,0),master(c).getFloat(
                T1TripRecorderService.FUEL_PRICE,2129f));
    }
    /**
     * True continuous passenger journey: the customer stays ACTIVE until the
     * user explicitly taps FINALIZAR. During old-version upgrades, a brand
     * new master day could be created and the original customer checkpoint
     * was left behind. Migrate that checkpoint into cumulative carry-over
     * WITHOUT closing the client or combining two unlike TRIP IDs.
     */
    static synchronized String reconcileInterrupted(Context c){
        SharedPreferences z=p(c),m=master(c);
        if(pending(c)){
            String expected=z.getString(PENDING_TRIP,"");
            String current=m.getString(T1TripRecorderService.ID,"");
            if(!current.isEmpty()&&!expected.equals(current))
                z.edit().putString(PENDING_TRIP,current).commit();
        }
        if(!active(c))return "";
        String old=z.getString(TRIP,"");
        String current=m.getString(T1TripRecorderService.ID,"");
        if(old.isEmpty()||current.isEmpty()||old.equals(current))return "";
        // This situation is specifically an old-version split, not an
        // intentional client finish. Keep ACTIVE and the original label.
        T1JourneyMetrics.Checkpoint first=startCheckpoint(c);
        String label=activeLabel(c);
        try{
            T1JourneyRecoveryFile.End oldLast=
                T1JourneyRecoveryFile.read(T1TripRecorderService.tripDir(c),old);
            if(oldLast.timestamp<first.time||oldLast.km<first.km)
                throw new IllegalStateException("CSV anterior anterior al cliente");
            int nf=oldLast.fuelComplete?oldLast.fuelCount:first.fuelCount;
            int ne=oldLast.electricComplete?oldLast.electricCount:first.electricCount;
            T1JourneyMetrics.Checkpoint last=
                new T1JourneyMetrics.Checkpoint(old,oldLast.timestamp,
                    oldLast.km,oldLast.fuelComplete?
                        oldLast.rawFuel:first.rawFuel,
                    oldLast.electricComplete?
                        oldLast.rawElectric:first.rawElectric,nf,ne);
            T1JourneyMetrics.Result recovered=T1JourneyMetrics.between(
                first,last,m.getFloat(T1TripRecorderService.FUEL_PRICE,2129f));
            boolean full=oldLast.fuelComplete&&oldLast.electricComplete;
            String chain=z.getString(CHAIN,old);
            long origin=z.getLong(ORIGINAL_TIME,first.time);
            // Snapshot the old CSV counters before switching the active
            // checkpoint to the new master. All source files are retained.
            boolean written=z.edit()
                .putString(TRIP,current)
                .putLong(TIME,m.getLong(T1TripRecorderService.START_TIME,
                    System.currentTimeMillis()))
                .putFloat(KM,0f).putFloat(FUEL,0f).putFloat(ELEC,0f)
                .putLong(START_PARKED,0L)
                .putLong(START_OFFLINE,0L)
                .putInt(N_FUEL,0).putInt(N_ELEC,0)
                .putFloat(CARRY_KM,z.getFloat(CARRY_KM,0f)+
                    (float)recovered.km)
                .putFloat(CARRY_FUEL,z.getFloat(CARRY_FUEL,0f)+
                    (float)recovered.rawFuel)
                .putFloat(CARRY_ELEC,z.getFloat(CARRY_ELEC,0f)+
                    (float)recovered.rawElectric)
                .putInt(CARRY_NF,z.getInt(CARRY_NF,0)+recovered.fuelEvents)
                .putInt(CARRY_NE,z.getInt(CARRY_NE,0)+recovered.electricEvents)
                .putLong(ORIGINAL_TIME,origin)
                .putString(CHAIN,chain+"+"+current)
                .putBoolean(CARRY_UNCERTAIN,
                    z.getBoolean(CARRY_UNCERTAIN,false)||!full)
                .putString(RECOVERY_NOTE,
                    "CLIENTE "+label+" SIGUE ABIERTO. Se recuperaron "+
                    T1JourneyMetrics.fmt(recovered.km,2)+
                    " km del CSV "+old+
                    " y se continúa en "+current+
                    " sin cerrar el trayecto ni inventar km del corte."+
                    (full?"":" Energía parcial sin eventos completos."))
                .commit();
            return written?recoveryNote(c):
                "No se pudo guardar la continuidad: cliente preservado.";
        }catch(Exception e){
            String note="CLIENTE "+label+
                " sigue abierto, pero el CSV de la jornada anterior "+
                "no se pudo reconstruir: "+e.getClass().getSimpleName()+
                ". No se borró ningún dato. Usá REPARAR CONTINUIDAD "+
                "para mantener el mismo cliente sin inventar mediciones.";
            z.edit().putString(RECOVERY_NOTE,note).apply();
            return note;
        }
    }
    static String recoveryNote(Context c){
        return p(c).getString(RECOVERY_NOTE,"");
    }
    static boolean stuck(Context c){
        if(!active(c))return false;
        String old=p(c).getString(TRIP,"");
        String current=master(c).getString(T1TripRecorderService.ID,"");
        return !old.isEmpty()&&!current.isEmpty()&&!old.equals(current);
    }
    static synchronized String repairWithoutOldCsv(Context c){
        if(!stuck(c))return "El cliente ya usa la jornada actual.";
        SharedPreferences z=p(c),m=master(c);
        String old=z.getString(TRIP,"");
        String current=m.getString(T1TripRecorderService.ID,"");
        long original=z.getLong(ORIGINAL_TIME,z.getLong(TIME,0L));
        String chain=z.getString(CHAIN,old);
        boolean result=z.edit().putString(TRIP,current)
            .putLong(ORIGINAL_TIME,original)
            .putString(CHAIN,chain+"+"+current)
            .putLong(TIME,m.getLong(T1TripRecorderService.START_TIME,
                System.currentTimeMillis()))
            .putFloat(KM,0).putFloat(FUEL,0).putFloat(ELEC,0)
            .putLong(START_PARKED,0L)
            .putLong(START_OFFLINE,0L)
            .putInt(N_FUEL,0).putInt(N_ELEC,0)
            .putBoolean(CARRY_UNCERTAIN,true)
            .putString(RECOVERY_NOTE,
                "CLIENTE CONTINÚA SIN CIERRE. No se reconstruyó el tramo "+
                old+"; CSV intacto, kilometraje parcial (subestimado).")
            .commit();
        return result?recoveryNote(c):
            "No se pudo reanudar. Se conserva el estado original.";
    }
    static String tripChain(Context c){
        return p(c).getString(CHAIN,p(c).getString(TRIP,""));
    }
    static boolean partialRecovery(Context c){
        return p(c).getBoolean(CARRY_UNCERTAIN,false);
    }
    static long elapsedMillis(Context c){
        if(!active(c))return 0;
        long began=p(c).getLong(ORIGINAL_TIME,
            p(c).getLong(TIME,System.currentTimeMillis()));
        SharedPreferences z=p(c);
        long pausedTime=z.getLong(PAUSED_SUM,0);
        if(paused(c))pausedTime+=Math.max(0,System.currentTimeMillis()-
            z.getLong(PAUSE_SINCE,System.currentTimeMillis()));
        return Math.max(0,System.currentTimeMillis()-began-pausedTime);
    }
    static long parkedMillis(Context c){
        if(!active(c))return 0;
        return Math.max(0,master(c).getLong(
            T1TripRecorderService.PARKED_MS,0L)-
            p(c).getLong(START_PARKED,0L));
    }
    static long offlineMillis(Context c){
        if(!active(c))return 0;
        return Math.max(0,master(c).getLong(
            T1TripRecorderService.OFFLINE_MS,0L)-
            p(c).getLong(START_OFFLINE,0L));
    }
    private static T1JourneyMetrics.Result withCarry(Context c,
        T1JourneyMetrics.Result part,double price){
        SharedPreferences z=p(c);
        return new T1JourneyMetrics.Result(
            part.km+z.getFloat(CARRY_KM,0),
            part.rawFuel+z.getFloat(CARRY_FUEL,0),
            part.rawElectric+z.getFloat(CARRY_ELEC,0),
            part.fuelEvents+z.getInt(CARRY_NF,0),
            part.electricEvents+z.getInt(CARRY_NE,0),price);
    }

    /** A one-tap client may start the master recorder first. The actual client
     * checkpoint is created by the recorder after its FIRST fresh OEM sample,
     * never from the previous finished 52.70 km snapshot. */
    static synchronized String queue(Context c,String label){
        if(active(c))return "Ya hay un trayecto activo; finalizalo primero.";
        if(pending(c))return "Iniciando "+pendingLabel(c)+"; esperando telemetría nueva.";
        SharedPreferences m=master(c);
        long sampleAge=System.currentTimeMillis()-
            m.getLong(T1TripRecorderService.LAST_SAMPLE_TIME,0L);
        boolean fresh=m.getBoolean(T1TripRecorderService.RUNNING,false)&&
            sampleAge>=0&&sampleAge<=15000;
        String expected=fresh?m.getString(T1TripRecorderService.ID,""):"";
        boolean saved=p(c).edit().putString(PENDING_LABEL,cleanLabel(label))
            .putLong(PENDING_TIME,System.currentTimeMillis())
            .putString(PENDING_TRIP,expected).commit();
        return saved?"Preparando trayecto: "+cleanLabel(label)+
            ". Quedará en 0,00 km cuando llegue la primera muestra nueva.":
            "Error al preparar trayecto";
    }
    private static void clearPending(Context c) {
        p(c).edit().remove(PENDING_LABEL).remove(PENDING_TIME)
            .remove(PENDING_TRIP).commit();
    }
    static synchronized void cancelPending(Context c){clearPending(c);}

    /** Called inside the foreground recorder after it persisted its sample.
     * Works even if the user switched to Maps or the original T1 interface. */
    static synchronized void fulfillPending(Context c){
        if(!pending(c)||active(c))return;
        SharedPreferences req=p(c),m=master(c);
        // Do not time out pending passengers on reboot or package update.
        if(!m.getBoolean(T1TripRecorderService.RUNNING,false)||
            m.getInt(T1TripRecorderService.SAMPLES,0)<1)return;
        String expected=req.getString(PENDING_TRIP,"");
        String current=m.getString(T1TripRecorderService.ID,"");
        if(!expected.isEmpty()&&!expected.equals(current)){
            req.edit().putString(PENDING_TRIP,current).commit();
        }
        String label=req.getString(PENDING_LABEL,"Trayecto");
        // start() is synchronized on the same monitor. Re-entrant is safe.
        start(c,label);
        if(active(c))clearPending(c);
    }
    static synchronized String start(Context c,String label){
        if(active(c))return "Ya hay un trayecto activo; finalizalo primero.";
        if(!master(c).getBoolean(T1TripRecorderService.RUNNING,false))
            return "Primero iniciá JORNADA para grabar el TRIP OEM.";
        T1JourneyMetrics.Checkpoint mark=now(c);
        label=T1AutoTripLabel.resolve(c,label,1,mark.time);
        boolean ok=p(c).edit().putBoolean(ACTIVE,true)
            .putBoolean(PAUSED,false).putLong(PAUSED_SUM,0L)
            .putLong(ORIGINAL_TIME,mark.time)
            .putString(CHAIN,mark.tripId)
            .putFloat(CARRY_KM,0f).putFloat(CARRY_FUEL,0f)
            .putFloat(CARRY_ELEC,0f)
            .putLong(START_PARKED,master(c).getLong(
                T1TripRecorderService.PARKED_MS,0))
            .putLong(START_OFFLINE,master(c).getLong(
                T1TripRecorderService.OFFLINE_MS,0))
            .putInt(CARRY_NF,0).putInt(CARRY_NE,0)
            .putBoolean(CARRY_UNCERTAIN,false)
            .putFloat(CLIENT_EV_KM,0f).putFloat(CLIENT_ICE_KM,0f)
            .putFloat(CLIENT_UNKNOWN_KM,0f)
            .remove(RECOVERY_NOTE)
            .putString(LABEL,cleanLabel(label)).putString(TRIP,mark.tripId)
            .putLong(TIME,mark.time).putFloat(KM,(float)mark.km)
            .putFloat(FUEL,(float)mark.rawFuel)
            .putFloat(ELEC,(float)mark.rawElectric)
            .putInt(N_FUEL,mark.fuelCount).putInt(N_ELEC,mark.electricCount)
            .commit();
        return ok?"Trayecto iniciado: "+cleanLabel(label)+
                " · sin reiniciar la jornada":"No se guardó el inicio";
    }
    private static T1JourneyMetrics.Checkpoint startCheckpoint(Context c){
        SharedPreferences z=p(c);
        return new T1JourneyMetrics.Checkpoint(z.getString(TRIP,""),
            z.getLong(TIME,0),z.getFloat(KM,0),z.getFloat(FUEL,0),
            z.getFloat(ELEC,0),z.getInt(N_FUEL,0),z.getInt(N_ELEC,0));
    }
    static synchronized T1JourneyMetrics.Result running(Context c) {
        if(!active(c))return null;
        if(paused(c))return pausedResult(c);
        try{
            double price=master(c).getFloat(
                T1TripRecorderService.FUEL_PRICE,2129f);
            return withCarry(c,T1JourneyMetrics.between(
                startCheckpoint(c),now(c),price),price);
        }
        catch(IllegalArgumentException|IllegalStateException ex){return null;}
    }
    static synchronized String finish(Context c){
        if(!active(c))return "No hay trayecto activo.";
        T1JourneyMetrics.Checkpoint start=startCheckpoint(c);
        T1JourneyMetrics.Result result;
        long ended;
        if(paused(c)){
            result=pausedResult(c);
            ended=p(c).getLong(PAUSE_SINCE,System.currentTimeMillis());
        }else{
            T1JourneyMetrics.Checkpoint end=now(c);
            if(!start.tripId.equals(end.tripId))
                return "Cambió la jornada: "+reconcileInterrupted(c)+
                    " · no se mezclaron kilómetros entre sesiones.";
            try{
                double price=master(c).getFloat(
                    T1TripRecorderService.FUEL_PRICE,2129f);
                result=withCarry(c,T1JourneyMetrics.between(start,end,price),price);
            }catch(IllegalArgumentException ex){
                return "Lecturas incoherentes: no se guardó";
            }
            ended=end.time;
        }
        long originalStart=p(c).getLong(ORIGINAL_TIME,start.time);
        String id=Long.toString(originalStart);
        String label=cleanLabel(activeLabel(c));
        double evKm=electricKm(c),iceKm=thermalKm(c),unknownKm=unknownKm(c);
        // Idempotent even when Android crashes between CSV flush and clearing prefs.
        for(Entry e:history(c))if(id.equals(e.id)){
            p(c).edit().clear().commit();
            return "Trayecto anterior ya estaba guardado.";
        }
        String row=id+";"+originalStart+";"+ended+";"+label+";"+
            tripChain(c)+
            ";"+db(result.km)+";"+result.fuelEvents+";"+db(result.rawFuel)+
            ";"+db(result.fuelLitersCandidate)+";"+result.electricEvents+
            ";"+db(result.rawElectric)+";"+db(result.electricNetKwhCandidate)+
            ";"+db(result.fuelCostCandidate)+";"+
            db(master(c).getFloat(T1TripRecorderService.FUEL_PRICE,2129f))+"\n";
        try{
            append(new File(T1TripRecorderService.tripDir(c),"client_trips.csv"),CSV_HEADER,row);
        }catch(IOException ex){return "No se guardó: "+ex.getClass().getSimpleName();}
        // Keep legacy client_trips.csv schema unchanged. The sidecar is
        // keyed by stable client ID, and the QR upload is a separate
        // GPS-free, label-free, idempotent private diagnostic CSV.
        String driveRow=id+";"+db(evKm)+";"+db(iceKm)+";"+
            db(unknownKm)+"\n";
        String cloudNote="";
        try{
            append(new File(T1TripRecorderService.tripDir(c),"client_drive_km.csv"),
                DRIVE_KM_HEADER,driveRow);
        }catch(IOException ex){cloudNote=" · desglose local pendiente"; }
        try{
            T1ClientTripSync.persist(c,id,originalStart,ended,tripChain(c),
                result,evKm,iceKm,unknownKm);
        }catch(IOException ex){cloudNote+=" · resumen QR local pendiente"; }
        p(c).edit().clear().commit();
        T1TripSync.trigger(c,true);
        T1ClientTripSync.trigger(c,true);
        return "GUARDADO: "+label+" · "+
            T1JourneyMetrics.fmt(result.km,2)+
            " km (velocidad OEM, ajuste provisional) · "+
            T1JourneyMetrics.fmt(evKm,2)+" km eléctricos 1035 · "+
            "sincronización QR en curso/pendiente"+cloudNote+" · "+
            (result.fuelAvailable?T1JourneyMetrics.fmt(result.fuelLitersCandidate,3)+
            " L experimentales":"sin tramo OEM completo")+
            " · costo de nafta experimental, NO costo total";
    }
    static synchronized void recordCharge(Context c,double kwh,double price,String note)
        throws IOException {
        if(!Double.isFinite(kwh)||kwh<=0||kwh>150||
                !Double.isFinite(price)||price<0||price>100000)
            throw new IllegalArgumentException("Revisá kWh medidos y tarifa");
        String row=System.currentTimeMillis()+";"+db(kwh)+";"+db(price)+
            ";"+db(kwh*price)+";"+cleanLabel(note)+"\n";
        append(new File(T1TripRecorderService.tripDir(c),"grid_charges.csv"),
            CHARGES_HEADER,row);
    }
    private static void append(File file,String header,String row)throws IOException {
        boolean first=!file.exists()||file.length()==0;
        try(FileOutputStream f=new FileOutputStream(file,true)){
            if(first)f.write(header.getBytes(StandardCharsets.UTF_8));
            f.write(row.getBytes(StandardCharsets.UTF_8));
            f.flush();
            f.getFD().sync();
        }
    }
    private static String db(double v){
        return Double.isFinite(v)?String.format(Locale.ROOT,"%.6f",v):"";
    }
    private static double number(String raw){
        try{return raw.isEmpty()?Double.NaN:Double.parseDouble(raw);}
        catch(Exception e){return Double.NaN;}
    }
    private static long time(String raw){
        try{return Long.parseLong(raw);}catch(Exception e){return 0L;}
    }
    static ArrayList<Entry> history(Context c) {
        ArrayList<Entry> out=new ArrayList<>();
        // Historical V1 clients predate RPM 1035 distance split; they stay
        // unknown rather than being labelled zero-electric.
        Map<String,double[]> drive=new LinkedHashMap<>();
        File driveFile=new File(T1TripRecorderService.tripDir(c),"client_drive_km.csv");
        if(driveFile.isFile())try(BufferedReader d=new BufferedReader(
                new FileReader(driveFile))){
            String line=d.readLine();
            while((line=d.readLine())!=null){
                String[] f=line.split(";",-1);
                if(f.length>=4)drive.put(f[0],new double[]{
                    number(f[1]),number(f[2]),number(f[3])});
            }
        }catch(IOException ignored){}
        File file=new File(T1TripRecorderService.tripDir(c),"client_trips.csv");
        if(!file.isFile())return out;
        LinkedHashMap<String,Entry> dedup=new LinkedHashMap<>();
        try(BufferedReader reader=new BufferedReader(new FileReader(file))){
            String line=reader.readLine(); // CSV header
            while((line=reader.readLine())!=null){
                String[] f=line.split(";",-1);
                if(f.length<14)continue;
                long a=time(f[1]),b=time(f[2]);
                if(a<=0||b<a||!Double.isFinite(number(f[5])))continue;
                int nf=Integer.parseInt(f[6]),ne=Integer.parseInt(f[9]);
                Entry e=new Entry(f[0],a,b,f[3],f[4],number(f[5]),
                    nf,number(f[8]),ne,number(f[11]),number(f[12]));
                double[] mode=drive.get(e.id);
                if(mode!=null){
                    e.evKm=mode[0];e.iceKm=mode[1];e.unknownKm=mode[2];
                }
                dedup.put(f[0],e);
            }
        }catch(IOException|NumberFormatException ignored){}
        out.addAll(dedup.values());
        return out;
    }
    static Totals totals(Context c,String datePrefix){
        Totals t=new Totals();
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
        for(Entry e:history(c)){
            if(fmt.format(new Date(e.started)).startsWith(datePrefix))t.add(e);
        }
        File file=new File(T1TripRecorderService.tripDir(c),"grid_charges.csv");
        if(file.isFile())try(BufferedReader reader=new BufferedReader(new FileReader(file))){
            reader.readLine();String line;
            while((line=reader.readLine())!=null){
                String[] f=line.split(";",-1);
                if(f.length<4)continue;
                if(!fmt.format(new Date(time(f[0]))).startsWith(datePrefix))continue;
                double k=number(f[1]),cost=number(f[3]);
                if(Double.isFinite(k)&&Double.isFinite(cost)){
                    t.charges++;t.gridKwh+=k;t.gridCost+=cost;
                }
            }
        }catch(IOException ignored){}
        return t;
    }
    static String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT)
        .format(new Date());}
    static String month(){return today().substring(0,7);}
    private T1JourneyStore(){}
}
