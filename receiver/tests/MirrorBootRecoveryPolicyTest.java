package ar.com.rango5gps.drivelabauto.receiver;
public final class MirrorBootRecoveryPolicyTest {
    static void ok(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }
    public static void main(String[] args){
        ok(MirrorBootRecoveryPolicy.recoverable(8,8,false),"saved eight pulses");
        ok(MirrorBootRecoveryPolicy.recoverable(0,3,false),"one side");
        ok(!MirrorBootRecoveryPolicy.recoverable(8,8,true),"inflight is unsafe");
        ok(!MirrorBootRecoveryPolicy.recoverable(0,0,false),"no blind movement");
        ok(!MirrorBootRecoveryPolicy.recoverable(-1,8,false),"invalid negative");
        ok(!MirrorBootRecoveryPolicy.recoverable(21,1,false),"out of range");
        ok(MirrorBootRecoveryPolicy.parkedForRestore(1,0),"P stopped");
        ok(!MirrorBootRecoveryPolicy.parkedForRestore(2,0),"not reverse");
        ok(!MirrorBootRecoveryPolicy.parkedForRestore(4,0),"not drive");
        ok(!MirrorBootRecoveryPolicy.parkedForRestore(1,2),"not moving");
        ok(!MirrorBootRecoveryPolicy.parkedForRestore(1,Double.NaN),"no reading");
        System.out.println("PASS safe boot return only for logged completed mirror pulses");
    }
}
