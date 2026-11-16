package ar.com.rango5gps.drivelabauto.receiver;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/** Spanish day and date without year, plus 24-hour time including seconds. */
final class T1HeaderClockFormat {
    private static final Locale ES_AR=new Locale("es","AR");
    private T1HeaderClockFormat(){}
    static String date(Date date){
        return new SimpleDateFormat("EEEE d 'de' MMMM",ES_AR).format(date);
    }
    static String time(Date date){
        return new SimpleDateFormat("HH:mm:ss",ES_AR).format(date);
    }
}
