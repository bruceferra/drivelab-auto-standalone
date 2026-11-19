package ar.com.rango5gps.drivelabauto.receiver;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/** Minimal left-hand clock for Freeform: 19 nov above 14:32. */
final class T1HeaderClockFormat {
    private static final Locale ES_AR=new Locale("es","AR");
    private T1HeaderClockFormat(){}
    static String date(Date date){
        return new SimpleDateFormat("d MMM",ES_AR).format(date);
    }
    static String time(Date date){
        return new SimpleDateFormat("HH:mm",ES_AR).format(date);
    }
}
