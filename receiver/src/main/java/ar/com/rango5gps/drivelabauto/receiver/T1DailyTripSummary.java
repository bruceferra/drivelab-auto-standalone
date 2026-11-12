package ar.com.rango5gps.drivelabauto.receiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * Read-only daily rollup of distinct master TRIP CSVs.
 * Explicit user STOP always ends one session. This view never joins the
 * original files or changes session / customer checkpoint state.
 */
final class T1DailyTripSummary {
    static final class Result {
        final double km;
        final int sessions;
        final int unreadable;
        Result(double km,int sessions,int unreadable) {
            this.km=km;this.sessions=sessions;this.unreadable=unreadable;
        }
    }
    static Result read(File dir,String day) {
        if(dir==null||day==null||!day.matches("[0-9]{8}"))
            return new Result(0,0,0);
        File[] files=dir.listFiles((parent,name)->
            name.matches("trip_"+day+"_[0-9]{6}\\.csv"));
        if(files==null)return new Result(0,0,0);
        Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
        double total=0;int count=0,bad=0;
        for(File file:files) {
            try {
                double end=lastRecordedKm(file);
                if(Double.isFinite(end)&&end>=0){total+=end;count++;}
                else bad++;
            }catch(IOException e){bad++;}
        }
        return new Result(total,count,bad);
    }
    private static double lastRecordedKm(File file)throws IOException {
        double last=Double.NaN;
        try(BufferedReader in=new BufferedReader(new FileReader(file))) {
            String h=in.readLine();
            if(h==null)return Double.NaN;
            String[] columns=h.split(",",-1);
            int idx=-1;
            for(int i=0;i<columns.length;i++)
                if("distance_km_est".equals(columns[i])){idx=i;break;}
            if(idx<0)return Double.NaN;
            String line;
            while((line=in.readLine())!=null) {
                String[] f=line.split(",",-1);
                // Ignore torn final lines and incomplete rows after a reboot.
                if(f.length!=columns.length)continue;
                try {
                    long timestamp=Long.parseLong(f[0]);
                    double km=Double.parseDouble(f[idx]);
                    if(timestamp>0&&Double.isFinite(km)&&km>=0&&
                        (!Double.isFinite(last)||km>=last))
                        last=km;
                }catch(NumberFormatException ignored){}
            }
        }
        return last;
    }
    private T1DailyTripSummary(){}
}
