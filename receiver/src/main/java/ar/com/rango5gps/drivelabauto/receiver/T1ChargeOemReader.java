package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** OEM-only, read-only charging measurements.
 * VehicleService 1060/1062/1063/1064 are kept independent of the VHAL ids.
 * T1 Launcher's CODE tab displays the VHAL decimal property ID over two
 * lines: 55994 + 0910 = 559940910 (FLOAT, EXT.BATT VOLT).
 * The HAL fallback is used for energy only when voltage and BOTH independent
 * current readings agree. Never infer wall-meter kWh from battery HV readings.
 */
final class T1ChargeOemReader {
    static final int HAL_EXT_BATT_VOLT=559940910;
    static final int HAL_EXT_BATT_CURRENT=559940911;
    static final int HAL_EXT_BATT_POWER=557843808;
    static final int HAL_SINGLE_CHARGE_ENERGY=559940899;
    static final class Reading {
        final long timeMs;
        final int cable;
        final double soc,volts,amps,vehicleVolts;
        final double halVolts,halAmps,halSingleChargeEnergy;
        final int halPower;
        final String voltageSource;
        final int volts1063Int,volts1090Int;
        final double volts1090Float,volts1063AlcarFloat;
        Reading(long t,int c,double s,double v,double a,double vehicleV,
                double halV,double halA,int power,double singleEnergy,
                String source,int vi,int ai,double vf,double av){
            timeMs=t;cable=c;soc=s;volts=v;amps=a;vehicleVolts=vehicleV;
            halVolts=halV;halAmps=halA;halPower=power;
            halSingleChargeEnergy=singleEnergy;voltageSource=source;
            volts1063Int=vi;volts1090Int=ai;
            volts1090Float=vf;volts1063AlcarFloat=av;
        }
    }
    private static final class HalReading {
        double voltage=Double.NaN,current=Double.NaN;
        double singleEnergy=Double.NaN,legacy1063=Double.NaN;
        int power=Integer.MIN_VALUE;
    }
    private static final String PKG="com.autolink.app.vehicleservice",
        CLS="com.autolink.app.vehicleservice.VehicleService",
        TOKEN="com.autolink.app.vehicleservice.IVehicleControl",
        ALCAR_PKG="com.autolink.carservice",
        ALCAR_CLS="com.autolink.carservice.ALCarService",
        ALCAR_TOKEN="com.autolink.adapterinterface.car.IALCar";
    private final Context app;
    T1ChargeOemReader(Context c){app=c.getApplicationContext();}

    Reading poll()throws Exception{
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> remote=new AtomicReference<>();
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                remote.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                remote.set(null);ready.countDown();
            }
            @Override public void onNullBinding(ComponentName n){ready.countDown();}
        };
        boolean bound=app.bindService(new Intent().setComponent(
            new ComponentName(PKG,CLS)),conn,0);
        if(!bound)throw new IllegalStateException("OEM sin enlace");
        try{
            if(!ready.await(2500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("OEM sin respuesta");
            IBinder b=remote.get();
            if(b==null||!b.isBinderAlive()||
                !TOKEN.equals(b.getInterfaceDescriptor()))
                throw new SecurityException("Interfaz OEM no válida");
            int cable=getInt(b,1060);
            double soc=getFloatOrUnknown(b,1062);
            double vehicleVoltage=getFloatOrUnknown(b,1063);
            double amps=getFloatOrUnknown(b,1064);
            boolean vehicleVoltageValid=validVoltage(vehicleVoltage);
            int raw1063=Integer.MIN_VALUE,raw1090Int=Integer.MIN_VALUE;
            double raw1090=Double.NaN;
            HalReading hal=new HalReading();
            if(cable==1&&!vehicleVoltageValid){
                // The integer and legacy candidates are diagnostic only.
                raw1063=getIntOrUnknown(b,1063);
                raw1090=getFloatOrUnknown(b,1090);
                raw1090Int=getIntOrUnknown(b,1090);
                hal=readHalBattery();
            }
            // HAL current (559940911) must independently agree with the
            // VehicleService 1064 current. Mismatched/unknown data stays
            // unverified and cannot accrue kWh.
            boolean matchingCurrent=Double.isFinite(amps)&&
                Double.isFinite(hal.current)&&
                amps>=-1000&&amps<=1000&&
                Math.abs(amps-hal.current)<=Math.max(2.5,
                    Math.abs(amps)*0.30);
            boolean halVoltageValid=cable==1&&
                validVoltage(hal.voltage)&&matchingCurrent;
            double validatedVolts=vehicleVoltageValid?vehicleVoltage:
                halVoltageValid?hal.voltage:Double.NaN;
            String source=vehicleVoltageValid?"VEHICLE 1063 float":
                halVoltageValid?"HAL 559940910 confirmado con 559940911":
                "SIN VOLTAJE HV VALIDADO";
            return new Reading(System.currentTimeMillis(),cable,soc,
                validatedVolts,amps,vehicleVoltage,hal.voltage,hal.current,
                hal.power,hal.singleEnergy,source,raw1063,raw1090Int,
                raw1090,hal.legacy1063);
        }finally{
            try{app.unbindService(conn);}catch(Exception ignored){}
        }
    }
    private static boolean validVoltage(double value){
        return Double.isFinite(value)&&value>=100&&value<=1000;
    }
    private static int getInt(IBinder b,int id)throws Exception{
        Parcel a=Parcel.obtain(),z=Parcel.obtain();
        try{
            a.writeInterfaceToken(TOKEN);a.writeInt(id);
            if(!b.transact(5,a,z,0))
                throw new IllegalStateException("readInt "+id);
            z.readException();return z.readInt();
        }finally{a.recycle();z.recycle();}
    }
    private static double getFloatOrUnknown(IBinder b,int id){
        Parcel a=Parcel.obtain(),z=Parcel.obtain();
        try{
            a.writeInterfaceToken(TOKEN);a.writeInt(id);
            if(!b.transact(6,a,z,0))return Double.NaN;
            z.readException();double x=z.readFloat();
            return Double.isFinite(x)?x:Double.NaN;
        }catch(Exception ignored){return Double.NaN;}
        finally{a.recycle();z.recycle();}
    }
    private static int getIntOrUnknown(IBinder b,int id){
        try{return getInt(b,id);}
        catch(Exception ignored){return Integer.MIN_VALUE;}
    }
    /** ALCarService typed read-only transactions (9=float, 2=int),
     * each with the full property ID and global area=0.
     */
    private static double alCarFloat(IBinder binder,int prop){
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(ALCAR_TOKEN);
            in.writeInt(prop);in.writeInt(0);
            if(!binder.transact(9,in,out,0))return Double.NaN;
            out.readException();float value=out.readFloat();
            return Float.isFinite(value)?value:Double.NaN;
        }catch(Exception ignored){return Double.NaN;}
        finally{in.recycle();out.recycle();}
    }
    private static int alCarInt(IBinder binder,int prop){
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(ALCAR_TOKEN);
            in.writeInt(prop);in.writeInt(0);
            if(!binder.transact(2,in,out,0))return Integer.MIN_VALUE;
            out.readException();return out.readInt();
        }catch(Exception ignored){return Integer.MIN_VALUE;}
        finally{in.recycle();out.recycle();}
    }
    private HalReading readHalBattery(){
        HalReading measurements=new HalReading();
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<IBinder> binder=new AtomicReference<>();
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName n,IBinder b){
                binder.set(b);ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName n){
                ready.countDown();
            }
            @Override public void onNullBinding(ComponentName n){
                ready.countDown();
            }
        };
        boolean bound=false;
        try{
            bound=app.bindService(new Intent().setComponent(new ComponentName(
                ALCAR_PKG,ALCAR_CLS)),conn,0);
            if(!bound||!ready.await(1500,TimeUnit.MILLISECONDS))
                return measurements;
            IBinder b=binder.get();
            if(b==null||!b.isBinderAlive()||
                !ALCAR_TOKEN.equals(b.getInterfaceDescriptor()))
                return measurements;
            measurements.voltage=alCarFloat(b,HAL_EXT_BATT_VOLT);
            measurements.current=alCarFloat(b,HAL_EXT_BATT_CURRENT);
            measurements.power=alCarInt(b,HAL_EXT_BATT_POWER);
            measurements.singleEnergy=alCarFloat(
                b,HAL_SINGLE_CHARGE_ENERGY);
            measurements.legacy1063=alCarFloat(b,1063);
            return measurements;
        }catch(Exception ignored){
            return measurements;
        }finally{
            if(bound)try{app.unbindService(conn);}catch(Exception ignored){}
        }
    }
}
