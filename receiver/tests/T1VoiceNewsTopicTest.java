package ar.com.rango5gps.drivelabauto.receiver;
public final class T1VoiceNewsTopicTest {
    static void eq(String phrase,String match){
        String actual=T1VoiceNewsTopic.topic(phrase);
        if(match==null?actual!=null:!match.equals(actual))
            throw new AssertionError(phrase+" => "+actual);
    }
    public static void main(String[] args){
        eq("¿Qué onda con Trump?","Donald Trump");
        eq("¿Cómo salió San Lorenzo?","San Lorenzo futbol resultado");
        eq("noticias de hoy","Argentina noticias");
        eq("¿Qué pasó en Alaska?","Alaska");
        eq("noticias sobre Alaska","Alaska");
        eq("¿Cómo está el clima en Alaska?",null);
        eq("Cuánto es 200 más 88",null);
        eq("Cómo está el clima hoy",null);
        eq("Ir al Obelisco",null);
        System.out.println("PASS live topic routing");
    }
}