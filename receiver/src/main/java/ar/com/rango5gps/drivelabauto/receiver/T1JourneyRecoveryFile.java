package ar.com.rango5gps.drivelabauto.receiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Reconstruct the last persisted local OEM checkpoint of a discontinued day
 * without changing files or inventing kilometres/segment energy.
 *
 * CSV schema indices are declared here; parsing uses only files stored by
 * T1TripRecorderService in the private files/t1_trips folder.
 */
final class T1JourneyRecoveryFile {
    static final class End {
        final long timestamp;
        final double km,rawFuel,rawElectric;
        final int fuelCount,electricCount;
        final boolean fuelComplete,electricComplete;
        End(long t,double km,double f,double e,int nf,int ne,
            boolean fuelComplete,boolean electricComplete){
            timestamp=t;this.km=km;rawFuel=f;rawElectric=e;
            fuelCount=nf;electricCount=ne;
            this.fuelComplete=fuelComplete;
            this.electricComplete=electricComplete;
        }
    }
    static End read(File dir,String tripId)throws IOException {
        if(tripId==null||!tripId.matches("[0-9]{8}_[0-9]{6}"))
            throw new IOException("Identificador de jornada inválido");
        File samples=new File(dir,"trip_"+tripId+".csv");
        if(!samples.isFile())
            throw new IOException("Falta el CSV original: "+samples.getName());
        long ts=0;double km=Double.NaN;int fuelCount=-1,electricCount=-1;
        try(BufferedReader in=new BufferedReader(new FileReader(samples))){
            String line=in.readLine();
            if(line==null||!line.startsWith("timestamp_epoch_ms,"))
                throw new IOException("Encabezado de jornada no reconocido");
            int columns=line.split(",",-1).length;
            while((line=in.readLine())!=null){
                String[] v=line.split(",",-1);
                if(v.length<columns||v.length<38)continue; // torn last record
                try{
                    long t=Long.parseLong(v[0]);
                    double k=Double.parseDouble(v[16]);
                    int nf=Integer.parseInt(v[36]),ne=Integer.parseInt(v[37]);
                    if(t>0&&Double.isFinite(k)&&k>=0&&nf>=0&&ne>=0){
                        ts=t;km=k;fuelCount=nf;electricCount=ne;
                    }
                }catch(NumberFormatException ignored){}
            }
        }
        if(ts<=0||!Double.isFinite(km))
            throw new IOException("CSV sin una muestra completa");
        int nf=0,ne=0;double fuel=0,elec=0;
        File events=new File(dir,"trip_oem_"+tripId+".csv");
        if(events.isFile()){
            try(BufferedReader in=new BufferedReader(new FileReader(events))){
                String line;
                while((line=in.readLine())!=null){
                    String[] v=line.split(",",-1);
                    if(v.length<3)continue;
                    try{
                        long t=Long.parseLong(v[0]);
                        double val=Double.parseDouble(v[2]);
                        if(t>ts||!Double.isFinite(val))continue;
                        if("NAFTA_500_m".equals(v[1])&&nf<fuelCount){
                            nf++;fuel+=val;
                        }else if("ELÉCTRICO_500_m".equals(v[1])&&ne<electricCount){
                            ne++;elec+=val;
                        }
                    }catch(NumberFormatException ignored){}
                }
            }
        }
        return new End(ts,km,fuel,elec,nf,ne,
            nf==fuelCount,ne==electricCount);
    }
    private T1JourneyRecoveryFile(){}
}
