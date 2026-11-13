package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Read-only OEM callback experiment based on original ClusterInteraction.apk.
 *
 * IClusterInteraction register codes: Elec500=133, Fu500=135,
 * FuCns50=137, ElecCns50=139. Unregister are +1.
 * Each callback: onAvg*Changed(float), Binder transaction 1.
 *
 * Listener registration is NOT an ECU setter or a car control. No vehicle
 * property writes. Callback values are raw/uncalibrated and NEVER trip litres.
 */
final class T1OemConsumptionCallbacks {
    private static final String PKG="com.autolink.clusterinteraction";
    private static final String CLASS="com.autolink.clusterinteraction.CiService";
    private static final String TOKEN=
        "com.autolink.adapterinterface.clusterinteraction.IClusterInteraction";
    private static final long DURATION_MS=180000L;
    private static final String[] LABELS={
        "NAFTA 500 m","ELÉCTRICO 500 m","NAFTA 50 km","ELÉCTRICO 50 km"
    };
    private static final String[] CALLBACK_TOKENS={
        "com.autolink.adapterinterface.clusterinteraction.IAvgFuCallback",
        "com.autolink.adapterinterface.clusterinteraction.IAvgElecCallback",
        "com.autolink.adapterinterface.clusterinteraction.IAvgFuCnsCallback",
        "com.autolink.adapterinterface.clusterinteraction.IAvgElecCnsCallback"
    };
    private static final int[] REGISTER={135,133,137,139};
    private static final int[] UNREGISTER={136,134,138,140};

    private final Context app;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private final Object dataLock=new Object();
    private final int[] count=new int[4];
    private final double[] rawSums=new double[2]; // 500m callback sums, raw and uncalibrated
    private final long[] lastEventMs=new long[4];
    private final java.util.ArrayDeque<Float> recentFuel=new java.util.ArrayDeque<>();
    private T1Fuel500mWindow history100=new T1Fuel500mWindow();
    private final String[] last={"—","—","—","—"};
    private volatile String state="Sin prueba. Registro de eventos apagado.";
    private volatile String registration="Sin registros.";
    private volatile File logFile;
    private volatile CountDownLatch stopSignal;
    private volatile long deadlineMs;
    private volatile boolean tripMode=false;
    private volatile boolean cockpitMode=false;
    private volatile long lastPublishAt=0;
    private volatile String tripId="";
    private volatile Thread listenerThread;
    private final float[] lastRaw={
        Float.NaN,Float.NaN,Float.NaN,Float.NaN
    };
    /** Read-only snapshot of actual OEM segment callbacks. Not litres/kWh. */
    static final class SegmentSnapshot {
        final int fuelCount,electricCount;
        final float fuelLast,electricLast,fuel50Last,electric50Last;
        final double fuelRawSum,electricRawSum;
        final long fuelLastAtMs;
        final double fuelRecentL100Candidate;
        final int fuelRecentCount;
        SegmentSnapshot(int fu,int el,float f,float e,float f50,float e50,
                        double fuelSum,double electricSum,long timestamp,
                        double recent,int recentCount){
            fuelCount=fu;electricCount=el;fuelLast=f;electricLast=e;
            fuel50Last=f50;electric50Last=e50;
            fuelRawSum=fuelSum;electricRawSum=electricSum;
            fuelLastAtMs=timestamp;
            fuelRecentL100Candidate=recent;
            fuelRecentCount=recentCount;
        }
    }
    SegmentSnapshot tripSegments(){
        synchronized(dataLock){
            double sum=0;
            for(float x:recentFuel)sum+=x;
            return new SegmentSnapshot(count[0],count[1],lastRaw[0],
                lastRaw[1],lastRaw[2],lastRaw[3],rawSums[0],rawSums[1],
                lastEventMs[0],
                recentFuel.isEmpty()?Double.NaN:sum/recentFuel.size(),
                recentFuel.size());
        }
    }

    T1OemConsumptionCallbacks(Context context){
        app=context.getApplicationContext();
    }
    boolean busy(){return running.get();}
    /**
     * Resolve a previously finished OEM recording even after Activity recreation
     * or switching screens. The test listener may be OFF while a CSV is still
     * fully available for export. Only Receiver-generated CSV names are allowed.
     */
    File lastLog(){
        File current=logFile;
        if(current!=null&&current.isFile()&&current.length()>0)return current;
        File[] saved=T1TripRecorderService.tripDir(app).listFiles(
            f->f.isFile()&&f.getName().matches(
                "oem_callbacks_[0-9]{8}_[0-9]{6}\\.csv"));
        if(saved==null||saved.length==0)return null;
        java.util.Arrays.sort(saved,(a,b)->a.getName().compareTo(b.getName()));
        return saved[saved.length-1];
    }
    String snapshot(){
        StringBuilder sb=new StringBuilder();
        sb.append(running.get()?
            (tripMode?"● TRIP · escuchando eventos OEM sin límite de 3 minutos":
                "● ESCUCHANDO eventos OEM · "+
                (Math.max(0,deadlineMs-SystemClock.elapsedRealtime())/1000)+" s restantes"):
            "○ "+state);
        sb.append("\n").append(registration);
        synchronized(dataLock){
            for(int i=0;i<4;i++){
                sb.append("\n").append(LABELS[i]).append(": ")
                  .append(count[i]).append(" evento(s) · último ")
                  .append(last[i]);
            }
        }
        sb.append("\nSin calibración: los eventos NO son litros ni kWh acumulados.");
        File available=lastLog();
        if(available!=null)
            sb.append("\nCSV guardado: ").append(available.getName())
                .append(running.get()?" (grabación activa)":
                    " · disponible para EXPORTAR aunque el registro esté apagado");
        else if(!running.get())
            sb.append("\nSin CSV anterior; tocá ESCUCHAR EVENTOS OEM · 3 MIN para crear uno.");
        return sb.toString();
    }
    boolean start(){return startInternal(false,"",false);}
    /** Listener lives for the entire user-started TRIP, not three minutes. */
    boolean startForTrip(String id){
        if(id==null||!id.matches("[0-9]{8}_[0-9]{6}"))return false;
        return startInternal(true,id,false);
    }
    /**
     * Dashboard-only, read-only OEM fuel callback stream. Does NOT create a
     * trip, enable recording, persist GPS or add any OEM test CSV.
     * Runs only while the Cockpit Activity is visible and no TRIP listener
     * is already feeding the shared live-value cache.
     */
    boolean startForCockpit(){return startInternal(false,"",true);}
    private boolean startInternal(boolean forTrip,String id,boolean passive){
        if(!running.compareAndSet(false,true))return false;
        synchronized(dataLock){
            for(int i=0;i<4;i++){
                count[i]=0;last[i]="—";lastRaw[i]=Float.NaN;
            }
            rawSums[0]=0d;rawSums[1]=0d;
            java.util.Arrays.fill(lastEventMs,0L);
            recentFuel.clear();
        }
        tripMode=forTrip;
        cockpitMode=passive;
        lastPublishAt=0;
        tripId=id;
        stopSignal=new CountDownLatch(1);
        state=forTrip?"TRIP · escuchando segmentos OEM":
            passive?"COCKPIT · lectura continua sin grabar viaje":
                "Iniciando prueba.";
        registration="Conectando ClusterInteraction OEM…";
        logFile=null;
        deadlineMs=forTrip||passive?Long.MAX_VALUE:
            SystemClock.elapsedRealtime()+DURATION_MS;
        listenerThread=new Thread(this::listen,
            forTrip?"T1-Trip-500m-OEM":
                passive?"T1-Cockpit-Live-Fuel":"T1-Avg-CI-Callbacks");
        listenerThread.start();
        return true;
    }
    void awaitStopped(long millis){
        Thread t=listenerThread;
        if(t!=null&&t!=Thread.currentThread())
            try{t.join(Math.max(1,millis));}catch(InterruptedException ignored){}
    }
    void stop(){
        CountDownLatch latch=stopSignal;
        if(latch!=null)latch.countDown();
    }
    private static void register(IBinder ci,int transaction,IBinder callback)
        throws Exception{
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(TOKEN);
            in.writeStrongBinder(callback);
            if(!ci.transact(transaction,in,out,0))
                throw new IllegalStateException("Binder tx "+transaction+" rechazado");
            out.readException();
        }finally{
            in.recycle();out.recycle();
        }
    }
    /** Rehydrate 100 raw OEM segment events on cold start. Historical TRIP
     * CSVs are read only when a persisted ring does not already exist.
     * No user GPS, individual trip history, or identifying data is copied.
     * Gaps between recordings are not interpreted as continuously driven 50 km.
     */
    private void restoreFuelHistory(){
        android.content.SharedPreferences p=app.getSharedPreferences(
            "t1_live_consumption",Context.MODE_PRIVATE);
        String encoded=p.getString("rawFuel100","");
        T1Fuel500mWindow window=T1Fuel500mWindow.decode(encoded);
        if(window.size()==0){
            try{
                File dir=T1TripRecorderService.tripDir(app);
                File[] files=dir.listFiles(f->f.isFile()&&
                    f.getName().matches("trip_oem_[0-9]{8}_[0-9]{6}\\\\.csv"));
                if(files!=null){
                    java.util.Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
                    // Read only the ends of recent trip files: avoids a full
                    // multi-GB historical scan and never runs on UI thread.
                    for(int i=Math.max(0,files.length-30);i<files.length;i++){
                        try(java.io.RandomAccessFile input=
                                new java.io.RandomAccessFile(files[i],"r")){
                            long offset=Math.max(0,input.length()-300000L);
                            input.seek(offset);
                            if(offset>0)input.readLine(); // partial line
                            String line;
                            while((line=input.readLine())!=null){
                                String[] fields=line.split(",",-1);
                                if(fields.length<3||
                                    !"NAFTA_500_m".equals(fields[1]))continue;
                                try{window.add(Float.parseFloat(fields[2]));}
                                catch(NumberFormatException ignored){}
                            }
                        }catch(Exception ignored){}
                    }
                }
            }catch(Exception ignored){}
            if(window.size()>0)
                p.edit().putString("rawFuel100",window.encode()).apply();
        }
        synchronized(dataLock){history100=window;}
    }
    private void record(int channel,float raw){
        String value=Float.toString(raw);
        synchronized(dataLock){
            // 50km OEM callbacks repeat the identical rolling value hundreds
            // of times. Compact ONLY the TRIP listener; NEVER dedup 500m
            // segments, even if two adjacent segments have the same value.
            if(tripMode&&channel>=2 &&
                Float.floatToIntBits(lastRaw[channel])==
                Float.floatToIntBits(raw))return;
            count[channel]++;
            last[channel]=value;
            lastRaw[channel]=raw;
            lastEventMs[channel]=System.currentTimeMillis();
            if(channel<2&&Float.isFinite(raw))rawSums[channel]+=raw;
            // Candidate rolling L/100 over the latest five *independent*
            // 500 m fuel callbacks. Not confirmed fuel metering.
            if(channel==0&&Float.isFinite(raw)&&raw>=0&&raw<=35){
                recentFuel.addLast(raw);
                while(recentFuel.size()>5)recentFuel.removeFirst();
                // One OEM notification = one reported 500m segment. Keep
                // at most 100 raw events across journeys/restarts; no conversion
                // to litres until calibrated against the OEM 50km reading.
                history100.add(raw);
                app.getSharedPreferences("t1_live_consumption",
                    Context.MODE_PRIVATE).edit()
                    .putString("rawFuel100",history100.encode())
                    .putLong("rawFuel100At",System.currentTimeMillis())
                    .apply();
            }
            // Keep dashboard fuel numbers fresh whether a trip is
            // being recorded or merely the Cockpit is open. The OEM
            // 50km channel may repeat extremely often: throttle that
            // publication, but never drop genuine 500m segment events.
            long now=lastEventMs[channel];
            if((tripMode||cockpitMode)&&
                (channel==0||(channel==2&&now-lastPublishAt>=8000L))){
                double sum=0;
                for(float recent:recentFuel)sum+=recent;
                float average=recentFuel.isEmpty()?Float.NaN:
                    (float)(sum/recentFuel.size());
                app.getSharedPreferences("t1_live_consumption",
                    Context.MODE_PRIVATE).edit()
                    .putLong("sampleAt",now)
                    .putLong("fuelAt",lastEventMs[0])
                    .putFloat("fuelRaw",lastRaw[0])
                    .putFloat("fuelRecent",average)
                    .putInt("fuelRecentCount",recentFuel.size())
                    .putFloat("oem50",lastRaw[2])
                    .putString("source",tripMode?"TRIP":"COCKPIT")
                    .apply();
                lastPublishAt=now;
            }
            File file=logFile;
            if(file!=null){
                String row=System.currentTimeMillis()+","+
                    LABELS[channel].replace(" ","_")+","+
                    value+"\n";
                try(FileOutputStream o=new FileOutputStream(file,true)){
                    o.write(row.getBytes(StandardCharsets.UTF_8));
                }catch(Exception ignored){}
            }
        }
    }
    private final class FloatCallback extends Binder {
        private final String descriptor;
        private final int channel;
        FloatCallback(int channel){
            this.channel=channel;
            descriptor=CALLBACK_TOKENS[channel];
            attachInterface(null,descriptor);
        }
        @Override protected boolean onTransact(int code,Parcel in,Parcel out,int flags)
            throws RemoteException{
            if(code==IBinder.INTERFACE_TRANSACTION){
                if(out!=null)out.writeString(descriptor);
                return true;
            }
            if(code==1){
                in.enforceInterface(descriptor);
                float raw=in.readFloat();
                record(channel,raw);
                if(out!=null)out.writeNoException();
                return true;
            }
            return super.onTransact(code,in,out,flags);
        }
    }
    private void listen(){
        ServiceConnection connection=null;
        boolean bound=false;
        IBinder ci=null;
        FloatCallback[] callbacks=new FloatCallback[4];
        boolean[] registered=new boolean[4];
        StringBuilder errors=new StringBuilder();
        try{
            // Restoration/backfill is performed on the listener's own thread,
            // not on Cockpit/UI. Never changes trip logs or source CSV files.
            restoreFuelHistory();
            File dir=T1TripRecorderService.tripDir(app);
            String time=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT)
                .format(new Date());
            File file=cockpitMode?null:new File(dir,tripMode?
                "trip_oem_"+tripId+".csv":"oem_callbacks_"+time+".csv");
            boolean resume=tripMode&&file!=null&&file.isFile()&&file.length()>0;
            if(resume){
                // Rebuild counts and raw sums from the original event log.
                // No previous 500 m event is duplicated or discarded merely
                // because Android restarted a foreground service.
                try(java.io.BufferedReader reader=new java.io.BufferedReader(
                        new java.io.FileReader(file))){
                    String line=reader.readLine();
                    if(line==null||!line.startsWith("timestamp_epoch_ms,channel,"))
                        throw new IllegalStateException(
                            "CSV OEM anterior con formato desconocido");
                    while((line=reader.readLine())!=null){
                        String[] fields=line.split(",",-1);
                        if(fields.length<3)continue;
                        int channel=-1;
                        for(int i=0;i<LABELS.length;i++)
                            if(LABELS[i].replace(" ","_").equals(fields[1])){
                                channel=i;break;
                            }
                        if(channel<0)continue;
                        try{
                            float value=Float.parseFloat(fields[2]);
                            synchronized(dataLock){
                                count[channel]++;
                                last[channel]=Float.toString(value);
                                lastRaw[channel]=value;
                                try{
                                    lastEventMs[channel]=Long.parseLong(fields[0]);
                                }catch(NumberFormatException ignored){}
                                if(channel<2&&Float.isFinite(value))
                                    rawSums[channel]+=value;
                                if(channel==0&&Float.isFinite(value)&&
                                   value>=0&&value<=35){
                                    recentFuel.addLast(value);
                                    while(recentFuel.size()>5)recentFuel.removeFirst();
                                }
                            }
                        }catch(NumberFormatException ignored){}
                    }
                }
            }
            if(!resume&&!cockpitMode){
                try(FileOutputStream out=new FileOutputStream(file,false)){
                    out.write("timestamp_epoch_ms,channel,value_float_raw\n"
                        .getBytes(StandardCharsets.UTF_8));
                }
            }else if(resume){
                // Isolate a torn final record from subsequent full lines.
                try(java.io.RandomAccessFile raf=new java.io.RandomAccessFile(
                        file,"rw")){
                    if(raf.length()>0){
                        raf.seek(raf.length()-1);
                        if(raf.readByte()!='\n'){
                            raf.seek(raf.length());raf.writeByte('\n');
                        }
                    }
                }
            }
            // Cockpit observation must never produce diagnostic CSV files.
            logFile=cockpitMode?null:file;
            CountDownLatch ready=new CountDownLatch(1);
            AtomicReference<IBinder> remote=new AtomicReference<>();
            connection=new ServiceConnection(){
                @Override public void onServiceConnected(ComponentName n,IBinder binder){
                    remote.set(binder);ready.countDown();
                }
                @Override public void onServiceDisconnected(ComponentName n){
                    ready.countDown();
                }
                @Override public void onNullBinding(ComponentName n){ready.countDown();}
                @Override public void onBindingDied(ComponentName n){ready.countDown();}
            };
            bound=app.bindService(new Intent().setComponent(
                new ComponentName(PKG,CLASS)),connection,0);
            if(!bound)throw new IllegalStateException("CiService no enlazó");
            if(!ready.await(4000,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Tiempo de espera CiService");
            ci=remote.get();
            if(ci==null||!ci.isBinderAlive()||
                !TOKEN.equals(ci.getInterfaceDescriptor()))
                throw new IllegalStateException("Descriptor IClusterInteraction distinto");
            int ok=0;
            for(int i=0;i<4;i++){
                callbacks[i]=new FloatCallback(i);
                try{
                    register(ci,REGISTER[i],callbacks[i]);
                    registered[i]=true;
                    ok++;
                }catch(Exception e){
                    if(errors.length()>0)errors.append("; ");
                    errors.append(LABELS[i]).append(": ")
                        .append(e.getClass().getSimpleName());
                }
            }
            registration="Inscripciones aceptadas: "+ok+"/4"+
                (errors.length()>0?" · "+errors:"");
            if(ok==0)throw new IllegalStateException("Todas las inscripciones rechazadas");
            if(tripMode||cockpitMode){
                stopSignal.await();
                state=tripMode?
                    "TRIP finalizado; eventos de 500 m conservados.":
                    "COCKPIT cerrado; escucha OEM terminada.";
            }else{
                long remaining=deadlineMs-SystemClock.elapsedRealtime();
                if(remaining>0)stopSignal.await(remaining,TimeUnit.MILLISECONDS);
                state="Prueba terminada; resultados conservados.";
            }
        }catch(Exception e){
            state="PRUEBA FALLIDA: "+e.getClass().getSimpleName()+
                (e.getMessage()==null?"":" · "+e.getMessage());
        }finally{
            if(ci!=null&&ci.isBinderAlive()){
                for(int i=0;i<4;i++)if(registered[i]&&callbacks[i]!=null){
                    try{register(ci,UNREGISTER[i],callbacks[i]);}
                    catch(Exception ignored){}
                }
            }
            if(bound&&connection!=null)
                try{app.unbindService(connection);}catch(Exception ignored){}
            running.set(false);
        }
    }
}
