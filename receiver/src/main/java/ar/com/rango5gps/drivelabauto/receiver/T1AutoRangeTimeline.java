package ar.com.rango5gps.drivelabauto.receiver;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Chronological, read-only join of two PRIVATE SQLite journals:
 * AUTO ignition journeys and cable charging. Neither journal is edited.
 * This is NOT a physical labelling of battery electrons by source.
 */
final class T1AutoRangeTimeline {
    private T1AutoRangeTimeline(){}
    static final class Event{
        String type,description,note;
        long from,until;
        double gps=Double.NaN,km=Double.NaN,hvOut=Double.NaN,
            hvIn=Double.NaN,fuelL=Double.NaN,chargeBattery=Double.NaN,
            meter=Double.NaN,cost=Double.NaN;
        Event(String kind,long start,long end,String detail,String quality){
            type=kind;from=start;until=end;
            description=detail;note=quality;
        }
    }
    static final class Result{
        final ArrayList<Event> events=new ArrayList<>();
        double chargeBattery,gridMeter,costEstimated;
        int finishedCharges,activeCharges,partialCharges,drives;
        String describe(){
            StringBuilder b=new StringBuilder(
                "CRONOLOGÍA REAL · ENCENDIDOS + ENCHUFES");
            b.append("\nEncendidos: ").append(drives)
                .append(" · Cargas finalizadas completas: ")
                .append(finishedCharges)
                .append(" · en curso: ").append(activeCharges)
                .append(" · parciales: ").append(partialCharges);
            b.append("\nCarga recibida a batería (sesiones completas): ")
                .append(fmt(chargeBattery,3)).append(" kWh");
            b.append("\nEnergía de red CON MEDIDOR en esas cargas: ")
                .append(gridMeter>0?fmt(gridMeter,3)+" kWh":
                    "SIN MEDIDOR REGISTRADO");
            b.append("\nCosto de cargas dentro del rango: $")
                .append(fmt(costEstimated,2))
                .append(" (medidor cuando existe; si no, "+
                    "estimación mínima sobre energía HV)");
            b.append("\nLos kWh de cargas y la energía HV recibida "+
                "durante conducción son dos fuentes diferentes: NO SUMARLAS.");
            b.append("\nNo es posible asignar físicamente cada kWh "+
                "gastado a enchufe, frenada o motor de combustión; "+
                "ese reparto requiere una convención contable y "+
                "más señales OEM validadas.");
            return b.toString();
        }
        String csv(T1AutoJourneyStore.RangeReport raw){
            StringBuilder out=new StringBuilder(
                "tipo;descripcion;desde_epoch_ms;hasta_epoch_ms;km_oem;"+
                "km_gps;hv_salida_kwh;hv_entrada_kwh;fuel_ci186_candidato_l;"+
                "carga_bateria_kwh;carga_red_medida_kwh;costo_ars;"+
                "calidad_y_advertencias\n");
            if(raw!=null){
                addSlice(out,"TOTAL",raw.total,raw.from,raw.until);
                for(T1AutoJourneyStore.Slice s:raw.days.values())
                    addSlice(out,"DIA",s,s.from,s.until);
            }
            for(Event e:events){
                out.append(e.type).append(';')
                    .append(clean(e.description)).append(';')
                    .append(e.from).append(';').append(e.until).append(';')
                    .append(n(e.km)).append(';').append(n(e.gps)).append(';')
                    .append(n(e.hvOut)).append(';').append(n(e.hvIn))
                    .append(';').append(n(e.fuelL)).append(';')
                    .append(n(e.chargeBattery)).append(';')
                    .append(n(e.meter)).append(';').append(n(e.cost))
                    .append(';').append(clean(e.note)).append('\n');
            }
            return out.toString();
        }
        private void addSlice(StringBuilder out,String kind,
                              T1AutoJourneyStore.Slice s,long from,long until){
            out.append(kind).append(';').append(clean(s.title)).append(';')
                .append(from).append(';').append(until).append(';')
                .append(n(s.km)).append(';').append(n(s.gps)).append(';')
                .append(s.hvMeasuredMs>0?n(s.out):"").append(';')
                .append(s.hvMeasuredMs>0?n(s.in):"").append(';')
                .append(s.fuelObservedMs>0?n(s.fuelCandidate):"")
                .append(";;;;")
                .append(clean("HV verificado "+s.hvMeasuredMs/60000+" min; "+
                    "CI186 candidato NO medido; no incluye kWh de enchufes"))
                .append('\n');
        }
    }
    static Result build(T1AutoJourneyStore.RangeReport report,
                        List<T1AutoJourneyStore.Journey> drives,
                        List<T1ChargeStore.Record> charges,
                        long from,long until,double fuelPrice){
        Result result=new Result();
        SimpleDateFormat time=new SimpleDateFormat(
            "dd/MM HH:mm",Locale.getDefault());
        for(T1AutoJourneyStore.Journey journey:drives){
            T1AutoJourneyStore.Slice s=report.byIgnition.get(journey.id);
            if(s==null||s.sampledMs<=0)continue;
            result.drives++;
            long end=journey.end>0?journey.end:journey.last;
            Event e=new Event("CONDUCCION",
                Math.max(from,journey.start),Math.min(until,end),
                "Encendido #"+journey.id,
                "OEM observado "+fmt(s.sampledMs/60000.0,1)+
                " min; HV validado "+fmt(s.hvMeasuredMs/60000.0,1)+
                " min; CI186 es estimación por nivel, NO medidor");
            e.km=s.km;e.gps=s.gps;
            if(s.hvMeasuredMs>0){e.hvOut=s.out;e.hvIn=s.in;}
            if(s.fuelObservedMs>0){
                e.fuelL=s.fuelCandidate;
                if(s.fuelCandidate>0&&Double.isFinite(fuelPrice)&&
                   fuelPrice>0){
                    e.note+="; nafta CANDIDATO $"+
                        fmt(s.fuelCandidate*fuelPrice,2)+
                        " (no facturable como medición exacta)";
                }
            }
            result.events.add(e);
        }
        for(T1ChargeStore.Record charge:charges){
            long end=charge.end>0?charge.end:
                charge.last>0?charge.last:charge.start;
            boolean complete=charge.open==0&&charge.end>0&&
                charge.start>=from&&end<=until;
            boolean valid=Double.isFinite(charge.kwh)&&charge.kwh>0;
            boolean meter=Double.isFinite(charge.meterKwh)&&
                charge.meterKwh>0;
            boolean overlap=charge.start<from||end>until;
            if(charge.open!=0)result.activeCharges++;
            else if(overlap)result.partialCharges++;
            else result.finishedCharges++;
            Event e=new Event(charge.open==0?"CARGA":"CARGA_EN_CURSO",
                Math.max(from,charge.start),Math.min(until,end),
                "Enchufe #"+charge.id+" · "+
                (charge.open==0?"FINALIZADA":"EN CURSO"),
                (overlap?"RECORTE DE INTERVALO: kWh de sesión no "+
                    "atribuibles íntegramente al rango; ":"")+
                (charge.open!=0?"CARGA PARCIAL AÚN ABIERTA; ":"")+
                (meter?"kWh red con medidor":"kWh batería HV estimados")+
                "; no mezclar con regeneración de conducción");
            if(valid)e.chargeBattery=charge.kwh;
            if(meter)e.meter=charge.meterKwh;
            if(valid||meter){
                double priced=meter?charge.meterKwh:charge.kwh;
                if(Double.isFinite(charge.price)&&charge.price>=0)
                    e.cost=priced*charge.price;
            }
            if(complete){
                if(valid)result.chargeBattery+=charge.kwh;
                if(meter)result.gridMeter+=charge.meterKwh;
                if(Double.isFinite(e.cost))result.costEstimated+=e.cost;
            }
            result.events.add(e);
        }
        result.events.sort(Comparator
            .comparingLong((Event e)->e.from)
            .thenComparing(e->e.type));
        return result;
    }
    static String description(Event e){
        SimpleDateFormat date=new SimpleDateFormat(
            "dd/MM HH:mm",Locale.getDefault());
        StringBuilder b=new StringBuilder(e.type+" · "+
            date.format(new Date(e.from))+" → "+
            date.format(new Date(e.until)));
        b.append("\n").append(e.description);
        if(Double.isFinite(e.gps))
            b.append("\nGPS ").append(fmt(e.gps,2)).append(" km");
        if(Double.isFinite(e.km))
            b.append(" · OEM ").append(fmt(e.km,2)).append(" km");
        if(Double.isFinite(e.hvOut))
            b.append("\nHV salida ").append(fmt(e.hvOut,3))
                .append(" kWh; entrada ").append(fmt(e.hvIn,3))
                .append(" kWh (motor+frenadas)");
        if(Double.isFinite(e.fuelL))
            b.append("\nTanque CI186 candidato: ~")
                .append(fmt(e.fuelL,2)).append(" L");
        if(Double.isFinite(e.chargeBattery))
            b.append("\nBatería del enchufe: ")
                .append(fmt(e.chargeBattery,3)).append(" kWh");
        if(Double.isFinite(e.meter))
            b.append(" · red MEDIDA ").append(fmt(e.meter,3))
                .append(" kWh");
        if(Double.isFinite(e.cost))
            b.append(" · costo $").append(fmt(e.cost,2));
        return b.append("\n").append(e.note).toString();
    }
    static String fmt(double value,int digits){
        return Double.isFinite(value)?
            String.format(Locale.getDefault(),"%."+digits+"f",value):
            "SIN DATOS";
    }
    private static String n(double value){
        return Double.isFinite(value)?
            String.format(Locale.ROOT,"%.5f",value):"";
    }
    private static String clean(String text){
        return text==null?"":text.replace(';',',')
            .replace('\r',' ').replace('\n',' ').replace('"',' ');
    }
}
