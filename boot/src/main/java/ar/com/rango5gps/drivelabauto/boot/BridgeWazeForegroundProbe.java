package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;

/** Strictly manual diagnostic for owner-observed Radio/Bluetooth overlay.
 * May raise only the already-running Waze task within its OWN Display1
 * root stack. Does not change stack/display ownership, touch OEM surfaces,
 * alter steering controls or execute anything on ignition or route receipt.
 */
final class BridgeWazeForegroundProbe {
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static final String STATUS="waze_foreground_probe_status";
    private BridgeWazeForegroundProbe(){}

    static String status(Context c){
        return BridgeStandaloneWindows.prefs(c).getString(
            STATUS,"Sin pruebas ADB del orden de Waze");
    }
    private static void record(Context c,String value){
        BridgeStandaloneWindows.prefs(c).edit()
            .putString(STATUS,value).apply();
    }
    static void ask(Activity owner){
        if(BUSY.get()){
            show(owner,"PRUEBA EN CURSO","Bridge todavía inspecciona Display 1.");
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle("PRUEBA ADB · WAZE ENCIMA EN DISPLAY 1")
            .setMessage("SOLO CON LA JETOUR ESTACIONADA.\n\n"+
                "Bridge consultará por ADB local el Stack ID y Task ID "+
                "actuales de Waze. Si Waze está SOLO en su propia pila "+
                "de Display 1, pedirá llevarlo a la parte superior "+
                "DE ESA MISMA PILA.\n\n"+
                "NO cerrará Radio/Bluetooth, no modificará capas OEM, "+
                "no tocará el volante, no moverá Waze de pantalla, "+
                "no eliminará datos.\n\n"+
                "Si el stack contiene tareas del auto, no está "+
                "identificado o Android lo rechaza, no se ejecutará "+
                "ningún cambio. No garantiza cubrir capas Jetour.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("INSPECCIONAR WAZE",(d,w)->inspect(owner))
            .show();
    }
    private static void inspect(Activity owner){
        if(!BUSY.compareAndSet(false,true))return;
        Context app=owner.getApplicationContext();
        record(app,"ADB local: identificando la pila de Waze en Display 1");
        new Thread(()->{
            try{
                String report=BridgeLocalAdb.shell(app,"am stack list");
                BridgeClusterRecoveryPolicy.Task match=
                    BridgeWazeForegroundPolicy.uniqueWaze(report);
                if(match==null){
                    String message="SIN CAMBIOS: no hay una tarea Waze "+
                        "única y aislada en una pila verificada de "+
                        "Display 1. El diagnóstico conserva el tablero.";
                    record(app,message);
                    show(owner,"NO SE PUEDE PROBAR",message);
                    return;
                }
                owner.runOnUiThread(()->{
                    if(owner.isFinishing()||owner.isDestroyed())return;
                    new AlertDialog.Builder(owner)
                        .setTitle("WAZE IDENTIFICADO · CONFIRMAR")
                        .setMessage("Display: 1\nPaquete: com.waze"+
                            "\nTASK_ID: "+match.taskId+
                            "\nSTACK_ID: "+match.stackId+
                            "\n\nSe solicitará subir ÚNICAMENTE ese "+
                            "Task ID al frente de su propia pila. "+
                            "Android puede conservar Radio/Bluetooth "+
                            "por encima si es una superficie OEM. "+
                            "¿Ejecutar UNA vez?")
                        .setNegativeButton("NO TOCAR",null)
                        .setPositiveButton("PROBAR WAZE AL FRENTE",
                            (d,w)->execute(owner,match))
                        .show();
                });
            }catch(Exception error){
                String message="NO SE MODIFICÓ DISPLAY 1: "+
                    BridgeLocalAdb.hint(error);
                record(app,message);
                show(owner,"FALLÓ INSPECCIÓN ADB",message);
            }finally{BUSY.set(false);}
        },"Bridge-Waze-Display1-Inspect-Only").start();
    }
    private static void execute(Activity owner,
            BridgeClusterRecoveryPolicy.Task expected){
        if(!BUSY.compareAndSet(false,true))return;
        Context app=owner.getApplicationContext();
        record(app,"Comprobando nuevamente el Task ID de Waze");
        new Thread(()->{
            try{
                // This method RE-READS am stack list inside ADB and
                // refuses to run if stack ID, task ID or package changed.
                String output=BridgeLocalAdb.bringVerifiedWazeToFront(
                    app,expected);
                String normalized=output.toLowerCase(java.util.Locale.ROOT);
                if(normalized.matches("(?s).*(error|denied|exception|unknown|not allowed).*"))
                    throw new SecurityException(
                        "Android rechazó move-task: "+
                        (output.length()>160?output.substring(0,160):output));
                String after=BridgeLocalAdb.shell(app,"am stack list");
                BridgeClusterRecoveryPolicy.Task found=
                    BridgeWazeForegroundPolicy.uniqueWaze(after);
                if(!BridgeWazeForegroundPolicy.same(expected,found))
                    throw new IllegalStateException(
                        "Waze cambió de tarea/pila tras el comando");
                String result="ANDROID RECIBIÓ move-task de Waze "+
                    "(tarea "+expected.taskId+", pila "+expected.stackId+
                    ") en Display 1. Verificado que Waze permanece "+
                    "en esa pila. NO es prueba de píxeles ni de "+
                    "que desapareció Radio/Bluetooth: comprobalo "+
                    "en el tablero estacionado.";
                record(app,result);
                show(owner,"COMANDO ENVIADO · COMPROBAR TABLERO",result);
            }catch(Exception error){
                String result="PRUEBA SIN CONFIRMACIÓN: "+
                    BridgeLocalAdb.hint(error)+
                    ". No se ordenó cerrar Radio/Bluetooth ni "+
                    "cambiar el volante.";
                record(app,result);
                show(owner,"RESULTADO ADB",result);
            }finally{BUSY.set(false);}
        },"Bridge-Waze-Display1-Move-Own-Task").start();
    }
    private static void show(Activity owner,String title,String message){
        owner.runOnUiThread(()->{
            if(owner.isFinishing()||owner.isDestroyed())return;
            new AlertDialog.Builder(owner).setTitle(title)
                .setMessage(message)
                .setPositiveButton("ENTENDIDO",null).show();
        });
    }
}
