package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Boot wheel-focus source regression. Does not emulate the Jetour ROM. */
public final class T1WheelIgnitionFocusRestoreSourceTest {
    static String read(String p)throws Exception{return Files.readString(
        Path.of("receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",p));}
    static void must(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        String boot=read("ReceiverService.java");
        String wheel=read("T1WheelAdbFallback.java");
        String oem=read("T1WazeWheelService.java");
        String resize=read("T1ClusterAdbResize.java");
        int route=boot.indexOf("if(currentAt==requestedAt){");
        int rebind=boot.indexOf("T1OemMicrophoneService.ensureRunning(this)",route);
        int a11y=boot.indexOf("T1WazeWheelService.requestClusterReady(this)",rebind);
        int focus=boot.indexOf("T1WheelAdbFallback.restoreFocusAfterBoot(this)",a11y);
        must(route>=0&&rebind>route&&a11y>rebind&&focus>a11y,
            "Routes may be superseded; wheel must always recover after map launch");
        must(!boot.contains("if(currentAt!=requestedAt)return;"),
            "A new route during boot must not skip wheel recovery");
        int init=wheel.indexOf("static void restoreFocusAfterBoot(Context context){");
        int next=wheel.indexOf("static void request(Context context,int oemCode){",init);
        must(init>=0&&next>init,"Separate boot path must exist");
        String method=wheel.substring(init,next);
        must(method.contains("T1ClusterWazeRouter.autoBoot(app)")&&
            method.contains("T1AdbTaskParser.unique(tasks,id,pkg)")&&
            method.contains("T1WheelAdbFocusPolicy.mayFocusMap(input,id,pkg)")&&
            method.contains("T1WheelAdbFocusPolicy.focusedOn(input,id,pkg)")&&
            method.contains("T1WheelAdbFocusPolicy.focusCommand(map.id)")&&
            method.contains("oem_wheel_event_at")&&
            oem.contains("putLong(\"oem_wheel_event_at\",System.currentTimeMillis())"),
            "Boot focus requires a verified map task and avoids interfering with manual wheel");
        must(!method.contains("T1WheelAdbFocusPolicy.command(")&&
            !method.contains("input dpad")&&
            !method.contains("am task resize")&&
            !method.contains("am stack remove")&&
            !method.contains("dispatchGesture")&&
            !method.contains("startActivity(")&&
            !method.contains("parkedForWheel")&&
            !method.contains("gear()")&&
            !method.contains("speedKmh()"),
            "Boot focus is only a quiet task-focus request, no key/touch/geometry/OEM changes");
        must(resize.contains("if(!parked(app))")&&
             resize.contains("oem.gear()==1")&&
             resize.contains("oem.speedKmh()"),
             "Window geometry remains parked-only");
        System.out.println("PASS boot wheel recovery even with newer Sender route; guarded focused app, no input injection");
    }
}
