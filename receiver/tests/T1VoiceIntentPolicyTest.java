package ar.com.rango5gps.drivelabauto.receiver;

public final class T1VoiceIntentPolicyTest {
    private static void route(String input,String destination){
        String actual=T1VoiceIntentPolicy.routeDestination(input);
        if(!destination.equals(actual))
            throw new AssertionError(input+" expected="+destination+" actual="+actual);
    }
    private static void question(String input){
        String actual=T1VoiceIntentPolicy.routeDestination(input);
        if(actual!=null)
            throw new AssertionError("False Waze match: "+input+" => "+actual);
    }
    public static void main(String[] args){
        route("navegá a Plaza de Mayo","Plaza de Mayo");
        route("navegar a Mar del Plata","Mar del Plata");
        route("ir a San Isidro","San Isidro");
        route("llevame hasta el supermercado","el supermercado");
        route("cómo llegar a la estación","la estación");
        route("Por favor, indicaciones hacia el aeropuerto?","el aeropuerto");
        route("quiero ir al trabajo","trabajo");
        route("Ruta a Avenida Libertador 1000","Avenida Libertador 1000");
        question("¿cuánto es 37 por 12?");
        question("contame las noticias de hoy");
        question("explicame cómo ir a Estados Unidos");
        question("¿cómo voy a hacer una cuenta?");
        question("navegar a ");
        question("Hola Jetour");
        question("");
        System.out.println("OK: 8 Waze routes, 7 AI/non-navigation utterances.");
    }
}