package ar.com.rango5gps.drivelabauto.receiver;
public final class T1EcoTripMetricsTest {
    static void check(boolean yes,String error){
        if(!yes)throw new AssertionError(error);
    }
    static void close(double actual,double expected,String error){
        if(Math.abs(actual-expected)>0.001)
            throw new AssertionError(error+": "+actual+" != "+expected);
    }
    public static void main(String[] unused){
        T1EcoTripMetrics m=new T1EcoTripMetrics();
        check(T1EcoTripMetrics.speedBin(0)==0,"0-5");
        check(T1EcoTripMetrics.speedBin(4.99)==0,"4.99 still 0-5");
        check(T1EcoTripMetrics.speedBin(5)==1,"5-10");
        check(T1EcoTripMetrics.speedBin(109.99)==21,"105-110");
        check(T1EcoTripMetrics.speedBin(110)==22,"110-115");
        check(T1EcoTripMetrics.speedBin(220)==40,"200 plus");
        check(T1EcoTripMetrics.speedBin(Double.NaN)==-1,"nan rejected");
        check(T1EcoTripMetrics.speedBin(-1)==-1,"negative rejected");
        check(Double.isNaN(m.ecoScore()),"no samples no rating");
        for(int i=0;i<30;i++)
            m.add(1600000000000L+i*5000L,.1,5,
                i<15?T1EcoTripMetrics.CITY:T1EcoTripMetrics.MOTORWAY,
                i<15?30:115,0.1);
        close(m.km(),3,"distance");
        close(m.roadKm(T1EcoTripMetrics.CITY),1.5,"city km");
        close(m.roadKm(T1EcoTripMetrics.MOTORWAY),1.5,"motorway km");
        close(m.percent(T1EcoTripMetrics.CITY),50,"city share");
        close(m.percent(T1EcoTripMetrics.MOTORWAY),50,"motorway share");
        close(m.roadCoverage(),1,"coverage");
        check(m.ecoScore()>=8.0,"smooth drive high eco");
        check(m.speedKm(6)>1.4,"30-35 bin");
        check(m.speedKm(23)>1.4,"115-120 bin");
        m.add(1600000000000L,15,300,
            T1EcoTripMetrics.CITY,30,0);
        close(m.km(),3,"bad gap rejected");
        T1EcoTripMetrics mixed=new T1EcoTripMetrics();
        for(int i=0;i<30;i++)
            mixed.add(1600000000000L+i*5000L,.1,5,
                i<10?T1EcoTripMetrics.UNKNOWN:T1EcoTripMetrics.ROAD,
                60,2.8);
        close(mixed.percent(T1EcoTripMetrics.UNKNOWN),100d/3,"unknown retained");
        check(!Double.isNaN(mixed.ecoScore()),"coverage 2/3");
        check(mixed.ecoScore()<m.ecoScore(),"abrupt driving lower eco");
        T1EcoTripMetrics unknown=new T1EcoTripMetrics();
        for(int i=0;i<30;i++)
            unknown.add(1600000000000L+i*5000L,.1,5,
                T1EcoTripMetrics.UNKNOWN,90,0.1);
        check(Double.isNaN(unknown.ecoScore()),"unknown OSM no score");
        System.out.println("OK: road type OSM shares, 5kmh distance/time bins, confidence and Eco v1");
    }
}