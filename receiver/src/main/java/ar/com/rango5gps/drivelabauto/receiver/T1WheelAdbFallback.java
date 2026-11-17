package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.Display;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Last-resort steering navigation on a ROM that renders Waze/Maps on Display 1
 * but omits BOTH app windows from Accessibility after ignition.
 *
 * Android 11's input -d N is display-scoped, NOT package-scoped; therefore
 * every single key requires all of:
 *   - the user's existing ADB cluster opt-in (no new permissions/root),
 *   - the selected non-primary display and enabled map panel,
 *   - ONE verifiable task for that map package ON the selected display,
 *   - a real hasFocus+canReceiveKeys window for the SAME app/display from
 *     InputDispatcher, immediately before the shell key.
 * Never use an ordinary input key without -d. No BACK, taps, coordinates,
 * OEM controls, unsolicited OK, task resizing, or window re-launch.
 * Recovery after a physical wheel turn may request am task focus only while
 * parked in OEM P/0 and with a verified target map window. The post-request
 * focused-window dump MUST confirm the change before any DPAD injection.
 * An ADB command finishing does NOT prove that Waze moved focus.
 */
final class T1WheelAdbFallback {
    static final String STATUS="adb_wheel_fallback_status";
    private static final String WAZE="com.waze";
    private static final String MAPS="com.google.android.apps.maps";
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final long OK_WINDOW_MS=10000L;
    private static volatile String armedPackage="";
    private static volatile int armedDisplay=-1;
    private static volatile long armedAt=0L;
    private T1WheelAdbFallback(){}

    static void request(Context context,int oemCode){
        final Context app=context.getApplicationContext();
        SharedPreferences prefs=app.getSharedPreferences(
            T1WazeWheelService.PREFS,Context.MODE_PRIVATE);
        if(!prefs.getBoolean(T1WazeWheelService.ENABLED,false)||
           !prefs.getBoolean(T1WazeWheelService.OEM_BINDER_ENABLED,false)||
           !T1ClusterAdbResize.enabled(app)||
           !T1ClusterWazeRouter.enabled(app))return;
        final Display display=T1ClusterWazeRouter.selected(app);
        final String pkg=T1WheelTarget.packageName(app);
        final int id=display==null?-1:display.getDisplayId();
        if(!T1WheelTarget.selectedPanelEnabled(app)||
           !T1WheelAdbFocusPolicy.supported(id,pkg))return;
        final int key;
        if(oemCode==1000)key=22; // DPAD_RIGHT
        else if(oemCode==1001)key=21; // DPAD_LEFT
        else if(oemCode==1003){
            if(!pkg.equals(armedPackage)||id!=armedDisplay||
               SystemClock.elapsedRealtime()-armedAt>OK_WINDOW_MS||
               SystemClock.elapsedRealtime()<armedAt){
                note(app,"ADB: OK bloqueado; primero girá y verificá "+
                    "visualmente qué botón quedó seleccionado en "+pkg);
                return;
            }
            key=23; // DPAD_CENTER: only AFTER deliberate wheel selection
        }else{
            note(app,"ADB: ATRÁS no se envía sin controles accesibles de Maps/Waze");
            return;
        }
        if(!BUSY.compareAndSet(false,true)){
            note(app,"ADB: tecla ignorada; anterior todavía verificándose");
            return;
        }
        new Thread(()->{
            try{
                // Re-evaluate configuration after the asynchronous handoff.
                Display fresh=T1ClusterWazeRouter.selected(app);
                if(fresh==null||fresh.getDisplayId()!=id||
                   !T1ClusterWazeRouter.enabled(app)||
                   !T1ClusterAdbResize.enabled(app)||
                   !T1WheelTarget.selectedPanelEnabled(app)||
                   !pkg.equals(T1WheelTarget.packageName(app)))
                    throw new IllegalStateException("Panel/display cambió; sin tecla");
                String tasks=T1LocalAdbClient.shell(app,"am stack list");
                T1AdbTaskParser.Task map=T1AdbTaskParser.unique(tasks,id,pkg);
                if(map==null){
                    // Android 11 Jetour may omit task metadata from stack list.
                    tasks=T1LocalAdbClient.shell(app,"dumpsys activity activities");
                    map=T1AdbTaskParser.unique(tasks,id,pkg);
                }
                if(map==null)throw new IllegalStateException(
                    "Waze/Maps sin tarea única comprobada en Display "+id);
                String input=T1LocalAdbClient.shell(app,"dumpsys input");
                if(!T1WheelAdbFocusPolicy.focusedOn(input,id,pkg)){
                    // User's real 2026-11-17 ignition trace: FocusedApplication
                    // was Waze, but FocusedWindow on Display 1 remained
                    // com.autolink.instrument while both freeform map windows
                    // were visible, key-capable and unfocused. An app/task
                    // without window focus cannot receive DPAD.
                    // Only a physical wheel ROTATION may restore task focus,
                    // only when OEM P and zero speed are confirmed, and only
                    // when the focus is currently OEM or another map; a
                    // dialog/other app can NEVER be displaced.
                    if(key==23||!T1WheelAdbFocusPolicy.mayFocusMap(input,id,pkg))
                        throw new IllegalStateException(
                            "Foco en OEM/u otra ventana; no se autoriza "+
                            "cambiarlo ni enviar tecla");
                    if(!T1ClusterAdbResize.parkedForWheel(app))
                        throw new IllegalStateException(
                            "Foco de mapa pendiente: solo recuperar en P y 0 km/h");
                    String focusReply=T1LocalAdbClient.shell(app,
                        T1WheelAdbFocusPolicy.focusCommand(map.id));
                    String feedback=focusReply==null?"":focusReply.toLowerCase(Locale.ROOT);
                    if(feedback.contains("error")||feedback.contains("denied")||
                       feedback.contains("exception")||feedback.contains("unknown"))
                        throw new IllegalStateException("Android rechazó enfocar mapa");
                    // Task focus is not window focus on the Jetour ROM;
                    // accept only hard InputDispatcher evidence AFTER request.
                    Thread.sleep(200L);
                    input=T1LocalAdbClient.shell(app,"dumpsys input");
                    if(!T1WheelAdbFocusPolicy.focusedOn(input,id,pkg))
                        throw new IllegalStateException(
                            "Android mantuvo com.autolink.instrument con foco; "+
                            "no se envió tecla al vehículo");
                }
                // Input -d scopes the event to the existing focused display.
                // Do not inject unless task, focus AND user panel still match.
                Display now=T1ClusterWazeRouter.selected(app);
                if(now==null||now.getDisplayId()!=id||
                   !pkg.equals(T1WheelTarget.packageName(app))||
                   !T1ClusterWazeRouter.enabled(app))
                    throw new IllegalStateException("Foco/panel cambió antes del envío");
                String reply=T1LocalAdbClient.shell(app,
                    T1WheelAdbFocusPolicy.command(id,key));
                String lower=reply==null?"":reply.toLowerCase(Locale.ROOT);
                if(lower.contains("error")||lower.contains("exception")||
                   lower.contains("denied")||lower.contains("unknown"))
                    throw new IllegalStateException("Android rechazó DPAD");
                if(key==21||key==22){
                    armedPackage=pkg;armedDisplay=id;
                    armedAt=SystemClock.elapsedRealtime();
                }else armedAt=0L;
                note(app,"ADB Display "+id+" · "+pkg+" · DPAD "+key+
                    " solicitado (verificar foco visual; no se tocó OEM)");
            }catch(Exception ex){
                if(key==23)armedAt=0L;
                note(app,"ADB NO ENVIÓ TECLA: "+
                    ex.getClass().getSimpleName()+
                    (ex.getMessage()==null?"":" · "+ex.getMessage()));
            }finally{
                BUSY.set(false);
            }
        },"T1-Wheel-Verified-Display-DPAD").start();
    }
    private static void note(Context app,String value){
        app.getSharedPreferences(T1WazeWheelService.PREFS,Context.MODE_PRIVATE)
            .edit().putString(STATUS,value).apply();
    }
}
