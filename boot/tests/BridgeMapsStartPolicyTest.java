package ar.com.rango5gps.drivelabauto.boot;
public final class BridgeMapsStartPolicyTest {
    private static void expect(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }
    public static void main(String[] args){
        for(String label:new String[]{"Iniciar","Iniciar navegación",
            "  INICIAR NAVEGACIÓN  ","Comenzar navegación",
            "Start navigation","Start driving"}){
            expect(BridgeMapsStartPolicy.explicitStart(label),
                "Exact Google Maps preview start: "+label);
        }
        for(String label:new String[]{"","Continuar","Aceptar","Sí",
            "Iniciar sesión","Start","Cancelar","Salir de navegación",
            "Detener navegación","Cerrar ruta","Iniciar nueva conversación",
            "No grabar","Telepase requerido"}){
            expect(!BridgeMapsStartPolicy.explicitStart(label),
                "Must NEVER auto-click generic or stop/permission action: "+label);
        }
        System.out.println("PASS exact Google Maps route-preview start-only policy");
    }
}
