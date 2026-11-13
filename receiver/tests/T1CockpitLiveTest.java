package ar.com.rango5gps.drivelabauto.receiver;

public final class T1CockpitLiveTest {
    private static void require(boolean ok,String error){
        if(!ok)throw new AssertionError(error);
    }
    private static void close(double got,double expected,String why){
        require(Math.abs(got-expected)<0.0001,why+": "+got+" vs "+expected);
    }
    public static void main(String[] ignored){
        // Previous recorded trip (52.70 km) must not be advertised as live.
        T1CockpitScope.Value stopped=T1CockpitScope.choose(false,false,false,
            null,52.699,99,99,495,-158,2129);
        require(stopped.kind==T1CockpitScope.Kind.IDLE,"stopped");
        close(stopped.km,0.0,"previous trip hidden");
        require(!stopped.fuelKnown,"no historical 2.475 L on current page");

        T1CockpitScope.Value pending=T1CockpitScope.choose(false,false,true,
            null,52.699,99,99,495,-158,2129);
        require(pending.kind==T1CockpitScope.Kind.PREPARING,"pending start");
        close(pending.km,0,"pending must say 0km");

        T1CockpitScope.Value day=T1CockpitScope.choose(true,false,false,
            null,52.699,99,99,495,-158,2129);
        close(day.km,52.699,"live day distance");
        close(day.fuelLiters,2.475,"fuel day");

        T1JourneyMetrics.Checkpoint a=new T1JourneyMetrics.Checkpoint(
            "20261111_220101",1000,52.699,495,-158,99,99);
        T1JourneyMetrics.Checkpoint b=new T1JourneyMetrics.Checkpoint(
            "20261111_220101",4000,53.019,505,-155,100,100);
        T1JourneyMetrics.Result segment=T1JourneyMetrics.between(a,b,2129);
        T1CockpitScope.Value client=T1CockpitScope.choose(true,true,false,
            segment,53.019,100,100,505,-155,2129);
        require(client.kind==T1CockpitScope.Kind.CLIENT,"passenger");
        close(client.km,0.32,"passenger starts at baseline");
        require(client.fuelEvents==1&&client.electricEvents==1,"only new callbacks");
        close(client.fuelLiters,0.05,"local consumption, not all-day consumption");
        close(client.electricNetKwh,0.015,"local electricity");
        T1CockpitScope.Value atZero=T1CockpitScope.choose(true,true,false,
            T1JourneyMetrics.between(a,a,2129),52.699,99,99,495,-158,2129);
        close(atZero.km,0.0,"client zero");
        require(!atZero.fuelKnown&&Double.isNaN(atZero.fuelLiters),
            "0 events must be awaiting OEM, not zero liters");
        T1CockpitScope.Value missing=T1CockpitScope.choose(false,true,false,
            null,52.699,99,99,495,-158,2129);
        require(missing.kind==T1CockpitScope.Kind.UNSYNCED,"stale day");
        require(!missing.fuelKnown,"not fabricate fuel");

        T1ExperimentalRange.Estimate x=T1ExperimentalRange.estimate(
            52.8,19,7.47,100);
        close(x.fuelKm,(52.8-5.3)*100/7.47,"fuel reserve cutout");
        close(x.electricKm,19,"soc 19 percent and 100 km nominal");
        close(x.totalKm,x.fuelKm+19,"hybrid experimental total");
        require(!T1ExperimentalRange.estimate(67,19,7.47,100).fuelOk,
            "upper fuel float saturation must show unavailable");
        require(!T1ExperimentalRange.estimate(Double.NaN,19,7.47,100).fuelOk,
            "no OEM fuel means no total");
        require("SMART".equals(T1SocModePolicy.name(2)),"smart mapping");
        require("FORZADO".equals(T1SocModePolicy.name(4)),"forced mapping");
        close(T1SocModePolicy.socTarget(56,1.0),50,
            "SOC 1041 raw56 is FORZADO 50% in owner photos");
        require(Double.isNaN(T1SocModePolicy.socTarget(70,1.0)),
            "1041 raw70 not mapped, and floatRaw1 is not percent");
        require("EV MAX".equals(T1ZonePolicy.name(3)),"blue EV code");
        require(T1PowerFlowPolicy.engine(1500)==T1PowerFlowPolicy.Engine.ON,"rpm engine on");
        require(T1PowerFlowPolicy.engine(0)==T1PowerFlowPolicy.Engine.OFF,"engine off");
        require(T1PowerFlowPolicy.engine(Double.NaN)==T1PowerFlowPolicy.Engine.UNKNOWN,"unknown rpm");
        require(T1PowerFlowPolicy.battery(-17)==T1PowerFlowPolicy.Battery.CHARGING,"charge current negative");
        require(T1PowerFlowPolicy.battery(20)==T1PowerFlowPolicy.Battery.DISCHARGING,"draw current positive");
        require(T1PowerFlowPolicy.battery(0)==T1PowerFlowPolicy.Battery.REST,"neutral current");
        require(T1PowerFlowPolicy.battery(Double.NaN)==T1PowerFlowPolicy.Battery.UNKNOWN,"missing HV");
        require(T1ManualHevPolicy.canRequest(1,0,1),"parked EV allows manual trial");
        require(T1ManualHevPolicy.canRequest(1,0,3),"parked EV MAX allows manual trial");
        require(!T1ManualHevPolicy.canRequest(4,0,1),"D blocked");
        require(!T1ManualHevPolicy.canRequest(1,1,1),"moving blocked");
        require(!T1ManualHevPolicy.canRequest(1,0,2),"HEV already set");
        System.out.println("OK: Cockpit client 0km, scoped 500m, stale trip, SOC, HV, range and parked HEV policy");
    }
}
