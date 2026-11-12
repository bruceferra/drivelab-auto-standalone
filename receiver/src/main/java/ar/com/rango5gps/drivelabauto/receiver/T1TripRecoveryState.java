package ar.com.rango5gps.drivelabauto.receiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Read last complete sample of an existing session before resuming after
 * power-off, process kill or APK update. Never truncate or rewrite the CSV.
 * Count all complete rows, even when an old SharedPreferences commit lags.
 */
final class T1TripRecoveryState {
    static final class State {
        final int samples;
        final long timestamp;
        final double km,hvOut,hvIn;
        State(int count,long timestamp,double km,double out,double in) {
            samples=count;this.timestamp=timestamp;this.km=km;
            hvOut=out;hvIn=in;
        }
    }
    static State read(File csv)throws IOException{
        if(csv==null||!csv.isFile()||csv.length()==0)
            throw new IOException("Archivo de TRIP ausente o vacío");
        int count=0;long timestamp=0;double km=0,out=0,in=0;
        try(BufferedReader reader=new BufferedReader(new FileReader(csv))){
            String header=reader.readLine();
            if(header==null||!header.startsWith("timestamp_epoch_ms,gear_1033"))
                throw new IOException("Formato de TRIP no reconocido");
            String line;
            while((line=reader.readLine())!=null){
                String[] v=line.split(",",-1);
                if(v.length<39)continue;
                try{
                    long t=Long.parseLong(v[0]);
                    double distance=Double.parseDouble(v[16]);
                    double grossOut=Double.parseDouble(v[17]);
                    double grossIn=Double.parseDouble(v[18]);
                    if(t>0&&Double.isFinite(distance)&&distance>=0&&
                        Double.isFinite(grossOut)&&grossOut>=0&&
                        Double.isFinite(grossIn)&&grossIn>=0){
                        count++;timestamp=t;km=distance;out=grossOut;in=grossIn;
                    }
                }catch(NumberFormatException ignored){}
            }
        }
        if(count==0)throw new IOException("No hay muestras completas de TRIP");
        return new State(count,timestamp,km,out,in);
    }
    private T1TripRecoveryState(){}
}
