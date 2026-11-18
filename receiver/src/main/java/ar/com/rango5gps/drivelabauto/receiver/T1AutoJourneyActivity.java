package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Automatic journey dashboard (separate from manually driven trips).
 * All GPS coordinates are local and only leave the car if user exports CSV.
 */
public final class T1AutoJourneyActivity extends T1ImmersiveActivity {
    private static final int SAVE_SESSIONS=4451,SAVE_POINTS=4452,
        BG=0xFF101820,CARD=0xFF1F2F3A,WHITE=Color.WHITE,
        MUTED=0xFFBECDD6,BLUE=0xFF285A6B;
    private TextView overview;
    private LinearLayout entries;
    private int pendingExport=0;
    private TextView label(String text,int size,int fg){
        TextView t=new TextView(this);
        t.setText(text);t.setTextColor(fg);t.setTextSize(size);
        t.setPadding(12,10,12,10);return t;
    }
    private void action(LinearLayout row,String caption,Runnable go){
        Button button=new Button(this);
        button.setText(caption);button.setTextSize(12);
        button.setAllCaps(false);button.setTextColor(WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        row.addView(button,new LinearLayout.LayoutParams(0,-2,1f));
        button.setOnClickListener(v->go.run());
    }
    private LinearLayout row(LinearLayout root){
        LinearLayout line=new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(line);return line;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12,15,12,25);
        sc.addView(root);
        root.addView(label("VIAJES AUTOMÁTICOS · CADA ENCENDIDO",22,WHITE));
        root.addView(label(
            "Se crean sin tocar INICIAR. Independientes de Viaje 1, "+
            "Viaje 2 y Total. Datos locales con hora, SOC, GPS (si está "+
            "disponible) y energía HV de entrada/salida.",13,MUTED));
        LinearLayout controls=row(root);
        action(controls,"AUTO ON",()->setAuto(true));
        action(controls,"AUTO OFF",()->setAuto(false));
        action(controls,"ACTUALIZAR",this::refresh);
        LinearLayout csv=row(root);
        action(csv,"EXPORTAR SESIONES",()->save(false));
        action(csv,"EXPORTAR PUNTOS GPS",()->save(true));
        overview=label("Leyendo base…",14,WHITE);
        overview.setBackgroundColor(CARD);root.addView(overview);
        root.addView(label("HISTORIAL POR ENCENDIDO",17,WHITE));
        entries=new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        root.addView(entries);
        root.addView(label("La entrada HV incluye regeneración y "+
            "generación por nafta: todavía no existe una señal confirmada "+
            "que separe ambos orígenes. El consumo atribuido al enchufe "+
            "es una estimación contable, no una medición física exacta. "+
            "El sistema no rellena los intervalos sin registro OEM/GPS.",
            13,MUTED));
        setContentView(sc);
        refresh();
    }
    private void setAuto(boolean enabled){
        SharedPreferences p=T1AutoJourneyService.prefs(this);
        if(!p.edit().putBoolean(T1AutoJourneyStore.ENABLED,enabled).commit()){
            note("No se pudo guardar el modo");return;
        }
        try{
            Intent service=new Intent(this,T1AutoJourneyService.class)
                .setAction(enabled?T1AutoJourneyService.ACTION_START:
                    T1AutoJourneyService.ACTION_STOP);
            if(enabled)startForegroundService(service);
            else startService(service);
            note(enabled?"Grabación automática por encendido ACTIVADA":
                "Grabación automática desactivada; registros conservados");
        }catch(Exception ex){
            note("Android no permitió cambiar el servicio: "+
                ex.getClass().getSimpleName());
        }
        refresh();
    }
    private void refresh(){
        if(overview==null)return;
        SharedPreferences p=T1AutoJourneyService.prefs(this);
        boolean enabled=p.getBoolean(T1AutoJourneyStore.ENABLED,true);
        long last=p.getLong(T1AutoJourneyStore.LAST_OEM,0);
        String tick=last<=0?"NINGUNA":
            new SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault())
                .format(new Date(last));
        StringBuilder text=new StringBuilder();
        text.append("AUTOMÁTICO ").append(enabled?"ON":"OFF")
            .append(" · OEM ").append(tick).append("\n")
            .append(p.getString(T1AutoJourneyStore.STATUS,
                "Esperando primera lectura OEM")).append("\n");
        entries.removeAllViews();
        try(T1AutoJourneyStore db=new T1AutoJourneyStore(this);
            T1ChargeStore chargeDb=new T1ChargeStore(this)){
            Date now=new Date();
            String today=new SimpleDateFormat("yyyy-MM-dd",
                Locale.getDefault()).format(now);
            String month=new SimpleDateFormat("yyyy-MM",
                Locale.getDefault()).format(now);
            long midnight=parseLocal(today+" 00:00");
            long first=parseLocal(month+"-01 00:00");
            text.append("\n").append(db.since(midnight)
                .describe("HOY")).append("\n")
                .append(db.since(first).describe("ESTE MES"));
            long chargedAt=0;
            double externalKwh=Double.NaN;
            String source="SIN CARGA REGISTRADA";
            for(T1ChargeStore.Record r:chargeDb.latest(300)){
                if(r.open!=0||r.end<=0||r.end>System.currentTimeMillis())
                    continue;
                if(r.end>chargedAt){
                    chargedAt=r.end;
                    boolean measured=Double.isFinite(r.meterKwh)&&
                        r.meterKwh>0;
                    externalKwh=measured?r.meterKwh:r.kwh;
                    source=measured?"RED MEDIDOR":"BATERÍA · ESTIMADO";
                }
            }
            if(chargedAt>0){
                T1AutoJourneyStore.Summary since=db.since(chargedAt);
                text.append("\n\n").append(since.describe(
                    "DESDE ÚLTIMA CARGA"));
                text.append("\nCarga finalizada: ")
                    .append(new SimpleDateFormat("dd/MM HH:mm",
                        Locale.getDefault()).format(new Date(chargedAt)))
                    .append(" · ").append(T1AutoJourneyStore.fmt(
                        externalKwh,2)).append(" kWh ").append(source);
                if(Double.isFinite(externalKwh)&&externalKwh>0)
                    text.append("\nImputación tentativa de enchufe: ")
                        .append(T1AutoJourneyStore.fmt(Math.min(
                            externalKwh,Math.max(0,since.net())),3))
                        .append(" kWh de saldo; el flujo interno se "+
                            "descuenta del consumo neto de batería");
            }else{
                text.append("\n\nDESDE ÚLTIMA CARGA: ")
                    .append(source)
                    .append(". Importala en CARGAS AUTOMÁTICAS.");
            }
            ArrayList<T1AutoJourneyStore.Journey> journeys=db.latest(100);
            for(T1AutoJourneyStore.Journey j:journeys){
                TextView e=label("SESIÓN #"+j.id+"\n"+j.describe(),14,WHITE);
                e.setBackgroundColor(CARD);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,-2);
                lp.topMargin=9;entries.addView(e,lp);
            }
            if(journeys.isEmpty())
                entries.addView(label("Sin sesiones todavía. "+
                    "La grabación comienza al observar un encendido OEM "+
                    "o movimiento de la camioneta. Un viaje anterior "+
                    "solo puede importarse desde su exportación.",
                    14,MUTED));
        }catch(Exception ex){
            text.append("\nBase SQLite: ")
                .append(ex.getClass().getSimpleName());
        }
        overview.setText(text);
    }
    private long parseLocal(String input){
        try{
            SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm",
                Locale.getDefault());
            f.setLenient(false);
            Date d=f.parse(input);return d==null?0:d.getTime();
        }catch(Exception e){return 0;}
    }
    private void save(boolean samples){
        pendingExport=samples?SAVE_POINTS:SAVE_SESSIONS;
        Intent request=new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/csv");
        request.putExtra(Intent.EXTRA_TITLE,
            "DriveLab_"+(samples?"Puntos_GPS":"Sesiones_Auto")+"_"+
            new SimpleDateFormat("yyyyMMdd",Locale.ROOT)
                .format(new Date())+".csv");
        try{startActivityForResult(request,pendingExport);}
        catch(Exception ex){note("No se pudo abrir selector de archivo CSV");}
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null||
            (req!=SAVE_SESSIONS&&req!=SAVE_POINTS))return;
        Uri dest=data.getData();
        boolean samples=req==SAVE_POINTS;
        new Thread(()->{
            String message;
            try(T1AutoJourneyStore db=new T1AutoJourneyStore(this);
                OutputStream out=getContentResolver().openOutputStream(dest,"wt")){
                if(out==null)throw new IllegalStateException("Sin archivo");
                db.export(out,samples);
                message=(samples?"Puntos GPS":"Sesiones")+" exportados en CSV";
            }catch(Exception ex){
                message="Error CSV: "+ex.getClass().getSimpleName();
            }
            String resultText=message;
            runOnUiThread(()->note(resultText));
        },"T1-Auto-GPS-CSV-Export").start();
    }
    private void note(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){
        super.onResume();if(overview!=null)refresh();
    }
}
