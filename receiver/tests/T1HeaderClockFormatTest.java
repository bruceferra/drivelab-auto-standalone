package ar.com.rango5gps.drivelabauto.receiver;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.nio.file.Files;
import java.nio.file.Path;
public final class T1HeaderClockFormatTest {
    static void ck(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Date d=new GregorianCalendar(2025,Calendar.MARCH,19,19,8,6).getTime();
        ck("miércoles 19 de marzo".equals(T1HeaderClockFormat.date(d)),
            "Spanish weekday and month without year");
        ck("19:08:06".equals(T1HeaderClockFormat.time(d)),
            "Full time in 24 hour format");
        Date mid=new GregorianCalendar(2025,Calendar.MARCH,19,0,3,1).getTime();
        ck("00:03:01".equals(T1HeaderClockFormat.time(mid)),
            "Midnight must be 24 hours");
        String tb=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1GlobalToolbar.java"));
        ck(tb.contains("clockBox")&&tb.contains("clockDate")&&
            tb.contains("clockTime")&&tb.contains("main.post(clockTick)")&&
            tb.contains("main.removeCallbacks(clockTick)"),
            "Shared global toolbar must show and refresh date/time");
        System.out.println("PASS shared top right Spanish date and 24h clock");
    }
}
