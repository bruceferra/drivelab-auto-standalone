package ar.com.rango5gps.drivelabauto.receiver;
import java.util.*;
final class T1AutoJourneyStore {
    static final class Journey {
        long id,start,end,last;
    }
    static final class Slice {
        final String title; final long from,until;
        int sessions;double km,gps,out,in,fuelCandidate;
        long sampledMs,hvMeasuredMs,fuelObservedMs;
        Slice(String name,long a,long b){title=name;from=a;until=b;}
    }
    static final class RangeReport {
        final long from,until;
        final Slice total;
        final TreeMap<String,Slice> days=new TreeMap<>();
        final LinkedHashMap<Long,Slice> byIgnition=new LinkedHashMap<>();
        RangeReport(long a,long b){from=a;until=b;total=new Slice("TOTAL",a,b);}
    }
}
