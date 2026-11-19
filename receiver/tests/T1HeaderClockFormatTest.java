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
        ck("19 mar".equals(T1HeaderClockFormat.date(d)),
            "Short Spanish day and month, no weekday");
        ck("19:08".equals(T1HeaderClockFormat.time(d)),
            "24-hour compact time without seconds");
        Date mid=new GregorianCalendar(2025,Calendar.MARCH,19,0,3,1).getTime();
        ck("00:03".equals(T1HeaderClockFormat.time(mid)),
            "Midnight must be 24 hours");
        String tb=Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/T1GlobalToolbar.java"));
        ck(tb.contains("clockBox")&&tb.contains("clockDate")&&
            tb.contains("clockTime")&&tb.contains("main.post(clockTick)")&&
            tb.contains("main.removeCallbacks(clockTick)"),
            "Shared global toolbar must show and refresh date/time");
        ck(tb.contains("header.addView(clockBox,0,")&&
            tb.contains("title.setText(\"T1\")")&&
            tb.contains("title.setVisibility(compact?View.GONE:View.VISIBLE)")&&
            tb.contains("Button update=button(header,\"⟳\""),
            "Clock must be first at driver's left, title unobtrusive, updater accessible");
        System.out.println("PASS left-positioned compact date/time without covering toolbar");
    }
}
