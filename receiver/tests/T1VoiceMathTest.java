package ar.com.rango5gps.drivelabauto.receiver;
public final class T1VoiceMathTest {
    static void expect(String phrase,String expected){
        String got=T1VoiceMath.calculate(phrase);
        if(expected==null?got!=null:!expected.equals(got))
            throw new AssertionError(phrase+" => "+got+" esperaba "+expected);
    }
    public static void main(String[] args){
        expect("doscientos más ochenta y ocho","El resultado es 288.");
        expect("¿Cuánto es 200 más 88?","El resultado es 288.");
        expect("treinta y siete por doce","El resultado es 444.");
        expect("275 dividido por 5","El resultado es 55.");
        expect("cuánto es 22 menos 12","El resultado es 10.");
        expect("cuánto es 10 dividido por 0","No se puede dividir por cero.");
        expect("navegá a el Obelisco",null);
        expect("cómo salió San Lorenzo",null);
        expect("qué onda con Trump",null);
        expect("¿cómo es el clima hoy?",null);
        expect("dos por Plaza de Mayo",null);
        System.out.println("PASS voice math and non-calculation boundaries");
    }
}