package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit user-only LOCAL history deletion; never touches server or other apps.
 * Rejects running, paused, desired or pending sessions before touching disk.
 * Maintains source CSV/sidecar consistency; does not delete charge records.
 */
final class T1TripDeletion {
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private T1TripDeletion(){}
    static boolean busy(){return BUSY.get();}
    private static boolean hasActive(Context c){
        SharedPreferences p=c.getSharedPreferences(T1TripRecorderService.PREFS,Context.MODE_PRIVATE);
        return p.getBoolean(T1TripRecorderService.RUNNING,false)||
            p.getBoolean(T1TripRecorderService.DESIRED,false)||
            p.getBoolean(T1TripRecorderService.PAUSED,false)||
            T1JourneyStore.active(c)||T1JourneyStore.pending(c)||
            T1JourneyStore2.active(c)||T1JourneyStore2.pending(c);
    }
    private static void inactive(Context c)throws IOException{
        if(hasActive(c))throw new IOException(
            "Hay una jornada/viaje activo, pausado o pendiente. TERMINÁ los tres antes de borrar.");
    }
    private static String[] fields(String s){return s.split(";",-1);}
    private static boolean isClientReport(File file){
        if(!file.getName().matches("code_diag_[0-9]{8}_[0-9]{6}\\.csv"))
            return false;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(
            new FileInputStream(file),StandardCharsets.UTF_8))){
            String h=r.readLine(),line=r.readLine();
            return h!=null&&h.startsWith("event_type;client_id;")&&
                line!=null&&(line.startsWith("CLIENT_FINISHED_V1;")||
                    line.startsWith("VIAJE_2_FINISHED_V1;"));
        }catch(IOException ex){return false;}
    }
    private static boolean isTripHistory(File file){
        String n=file.getName();
        return n.matches("trip_[0-9]{8}_[0-9]{6}\\.csv")||
            n.matches("trip_oem_[0-9]{8}_[0-9]{6}\\.csv")||
            n.matches("eco_[0-9]{8}_[0-9]{6}\\.csv")||
            n.equals("client_trips.csv")||n.equals("client_trips_2.csv")||
            n.equals("client_drive_km.csv")||n.equals("client_drive_km_2.csv")||
            n.equals(T1HistoricalDistanceRepair.FILE)||
            n.equals(T1HistoricalDistanceRepair.FILE+".backup")||
            isClientReport(file);
    }
    private static void move(File source,File dest)throws IOException{
        if(!source.renameTo(dest))throw new IOException("No se pudo mover "+source.getName());
    }
    private static void clearTripState(Context c){
        SharedPreferences pref=c.getSharedPreferences(T1TripRecorderService.PREFS,Context.MODE_PRIVATE);
        float price=pref.getFloat(T1TripRecorderService.FUEL_PRICE,2129f);
        float kwh=pref.getFloat(T1TripRecorderService.KWH_PRICE,0f);
        pref.edit().clear().putFloat(T1TripRecorderService.FUEL_PRICE,price)
            .putFloat(T1TripRecorderService.KWH_PRICE,kwh).commit();
        c.getSharedPreferences("t1_passenger_journeys",Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences("t1_passenger_journeys_slot2",Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences("t1_eco_trip_live_v1",Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences("t1_client_trip_sync_v1",Context.MODE_PRIVATE)
            .edit().clear().putBoolean("legacy_imported_v1",true)
            .putString("status","Viajes locales eliminados. Nube NO eliminada.").commit();
        // Preserve cloud opt-in, credentials, maps and fuel preferences.
        SharedPreferences cloud=T1TripSync.prefs(c);
        SharedPreferences.Editor edit=cloud.edit();
        for(String key:cloud.getAll().keySet())if(key.startsWith("cursor_"))edit.remove(key);
        edit.putString(T1TripSync.STATUS,"Historial local eliminado; nube no eliminada.").commit();
    }
    static String deleteAll(Context c)throws IOException{
        if(!BUSY.compareAndSet(false,true))throw new IOException("Ya hay un borrado en curso");
        try{
            inactive(c);
            File dir=T1TripRecorderService.tripDir(c);
            File[] files=dir.listFiles();
            if(files==null)throw new IOException("No se puede leer la carpeta de viajes");
            File trash=new File(dir,".delete_confirmed_"+System.currentTimeMillis());
            if(!trash.mkdir())throw new IOException("No se pudo preparar borrado seguro");
            ArrayList<File> moved=new ArrayList<>();
            try{
                for(File file:files){
                    inactive(c);
                    if(file.isFile()&&isTripHistory(file)){
                        move(file,new File(trash,file.getName()));
                        moved.add(file);
                    }
                }
            }catch(IOException ex){
                for(int i=moved.size()-1;i>=0;i--){
                    File original=moved.get(i);
                    new File(trash,original.getName()).renameTo(original);
                }
                trash.delete();
                throw ex;
            }
            // Data already moved out of readable history; no background
            // worker may start because inactive() rejected desired sessions.
            clearTripState(c);
            for(File file:moved)new File(trash,file.getName()).delete();
            trash.delete();
            return "Se eliminaron "+moved.size()+
                " archivos locales de viajes y jornadas. Cargas, mapas y datos de nube intactos.";
        }finally{BUSY.set(false);}
    }
    private static boolean containsDay(String chain,String id){
        if(chain==null)return false;
        for(String part:chain.split("\\+"))if(id.equals(part))return true;
        return false;
    }
    private static boolean dayIsReferenced(File dir,String id)throws IOException{
        for(String fileName:new String[]{"client_trips.csv","client_trips_2.csv"}){
            File file=new File(dir,fileName);
            if(!file.isFile())continue;
            try(BufferedReader r=new BufferedReader(new InputStreamReader(
                new FileInputStream(file),StandardCharsets.UTF_8))){
                r.readLine();String line;
                while((line=r.readLine())!=null){
                    String[] f=fields(line);
                    if(f.length>4&&containsDay(f[4],id))return true;
                }
            }
        }
        return false;
    }
    private static void rewriteWithout(File file,int idColumn,String id)throws IOException{
        if(!file.isFile())return;
        File tmp=new File(file.getParentFile(),file.getName()+".deleting.tmp");
        boolean changed=false;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(
                new FileInputStream(file),StandardCharsets.UTF_8));
            BufferedWriter w=new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tmp),StandardCharsets.UTF_8))){
            String line=r.readLine();
            if(line==null)throw new IOException("CSV sin cabecera: "+file.getName());
            w.write(line);w.newLine();
            while((line=r.readLine())!=null){
                String[] cols=fields(line);
                if(cols.length>idColumn&&id.equals(cols[idColumn])){
                    changed=true;continue;
                }
                w.write(line);w.newLine();
            }
        }catch(IOException e){tmp.delete();throw e;}
        if(!changed){tmp.delete();return;}
        try{
            Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }catch(java.nio.file.AtomicMoveNotSupportedException ex){
            Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }finally{tmp.delete();}
    }
    private static boolean historyHasId(File file,String id)throws IOException{
        if(!file.isFile())return false;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(
            new FileInputStream(file),StandardCharsets.UTF_8))){
            r.readLine();String line;
            while((line=r.readLine())!=null){
                String[] cols=fields(line);
                if(cols.length>0&&id.equals(cols[0]))return true;
            }
        }
        return false;
    }
    private static void removeClientReports(File dir,String clientId){
        File[] files=dir.listFiles();
        if(files==null)return;
        for(File file:files)if(file.isFile()&&isClientReport(file)){
            try(BufferedReader r=new BufferedReader(new InputStreamReader(
                new FileInputStream(file),StandardCharsets.UTF_8))){
                r.readLine();String[] cols=fields(r.readLine());
                if(cols.length>1&&clientId.equals(cols[1]))file.delete();
            }catch(Exception ignored){}
        }
    }
    static String deleteSelected(Context c,int slot,String id)throws IOException{
        if(!BUSY.compareAndSet(false,true))throw new IOException("Ya hay un borrado en curso");
        try{
            inactive(c);
            File dir=T1TripRecorderService.tripDir(c);
            if(slot==0){
                if(!id.matches("[0-9]{8}_[0-9]{6}"))
                    throw new IOException("Jornada inválida");
                File day=new File(dir,"trip_"+id+".csv");
                if(!day.isFile())throw new IOException("Jornada ya no existe");
                if(dayIsReferenced(dir,id))throw new IOException(
                    "Esta jornada tiene Viajes 1/2 asociados. Borrá esos viajes primero "+
                    "o usá ELIMINAR TODO.");
                for(String prefix:new String[]{"trip_","trip_oem_","eco_"}){
                    File target=new File(dir,prefix+id+".csv");
                    if(target.isFile()&&!target.delete())
                        throw new IOException("No se pudo borrar "+target.getName());
                }
            }else if(slot==1||slot==2){
                if(!id.matches("[0-9]{12,16}"))throw new IOException("Viaje inválido");
                File history=new File(dir,slot==1?"client_trips.csv":"client_trips_2.csv");
                if(!historyHasId(history,id))throw new IOException("Viaje ya no existe");
                rewriteWithout(history,0,id);
                rewriteWithout(new File(dir,slot==1?
                    "client_drive_km.csv":"client_drive_km_2.csv"),0,id);
                // Keep other slot's corrections and original OEM journey intact.
                File corrections=new File(dir,T1HistoricalDistanceRepair.FILE);
                if(corrections.isFile()){
                    Map<String,T1HistoricalDistanceRepair.Entry> existing=
                        T1HistoricalDistanceRepair.load(dir);
                    if(existing.remove(slot+":"+id)!=null)
                        T1HistoricalDistanceRepair.save(dir,
                            new ArrayList<>(existing.values()));
                }
                removeClientReports(dir,id);
            }else throw new IOException("Tipo de historial desconocido");
            return "Registro local eliminado. Copias ya sincronizadas en nube no se borraron.";
        }finally{BUSY.set(false);}
    }
}