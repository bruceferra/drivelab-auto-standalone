package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Lightweight charge-only page. Independent of journeys and T1Launcher.
 * Import/export via Android SAF, no direct access to another app's SQLite. */
public final class T1ChargeHistoryActivity extends T1ImmersiveActivity {
    private static final int EXPORT_CSV=4409;
    private static final int BG=0xFF141C24,CARD=0xFF23313B,
        FG=Color.WHITE,MUTED=0xFFB9C5CE,ACCENT=0xFF286276;
    private LinearLayout content,history;
    private TextView live;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);sc.setBackgroundColor(BG);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);content.setPadding(18,16,18,22);
        sc.addView(content);
        TextView header=text("CARGAS · REGISTRO INDEPENDIENTE",23,FG);
        content.addView(header);
        content.addView(text("Lee cable OEM 1060 y batería 1062/1063/1064. "+
            "Guarda sesiones propias en SQLite aunque no haya viaje GPS. "+
            "T1 Launcher NO es necesario.",14,MUTED));
        live=text("Verificando servicio…",15,FG);
        live.setBackgroundColor(CARD);live.setPadding(15,12,15,12);
        LinearLayout buttons=row();content.addView(buttons);
        button(buttons,"ACTIVAR AUTO",()->toggle(true));
        button(buttons,"DETENER AUTO",()->toggle(false));
        button(buttons,"ACTUALIZAR",this::refresh);
        content.addView(live);
        LinearLayout actions=row();content.addView(actions);
        button(actions,"IMPORTAR 1.ª CARGA",this::importFirstCharge);
        button(actions,"EXPORTAR CSV",this::exportCsv);
        LinearLayout fix=row();content.addView(fix);
        button(fix,"CORREGIR HORAS DE CARGA IMPORTADA",
            this::correctImportedHours);
        content.addView(text("kWh de batería = energía recibida estimada. "+
            "Los kWh de red medidos incluyen pérdidas y pueden ser mayores. "+
            "Cuando no hay medidor, el costo mostrado es una ESTIMACIÓN. "+
            "El registro de la 1.ª carga de T1 Launcher se carga a mano, "+
            "sin acceso a su base privada.",13,MUTED));
        history=new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        content.addView(history);
        setContentView(sc);
        refresh();
    }
    private LinearLayout row(){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }
    private TextView text(String v,int size,int color){
        TextView t=new TextView(this);
        t.setText(v);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(8,10,8,10);return t;
    }
    private void button(LinearLayout parent,String label,Runnable task){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextSize(12);b.setTextColor(FG);b.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(ACCENT));
        parent.addView(b,new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT,1));
        b.setOnClickListener(v->task.run());
    }
    private void toggle(boolean on){
        SharedPreferences p=getSharedPreferences(T1ChargeStore.PREFS,MODE_PRIVATE);
        boolean saved=p.edit().putBoolean(T1ChargeStore.ENABLED,on).commit();
        if(!saved){msg("No se pudo guardar el ajuste");return;}
        Intent service=new Intent(this,T1ChargeRecorderService.class)
            .setAction(on?T1ChargeRecorderService.ACTION_START:
                T1ChargeRecorderService.ACTION_STOP);
        try{
            if(on)startForegroundService(service);
            else startService(service);
            msg(on?"Registro de cargas activado: solo lectura OEM":
                "Registro detenido. Historial conservado.");
        }catch(Exception ex){
            msg("Android no permitió cambiar servicio: "+
                ex.getClass().getSimpleName());
        }
        refresh();
    }
    private void refresh(){
        SharedPreferences p=getSharedPreferences(T1ChargeStore.PREFS,MODE_PRIVATE);
        boolean enabled=p.getBoolean(T1ChargeStore.ENABLED,false);
        live.setText((enabled?"AUTO ACTIVADO":"AUTO DESACTIVADO")+
            " · Cable OEM 1060="+p.getInt(T1ChargeStore.LAST_CABLE,-1)+
            "\n"+p.getString(T1ChargeStore.STATUS,"Sin lectura OEM reciente")+
            "\nÚltima lectura: "+
            (p.getLong(T1ChargeStore.LAST_READ,0)==0?"sin datos":
                new SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault())
                    .format(new Date(p.getLong(T1ChargeStore.LAST_READ,0)))));
        history.removeAllViews();
        try(T1ChargeStore db=new T1ChargeStore(this)){
            ArrayList<T1ChargeStore.Record> rows=db.latest(80);
            history.addView(text("HISTORIAL · "+rows.size()+
                " sesiones más recientes",17,FG));
            for(T1ChargeStore.Record entry:rows){
                TextView card=text(entry.describe(),14,FG);
                card.setBackgroundColor(CARD);card.setPadding(13,13,13,13);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin=8;history.addView(card,lp);
            }
            if(rows.isEmpty())history.addView(text(
                "Todavía no hay sesiones. Activá AUTO para la próxima "+
                "carga, o importá la primera desde T1 Launcher.",14,MUTED));
        }catch(Exception ex){history.addView(text(
            "Error de SQLite: "+ex.getClass().getSimpleName(),14,MUTED));}
    }
    private EditText number(LinearLayout parent,String name,String sample){
        EditText e=new EditText(this);e.setSingleLine(true);
        // Android AlertDialog uses LIGHT theme, unlike this dark activity.
        e.setTextColor(Color.BLACK);e.setHintTextColor(Color.GRAY);
        e.setHint(name+" (ejemplo "+sample+")");
        e.setInputType(InputType.TYPE_CLASS_NUMBER|
            InputType.TYPE_NUMBER_FLAG_DECIMAL);
        parent.addView(e);return e;
    }
    private static double numeric(EditText t,boolean optional){
        String value=t.getText().toString().trim().replace(",",".");
        return optional&&value.isEmpty()?Double.NaN:Double.parseDouble(value);
    }
    private void importFirstCharge(){
        // Values are HINTS only: the owner's preliminary 18%-100%,
        // ~80.4Ah, ~25.6kWh require checking on T1Launcher screen.
        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(22,10,22,5);
        TextView note=text("Ingresá la fecha/hora de FINALIZACIÓN y "+
            "los valores tal como aparezcan en T1 Launcher. Los ejemplos "+
            "NO se guardan automáticamente. Si tenés kWh medidos en el "+
            "cargador, añadilos: tienen prioridad para calcular el costo.",
            13,Color.DKGRAY);
        fields.addView(note);
        EditText time=new EditText(this);
        time.setSingleLine(true);
        time.setHint("Final: aaaa-MM-dd HH:mm (hora local)");
        time.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm",
            Locale.getDefault()).format(new Date()));
        fields.addView(time);
        EditText soc0=number(fields,"SOC inicial (%)","18");
        EditText soc1=number(fields,"SOC final (%)","100");
        EditText ah=number(fields,"Ah de batería","80,4");
        EditText kwh=number(fields,"kWh de batería","25,6");
        EditText meter=number(fields,"kWh del medidor de red, opcional","27,0");
        EditText price=number(fields,"Precio ARS/kWh","250");
        float set=getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
            .getFloat(T1TripRecorderService.KWH_PRICE,Float.NaN);
        if(Float.isFinite(set)&&set>=0)
            price.setText(String.format(Locale.ROOT,"%.2f",set));
        ScrollView scroll=new ScrollView(this);scroll.addView(fields);
        new AlertDialog.Builder(this)
            .setTitle("IMPORTAR CARGA · T1 LAUNCHER")
            .setView(scroll).setNegativeButton("CANCELAR",null)
            .setPositiveButton("IMPORTAR",(dialog,which)->{
                try{
                    SimpleDateFormat fmt=new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",Locale.getDefault());
                    fmt.setLenient(false);
                    Date date=fmt.parse(time.getText().toString().trim());
                    if(date==null||date.getTime()>System.currentTimeMillis()+300000)
                        throw new IllegalArgumentException("Fecha inválida");
                    double v0=numeric(soc0,false),v1=numeric(soc1,false),
                        va=numeric(ah,false),vk=numeric(kwh,false),
                        vm=numeric(meter,true),vp=numeric(price,false);
                    try(T1ChargeStore db=new T1ChargeStore(this)){
                        long id=db.importLauncher(date.getTime(),v0,v1,
                            va,vk,vm,vp);
                        db.exportForCosts(this);
                        getSharedPreferences(T1TripRecorderService.PREFS,MODE_PRIVATE)
                            .edit().putFloat(T1TripRecorderService.KWH_PRICE,
                                (float)vp).commit();
                        msg("Carga importada con ID "+id+
                            " · historial SQLite + CSV propio");
                    }
                    refresh();
                }catch(Exception ex){
                    msg("No se importó: "+ex.getMessage()+
                        ". Revisá fecha, números y duplicados.");
                }
            }).show();
    }
    private static String stamp(long at){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm",
            Locale.getDefault()).format(new Date(at));
    }
    private static long parseStamp(String input)throws Exception{
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",
            Locale.getDefault());
        format.setLenient(false);
        java.text.ParsePosition position=new java.text.ParsePosition(0);
        Date d=format.parse(input.trim(),position);
        if(d==null||position.getIndex()!=input.trim().length())
            throw new IllegalArgumentException(
                "Formato: aaaa-MM-dd HH:mm");
        return d.getTime();
    }
    private void correctImportedHours(){
        T1ChargeStore.Record selected=null;
        try(T1ChargeStore db=new T1ChargeStore(this)){
            for(T1ChargeStore.Record row:db.latest(500)){
                if(row.source.contains("IMPORTACIÓN MANUAL")){
                    selected=row;break;
                }
            }
        }catch(Exception ex){
            msg("No pude leer el historial: "+
                ex.getClass().getSimpleName());return;
        }
        if(selected==null){
            msg("No encontré cargas importadas manualmente");return;
        }
        final T1ChargeStore.Record target=selected;
        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(18,10,18,10);
        TextView expl=text("Sesión #"+target.id+
            ". El horario de 22:25 que recordás es el INICIO de enchufe; "+
            "el FIN debe ser el real de T1 Launcher o tu observación. "+
            "Si no sabés cuándo terminó, CANCELÁ: DriveLab no debe "+
            "inventar la hora de corte.",14,Color.DKGRAY);
        fields.addView(expl);
        EditText startField=new EditText(this);
        startField.setSingleLine(true);
        startField.setInputType(InputType.TYPE_CLASS_DATETIME|
            InputType.TYPE_DATETIME_VARIATION_NORMAL);
        startField.setHint("INICIO aaaa-MM-dd HH:mm");
        startField.setText(stamp(target.start));
        fields.addView(startField);
        EditText endField=new EditText(this);
        endField.setSingleLine(true);
        endField.setInputType(InputType.TYPE_CLASS_DATETIME|
            InputType.TYPE_DATETIME_VARIATION_NORMAL);
        endField.setHint("FIN REAL / INFORMADO aaaa-MM-dd HH:mm");
        endField.setText(stamp(target.end));
        fields.addView(endField);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(fields);
        new AlertDialog.Builder(this)
            .setTitle("CORREGIR INICIO Y FIN · CARGA #"+target.id)
            .setView(scroll)
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("GUARDAR HORAS",(dialog,which)->{
                try(T1ChargeStore db=new T1ChargeStore(this)){
                    long from=parseStamp(startField.getText().toString());
                    long until=parseStamp(endField.getText().toString());
                    db.correctManualHours(target.id,from,until);
                    db.exportForCosts(this);
                    msg("Horarios manuales guardados en la misma "+
                        "sesión; kWh, Ah y tarifa conservados");
                    refresh();
                }catch(Exception ex){
                    msg("No se corrigió: "+ex.getMessage());
                }
            }).show();
    }

    private void exportCsv(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,
            "DriveLab_T1_Cargas_"+new SimpleDateFormat("yyyyMMdd",
                Locale.ROOT).format(new Date())+".csv");
        try{startActivityForResult(intent,EXPORT_CSV);}
        catch(Exception ex){msg("El sistema no permite guardar CSV");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=EXPORT_CSV||result!=Activity.RESULT_OK||
            data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try(T1ChargeStore db=new T1ChargeStore(this);
            OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
            if(out==null)throw new IllegalStateException("Destino no disponible");
            out.write(db.csv().getBytes(StandardCharsets.UTF_8));
            out.flush();
            msg("Historial CSV exportado");
        }catch(Exception ex){msg("No se pudo guardar CSV: "+
            ex.getClass().getSimpleName());}
    }
    private void msg(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();if(live!=null)refresh();}
}
