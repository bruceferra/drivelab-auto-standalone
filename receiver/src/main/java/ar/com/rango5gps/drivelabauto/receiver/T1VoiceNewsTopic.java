package ar.com.rango5gps.drivelabauto.receiver;

/** Pure-JVM topic detector; not a score provider. */
final class T1VoiceNewsTopic {
    private T1VoiceNewsTopic(){}
    static String topic(String phrase){
        if(phrase==null)return null;
        String s=T1VoiceMath.normalize(phrase);
        if(s.matches(".*\\b(?:trump|presidente de estados unidos)\\b.*"))
            return "Donald Trump";
        if(s.matches(".*\\b(?:san lorenzo|ciclon)\\b.*"))
            return "San Lorenzo futbol resultado";
        if(s.matches(".*\\b(?:noticias|actualidad|titulares|que paso hoy)\\b.*"))
            return "Argentina noticias";
        return null;
    }
}