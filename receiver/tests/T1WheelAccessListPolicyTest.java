package ar.com.rango5gps.drivelabauto.receiver;

/** Pure tests for safe automatic restoration of a PREVIOUSLY authorized
 * Accessibility service. OEM/T1Launcher entries must survive byte-for-byte.
 */
public final class T1WheelAccessListPolicyTest {
    static void ok(boolean cond,String why){if(!cond)throw new AssertionError(why);}
    public static void main(String[] args){
        String a="com.t1launcher/.cluster.T1MapaServicio";
        String b="com.t1launcher/.gestos.GestosService";
        String own=T1WheelAccessListPolicy.TARGET;
        String original=a+":"+b+":"+own;
        String without=T1WheelAccessListPolicy.withoutOurService(original);
        ok((a+":"+b).equals(without),"Preserve both OEM accessibility services");
        ok(T1WheelAccessListPolicy.command(original).equals(
            "settings put secure enabled_accessibility_services "+original),
            "Restore exact original list without extra commands");
        ok(T1WheelAccessListPolicy.withoutOurService(
            own+":"+a+":"+b).equals(a+":"+b),
            "Original order is preserved when our entry is first");
        ok(T1WheelAccessListPolicy.withoutOurService(
            a+":"+own+":"+b).equals(a+":"+b),
            "Original order is preserved when our entry is in middle");
        String full="ar.com.rango5gps.drivelabauto.receiver/"+
            "ar.com.rango5gps.drivelabauto.receiver.T1WazeWheelService";
        ok(T1WheelAccessListPolicy.withoutOurService(a+":"+full).equals(a),
            "Full qualified Android service alias accepted");
        ok(T1WheelAccessListPolicy.withoutOurService(a+":"+b)==null,
            "Never enable an unapproved service for the first time");
        ok(T1WheelAccessListPolicy.withoutOurService(a+":"+own+":"+own)==null,
            "Never silently handle ambiguous duplicate component");
        ok(T1WheelAccessListPolicy.withoutOurService(own)==null,
            "Never disable only user Accessibility service, leaving empty list");
        ok(T1WheelAccessListPolicy.withoutOurService(
            a+":"+own+":com.example/Service;rm -rf /")==null,
            "Reject shell metacharacters");
        ok(T1WheelAccessListPolicy.withoutOurService(
            a+":"+own+":"+a+" && input keyevent 4")==null,
            "Reject shell operators");
        for(String unsafe:new String[]{"","foo bar","foo;bar",
              "foo|bar","foo&bar","foo'bar","foo\nbar"}){
            boolean rejected=false;
            try{T1WheelAccessListPolicy.command(unsafe);}
            catch(IllegalArgumentException expected){rejected=true;}
            ok(rejected,"Unsafe component list must be rejected: "+unsafe);
        }
        System.out.println("PASS exact prior Accessibility consent; preserve T1Launcher and reject unsafe values");
    }
}
