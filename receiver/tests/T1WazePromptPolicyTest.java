package ar.com.rango5gps.drivelabauto.receiver;

/** Test localized Waze continuation prompt targets as reported by T1 owner. */
public final class T1WazePromptPolicyTest {
    private static int checks;
    private static void yes(boolean condition,String message){
        checks++;if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        yes(T1WazePromptPolicy.question("¿Aún vas al Obelisco?"),"spanish question");
        yes(T1WazePromptPolicy.question("¿Todavía vas a la estación?"),"spanish feminine question");
        yes(T1WazePromptPolicy.question("Are you still going home?"),"English question");
        yes(T1WazePromptPolicy.continueTrip("Continuar viaje"),"resume trip");
        yes(T1WazePromptPolicy.continueTrip("Retomar viaje"),"resume alternative");
        yes(T1WazePromptPolicy.dismiss("No, gracias"),"dismiss comma");
        yes(T1WazePromptPolicy.dismiss("No grabar"),"dismiss recorded phrase");
        yes(T1WazePromptPolicy.dismiss("NOT NOW"),"dismiss English");
        yes(T1WazePromptPolicy.action("Confirmar"),"confirmation");
        yes(!T1WazePromptPolicy.action("Aún vas al Obelisco"),"question not clickable choice");
        yes(!T1WazePromptPolicy.action("Mapa de Buenos Aires"),"map title not clickable");
        yes(!T1WazePromptPolicy.continueTrip("Reanudar actualización de vehículo"),"no fuzzy untrusted action");
        yes(!T1WazePromptPolicy.dismiss("Cerrar todas las ventanas"),"no broad back action");
        System.out.println("PASS "+checks+" Waze prompt labels including Obelisco, continuar and no-gracias");
    }
}
