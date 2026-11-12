package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Private, one-click diagnostic report upload through the existing QR-linked
 * Supabase channel. No clipboard, no VIN, no GPS; local append-only CSV is
 * preserved even if server upload fails. All report uploads are serialized. */
final class T1ReportShare {
    interface Callback { void finished(String status); }
    private static final ExecutorService QUEUE=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final int MAX_TEXT=250000;

    private T1ReportShare(){}
    private static String csv(String value){
        return "\""+value.replace("\u0000","").replace("\"","\"\"")+"\"";
    }
    static void share(Context c,String type,String report,Callback cb){
        Context app=c.getApplicationContext();
        QUEUE.execute(()->{
            String status;
            try{
                if(report==null||report.trim().isEmpty())
                    throw new IllegalArgumentException("informe vacío");
                if(report.length()>MAX_TEXT)
                    throw new IllegalArgumentException("informe demasiado largo");
                String category=type==null?"generico":type.replaceAll(
                    "[^A-Za-z0-9_-]","_");
                if(category.isEmpty())category="generico";
                String date=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT)
                    .format(new Date());
                File dir=T1TripRecorderService.tripDir(app);
                File file=null;
                for(int i=0;i<50;i++){
                    String id=i==0?date:new SimpleDateFormat(
                        "yyyyMMdd_HHmmss",Locale.ROOT)
                        .format(new Date(System.currentTimeMillis()+i*1000L));
                    File candidate=new File(dir,"code_diag_"+id+".csv");
                    if(!candidate.exists()){file=candidate;break;}
                }
                if(file==null)throw new IllegalStateException("sin nombre CSV libre");
                String body="timestamp_epoch_ms;tipo;contenido\n"+
                    System.currentTimeMillis()+";"+csv(category)+";"+
                    csv(report)+"\n";
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                if(bytes.length>560000)
                    throw new IllegalArgumentException("informe excede tamaño de nube");
                try(FileOutputStream out=new FileOutputStream(file,false)){
                    out.write(bytes);out.flush();
                }
                if(!T1CloudDiagnostics.active(app)){
                    status="GUARDADO LOCAL: "+file.getName()+
                        " · activá nube con QR en DIAGNÓSTICO para subir.";
                }else{
                    CountDownLatch receipt=new CountDownLatch(1);
                    final String[] reply={"Error de subida no confirmado"};
                    T1CloudDiagnostics.upload(app,file,(message,url)->{
                        reply[0]=message;receipt.countDown();
                    });
                    if(receipt.await(90,TimeUnit.SECONDS))
                        status=reply[0];
                    else status="CSV LOCAL GUARDADO: "+file.getName()+
                        " · nube sin respuesta, no se perdió informe";
                }
            }catch(Exception e){
                status="No se pudo compartir informe: "+
                    e.getClass().getSimpleName()+
                    (e.getMessage()==null?"":" · "+e.getMessage());
            }
            final String done=status;
            if(cb!=null)MAIN.post(()->cb.finished(done));
        });
    }
}
