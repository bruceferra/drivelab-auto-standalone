package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static safety regression for T1 OEM hard-key interoperability.
 * Owner provided T1Launcher T1N134 (SHA-256 6765a8...) had:
 * IHardKeyService transaction 1/2 with IBinder callback;
 * IHardKeyCallback transaction 3, three ints; key 1000..1003.
 * This test does NOT claim that the vendor service accepts a second client.
 */
public final class T1WheelOemBinderSourceContractTest {
    private static void check(boolean b,String message){
        if(!b)throw new AssertionError(message);
    }
    private static String source(String file)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",file));
    }
    public static void main(String[] args)throws Exception{
        String bridge=source("T1OemHardKeyBridge.java");
        String svc=source("T1WazeWheelService.java");
        String controls=source("T1WheelSettingsActivity.java");
        String manifest=Files.readString(
            Path.of("receiver/src/main/AndroidManifest.xml"));
        String xml=Files.readString(
            Path.of("receiver/src/main/res/xml/waze_wheel_service.xml"));
        check(bridge.contains("com.autolink.hardkeyservice.core.ALHardKeyService")&&
            bridge.contains("com.autolink.adapterinterface.IHardKeyService")&&
            bridge.contains("com.autolink.adapterinterface.IHardKeyCallback")&&
            bridge.contains("REGISTER=1,UNREGISTER=2,EVENT=3"),
            "Use exact T1N134 observed OEM hard-key Binder ABI");
        check(bridge.contains("data.readInt(),action=data.readInt(),extra=data.readInt()")&&
            bridge.contains("if(key>=1000&&key<=1003)")&&
            bridge.contains("data.writeInterfaceToken(SERVER_TOKEN)")&&
            bridge.contains("data.writeStrongBinder(callback)")&&
            bridge.contains("call(UNREGISTER)")&&
            bridge.contains("unbindService(connection)"),
            "Subscribe only four map keys and unregister/release Binder on exit");
        check(bridge.contains("void start()")&&bridge.contains("void stop()")&&
            !bridge.contains("BOOT_COMPLETED")&&
            !bridge.contains("transact(3,")&&
            !bridge.contains("new Thread(")&&
            !bridge.contains("Runtime.getRuntime()"),
            "No boot-only listener, no OEM control writes, no shell injection");
        check(svc.contains("getWindowsOnAllDisplays()")&&
            svc.contains("AccessibilityWindowInfo")&&
            svc.contains("WAZE.equals(")&&
            svc.contains("String.valueOf(root.getPackageName())")&&
            svc.contains("if(enabled&&wazeRoot()!=null)oemBridge.start();")&&
            svc.contains("else oemBridge.stop();"),
            "Waze-only opt-in service must bind only while a Waze window is visible on any display");
        check(svc.contains("case 1000:mapped=KeyEvent.KEYCODE_DPAD_RIGHT")&&
            svc.contains("case 1001:mapped=KeyEvent.KEYCODE_DPAD_LEFT")&&
            svc.contains("case 1002:mapped=KeyEvent.KEYCODE_BACK")&&
            svc.contains("case 1003:mapped=KeyEvent.KEYCODE_DPAD_CENTER")&&
            svc.contains("if(action!=100&&action!=101)return;"),
            "T1N134 wheel rotate/back/OK codes mapped correctly without reacting to other signals");
        check(svc.contains("if(confirm&&mapMenuArmed&&collectMapChoices(root))")&&
            svc.contains("mapMenuArmed=true;")&&
            svc.contains("performAction(AccessibilityNodeInfo.ACTION_CLICK)")&&
            !svc.contains("injectInputEvent(")&&
            !svc.contains("getevent -"),
            "Never click an arbitrary first map control or inject synthetic system input");
        check(controls.contains("CONECTAR / DESCONECTAR RUEDITA OEM")&&
            controls.contains("new android.app.AlertDialog.Builder(this)")&&
            controls.contains("OEM_BINDER_ENABLED,true")&&
            controls.contains("OEM_BINDER_ENABLED,false")&&
            controls.contains("UN solo receptor")&&
            controls.contains("únicamente con el auto detenido en P.")==false&&
            controls.contains("auto detenido en P"),
            "OEM Binder requires explicit user consent and explains possible OEM exclusivity");
        check(xml.contains("flagRetrieveInteractiveWindows")&&
            xml.contains("android:canRetrieveWindowContent=\"true\"")&&
            manifest.contains("<package android:name=\"com.autolink.hardkeyservice\" />"),
            "Enable multi-display Waze accessibility and Android 11 visibility of OEM binder service");
        System.out.println("PASS OEM wheel Binder ABI, explicit opt-in, Waze display 2 and safe no-blind-click controls");
    }
}
