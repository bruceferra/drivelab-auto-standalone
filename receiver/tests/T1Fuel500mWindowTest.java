package ar.com.rango5gps.drivelabauto.receiver;
public final class T1Fuel500mWindowTest {
    private static void check(boolean x,String msg){
        if(!x)throw new AssertionError(msg);
    }
    public static void main(String[] argv){
        T1Fuel500mWindow w=new T1Fuel500mWindow();
        for(int i=0;i<120;i++)w.add(i<20?0f:7f);
        check(w.size()==100,"100 events retained after 120");
        check(w.mean()==7.0,"100-segment mean, old EV values expelled");
        w.add(Float.NaN);w.add(-1);w.add(400);
        check(w.size()==100,"invalid values ignored");
        T1Fuel500mWindow recovered=T1Fuel500mWindow.decode(w.encode());
        check(recovered.size()==100&&recovered.mean()==7.0,
              "persisted state round trip");
        T1Fuel500mWindow old=T1Fuel500mWindow.decode("NaN;abc;-1;2.5;3.5");
        check(old.size()==2&&old.mean()==3,"reject bad stored events");
        check(Double.isNaN(new T1Fuel500mWindow().mean()),
              "no samples must not appear as zero gasoline");
        System.out.println("PASS 100x500m raw event window and persistent encoding");
    }
}
