package ar.com.rango5gps.drivelabauto.receiver;

public final class T1JourneyMetricsTest {
    private static void check(boolean yes,String message) {
        if(!yes)throw new AssertionError(message);
    }
    private static void close(double a,double b,String msg){
        check(Math.abs(a-b)<1e-7,msg+": "+a+" != "+b);
    }
    public static void main(String[] args){
        // 50 km real trip: 99 OEM callbacks, raw fuel 495, electricity -158.
        T1JourneyMetrics.Checkpoint start=new T1JourneyMetrics.Checkpoint(
            "20261111_183400",1000,0,0,0,0,0);
        T1JourneyMetrics.Checkpoint end=new T1JourneyMetrics.Checkpoint(
            "20261111_183400",2000,52.69891,495,-158,99,99);
        T1JourneyMetrics.Result total=T1JourneyMetrics.between(start,end,2129);
        close(total.km,52.69891,"distance raw");
        close(total.fuelLitersCandidate,2.475,"hypothetical fuel");
        close(total.electricNetKwhCandidate,-0.79,"net signed electricity");
        close(total.fuelCostCandidate,2.475*2129,"fuel only partial cost");
        check(total.paired&&total.pairedEvents==99,"paired all callbacks");
        // Child check-point across 20 new intervals. Snapshot differences are
        // not re-summed from the entire long trip or 50-km moving average.
        T1JourneyMetrics.Checkpoint mid=new T1JourneyMetrics.Checkpoint(
            "20261111_183400",1500,11.5,100,-30,20,20);
        T1JourneyMetrics.Result child=T1JourneyMetrics.between(mid,end,2129);
        close(child.fuelLitersCandidate,1.975,"independent passenger trip");
        close(child.electricNetKwhCandidate,-0.64,"child electric net");
        check(child.fuelEvents==79,"child callback counts");
        T1JourneyMetrics.Result empty=T1JourneyMetrics.between(mid,mid,2129);
        check(!empty.fuelAvailable&&Double.isNaN(empty.fuelCostCandidate),
            "no callback means unknown, not 0 litres");
        boolean blocked=false;
        try{T1JourneyMetrics.between(start,new T1JourneyMetrics.Checkpoint(
            "20261111_200000",2100,60,500,-160,101,101),2129);}
        catch(IllegalArgumentException expected){blocked=true;}
        check(blocked,"never join different master trip IDs");
        blocked=false;
        try{T1JourneyMetrics.between(end,start,2129);}
        catch(IllegalArgumentException expected){blocked=true;}
        check(blocked,"negative distance not accepted");
        System.out.println("OK: passenger ledger math, signed energy, gaps and trip isolation");
    }
}
