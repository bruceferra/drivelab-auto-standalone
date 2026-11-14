package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

/** Select a recorded JOURNEY or whole-day TRIP; replay its actual GPS CSV
 * through 3 installed offline maps on a worker, never through vehicle OEM.
 * No map source influences driving modes, no raw GPS is exported.
 */
public final class T1TripMapAnalysisActivity extends T1ImmersiveActivity {
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private LinearLayout items;
    private TextView status,report;
    @Override protected int overlayClearBackgroundColor(){
        return T1JetourTheme.NIGHT;
    }
    private TextView label(String text,int size,int color){
        TextView v=new TextView(this);
        v.setText(text);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(12,8,12,8);
        return v;
    }
    private void button(LinearLayout into,String title,Runnable action){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(title);
        T1JetourTheme.button(b,T1JetourTheme.PANEL_HI);
        b.setOnClickListener(v->action.run());
        into.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,12,18,28);
        root.setBackgroundColor(T1JetourTheme.NIGHT);
        scroll.addView(root);
        root.addView(label("REANALIZAR VIAJE · TRES MAPAS",
            22,T1JetourTheme.CYAN));
        root.addView(label("Compará un viaje grabado utilizando sus puntos "+
            "GPS reales: ciudad, carretera/ruta, autopista y desconocida "+
            "por km y porcentaje, en OSM, Overture e IGN.\n"+
            "ANÁLISIS SIN MOVER EL AUTO NI CAMBIAR MODOS OEM. "+
            "Hacelo estacionado. No se suben coordenadas.\n"+
            "Los segmentos sin GPS válido no se inventan ni se suman.",
            14,T1JetourTheme.WHITE));
        status=label("Buscando viajes grabados…",14,T1JetourTheme.CYAN);
        root.addView(status);
        report=label("Elegí un viaje para iniciar la comparación offline.",
            15,Color.WHITE);
        report.setTextIsSelectable(false);
        report.setFocusable(false);
        items=new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        root.addView(items);
        root.addView(label("RESULTADO · PORCENTAJES BASADOS EN DISTANCIA GPS",
            16,T1JetourTheme.CYAN));
        root.addView(report);
        button(root,"VOLVER A CONFIGURAR OPTIMIZADOR",this::finish);
        setContentView(scroll);
        loadHistory();
    }
    private void loadHistory(){
        new Thread(()->{
            final File dir=T1TripRecorderService.tripDir(this);
            File[] files=dir.listFiles((folder,name)->
                name.matches("trip_[0-9]{8}_[0-9]{6}\\.csv"));
            if(files==null)files=new File[0];
            Arrays.sort(files,Comparator.comparing(File::getName).reversed());
            ArrayList<String> masterIds=new ArrayList<>();
            for(File f:files){
                if(masterIds.size()>=36)break;
                masterIds.add(f.getName().substring(5,f.getName().length()-4));
            }
            ArrayList<T1JourneyStore.Entry> clients=T1JourneyStore.history(this);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                items.removeAllViews();
                items.addView(label("JORNADAS COMPLETAS · CSV originales",
                    17,T1JetourTheme.CYAN));
                if(masterIds.isEmpty())items.addView(label(
                    "No hay jornadas guardadas en la T1.",
                    14,T1JetourTheme.MUTED));
                String active=getSharedPreferences(T1TripRecorderService.PREFS,
                    Context.MODE_PRIVATE).getString(T1TripRecorderService.ID,"");
                boolean recording=getSharedPreferences(
                    T1TripRecorderService.PREFS,Context.MODE_PRIVATE)
                    .getBoolean(T1TripRecorderService.RUNNING,false);
                for(String id:masterIds){
                    String when=id.substring(6,8)+"/"+id.substring(4,6)+"/"+
                        id.substring(0,4)+" "+id.substring(9,11)+":"+
                        id.substring(11,13);
                    button(items,"JORNADA "+when+
                        (recording&&active.equals(id)?" · EN GRABACIÓN":"")+
                        " · ANALIZAR 3 MAPAS",
                        ()->analyze(id,0,Long.MAX_VALUE));
                }
                items.addView(label("VIAJES INDIVIDUALES · inicio y fin guardados",
                    17,T1JetourTheme.CYAN));
                int count=0;
                for(int i=clients.size()-1;i>=0&&count<32;i--){
                    T1JourneyStore.Entry e=clients.get(i);
                    if(e.tripId==null||
                        !e.tripId.matches("[0-9]{8}_[0-9]{6}")||
                        e.started<=0||e.ended<=e.started||
                        !masterIds.contains(e.tripId))continue;
                    final String id=e.tripId;
                    final long start=e.started,end=e.ended;
                    String name=e.label==null?"Viaje":e.label;
                    if(name.length()>36)name=name.substring(0,36);
                    button(items,"VIAJE "+name+" · "+e.id+
                        " · ANALIZAR 3 MAPAS",
                        ()->analyze(id,start,end));
                    count++;
                }
                if(count==0)items.addView(label(
                    "Sin viajes individuales vinculados a un único CSV. "+
                    "Podés analizar la jornada completa de arriba.",
                    13,T1JetourTheme.MUTED));
                status.setText("Jornadas disponibles: "+masterIds.size()+
                    " · viajes individuales disponibles: "+count+
                    "\nNecesita las 3 bases instaladas en DriveLab.");
            });
        },"T1-Trip-Map-List").start();
    }
    private void analyze(String id,long start,long end){
        if(!busy.compareAndSet(false,true)){
            status.setText("Ya hay un análisis en curso; no se inició otro.");
            return;
        }
        status.setText("Leyendo viaje "+id+" en segundo plano…");
        report.setText("Calculando distancias GPS y clasificación de vías…");
        new Thread(()->{
            String result;
            try{
                T1TripMapReplay.Report summary=T1TripMapReplay.analyze(
                    getApplicationContext(),id,start,end,progress->
                        runOnUiThread(()->{
                            if(!isFinishing()&&!isDestroyed())
                                status.setText(progress);
                        }));
                result=summary.text;
            }catch(Exception failure){
                result="NO SE PUDO ANALIZAR "+id+": "+
                    failure.getClass().getSimpleName()+" · "+
                    (failure.getMessage()==null?"sin detalle":failure.getMessage())+
                    "\nLos viajes originales siguen intactos.";
            }finally{busy.set(false);}
            final String complete=result;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                status.setText("Análisis de "+id+" finalizado. "+
                    "Los registros originales no se modificaron.");
                report.setText(complete);
            });
        },"T1-Triple-Map-History-Replay").start();
    }
}
