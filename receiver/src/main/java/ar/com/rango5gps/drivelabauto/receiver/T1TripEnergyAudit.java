package ar.com.rango5gps.drivelabauto.receiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/** Read-only HV energy audit for a selected historical client trip.
 * Works on the ORIGINAL master CSV only and never alters distance/samples.
 * This runs off Android UI thread. Battery charge may be regeneration OR
 * engine-driven generation; source cannot be separated from 1063/1064.
 */
final class T1TripEnergyAudit {
    static final class Result {
        final double grossKwh,chargedKwh;
        final String detail;
        Result(double gross,double charged,String detail){
            grossKwh=gross;chargedKwh=charged;this.detail=detail;
        }
        boolean available(){
            return Double.isFinite(grossKwh)&&Double.isFinite(chargedKwh);
        }
    }
    private T1TripEnergyAudit(){}
    private static int col(String[] header,String name){
        for(int i=0;i<header.length;i++)if(name.equals(header[i]))return i;
        return -1;
    }
    private static double value(String[] cells,int i){
        if(i<0||i>=cells.length)return Double.NaN;
        try{return Double.parseDouble(cells[i]);}
        catch(NumberFormatException ex){return Double.NaN;}
    }
    static Result read(File folder,String chain,long start,long end){
        if(folder==null||chain==null||start<=0||end<start)
            return new Result(Double.NaN,Double.NaN,"Intervalo sin datos");
        String[] ids=chain.split("\\+");
        if(ids.length==0||ids.length>20)
            return new Result(Double.NaN,Double.NaN,"Cadena no válida");
        double gross=0,charge=0;
        int segments=0;
        for(String id:ids){
            if(!id.matches("[0-9]{8}_[0-9]{6}"))
                return new Result(Double.NaN,Double.NaN,"ID de viaje inválido");
            File file=new File(folder,"trip_"+id+".csv");
            if(!file.isFile())
                return new Result(Double.NaN,Double.NaN,"Falta CSV "+id);
            try(BufferedReader reader=new BufferedReader(new FileReader(file))){
                String h=reader.readLine();
                if(h==null)return new Result(Double.NaN,Double.NaN,
                    "CSV sin encabezado");
                String[] cols=h.split(",",-1);
                int time=col(cols,"timestamp_epoch_ms");
                int discharge=col(cols,"hv_gross_discharge_kwh_est");
                int charged=col(cols,"hv_charge_kwh_est");
                if(time<0||discharge<0||charged<0)
                    return new Result(Double.NaN,Double.NaN,
                        "CSV anterior sin contadores HV");
                double gFirst=Double.NaN,gLast=Double.NaN,
                    cFirst=Double.NaN,cLast=Double.NaN;
                int samples=0;
                String line;
                while((line=reader.readLine())!=null){
                    String[] cells=line.split(",",-1);
                    long tick=(long)value(cells,time);
                    if(tick<start||tick>end)continue;
                    double a=value(cells,discharge),
                        b=value(cells,charged);
                    if(!Double.isFinite(a)||a<0||
                       !Double.isFinite(b)||b<0)continue;
                    if(samples==0){gFirst=a;cFirst=b;}
                    gLast=a;cLast=b;samples++;
                }
                if(samples>=2&&gLast>=gFirst&&cLast>=cFirst){
                    gross+=gLast-gFirst;
                    charge+=cLast-cFirst;segments++;
                }
            }catch(IOException ex){
                return new Result(Double.NaN,Double.NaN,
                    "CSV no disponible");
            }
        }
        if(segments==0||segments!=ids.length)
            return new Result(Double.NaN,Double.NaN,
                "Faltan muestras HV suficientes");
        return new Result(gross,charge,
            "HV acumulado por intervalos de las jornadas enlazadas; "+
            "solo aproximación de energía comprada");
    }
}
