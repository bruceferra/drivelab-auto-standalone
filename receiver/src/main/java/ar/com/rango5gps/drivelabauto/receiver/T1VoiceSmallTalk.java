package ar.com.rango5gps.drivelabauto.receiver;

/**
 * A tiny, deterministic greeting handler. No generative AI or remote service:
 * small talk should respond politely instead of going to the "not connected" path.
 */
final class T1VoiceSmallTalk {
    private T1VoiceSmallTalk(){}
    static String answer(String phrase){
        if(phrase==null)return null;
        String s=T1VoiceMath.normalize(phrase)
            .replaceAll("[.,;:]"," ").replaceAll("\\s+"," ").trim();
        if(s.matches("^(?:hola|holaa|buenas|buen dia|buenos dias|buenas tardes|"+
                "buenas noches)(?: (?:como andas|como estas|que tal|amigo|"+
                "amigazo|che|todo bien))*$")||
            s.matches("^(?:como andas|como estas|todo bien)(?: (?:amigo|amigazo))?$"))
            return "¡Hola! Todo bien por acá. Decime a dónde querés ir "+
                "o preguntame por el clima, las noticias o una cuenta.";
        return null;
    }
}
