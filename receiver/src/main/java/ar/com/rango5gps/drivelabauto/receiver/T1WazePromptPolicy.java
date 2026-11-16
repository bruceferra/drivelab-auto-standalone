package ar.com.rango5gps.drivelabauto.receiver;

import java.text.Normalizer;
import java.util.Locale;

/** Pure Waze prompt-label recognition: no screen position guesses or clicks.
 * Supports localized continue-trip and dismissal prompts as reported on T1.
 */
final class T1WazePromptPolicy {
    private T1WazePromptPolicy(){}
    static String normalize(String input){
        if(input==null)return "";
        String t=Normalizer.normalize(input,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        return t.replaceAll("[^\\p{L}\\p{N}]+"," ").trim()
            .replaceAll("\\s+"," ");
    }
    static boolean question(String label){
        String s=normalize(label);
        return s.equals("pase requerido")||
            s.contains("pase requerido")||
            s.contains("telepase requerido")||
            s.contains("se requiere un pase")||
            s.contains("toll pass required")||
            s.contains("pass required")||
            s.startsWith("aun vas ")||
            s.startsWith("todavia vas ")||
            s.startsWith("seguis yendo ")||
            s.startsWith("vas al ")||
            s.startsWith("vas a la ")||
            s.contains("continue driving")||
            s.startsWith("still going ")||
            s.startsWith("are you still going ")||
            s.startsWith("do you still want to");
    }
    static boolean continueTrip(String label){
        String s=normalize(label);
        return s.equals("continuar viaje")||
            s.equals("continuar")||
            s.equals("seguir viaje")||
            s.equals("seguir viajando")||
            s.equals("seguir ruta")||
            s.equals("continuar ruta")||
            s.equals("reanudar viaje")||
            s.equals("reanudar")||
            s.equals("retomar viaje")||
            s.equals("continue trip")||
            s.equals("continue drive")||
            s.equals("resume trip")||
            s.equals("resume")||
            s.equals("keep driving")||
            s.equals("continue");
    }
    static boolean dismiss(String label){
        String s=normalize(label);
        return s.equals("omitir pase")||
            s.equals("sin pase")||
            s.equals("continuar sin pase")||
            s.equals("no agregar pase")||
            s.equals("cerrar aviso")||
            s.equals("no gracias")||
            s.equals("no grabar")||
            s.equals("ahora no")||
            s.equals("no")||
            s.equals("cancelar")||
            s.equals("descartar")||
            s.equals("volver")||
            s.equals("cerrar")||
            s.equals("not now")||
            s.equals("no thanks")||
            s.equals("cancel")||
            s.equals("dismiss")||
            s.equals("skip")||
            s.equals("back");
    }
    static boolean otherAction(String label){
        String s=normalize(label);
        return s.equals("agregar pase")||
            s.equals("anadir pase")||
            s.equals("configurar pase")||
            s.equals("agregar telepase")||
            s.equals("anadir telepase")||
            s.equals("add pass")||
            s.equals("manage passes")||
            s.equals("aceptar")||s.equals("confirmar")||
            s.equals("si")||s.equals("entendido")||
            s.equals("listo")||s.equals("continuar navegando")||
            s.equals("iniciar")||s.equals("guardar")||
            s.equals("seguir")||s.equals("confirm")||
            s.equals("accept")||s.equals("yes")||
            s.equals("done")||s.equals("save")||
            s.equals("start")||s.equals("ok");
    }
    static boolean action(String label){
        return continueTrip(label)||dismiss(label)||otherAction(label);
    }
}
