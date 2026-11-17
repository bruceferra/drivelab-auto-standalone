package ar.com.rango5gps.drivelabauto.receiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Attribution estimate backed ONLY by explicitly recorded wall-plug charges.
 * Battery HV current cannot identify home electricity versus regeneration or
 * engine generation. Internal HV charging is never entered in the grid ledger.
 * Scopes "Total" and "Client" are separate, nonadditive views.
 */
final class T1PlugChargeLedger {
    static final class Charge {
        final long time;final double kwh;
        Charge(long time,double kwh){this.time=time;this.kwh=kwh;}
    }
    static final class Trip {
        final String key;final long start;final double netHvKwh;
        Trip(String key,long start,double netHvKwh){
            this.key=key;this.start=start;this.netHvKwh=netHvKwh;
        }
    }
    static final class Attribution {
        final double plugKwh;
        final double remainingKwh;
        final boolean sourceRecorded;
        Attribution(double plug,double remaining,boolean recorded){
            plugKwh=plug;remainingKwh=remaining;sourceRecorded=recorded;
        }
    }
    private T1PlugChargeLedger(){}
    static List<Charge> read(File csv){
        List<Charge> result=new ArrayList<>();
        if(csv==null||!csv.isFile())return result;
        try(BufferedReader in=new BufferedReader(new FileReader(csv))){
            String header=in.readLine();
            if(header==null||!header.contains("kwh_red_medidos"))return result;
            String line;
            while((line=in.readLine())!=null){
                String[] parts=line.split(";",-1);
                if(parts.length<4)continue;
                try{
                    long at=Long.parseLong(parts[0]);
                    double kwh=Double.parseDouble(parts[1]);
                    if(at>0&&Double.isFinite(kwh)&&kwh>0&&kwh<=150)
                        result.add(new Charge(at,kwh));
                }catch(NumberFormatException ignored){}
            }
        }catch(IOException ignored){}
        return result;
    }
    /**
     * FIFO grid-origin budget. Assigns only net HV depletion backed by
     * a real recorded PLUG session earlier than this trip. Never treats
     * returned HV energy (braking/engine) as bought electricity.
     *
     * This is an approximate accounting attribution, NOT a physical proof:
     * unknown battery opening balance and unlogged plug sessions matter.
     */
    static Map<String,Attribution> estimate(List<Charge> purchases,
                                             List<Trip> journeys){
        ArrayList<Charge> sources=new ArrayList<>(purchases);
        sources.sort(Comparator.comparingLong(c->c.time));
        ArrayList<Trip> trips=new ArrayList<>(journeys);
        trips.sort(Comparator.comparingLong(t->t.start));
        Map<String,Attribution> out=new HashMap<>();
        int index=0;double budget=0;
        boolean hadCharge=false,uncertain=false;
        for(Trip t:trips){
            while(index<sources.size()&&sources.get(index).time<=t.start){
                double added=sources.get(index++).kwh;
                // Wall-meter energy is an input, never evidence that it
                // was immediately spent during a particular trip.
                budget+=added;
                hadCharge=true;
                uncertain=false;
            }
            if(t.start<=0||!hadCharge||uncertain||
               !Double.isFinite(t.netHvKwh)||Math.abs(t.netHvKwh)>10000){
                out.put(t.key,new Attribution(Double.NaN,Double.NaN,hadCharge));
                if(hadCharge&&!Double.isFinite(t.netHvKwh))uncertain=true;
                continue;
            }
            double required=Math.max(0,t.netHvKwh);
            double attributed=Math.min(required,budget);
            budget=Math.max(0,budget-attributed);
            out.put(t.key,new Attribution(attributed,required-attributed,true));
        }
        return out;
    }
}
