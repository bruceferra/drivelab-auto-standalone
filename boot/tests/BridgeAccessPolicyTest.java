package ar.com.rango5gps.drivelabauto.boot;

/** Pure JVM: no accessibility write may lose T1Launcher or OEM entries. */
public final class BridgeAccessPolicyTest {
    private static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        String t1="com.t1launcher/.cluster.T1MapaServicio";
        String other="com.jetour.access/.ExistingService";
        String rx="ar.com.rango5gps.drivelabauto.receiver/.T1WazeWheelService";
        String old=t1+":"+rx+":"+other;
        String added=BridgeAccessPolicy.addOwn(old);
        ok((old+":"+BridgeAccessPolicy.FULL).equals(added),
            "First owner approval must preserve every previous service and order");
        ok(added.equals(BridgeAccessPolicy.addOwn(added)),
            "Idempotent; never create a duplicate");
        ok(old.equals(BridgeAccessPolicy.removeOwn(added)),
            "Only Bridge LAB can be removed; other entries survive");
        ok(BridgeAccessPolicy.removeOwn(old)==null,
            "Never remove a service not in Android settings");
        ok(BridgeAccessPolicy.removeOwn(BridgeAccessPolicy.FULL)==null,
            "Never disable last enabled Android accessibility service");
        ok((BridgeAccessPolicy.FULL).equals(BridgeAccessPolicy.addOwn("")),
            "Explicit owner-approved first grant uses exact class from manifest");
        ok(BridgeAccessPolicy.addOwn(added+":"+BridgeAccessPolicy.FULL)==null,
            "Reject duplicated own component");
        ok(BridgeAccessPolicy.enabled(added),"Full component must be recognized");
        ok(BridgeAccessPolicy.enabled(old+":"+BridgeAccessPolicy.SHORT),
            "Short Android alias may be read if supplied by OEM");
        ok(!BridgeAccessPolicy.enabled(old),"Other accessibility apps never imply our grant");
        for(String unsafe:new String[]{"bad;root","foo bar","abc|evil","a/b:c\nd",
                "a/b:a/b:",":a/b","a/b:c/d && pm clear","a/b:/x"}){
            ok(BridgeAccessPolicy.addOwn(unsafe)==null,
                "Reject malformed or shell metacharacters: "+unsafe);
        }
        String command=BridgeAccessPolicy.command(added);
        ok(command.equals("settings put secure enabled_accessibility_services "+added),
            "Exact safe ADB write, no generic shell");
        System.out.println("PASS exact LAB accessibility policy preserves OEM, Receiver and T1Launcher");
    }
}
