package ar.com.rango5gps.drivelabauto.receiver;
public final class T1VoiceSmallTalkTest {
    private static void expect(String phrase,boolean recognized){
        boolean actual=T1VoiceSmallTalk.answer(phrase)!=null;
        if(actual!=recognized)throw new AssertionError(phrase+" greeting? "+actual);
    }
    public static void main(String[] args){
        expect("Hola, ¿cómo andás amigazo?",true);
        expect("Hola amigo",true);
        expect("¿Cómo estás?",true);
        expect("Qué clima hay hoy",false);
        expect("Llevame a Ezeiza",false);
        expect("400 más 200",false);
        expect("Qué pasó en Alaska",false);
        System.out.println("PASS small talk routing");
    }
}
