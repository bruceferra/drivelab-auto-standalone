import ar.com.rango5gps.drivelabauto.receiver.RecirculationPolicy;
public final class RecirculationPolicyTest {
    public static void main(String[] args){
        if(!RecirculationPolicy.valid(1)||!RecirculationPolicy.valid(2)||
                RecirculationPolicy.valid(0))throw new AssertionError("1004 states");
        if(RecirculationPolicy.needsActivation(1)||!RecirculationPolicy.needsActivation(2))
            throw new AssertionError("Recirculation polarity inverted");
        if(!RecirculationPolicy.describe(1).contains("ENCENDIDA"))
            throw new AssertionError("1004=1 must recirculate interior");
        try{
            RecirculationPolicy.needsActivation(2101);
            throw new AssertionError("2101 cannot be used as 1004 command");
        }catch(IllegalArgumentException expected){}
        System.out.println("PASS recirc 1004=1 interior, 2 exterior, 2101 read only");
    }
}
