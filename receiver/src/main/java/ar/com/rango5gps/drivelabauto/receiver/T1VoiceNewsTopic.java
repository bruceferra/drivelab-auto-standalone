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
        // Narrow, explicit public-topic news questions: "¿Qué pasó en Alaska?"
        // Search headlines only; do NOT pretend this is a Gemini conversation.
        if(s.matches("^(?:que paso|que esta pasando|que ocurre) en [a-z ]{3,48}$")){
            String place=s.substring(s.indexOf(" en ")+4).trim();
            if(place.length()>=3&&!place.matches("(?i).*(?:mi casa|mi trabajo|mi calle).*"))
                return Character.toUpperCase(place.charAt(0))+place.substring(1);
        }
        if(s.equals("noticias de hoy")||s.equals("noticias sobre hoy"))
            return "Argentina noticias";
        if(s.matches("^noticias (?:de|sobre) [a-z ]{3,48}$")){
            String subject=s.replaceFirst("^noticias (?:de|sobre) ","").trim();
            return Character.toUpperCase(subject.charAt(0))+subject.substring(1);
        }
        if(s.matches(".*\\b(?:noticias|actualidad|titulares|que paso hoy)\\b.*"))
            return "Argentina noticias";
        return null;
    }
}