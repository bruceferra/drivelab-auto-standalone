package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Manual pump-led fuel anchor, independent from T1Launcher formula. */
final class T1FuelCalibration {
    static final String PREFS="t1_fuel_calibration";
    static final String ANCHORED="anchor_full",SINCE_KM="since_full_km",
        ANCHOR_EPOCH="full_epoch",RAW_AT_FULL="oem_at_full",
        LAST_L100="last_l100",LAST_PUMP="last_pump_l",LAST_KM="last_jetour_km",
        CYCLES="measured_cycles";
    private T1FuelCalibration(){}
    static SharedPreferences p(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static void markFull(Context context,double oemReading){
        if(!Double.isFinite(oemReading)||oemReading<60.5||
                oemReading>70)
            throw new IllegalArgumentException("Se exige lectura OEM próxima a tanque lleno");
        p(context).edit().putBoolean(ANCHORED,true)
            .putFloat(SINCE_KM,0f).putLong(ANCHOR_EPOCH,System.currentTimeMillis())
            .putFloat(RAW_AT_FULL,(float)oemReading).apply();
    }
    static double latestL100(Context context){
        double value=p(context).getFloat(LAST_L100,(float)T1TripMath.lPer100());
        return Double.isFinite(value)&&value>0.1&&value<30?value:T1TripMath.lPer100();
    }
    static double remainingKm(Context context,Double oemValue){
        if(!p(context).getBoolean(ANCHORED,false))return Double.NaN;
        double km=p(context).getFloat(SINCE_KM,0f);
        if(oemValue!=null&&Double.isFinite(oemValue)&&
                oemValue<=T1TripMath.CUTOFF_OEM)return 0;
        double nominalKm=T1TripMath.PUMP_LITERS*100/latestL100(context);
        return Math.max(0,nominalKm-Math.max(0,km));
    }
    /** Verified refill pairs are appended, NEVER overwrite the historic evidence. */
    static void registerPump(Context c,double pumpLiters,double jetourKm){
        if(!Double.isFinite(pumpLiters)||pumpLiters<1||pumpLiters>100||
                !Double.isFinite(jetourKm)||jetourKm<10||jetourKm>3000)
            throw new IllegalArgumentException("Carga/km fuera de rango");
        double economy=100*pumpLiters/jetourKm;
        File file=new File(c.getFilesDir(),"t1_refills_user.csv");
        try(FileOutputStream stream=new FileOutputStream(file,true)){
            if(file.length()==0)stream.write(
                "timestamp_ms,surtidor_l,jetour_km,l_100,source\n"
                    .getBytes(StandardCharsets.UTF_8));
            String row=System.currentTimeMillis()+","+
                String.format(Locale.US,"%.3f",pumpLiters)+","+
                String.format(Locale.US,"%.2f",jetourKm)+","+
                String.format(Locale.US,"%.5f",economy)+",USER_PUMP\n";
            stream.write(row.getBytes(StandardCharsets.UTF_8));
        }catch(Exception error){
            throw new IllegalStateException("No se pudo conservar la evidencia",error);
        }
        p(c).edit().putFloat(LAST_L100,(float)economy)
            .putFloat(LAST_PUMP,(float)pumpLiters)
            .putFloat(LAST_KM,(float)jetourKm)
            .putInt(CYCLES,p(c).getInt(CYCLES,2)+1).apply();
    }
}
