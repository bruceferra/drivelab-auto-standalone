package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A strictly READ-ONLY Android Automotive CarPropertyManager probe.
 * Uses reflection so Receiver compiles without android.car system SDK stubs.
 * Unsupported/denied properties are diagnostic outcomes, NOT zero positions.
 *
 * No setter, no guessing a physical end stop, no calibration movement.
 * Only executes when explicitly requested from the foreground vehicle UI.
 */
public final class MirrorPositionProbe {
    public interface Callback { void done(String result); }
    private static final int[] CANDIDATES={
        339741504, // Android Automotive MIRROR_Z_POS vertical
        339741506, // MIRROR_Y_POS horizontal
        557843649, // vendor passenger mirror location candidate
        557867096, // vendor seat ext mirror position candidate
        557875203, // vendor settings mirror position candidate
        557879310  // vendor seat ext mirror position by QNX candidate
    };
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private volatile boolean foreground=false;

    public MirrorPositionProbe(Context context){app=context.getApplicationContext();}
    public void setForeground(boolean value){foreground=value;}
    public boolean busy(){return busy.get();}

    public boolean readOnce(Callback callback){
        if(!foreground || !busy.compareAndSet(false,true))return false;
        new Thread(()->{
            String result;
            try{result=read();}
            catch(Exception e){result="SIN LECTURA · "+friendly(e);}
            finally{busy.set(false);}
            final String output=result;
            main.post(()->callback.done(output));
        },"T1-Mirror-Position-ReadOnly").start();
        return true;
    }

    private String read()throws Exception{
        if(!foreground)throw new IllegalStateException("Panel no visible");
        Class<?> carClass=Class.forName("android.car.Car");
        Object car=carClass.getMethod("createCar",Context.class).invoke(null,app);
        if(car==null)throw new IllegalStateException("Android Car no disponible");
        try{
            boolean connected=false;
            try{connected=(Boolean)carClass.getMethod("isConnected").invoke(car);}
            catch(NoSuchMethodException ignored){}
            if(!connected)carClass.getMethod("connect").invoke(car);
            Object manager=carClass.getMethod("getCarManager",String.class)
                .invoke(car,"property");
            if(manager==null)throw new IllegalStateException("CarPropertyManager ausente");
            Object configs=manager.getClass().getMethod("getPropertyList").invoke(manager);
            if(!(configs instanceof List))throw new IllegalStateException("Catálogo HAL ilegible");
            List<?> list=(List<?>)configs;
            Method getter=manager.getClass().getMethod(
                "getProperty",Class.class,int.class,int.class);
            Set<Integer> found=new HashSet<>();
            ArrayList<String> lines=new ArrayList<>();
            lines.add("POSICIÓN DE ESPEJOS · HAL solo lectura");
            lines.add("Los límites son rangos declarados, no topes físicos validados.");
            for(Object config:list){
                if(!foreground){
                    lines.add("Detenido: panel fuera de foco");
                    break;
                }
                int id=(Integer)config.getClass().getMethod("getPropertyId")
                    .invoke(config);
                if(!candidate(id))continue;
                found.add(id);
                String typeName="desconocido";
                Class<?> type=(Class<?>)config.getClass()
                    .getMethod("getPropertyType").invoke(config);
                if(type!=null)typeName=type.getSimpleName();
                int[] areaIds=(int[])config.getClass().getMethod("getAreaIds")
                    .invoke(config);
                if(areaIds==null||areaIds.length==0)areaIds=new int[]{0};
                for(int area:areaIds){
                    String range="sin rango";
                    try{
                        Object lo=config.getClass().getMethod("getMinValue",int.class)
                            .invoke(config,area);
                        Object hi=config.getClass().getMethod("getMaxValue",int.class)
                            .invoke(config,area);
                        if(lo!=null || hi!=null)range="min="+lo+", max="+hi;
                    }catch(Exception ignored){}
                    String val;
                    try{
                        Object property=getter.invoke(manager,type,id,area);
                        if(property==null){val="sin valor";}else{
                            Object value=property.getClass().getMethod("getValue")
                                .invoke(property);
                            val=String.valueOf(value);
                        }
                    }catch(Exception e){val="SIN LECTURA ("+friendly(e)+")";}
                    lines.add(name(id)+" "+id+" · area="+area+
                        " · tipo="+typeName+" · "+range+" · valor="+val);
                }
            }
            for(int id:CANDIDATES){
                if(!found.contains(id))lines.add(name(id)+" "+id+
                    " · NO EXPUESTA/NO AUTORIZADA en lista de propiedades");
            }
            lines.add("Compará lecturas ANTES y DESPUÉS de un pequeño ajuste con control OEM.");
            lines.add("Un mismo valor en ambos puntos NO demuestra posición física.");
            return String.join("\n",lines);
        }finally{
            try{carClass.getMethod("disconnect").invoke(car);}catch(Exception ignored){}
        }
    }

    private static boolean candidate(int id){
        for(int item:CANDIDATES)if(item==id)return true;
        return false;
    }
    private static String name(int id){
        switch(id){
            case 339741504:return "MIRROR_Z_POS";
            case 339741506:return "MIRROR_Y_POS";
            case 557843649:return "PASSENGER_MIRROR_LOCATION";
            case 557867096:return "SEAT_EXT_MIRROR_POS_A";
            case 557875203:return "SEAT_EXT_MIRROR_POS_B";
            case 557879310:return "SEAT_EXT_MIRROR_POS_QNX";
            default:return "UNKNOWN";
        }
    }
    private static String friendly(Exception ex){
        Throwable err=ex;
        while(err instanceof InvocationTargetException &&
                ((InvocationTargetException)err).getCause()!=null)
            err=((InvocationTargetException)err).getCause();
        String msg=err.getMessage();
        if(msg!=null&&msg.length()>90)msg=msg.substring(0,90);
        return err.getClass().getSimpleName()+
            (msg==null?"":(": "+msg));
    }
}
