package ar.com.rango5gps.drivelabauto.receiver;

/** Exhaustive trip-button state combinations, independent of Android. */
public final class T1TripActionPolicyTest {
    static void eq(boolean value,boolean expected,String label){
        if(value!=expected)throw new AssertionError(label+
            ": expected="+expected+", actual="+value);
    }
    static void test(boolean paused,boolean active,boolean live,
                     boolean canStart,boolean canResume){
        T1TripActionPolicy.State s=T1TripActionPolicy.resolve(
            paused,active,live,canStart,canResume);
        eq(s.start,!paused&&!active&&canStart,"start");
        eq(s.pause,!paused&&active&&live,"pause");
        eq(s.resume,paused&&canResume,"resume");
        eq(s.end,paused||active,"end");
        if(s.start&&(s.pause||s.resume||s.end))
            throw new AssertionError("START conflicts with other actions");
        if(s.pause&&s.resume)
            throw new AssertionError("PAUSE and RESUME cannot coexist");
    }
    public static void main(String[] args){
        int cases=0;
        for(int bits=0;bits<32;bits++){
            test((bits&1)!=0,(bits&2)!=0,(bits&4)!=0,
                (bits&8)!=0,(bits&16)!=0);
            cases++;
        }
        System.out.println("Trip action policy: "+cases+" combinations OK");
    }
}
