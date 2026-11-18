package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.Locale;

/**
 * Owner-authorized 18-Nov-2026 historical reconstruction from the exported
 * T1Launcher monthly CSV and two screenshots. Does NOT invent OEM samples,
 * GPS, fuel-pulse events, charging-meter readings or the absolute odometer.
 *
 * The closed morning is a separate, immutable historical checkpoint. The
 * current-day recorder gets a FRESH CSV and appends only post-import readings;
 * the UI uses a read-only carry, never appends fictitious raw samples.
 */
final class T1RebuiltMorning {
    static final String PREFS="t1_rebuilt_20261118_v1";
    static final String ID="20261118074300";
    static final double KM=21.03,EV_KM=15.61,ICE_KM=5.42,LITERS=0.24,
        PACK_KWH=6.23,REGEN_KWH=1.59;
    static final long FROM=1794998580000L; // 2026-11-18 07:43 ART
    static final long UNTIL=1795003920000L; // 2026-11-18 09:12 ART
    private static final String APPLIED="applied",PENDING="awaiting_new_trip",
        TRIP_ID="live_trip_id";
    private T1RebuiltMorning(){}
    private static SharedPreferences p(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean installed(Context c){return p(c).getBoolean(APPLIED,false);}
    static boolean isReconstructed(String key){
        return ("1:"+ID).equals(key);
    }
    static synchronized String install(Context c)throws IOException{
        if(installed(c))return "Reconstrucción 18/11 ya instalada; no se repite el borrado.";
        String guard=T1TripDeletion.blockReason(c);
        if(guard!=null)throw new IOException(guard);
        // Clear all OLD manual journeys via the existing guarded deletion.
        // Charge SQLite, tariffs, OEM preferences, map/ADB/wheel and cloud
        // credentials are deliberately retained. A cloud deletion is NOT made.
        String removed=T1TripDeletion.deleteAll(c);
        // Automatic ignition history is a separate independent SQLite DB.
        // Disable and stop its producer before clearing previous sessions.
        SharedPreferences auto=T1AutoJourneyService.prefs(c);
        if(!auto.edit().putBoolean(T1AutoJourneyStore.ENABLED,false).commit())
            throw new IOException("No se pudo detener viajes AUTO");
        try{
            c.startService(new Intent(c,T1AutoJourneyService.class)
                .setAction(T1AutoJourneyService.ACTION_STOP));
        }catch(RuntimeException error){
            throw new IOException("No se pudo solicitar detener AUTO: "+
                error.getClass().getSimpleName());
        }
        try{Thread.sleep(1800L);}
        catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            throw new IOException("Limpieza interrumpida");
        }
        try(T1AutoJourneyStore db=new T1AutoJourneyStore(c)){
            android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();
            sql.beginTransaction();
            try{
                sql.delete("samples",null,null);
                sql.delete("sessions",null,null);
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
        }
        if(!p(c).edit().putBoolean(APPLIED,true)
                .putBoolean(PENDING,true)
                .putString("source","T1Launcher historial-2026-11.csv "+
                    "+ capturas 252169/252168")
                .putString("cleanup",removed)
                .putLong("imported_at",System.currentTimeMillis())
                .commit())
            throw new IOException("No se pudo grabar saldo reconstruido");
        // Keep automatic future ignition sessions, independently from manual
        // TRIP TOTAL. The old AUTO records were removed as requested.
        auto.edit().putBoolean(T1AutoJourneyStore.ENABLED,true)
            .putString(T1AutoJourneyStore.STATUS,
                "Historial AUTO reiniciado; nueva jornada independiente")
            .commit();
        T1AutoJourneyService.startIfEnabled(c);
        try{
            c.startForegroundService(new Intent(c,T1TripRecorderService.class)
                .setAction(T1TripRecorderService.ACTION_START));
        }catch(RuntimeException ex){
            return removed+" · SALDO HISTÓRICO GUARDADO, pero Android no "+
                "inició la jornada nueva: tocá INICIAR en VIAJE TOTAL.";
        }
        return removed+" · Parcial 07:43–09:12 cerrado (21,03 km). "+
            "Jornada del 18/11 inicializada con saldo de 21,03 km; "+
            "nuevos kilómetros se registran sin duplicación.";
    }
    /** Called synchronously only on creation of a fresh real recorder CSV. */
    static void bindNewTrip(Context c,String id){
        SharedPreferences prefs=p(c);
        if(!prefs.getBoolean(APPLIED,false)||
           !prefs.getBoolean(PENDING,false))return;
        if(id==null||!id.matches("[0-9]{8}_[0-9]{6}"))return;
        prefs.edit().putString(TRIP_ID,id).putBoolean(PENDING,false).commit();
    }
    static boolean attached(Context c){
        if(!installed(c))return false;
        String bound=p(c).getString(TRIP_ID,"");
        return !bound.isEmpty()&&bound.equals(c.getSharedPreferences(
            T1TripRecorderService.PREFS,Context.MODE_PRIVATE)
            .getString(T1TripRecorderService.ID,""));
    }
    static double totalKm(Context c){
        return KM+(attached(c)?Math.max(0,c.getSharedPreferences(
            T1TripRecorderService.PREFS,Context.MODE_PRIVATE)
            .getFloat(T1TripRecorderService.DISTANCE,0f)):0);
    }
    static double totalEvKm(Context c){
        return EV_KM+(attached(c)?Math.max(0,c.getSharedPreferences(
            T1TripRecorderService.PREFS,Context.MODE_PRIVATE)
            .getFloat(T1TripRecorderService.EV_DISTANCE,0f)):0);
    }
    static String fmt(double n,int precision){
        return String.format(Locale.getDefault(),"%."+precision+"f",n);
    }
    static String costs(Context c,double km,double liters,double packKwh){
        SharedPreferences rate=c.getSharedPreferences(
            T1TripRecorderService.PREFS,Context.MODE_PRIVATE);
        double fuelPrice=rate.getFloat(T1TripRecorderService.FUEL_PRICE,2129f);
        boolean hasKwhPrice=rate.contains(T1TripRecorderService.KWH_PRICE);
        double electricPrice=rate.getFloat(T1TripRecorderService.KWH_PRICE,0f);
        double gasolineCost=liters*fuelPrice;
        double referenceL100=5.25; // provisional user reference, configurable later
        double wouldBurn=km*referenceL100/100.0;
        double gasOnlyCost=wouldBurn*fuelPrice;
        StringBuilder out=new StringBuilder();
        out.append("NAFTA OBSERVADA: ").append(fmt(liters,3))
            .append(" L × $").append(fmt(fuelPrice,2))
            .append("/L = $").append(fmt(gasolineCost,2))
            .append("\nSI TODO FUERA NAFTA (REFERENCIA 5,25 L/100): ")
            .append(fmt(wouldBurn,3)).append(" L = $")
            .append(fmt(gasOnlyCost,2))
            .append("\nAHORRO ANTES DE ELECTRICIDAD: $")
            .append(fmt(gasOnlyCost-gasolineCost,2));
        if(hasKwhPrice&&Double.isFinite(electricPrice)&&electricPrice>=0){
            // Scenario only: pack usage cannot be automatically charged to
            // the grid. The cash total is NOT claimed as actually billed.
            double simulatedElectric=packKwh*electricPrice;
            double total=gasolineCost+simulatedElectric;
            double equiv=total/fuelPrice;
            out.append("\nESCENARIO 6,23 kWh × $")
                .append(fmt(electricPrice,2)).append("/kWh = $")
                .append(fmt(simulatedElectric,2))
                .append("\nCOSTO MIXTO SIMULADO = $").append(fmt(total,2))
                .append("\nPONDERADO ECONÓMICO SIMULADO: ")
                .append(fmt(equiv,3)).append(" L equivalentes · ")
                .append(fmt(equiv*100/km,2)).append(" L/100 km · ")
                .append(fmt(km/equiv,2)).append(" km/L")
                .append("\nAHORRO SIMULADO VS TODO NAFTA: $")
                .append(fmt(gasOnlyCost-total,2));
        }else{
            out.append("\nPONDERADO ECONÓMICO: SIN DATO HASTA "+
                "CONFIGURAR $/kWh Y ATRIBUIR LA CARGA.");
        }
        out.append("\nELECTRICIDAD PAGADA REAL: SIN DATO si no hay "+
            "medición y atribución documentada de la carga del 17/11. "+
            "El escenario NO es facturación. Regeneración se informa "+
            "aparte y NO se descuenta dos veces de los 6,23 kWh.");
        return out.toString();
    }
    static String summary(Context c){
        if(!installed(c))return "RECONSTRUCCIÓN 18/11 AÚN NO IMPORTADA";
        double km=totalKm(c);
        double fresh=km-KM;
        StringBuilder s=new StringBuilder();
        s.append("PARCIAL CERRADO · 18/11 07:43–09:12 · T1Launcher")
            .append("\n21,03 km: EV 15,61 km + nafta 5,42 km")
            .append("\nNafta 0,240 L · batería reportada 6,230 kWh")
            .append(" · recuperados 1,590 kWh (informativo)")
            .append("\nSOC 99,9 % → 79,5 % · máxima 129 km/h")
            .append("\nJORNADA 18/11: ").append(fmt(km,2))
            .append(" km = 21,03 km importados + ")
            .append(fmt(fresh,2)).append(" km nuevos OEM")
            .append(attached(c)?" · MISMO TRIP nuevo":" · sin TRIP enlazado")
            .append("\n").append(costs(c,KM,LITERS,PACK_KWH))
            .append("\nFuente: historial-2026-11.csv y capturas. "+
                "NO sumar viaje superpuesto de 2,85 km a las 09:06. "+
                "Sin odómetro absoluto ni muestras GPS/OEM importadas.");
        return s.toString();
    }
}
