package ar.com.rango5gps.drivelabauto.boot;
public final class BridgeWheelPolicyTest {
    private static void expect(int actual,int expected,String note){
        if(actual!=expected)throw new AssertionError(note+" "+actual+" != "+expected);
    }
    public static void main(String[] args){
        expect(BridgeWheelPolicy.translate(1000,100,false),BridgeWheelPolicy.NEXT,"first notch");
        expect(BridgeWheelPolicy.translate(1001,100,false),BridgeWheelPolicy.PREVIOUS,"second notch");
        expect(BridgeWheelPolicy.translate(1002,100,false),BridgeWheelPolicy.CANCEL,"back");
        expect(BridgeWheelPolicy.translate(1003,100,false),BridgeWheelPolicy.CONFIRM,"ok");
        expect(BridgeWheelPolicy.translate(1000,101,false),BridgeWheelPolicy.NONE,"release ignored");
        expect(BridgeWheelPolicy.translate(1010,100,false),BridgeWheelPolicy.NONE,"voice untouched");
        expect(BridgeWheelPolicy.translate(2200,100,false),BridgeWheelPolicy.NONE,"MODE untouched");
        expect(BridgeWheelPolicy.translate(1000,100,true),BridgeWheelPolicy.PREVIOUS,"reverse option navigation");
        expect(BridgeWheelPolicy.translate(1001,100,true),BridgeWheelPolicy.NEXT,"reverse option navigation");
        System.out.println("PASS independent Bridge wheel: OEM key filter and press/release semantics");
    }
}
