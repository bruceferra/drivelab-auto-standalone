package ar.com.rango5gps.drivelabauto.receiver;
public final class T1VoiceIntentWeatherTest {
    private static void yes(String q){
        if(!T1VoiceIntentPolicy.isWeatherQuery(q))throw new AssertionError(q);
    }
    private static void no(String q){
        if(T1VoiceIntentPolicy.isWeatherQuery(q))throw new AssertionError(q);
    }
    public static void main(String[] args){
        yes("¿Cómo es el clima hoy?");
        yes("¿Va a llover?");
        yes("¿Qué temperatura hace?");
        yes("¿Cómo está el tiempo hoy?");
        no("¿Cómo salió San Lorenzo?");
        no("¿Qué onda con Trump?");
        no("Doscientos más ochenta y ocho");
        no("Ir al Obelisco");
        System.out.println("PASS weather classification vs navigation/sport/news");
    }
}