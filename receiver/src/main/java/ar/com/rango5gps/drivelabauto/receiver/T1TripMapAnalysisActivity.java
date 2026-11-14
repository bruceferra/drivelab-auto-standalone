package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import android.content.SharedPreferences;

/** Select a recorded JOURNEY or whole-day TRIP; replay its actual GPS CSV
 * through 3 installed offline maps on a worker, never through vehicle OEM.
 * No map source influences driving modes, no raw GPS is exported.
 */
public final class T1TripMapAnalysisActivity extends T1ImmersiveActivity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private boolean visible;
    private String lastDisplayedKey="";
    private final Runnable refresher=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            refreshReplay();
            ui.postDelayed(this,900L);
        }
    };
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
        root.addView(label("Podés cambiar de solapa y volver: el análisis "+
            "sigue ejecutándose en segundo plano y conserva su progreso. "+
            "No vuelvas a tocar el mismo viaje mientras figure EN CURSO.",
            13,T1JetourTheme.CYAN));
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
        T1TripMapReplayService.reconcile(this);
        if(T1TripMapReplayService.isWorking()){
            refreshReplay();
            status.setText("YA HAY UN ANÁLISIS EN CURSO. "+
                "No se reinició ni se duplicó. "+
                T1TripMapReplayService.snapshot(this)
                .getString(T1TripMapReplayService.PROGRESS,""));
            return;
        }
        Intent request=new Intent(this,T1TripMapReplayService.class);
        request.setAction(T1TripMapReplayService.ACTION_START);
        request.putExtra(T1TripMapReplayService.EXTRA_ID,id);
        request.putExtra(T1TripMapReplayService.EXTRA_FROM,start);
        request.putExtra(T1TripMapReplayService.EXTRA_UNTIL,end);
        try{
            startForegroundService(request);
            status.setText("ANÁLISIS SOLICITADO · "+id+
                "\nPodés navegar por DriveLab: seguirá en segundo plano.");
            report.setText("Consultando mapas locales…");
        }catch(RuntimeException ex){
            status.setText("No se pudo iniciar segundo plano: "+
                ex.getClass().getSimpleName()+". "+
                (ex.getMessage()==null?"":ex.getMessage()));
        }
    }
    private void refreshReplay(){
        T1TripMapReplayService.reconcile(this);
        SharedPreferences p=T1TripMapReplayService.snapshot(this);
        String state=p.getString(T1TripMapReplayService.STATE,"");
        if(state.isEmpty())return;
        String id=p.getString(T1TripMapReplayService.ID,"");
        String progress=p.getString(T1TripMapReplayService.PROGRESS,"");
        String stateLine=T1TripMapReplayService.RUNNING.equals(state)?
            "EN CURSO · SIN REINICIAR":T1TripMapReplayService.DONE.equals(state)?
            "COMPLETADO":T1TripMapReplayService.INTERRUPTED.equals(state)?
            "INTERRUMPIDO":"NO COMPLETADO";
        String line=stateLine+" · "+id+"\n"+progress+
            (T1TripMapReplayService.RUNNING.equals(state)?
                "\nPodés salir de esta pantalla; el procesamiento continúa.":"");
        if(!line.equals(status.getText().toString()))
            status.setText(line);
        String result=p.getString(T1TripMapReplayService.RESULT,"");
        String key=state+"|"+id+"|"+p.getLong(
            T1TripMapReplayService.FROM,0)+"|"+p.getLong(
            T1TripMapReplayService.UNTIL,0)+"|"+result.length();
        if(!key.equals(lastDisplayedKey)){
            lastDisplayedKey=key;
            if(!result.isEmpty())report.setText(result);
            else if(T1TripMapReplayService.RUNNING.equals(state))
                report.setText("Analizando GPS y tres mapas en segundo plano…");
            else if(!progress.isEmpty())report.setText(progress);
        }
    }
    @Override protected void onResume(){
        super.onResume();
        visible=true;
        ui.removeCallbacks(refresher);
        ui.post(refresher);
    }
    @Override protected void onPause(){
        visible=false;
        ui.removeCallbacks(refresher);
        super.onPause();
    }

}
