package ar.com.rango5gps.drivelabauto.receiver;

import java.util.ArrayDeque;
import java.util.Locale;

/** Raw OEM fuel callbacks, at most the latest 100 reported 500 m events.
 * This is an event window, NOT certified 50 km distance or calibrated fuel.
 * Persist across Receiver sessions without touching original CSV rows.
 */
final class T1Fuel500mWindow {
    static final int MAX=100;
    private final ArrayDeque<Float> values=new ArrayDeque<>();
    void add(float value){
        if(!Float.isFinite(value)||value<0||value>35)return;
        values.addLast(value);
        if(values.size()>MAX)values.removeFirst();
    }
    int size(){return values.size();}
    double mean(){
        if(values.isEmpty())return Double.NaN;
        double total=0;
        for(float v:values)total+=v;
        return total/values.size();
    }
    String encode(){
        StringBuilder s=new StringBuilder();
        for(float v:values){
            if(s.length()>0)s.append(';');
            s.append(Float.toString(v));
        }
        return s.toString();
    }
    static T1Fuel500mWindow decode(String stored){
        T1Fuel500mWindow window=new T1Fuel500mWindow();
        if(stored==null||stored.isEmpty())return window;
        for(String part:stored.split(";")){
            try{window.add(Float.parseFloat(part));}
            catch(NumberFormatException ignored){}
        }
        return window;
    }
}
