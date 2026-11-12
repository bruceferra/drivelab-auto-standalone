package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/** Optional read-only Android head-unit linear accelerometer.
 * Axes are in device coordinates, NOT calibrated car longitudinal/lateral axes.
 * Raw values are stored only if a sensor exists and a recent event arrives.
 */
final class T1MotionSensor implements SensorEventListener {
    static final class Sample {
        final double x,y,z,ageMs;
        Sample(double xx,double yy,double zz,double age){
            x=xx;y=yy;z=zz;ageMs=age;
        }
    }
    private final SensorManager manager;
    private Sensor sensor;
    private volatile double x=Double.NaN,y=Double.NaN,z=Double.NaN;
    private volatile long seen;
    T1MotionSensor(Context context){
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
    }
    boolean start(){
        if(manager==null)return false;
        sensor=manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(sensor==null)return false;
        try{return manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);}
        catch(Exception ex){return false;}
    }
    void stop(){
        if(manager!=null)try{manager.unregisterListener(this);}catch(Exception ignored){}
    }
    Sample snapshot(){
        long age=SystemClock.elapsedRealtimeNanos()-seen;
        if(seen==0||age<0||age>5000000000L)
            return new Sample(Double.NaN,Double.NaN,Double.NaN,Double.NaN);
        return new Sample(x,y,z,age/1000000.0);
    }
    @Override public void onSensorChanged(SensorEvent e){
        if(e==null||sensor==null||e.sensor!=sensor||e.values.length<3)return;
        x=e.values[0];y=e.values[1];z=e.values[2];seen=e.timestamp;
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
