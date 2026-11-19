package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** Screenshot archive is private to Receiver until user presses Compartir.
 * Never uploads automatically; never captures / reads any credential, OEM
 * signal, clipboard, camera or arbitrary file.
 */
final class T1ScreenshotShareStore {
    private T1ScreenshotShareStore(){}
    static File folder(Context context){
        File dir=new File(context.getFilesDir(),"screenshots");
        if(!dir.isDirectory()&&!dir.mkdirs())
            throw new IllegalStateException("No se pudo crear carpeta capturas");
        return dir;
    }
    static String save(Context context,HardwareBuffer buffer,
            ColorSpace colorSpace)throws Exception{
        if(buffer==null)throw new IllegalArgumentException("Captura vacía");
        Bitmap hardware=null;
        Bitmap cpu=null;
        try{
            hardware=Bitmap.wrapHardwareBuffer(buffer,colorSpace);
            if(hardware==null)
                throw new IllegalStateException("Android no dio imagen");
            if(hardware.getWidth()>4096||hardware.getHeight()>2160||
                hardware.getWidth()<320||hardware.getHeight()<240)
                throw new IllegalArgumentException("Tamaño de captura inválido");
            cpu=hardware.copy(Bitmap.Config.ARGB_8888,false);
            if(cpu==null)
                throw new IllegalStateException("No se copió imagen del sistema");
            File dir=folder(context);
            String name="t1_capture_"+
                new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT)
                    .format(new Date())+".png";
            File target=new File(dir,name);
            try(FileOutputStream stream=new FileOutputStream(target)){
                if(!cpu.compress(Bitmap.CompressFormat.PNG,100,stream))
                    throw new IllegalStateException("No se generó PNG");
                stream.flush();
            }
            if(target.length()<100||target.length()>18_000_000L){
                target.delete();
                throw new IllegalStateException("Captura PNG fuera de límite");
            }
            cleanup(dir);
            return name;
        }finally{
            if(cpu!=null&&!cpu.isRecycled())cpu.recycle();
            if(hardware!=null&&!hardware.isRecycled())hardware.recycle();
            if(buffer!=null)buffer.close();
        }
    }
    static File selected(Context context,String name){
        if(name==null||!name.matches(
                "t1_capture_[0-9]{8}_[0-9]{6}_[0-9]{3}\\.png"))
            throw new SecurityException("Identificador de captura inválido");
        File file=new File(folder(context),name);
        if(!file.isFile()||file.length()<100||file.length()>18_000_000L)
            throw new IllegalStateException("Captura no encontrada");
        return file;
    }
    static File latest(Context context){
        File[] files=folder(context).listFiles(
            (dir,name)->name.matches(
                "t1_capture_[0-9]{8}_[0-9]{6}_[0-9]{3}\\.png"));
        if(files==null||files.length==0)return null;
        Arrays.sort(files,Comparator.comparing(File::getName).reversed());
        return files[0];
    }
    private static void cleanup(File dir){
        File[] files=dir.listFiles(
            (d,n)->n.matches(
                "t1_capture_[0-9]{8}_[0-9]{6}_[0-9]{3}\\.png"));
        if(files==null||files.length<=12)return;
        Arrays.sort(files,Comparator.comparing(File::getName).reversed());
        for(int i=12;i<files.length;i++)files[i].delete();
    }
}
