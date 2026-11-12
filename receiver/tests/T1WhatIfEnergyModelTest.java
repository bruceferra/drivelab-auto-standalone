package ar.com.rango5gps.drivelabauto.receiver;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;

public final class T1WhatIfEnergyModelTest {
    private static void check(boolean c,String m){
        if(!c)throw new AssertionError(m);
    }
    static void sampleAndPhysics(){
        long base=1794500000000L;
        T1WhatIfEnergyModel.Trip trip=new T1WhatIfEnergyModel.Trip();
        trip.points.add(new T1WhatIfEnergyModel.Point(
            base,0,60,80,1500,10,Double.NaN,1,2,2,2,0,0));
        trip.points.add(new T1WhatIfEnergyModel.Point(
            base+3000,0.03,30,70,0,-15,Double.NaN,0,2,2,2,0,1));
        trip.points.add(new T1WhatIfEnergyModel.Point(
            base+6000,0.06,10,20,1400,20,Double.NaN,1,2,2,2,1,1));
        T1WhatIfEnergyModel.Actual x=T1WhatIfEnergyModel.inspect(trip,2100);
        check(x.rows==3,"rows");
        check(Math.abs(x.km-0.06)<1e-8,"distance");
        check(x.motorOffKm>0.029&&x.motorOffKm<0.031,"engine off distance");
        check(x.motorOnKm>0.029&&x.motorOnKm<0.031,"engine on distance");
        check(x.idealBrakingKwh>0,"kinetic braking upper bound");
        check(x.voltageRows==0,"no HV measurement when absent");
        check(x.socStart==80&&x.socEnd==20,"OEM SOC");
        T1WhatIfEnergyModel.Scenario forced=
            T1WhatIfEnergyModel.compare(x,80,true,50,20,false,false);
        check(forced.hasSoc,"valid SOC");
        check(Math.abs(forced.energyGapKwh-6.0)<1e-8,
            "Energy to bring endpoint from SOC20 to SOC50");
        check(Math.abs(forced.extraGasolineMin-1.875)<1e-6,
            "1.8..3.2 kWh per litre stated assumptions");
        check(!forced.hasQuantifiedMode,"never falsely claim ECU simulation");
        T1WhatIfEnergyModel.Scenario missing=
            T1WhatIfEnergyModel.compare(x,80,false,50,20,false,true);
        check(Double.isNaN(missing.energyGapKwh),"Smart never fake deficit");
        T1WhatIfEnergyModel.Scenario alternateStart=
            T1WhatIfEnergyModel.compare(x,100,true,50,20,false,false);
        check(Math.abs(alternateStart.shiftedEndpointSoc-40)<1e-8,
            "Shift observed battery curve, not ECU forecast");
        check(Math.abs(alternateStart.energyGapKwh-2)<1e-8,
            "Forced target not assumed to change mode strategy");
    }
    static void parseBackCompat()throws Exception{
        File f=File.createTempFile("trip_20261112_120000",".csv");
        File folder=new File(System.getProperty("java.io.tmpdir"),"trip_test_"+System.nanoTime());
        if(!folder.mkdirs())throw new Exception("temp folder");
        File file=new File(folder,"trip_20261112_120000.csv");
        try(FileWriter out=new FileWriter(file)){
            StringBuilder header=new StringBuilder("timestamp_epoch_ms,gear_1033");
            for(int i=2;i<42;i++)header.append(",field_").append(i);
            out.write(header+"\n");
            String[] one=new String[42];Arrays.fill(one,"");
            one[0]="1794500000000";one[16]="0";one[2]="15";one[4]="50";
            String[] two=one.clone();two[0]="1794500003000";
            two[16]="0.01";two[4]="49";
            out.write(String.join(",",one)+"\n");
            out.write(String.join(",",two)+"\n");
        }
        try{
            T1WhatIfEnergyModel.Trip data=T1WhatIfEnergyModel.read(file);
            check(data.originalColumns==42,"old schema");
            check(data.points.size()==2,"old rows");
            check(data.points.get(0).mode==Integer.MIN_VALUE,
                "missing HEV raw becomes unknown, never Smart by default");
        }finally{
            file.delete();folder.delete();f.delete();
        }
    }
    public static void main(String[] args)throws Exception{
        sampleAndPhysics();
        parseBackCompat();
        System.out.println("OK: history simulator energy bounds and CSV compatibility");
    }
}
