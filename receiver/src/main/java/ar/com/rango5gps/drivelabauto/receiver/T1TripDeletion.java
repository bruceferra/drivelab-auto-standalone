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
    /** A precise reason near the history deletion buttons, not a generic
     * message up in the cockpit's live controls.
     * Never clears a paused/desired recorder implicitly: its CSV must first
     * be closed by the proper "TERMINAR" action.
     */
    static String blockReason(Context c){
        SharedPreferences p=c.getSharedPreferences(
            T1TripRecorderService.PREFS,Context.MODE_PRIVATE);
        ArrayList<String> open=new ArrayList<>();
        if(p.getBoolean(T1TripRecorderService.RUNNING,false)||
           p.getBoolean(T1TripRecorderService.DESIRED,false)||
           p.getBoolean(T1TripRecorderService.PAUSED,false))
            open.add("VIAJE TOTAL/JORNADA"+
                (p.getBoolean(T1TripRecorderService.PAUSED,false)?
                    " (PAUSADA)":" (EN GRABACIÓN O PENDIENTE DE CIERRE)"));
        if(T1JourneyStore.active(c)||T1JourneyStore.pending(c))
            open.add("VIAJE 1"+
                (T1JourneyStore.paused(c)?" (PAUSADO)":
                T1JourneyStore.pending(c)?" (PENDIENTE)":" (ACTIVO)"));
        if(T1JourneyStore2.active(c)||T1JourneyStore2.pending(c))
            open.add("VIAJE 2"+
                (T1JourneyStore2.paused(c)?" (PAUSADO)":
                T1JourneyStore2.pending(c)?" (PENDIENTE)":" (ACTIVO)"));
        if(open.isEmpty())return null;
        return "BORRADO BLOQUEADO: "+String.join(", ",open)+
            ". Desde COCKPIT, usá TERMINAR en VIAJE TOTAL "+
            "(guarda y termina los Viajes 1/2 abiertos) y verificá que "+
            "los tres indiquen DETENIDO. No alcanza con PAUSAR. "+
            "Después volvé a intentar borrar.";
    }
    private static void inactive(Context c)throws IOException{
        String reason=blockReason(c);
        if(reason!=null)throw new IOException(reason);
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
    private static void requireCommit(boolean success,String what)
            throws IOException{
        if(!success)throw new IOException(
            "No se pudo confirmar el borrado del estado "+what);
    }
    private static void clearTripState(Context c)throws IOException{
        SharedPreferences pref=c.getSharedPreferences(T1TripRecorderService.PREFS,Context.MODE_PRIVATE);
        float price=pref.getFloat(T1TripRecorderService.FUEL_PRICE,2129f);
        float kwh=pref.getFloat(T1TripRecorderService.KWH_PRICE,0f);
        requireCommit(pref.edit().clear()
            .putFloat(T1TripRecorderService.FUEL_PRICE,price)
            .putFloat(T1TripRecorderService.KWH_PRICE,kwh).commit(),
            "VIAJE TOTAL");
        requireCommit(c.getSharedPreferences("t1_passenger_journeys",
            Context.MODE_PRIVATE).edit().clear().commit(),"VIAJE 1");
        requireCommit(c.getSharedPreferences("t1_passenger_journeys_slot2",
            Context.MODE_PRIVATE).edit().clear().commit(),"VIAJE 2");
        requireCommit(c.getSharedPreferences("t1_eco_trip_live_v1",
            Context.MODE_PRIVATE).edit().clear().commit(),"ECO EN VIVO");
        requireCommit(c.getSharedPreferences("t1_client_trip_sync_v1",
            Context.MODE_PRIVATE).edit().clear()
            .putBoolean("legacy_imported_v1",true)
            .putString("status","Viajes locales eliminados. Nube NO eliminada.")
            .commit(),"SINCRONIZACIÓN CLIENTES");
        // Preserve cloud opt-in, credentials, maps and fuel preferences.
        SharedPreferences cloud=T1TripSync.prefs(c);
        SharedPreferences.Editor edit=cloud.edit();
        for(String key:cloud.getAll().keySet())if(key.startsWith("cursor_"))edit.remove(key);
        requireCommit(edit.putString(T1TripSync.STATUS,
            "Historial local eliminado; nube no eliminada.").commit(),
            "CURSORES LOCALES");
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
            int remaining=0;
            for(File file:moved)
                if(!new File(trash,file.getName()).delete())remaining++;
            if(remaining==0)trash.delete();
            File[] current=dir.listFiles();
            if(current==null)throw new IOException(
                "No se pudo verificar la carpeta después del borrado");
            for(File file:current)
                if(file.isFile()&&isTripHistory(file))
                    throw new IOException("El borrado NO quedó completo: "+
                        file.getName()+" todavía existe.");
            if(remaining>0)throw new IOException(
                "El historial visible ya se quitó, pero "+remaining+
                " archivo(s) siguen en la cuarentena privada "+
                trash.getName()+". No se informó como borrado completo.");
            return "BORRADO CONFIRMADO: "+moved.size()+
                " archivos de viajes y jornadas eliminados. "+
                "Cargas, mapas y nube conservados.";
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
                if(historyHasId(history,id))
                    throw new IOException("El Viaje "+slot+" sigue en el CSV "+
                        "después del intento de borrado; comprobación fallida.");
            }else throw new IOException("Tipo de historial desconocido");
            return "BORRADO CONFIRMADO: registro local ya no aparece en "+
                "su historial original. Nube no eliminada.";
        }finally{BUSY.set(false);}
    }
}