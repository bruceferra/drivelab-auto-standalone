package ar.com.rango5gps.drivelabauto.receiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Counterfactual TRIP explorer. The recorder supplies real wheel speed,
 * timestamp, RPM, SOC and optional OEM mode. This class NEVER asserts
 * that the ECU would have chosen a particular strategy.
 *
 * The first-step what-if estimate is an ENERGY-BALANCE LOWER BOUND:
 * amount of extra useful energy that a generator would have to supply to
 * finish at the requested SOC if an otherwise identical driving cycle
 * followed the observed net SOC trajectory. No fuel prediction is
 * fabricated without measured baseline liters and calibrated conversion.
 */
final class T1WhatIfEnergyModel {
    static final class Point {
        final long ts;
        final double km,speed,soc,rpm,current,voltage;
        final int motorOn,mode,socMode,regen,driveMode,brake;
        Point(long t,double k,double v,double s,double r,double a,double hv,
              int on,int power,int strategy,int recovery,int drive,int pedal){
            ts=t;km=k;speed=v;soc=s;rpm=r;current=a;voltage=hv;
            motorOn=on;mode=power;socMode=strategy;regen=recovery;
            driveMode=drive;brake=pedal;
        }
    }
    static final class Trip {
        final List<Point> points=new ArrayList<>();
        int originalColumns=0,invalidRows=0;
        String fileName="";
    }
    static final class Actual {
        int rows=0,voltageRows=0,modeRows=0,regenRows=0,brakeRows=0;
        long durationMillis=0;
        double km=0,socStart=Double.NaN,socEnd=Double.NaN,
            motorOffKm=0,motorOnKm=0,unknownMotorKm=0,
            idealBrakingKwh=0;
    }
    static final class Scenario {
        final Actual actual;
        final double requestedStartSoc,shiftedEndpointSoc,targetSoc,
            usableBatteryKwh,energyGapKwh,extraGasolineMin,extraGasolineMax;
        final boolean forced,hasSoc,hasLiterBaseline,hasQuantifiedMode;
        final String note;
        Scenario(Actual a,double ini,double end,double target,double pack,
                 double gap,double min,double max,boolean force,
                 boolean hasValues,boolean measured,boolean quantified,
                 String explanation){
            actual=a;requestedStartSoc=ini;shiftedEndpointSoc=end;
            targetSoc=target;usableBatteryKwh=pack;energyGapKwh=gap;
            extraGasolineMin=min;extraGasolineMax=max;
            forced=force;hasSoc=hasValues;hasLiterBaseline=measured;
            hasQuantifiedMode=quantified;note=explanation;
        }
    }
    private T1WhatIfEnergyModel(){}
    static double number(String[] f,int index){
        if(index>=f.length||f[index].trim().isEmpty())return Double.NaN;
        try{
            double x=Double.parseDouble(f[index]);
            return Double.isFinite(x)?x:Double.NaN;
        }catch(NumberFormatException e){return Double.NaN;}
    }
    static int code(String[] f,int at){
        double x=number(f,at);
        return Double.isFinite(x)&&x>=Integer.MIN_VALUE&&
            x<=Integer.MAX_VALUE?(int)x:Integer.MIN_VALUE;
    }
    static Trip read(File source)throws IOException{
        if(source==null||!source.isFile()||!source.getName().matches(
                "trip_[0-9]{8}_[0-9]{6}\\.csv"))
            throw new IOException("Se necesita un TRIP local válido");
        Trip t=new Trip();t.fileName=source.getName();
        try(BufferedReader in=new BufferedReader(new FileReader(source))){
            String header=in.readLine();
            if(header==null||!header.startsWith("timestamp_epoch_ms,gear_1033"))
                throw new IOException("Cabecera TRIP no reconocida");
            t.originalColumns=header.split(",",-1).length;
            String line;
            while((line=in.readLine())!=null&&t.points.size()<120000){
                String[] f=line.split(",",-1);
                if(f.length<t.originalColumns||f.length<42){
                    t.invalidRows++;continue;
                }
                try{
                    long ts=Long.parseLong(f[0]);
                    double km=number(f,16);
                    if(ts<1600000000000L||!Double.isFinite(km)||km<0){
                        t.invalidRows++;continue;
                    }
                    t.points.add(new Point(ts,km,number(f,2),number(f,4),
                        number(f,23),number(f,7),number(f,6),
                        code(f,24),code(f,42),code(f,43),code(f,46),
                        code(f,48),code(f,49)));
                }catch(Exception e){t.invalidRows++;}
            }
        }
        if(t.points.size()<2)throw new IOException(
            "No hay suficientes muestras para comparar");
        return t;
    }
    static Actual inspect(Trip t,double massKg){
        if(t==null||t.points.size()<2)throw new IllegalArgumentException("TRIP vacío");
        Actual a=new Actual();
        Point first=t.points.get(0),last=t.points.get(t.points.size()-1);
        a.rows=t.points.size();
        a.km=Math.max(0,last.km-first.km);
        a.durationMillis=Math.max(0,last.ts-first.ts);
        for(Point p:t.points){
            if(Double.isFinite(p.soc)&&p.soc>=0&&p.soc<=100){
                if(!Double.isFinite(a.socStart))a.socStart=p.soc;
                a.socEnd=p.soc;
            }
            if(Double.isFinite(p.voltage)&&p.voltage>100&&p.voltage<1500)
                a.voltageRows++;
            if(p.mode>=1&&p.mode<=3)a.modeRows++;
            if(p.regen>=1&&p.regen<=3)a.regenRows++;
            if(p.brake==0||p.brake==1)a.brakeRows++;
        }
        for(int i=1;i<t.points.size();i++){
            Point p=t.points.get(i-1),q=t.points.get(i);
            long dt=q.ts-p.ts;double dk=q.km-p.km;
            if(dt<500||dt>12000||!Double.isFinite(dk)||dk<0||dk>0.5)
                continue;
            if(q.motorOn==0)a.motorOffKm+=dk;
            else if(q.motorOn==1)a.motorOnKm+=dk;
            else a.unknownMotorKm+=dk;
            if(massKg>100&&massKg<5000&&Double.isFinite(p.speed)&&
                Double.isFinite(q.speed)&&p.speed>=0&&q.speed>=0&&
                p.speed<=220&&q.speed<=220&&p.speed>q.speed){
                double v1=p.speed/3.6,v2=q.speed/3.6;
                // Pure upper-bound kinetic-energy loss from braking.
                // Without slope/actual torque it is NOT recovered battery kWh.
                a.idealBrakingKwh+=0.5*massKg*(v1*v1-v2*v2)/3600000.0;
            }
        }
        return a;
    }
    static Scenario compare(Actual actual,double initialSoc,
                            boolean forced,double targetSoc,
                            double packKwh,boolean measuredFuel,
                            boolean changedPowertrain){
        boolean hasSoc=Double.isFinite(actual.socStart)&&
            Double.isFinite(actual.socEnd)&&
            Double.isFinite(initialSoc)&&initialSoc>=0&&initialSoc<=100&&
            Double.isFinite(packKwh)&&packKwh>=5&&packKwh<=100;
        double shifted=hasSoc?Math.max(0,Math.min(100,
            actual.socEnd+initialSoc-actual.socStart)):Double.NaN;
        double gap=hasSoc&&forced&&Double.isFinite(targetSoc)&&
            targetSoc>=0&&targetSoc<=100?
            Math.max(0,targetSoc-shifted)*packKwh/100.0:Double.NaN;
        // Assumptions ONLY, not measured Jetour engine-to-pack efficiency:
        // 1.8..3.2 useful battery kWh per litre of gasoline.
        double lower=Double.isFinite(gap)?gap/3.2:Double.NaN;
        double upper=Double.isFinite(gap)?gap/1.8:Double.NaN;
        String note=changedPowertrain?
            "Modo de propulsión diferente: sin modelo de ECU ni fuel-rate "+
            "calibrado, no es posible calcular litros totales confiables.":
            forced?"Balance energético: el intervalo de litros es SOLO "+
                "equivalente teórico para cubrir déficit SOC al finalizar, "+
                "NO predicción de combustible durante el viaje.":
                "HEV Smart conserva la evolución de SOC observada como "+
                "referencia; no se simulan decisiones internas de ECU.";
        return new Scenario(actual,initialSoc,shifted,targetSoc,packKwh,
            gap,lower,upper,forced,hasSoc,measuredFuel,false,note);
    }
}
