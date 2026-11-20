package ar.com.rango5gps.drivelabauto.receiver;
import java.util.*;
public final class T1AutoRangeTimelineTest {
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private static void near(double actual,double expected){
        if(Math.abs(actual-expected)>1e-7)
            throw new AssertionError(actual+" != "+expected);
    }
    static T1ChargeStore.Record charge(long id,long from,long to,
                                      double pack,double grid,int open,
                                      String origin){
        T1ChargeStore.Record c=new T1ChargeStore.Record();
        c.id=id;c.start=from;c.end=open==0?to:0;c.last=to;
        c.kwh=pack;c.meterKwh=grid;c.open=open;
        c.price=250;c.source=origin;return c;
    }
    public static void main(String[] args){
        long from=1000,until=120000;
        T1AutoJourneyStore.RangeReport report=
            new T1AutoJourneyStore.RangeReport(from,until);
        T1AutoJourneyStore.Journey drive=new T1AutoJourneyStore.Journey();
        drive.id=1;drive.start=10000;drive.last=20000;
        T1AutoJourneyStore.Slice slice=
            new T1AutoJourneyStore.Slice("ENCENDIDO #1",from,until);
        slice.gps=14;slice.km=13.7;slice.out=4;slice.in=1;
        slice.fuelCandidate=0.4;slice.sampledMs=9000;
        slice.hvMeasuredMs=8500;slice.fuelObservedMs=8500;
        report.byIgnition.put(1L,slice);
        report.total.gps=14;report.total.km=13.7;
        report.total.out=4;report.total.in=1;
        report.total.hvMeasuredMs=8500;
        List<T1ChargeStore.Record> charges=Arrays.asList(
            charge(1,30000,50000,6,7,0,"OEM AUTOMÁTICA"),
            charge(2,42000,48000,6,7,0,"T1LAUNCHER IMPORTACIÓN MANUAL"),
            charge(3,60000,70000,.3,Double.NaN,1,"OEM AUTOMÁTICA"),
            charge(4,50,1600,1,Double.NaN,0,"ANTERIOR")
        );
        T1AutoRangeTimeline.Result result=
            T1AutoRangeTimeline.build(report,Arrays.asList(drive),
                charges,from,until,1900);
        check(result.drives==1,"one ignition");
        check(result.finishedCharges==1,"manual duplicate must not be counted");
        check(result.activeCharges==1,"currently plugged should remain separate");
        check(result.partialCharges==1,"overlapping range charge is partial");
        near(result.chargeBattery,6);
        near(result.gridMeter,7);
        near(result.costEstimated,7*250);
        check(result.events.size()==5,"all source records remain visible");
        check("CONDUCCION".equals(result.events.get(0).type),
              "chronology sorted by time");
        String csv=result.csv(report);
        check(csv.contains("CONDUCCION")&&csv.contains("CARGA_EN_CURSO")&&
              csv.contains("IMPORTACIÓN MANUAL SUPERPUESTA")&&
              csv.contains("kilometros")==false,
              "report exports independent event types and provenance");
        check(!csv.contains("gps_lat")&&!csv.contains("gps_lon"),
            "summary never leaks raw coordinates");
        T1AutoRangeTimeline.Result noHv=
            T1AutoRangeTimeline.build(
                new T1AutoJourneyStore.RangeReport(from,until),
                Collections.emptyList(),Collections.emptyList(),
                from,until,1900);
        check(noHv.chargeBattery==0&&noHv.drives==0,
              "missing measurements must not become fabricated kWh");
        System.out.println("PASS chronological journeys and charges, duplicate protection, meter-vs-HV and partial records");
    }
}
