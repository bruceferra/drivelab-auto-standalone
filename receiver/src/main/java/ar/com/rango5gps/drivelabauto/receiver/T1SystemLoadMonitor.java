package ar.com.rango5gps.drivelabauto.receiver;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/**
 * Low-cost read-only Android 11 telemetry for the whole head unit plus
 * DriveLab. Does not need root, DUMP, PACKAGE_USAGE_STATS or system APIs.
 *
 * RAM: ActivityManager.MemoryInfo totalMem/availMem = WHOLE Android OS.
 * CPU global: /proc/stat deltas if accessible; Android OEM kernels may
 * restrict it; never misrepresent app CPU as total CPU.
 * App RAM: Debug.getPss() own process only, not launcher/OEM apps.
 * App CPU: Process.getElapsedCpuTime() delta / uptime / online cores.
 * UI lag: measured by caller on the main thread, not synthesized here.
 */
final class T1SystemLoadMonitor {
    static final class Result {
        final long totalBytes,availableBytes,appPssKb,thresholdBytes;
        final boolean lowMemory;
        final double cpuGlobalPct,cpuAppPct;
        final int cores;
        final String hardware,board,model;
        Result(long total,long available,long pss,long threshold,
               boolean low,double cpuGlobal,double cpuApp,int cpuCores,
               String hw,String brd,String mdl){
            totalBytes=total;availableBytes=available;appPssKb=pss;
            thresholdBytes=threshold;lowMemory=low;
            cpuGlobalPct=cpuGlobal;cpuAppPct=cpuApp;cores=cpuCores;
            hardware=hw;board=brd;model=mdl;
        }
        long usedBytes(){
            return Math.max(0,totalBytes-availableBytes);
        }
        static String mb(long bytes){
            return String.format(Locale.ROOT,"%.0f",bytes/1048576.0);
        }
        static String gb(long bytes){
            return String.format(Locale.ROOT,"%.1f",bytes/1073741824.0);
        }
        static String pct(double v){
            return Double.isFinite(v)?
                String.format(Locale.ROOT,"%.0f%%",v):"N/D";
        }
        String compact(){
            return "RAM SISTEMA "+gb(usedBytes())+"/"+gb(totalBytes())+
                " GB · LIBRE "+gb(availableBytes())+" GB"+
                (lowMemory?" · MEMORIA BAJA":"")+
                "    CPU TOTAL "+pct(cpuGlobalPct)+
                "    APP "+mb(appPssKb*1024L)+" MB / "+
                pct(cpuAppPct);
        }
        String details(){
            return compact()+"\n"+
                "RAM libre: "+mb(availableBytes())+" MB · umbral Android "+
                mb(thresholdBytes)+" MB"+
                "\nCPU total: "+pct(cpuGlobalPct)+
                " (lectura /proc/stat, si está permitido)"+
                "\nCPU DriveLab: "+pct(cpuAppPct)+
                " (normalizado sobre "+cores+" núcleos)"+
                "\nRAM DriveLab PSS: "+mb(appPssKb*1024L)+
                " MB; NO incluye el launcher ni otros procesos"+
                "\nHW Android: "+hardware+" · BOARD "+board+
                " · DEVICE "+model+
                "\nNota: RAM TOTAL incluye sistema y launcher; CPU TOTAL "+
                "puede ser N/D si el fabricante bloqueó /proc/stat.";
        }
    }
    private static final class CpuTicks {
        long busy,idle;
        CpuTicks(long b,long i){busy=b;idle=i;}
    }
    private final Context app;
    private CpuTicks previousCpu;
    private long previousAppCpu=-1,previousTime=-1;

    T1SystemLoadMonitor(Context c){app=c.getApplicationContext();}
    Result sample(){
        ActivityManager manager=
            (ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
        if(manager!=null)manager.getMemoryInfo(mem);
        // Own process PSS is accessible on Android 11 without privileges.
        long pss=0;
        try{pss=Debug.getPss();}
        catch(Exception ignored){}
        long now=SystemClock.elapsedRealtime();
        long appCpu=Process.getElapsedCpuTime();
        int cpuCores=Math.max(1,Runtime.getRuntime().availableProcessors());
        double appPct=Double.NaN;
        if(previousTime>0&&now>previousTime&&
           appCpu>=previousAppCpu){
            appPct=100.0*(appCpu-previousAppCpu)/
                ((now-previousTime)*(double)cpuCores);
            appPct=Math.max(0,Math.min(100,appPct));
        }
        previousTime=now;previousAppCpu=appCpu;

        CpuTicks cpu=readCpu();
        double global=Double.NaN;
        if(cpu!=null&&previousCpu!=null){
            long busy=cpu.busy-previousCpu.busy;
            long idle=cpu.idle-previousCpu.idle;
            if(busy>=0&&idle>=0&&busy+idle>0)
                global=100.0*busy/(busy+idle);
        }
        previousCpu=cpu;
        return new Result(mem.totalMem,mem.availMem,pss,mem.threshold,
            mem.lowMemory,global,appPct,cpuCores,
            Build.HARDWARE,Build.BOARD,Build.MODEL);
    }
    private static CpuTicks readCpu(){
        try(BufferedReader reader=new BufferedReader(
                new FileReader("/proc/stat"))){
            String line=reader.readLine();
            if(line==null||!line.startsWith("cpu "))return null;
            String[] t=line.trim().split("\\s+");
            if(t.length<5)return null;
            long busy=0,idle=0;
            for(int i=1;i<t.length;i++){
                long value=Long.parseLong(t[i]);
                if(i==4||i==5)idle+=value;
                else busy+=value;
            }
            return new CpuTicks(busy,idle);
        }catch(Exception ignored){
            // Android 11 OEM /proc/stat SELinux policy may deny reads.
            return null;
        }
    }
}
