package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;
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
        SAVE_RANGE=4453,SAVE_RANGE_POINTS=4454,
        BG=0xFF101820,CARD=0xFF1F2F3A,WHITE=Color.WHITE,
        MUTED=0xFFBECDD6,BLUE=0xFF285A6B;
    private TextView overview,rangeStatus,reconciledHistorical;
    private LinearLayout entries,rangeDays,rangeIgnitions,rangeTimeline;
    private EditText dateFrom,dateUntil;
    private final AtomicBoolean computingRange=new AtomicBoolean(false);
    private volatile long selectedFrom=0,selectedUntil=0;
    private String rangeCsv="";
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
        // Historic external reference belongs AFTER the selected date report,
        // not above it: the previous screen made 18/11 appear to be the
        // current selected period and confused the owner.
        LinearLayout controls=row(root);
        action(controls,"AUTO ON",()->setAuto(true));
        action(controls,"AUTO OFF",()->setAuto(false));
        action(controls,"ACTUALIZAR",this::refresh);
        LinearLayout csv=row(root);
        action(csv,"EXPORTAR SESIONES",()->save(false));
        action(csv,"EXPORTAR PUNTOS GPS",()->save(true));
        root.addView(label("INFORME AUTOMÁTICO POR FECHAS · "+
            "CONDUCCIÓN + CARGAS",17,WHITE));
        root.addView(label("Elegí DESDE y HASTA con fecha y hora, al minuto. "+
            "Ejemplo: sábado 19:00 a sábado 23:50. "+
            "También permite períodos de varios días desde las 00:00. "+
            "Un encendido que comenzó antes se recorta al horario elegido.",
            13,MUTED));
        LinearLayout dates=row(root);
        dateFrom=dateInput(dates,"DESDE · aaaa-MM-dd HH:mm");
        dateUntil=dateInput(dates,"HASTA · aaaa-MM-dd HH:mm");
        String key="t1_auto_range_filter";
        SharedPreferences saved=getSharedPreferences(key,MODE_PRIVATE);
        Calendar local=Calendar.getInstance();
        String until=new SimpleDateFormat("yyyy-MM-dd HH:mm",
            Locale.getDefault()).format(local.getTime());
        local.set(Calendar.HOUR_OF_DAY,0);
        local.set(Calendar.MINUTE,0);
        local.set(Calendar.SECOND,0);
        dateFrom.setText(saved.getString("start",new SimpleDateFormat(
            "yyyy-MM-dd HH:mm",Locale.getDefault()).format(local.getTime())));
        dateUntil.setText(saved.getString("until",until));
        LinearLayout pickers=row(root);
        action(pickers,"ELEGIR DESDE",()->pickDateTime(dateFrom));
        action(pickers,"ELEGIR HASTA",()->pickDateTime(dateUntil));
        LinearLayout process=row(root);
        action(process,"GENERAR DETALLE",this::generateRange);
        action(process,"EXPORTAR RESUMEN",()->exportRange(false));
        action(process,"EXPORTAR GPS FILTRADO",()->exportRange(true));
        LinearLayout presets=row(root);
        action(presets,"HOY · DESDE 00:00",()->presetRange(0));
        action(presets,"ÚLTIMOS 7 DÍAS",()->presetRange(7));
        LinearLayout cutoffRow=row(root);
        action(cutoffRow,"DESDE 19/11/2026 08:00 · SIN INVENTAR KM",()->{
            dateFrom.setText("2026-11-19 08:00");
            dateUntil.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm",
                Locale.getDefault()).format(new Date()));
            generateRange();
        });
        rangeStatus=label("Definí un intervalo y tocá GENERAR DETALLE.",14,WHITE);
        rangeStatus.setBackgroundColor(CARD);root.addView(rangeStatus);
        rangeDays=rowList(root,"ESTADÍSTICAS POR DÍA");
        rangeIgnitions=rowList(root,"DESGLOSE POR CADA ENCENDIDO");
        rangeTimeline=rowList(root,"CRONOLOGÍA · VIAJES Y CARGAS");
        root.addView(label("REFERENCIA HISTÓRICA AJENA AL FILTRO · 18/11",
            14,MUTED));
        reconciledHistorical=label(
            "18/11 · LEYENDO RECONSTRUCCIÓN T1LAUNCHER…",13,MUTED);
        reconciledHistorical.setBackgroundColor(CARD);
        root.addView(reconciledHistorical);
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
    private EditText dateInput(LinearLayout row,String hint){
        EditText e=new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(WHITE);
        e.setTextSize(13);
        e.setInputType(InputType.TYPE_CLASS_DATETIME|
            InputType.TYPE_DATETIME_VARIATION_NORMAL);
        row.addView(e,new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT,1));
        return e;
    }
    private LinearLayout rowList(LinearLayout root,String name){
        root.addView(label(name,16,WHITE));
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        return list;
    }
    private void pickDateTime(EditText destination){
        Calendar now=Calendar.getInstance();
        try{
            java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm",Locale.getDefault());
            fmt.setLenient(false);
            Date selected=fmt.parse(destination.getText().toString().trim());
            if(selected!=null)now.setTime(selected);
        }catch(Exception ignored){}
        new DatePickerDialog(this,(view,year,month,day)->{
            Calendar date=Calendar.getInstance();
            date.set(year,month,day,
                now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE));
            new TimePickerDialog(this,(time,hour,min)->{
                date.set(Calendar.HOUR_OF_DAY,hour);
                date.set(Calendar.MINUTE,min);
                date.set(Calendar.SECOND,0);
                date.set(Calendar.MILLISECOND,0);
                destination.setText(new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",Locale.getDefault()).format(
                        date.getTime()));
            },now.get(Calendar.HOUR_OF_DAY),
              now.get(Calendar.MINUTE),true).show();
        },now.get(Calendar.YEAR),now.get(Calendar.MONTH),
          now.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void presetRange(int days){
        Calendar start=Calendar.getInstance();
        Calendar now=Calendar.getInstance();
        if(days==0){
            start.set(Calendar.HOUR_OF_DAY,0);
            start.set(Calendar.MINUTE,0);
            start.set(Calendar.SECOND,0);
        }else{
            start.add(Calendar.DAY_OF_YEAR,-days);
        }
        SimpleDateFormat fmt=new SimpleDateFormat(
            "yyyy-MM-dd HH:mm",Locale.getDefault());
        dateFrom.setText(fmt.format(start.getTime()));
        dateUntil.setText(fmt.format(now.getTime()));
        generateRange();
    }
    private void generateRange(){
        if(!computingRange.compareAndSet(false,true)){
            note("Ya se está calculando un intervalo");return;
        }
        final long from,until;
        try{
            from=parseDateTime(dateFrom.getText().toString().trim());
            until=parseDateTime(dateUntil.getText().toString().trim());
            if(from<=0||until<=from||until-from>370L*86400000L)
                throw new IllegalArgumentException(
                    "HASTA debe ser posterior a DESDE (máximo 370 días)");
            getSharedPreferences("t1_auto_range_filter",MODE_PRIVATE).edit()
                .putString("start",dateFrom.getText().toString().trim())
                .putString("until",dateUntil.getText().toString().trim())
                .apply();
        }catch(Exception ex){
            computingRange.set(false);
            note("Rango inválido: "+ex.getMessage());
            return;
        }
        rangeStatus.setText("Calculando desde "+
            dateFrom.getText()+" hasta "+dateUntil.getText()+
            " con puntos OEM/GPS, sin modificar sesiones…");
        new Thread(()->{
            T1AutoJourneyStore.RangeReport result=null;
            T1AutoRangeTimeline.Result timeline=null;
            String error="";
            try(T1AutoJourneyStore db=new T1AutoJourneyStore(this);
                T1ChargeStore chargeDb=new T1ChargeStore(this)){
                result=db.between(from,until);
                float fuelPrice=getSharedPreferences(
                    T1TripRecorderService.PREFS,MODE_PRIVATE)
                    .getFloat(T1TripRecorderService.FUEL_PRICE,Float.NaN);
                timeline=T1AutoRangeTimeline.build(result,
                    db.overlapping(from,until),chargeDb.overlapping(from,until),
                    from,until,fuelPrice);
            }catch(Exception ex){
                error=ex.getClass().getSimpleName()+": "+
                    String.valueOf(ex.getMessage());
            }
            final T1AutoJourneyStore.RangeReport report=result;
            final T1AutoRangeTimeline.Result merged=timeline;
            final String failure=error;
            runOnUiThread(()->{
                computingRange.set(false);
                if(isFinishing()||isDestroyed())return;
                if(report==null){
                    rangeStatus.setText("No se pudo generar: "+failure);
                    return;
                }
                selectedFrom=from;selectedUntil=until;
                rangeCsv=merged==null?report.csv():merged.csv(report);
                boolean includesReconstructed=from<=T1RebuiltMorning.FROM&&
                    until>=T1RebuiltMorning.UNTIL;
                String sourceWarning=includesReconstructed?
                    "RECONSTRUCCIÓN HISTÓRICA T1LAUNCHER: "+
                    T1RebuiltMorning.fmt(T1RebuiltMorning.KM,2)+" km, "+
                    T1RebuiltMorning.fmt(T1RebuiltMorning.PACK_KWH,3)+
                    " kWh UTILIZADOS, "+
                    T1RebuiltMorning.fmt(T1RebuiltMorning.REGEN_KWH,3)+
                    " kWh REGENERADOS. No se suman a los datos OEM.\n":
                    "";
                rangeStatus.setText(
                    "INFORME HISTÓRICO POR RANGO · OEM + GPS + CARGAS"+
                    "\n"+dateFrom.getText()+" → "+dateUntil.getText()+
                    "\n"+sourceWarning+
                    "\nOEM OBSERVADO (SIN RECONCILIACIÓN): "+
                    report.total.describe()+
                    (merged==null?"":"\n\n"+merged.describe())+
                    "\nLos kilómetros OEM, kWh, niveles CI186 y cargas "+
                    "corresponden sólo a lecturas registradas; no hay "+
                    "reconstrucción de datos ausentes. El HV de entrada "+
                    "durante conducción incluye generador térmico y frenadas.");
                renderSlices(rangeDays,report.days.values());
                renderSlices(rangeIgnitions,report.byIgnition.values());
                rangeTimeline.removeAllViews();
                if(merged!=null){
                    int visible=0;
                    for(T1AutoRangeTimeline.Event event:merged.events){
                        if(visible>=200)break;
                        TextView card=label(
                            T1AutoRangeTimeline.description(event),13,WHITE);
                        card.setBackgroundColor(CARD);
                        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,-2);
                        lp.topMargin=8;rangeTimeline.addView(card,lp);
                        visible++;
                    }
                    if(merged.events.size()>visible)
                        rangeTimeline.addView(label(
                            "Mostrando "+visible+" de "+merged.events.size()+
                            " eventos; exportá CSV para verlos todos",13,MUTED));
                    if(merged.events.isEmpty())
                        rangeTimeline.addView(label(
                            "Sin conducciones ni cargas observadas "+
                            "en el intervalo",13,MUTED));
                }
            });
        },"T1-Auto-Range-Background").start();
    }
    private long parseDateTime(String raw)throws Exception{
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm",Locale.getDefault());
        f.setLenient(false);
        java.text.ParsePosition pos=new java.text.ParsePosition(0);
        Date d=f.parse(raw,pos);
        if(d==null||pos.getIndex()!=raw.length())
            throw new IllegalArgumentException(
                "Usá aaaa-MM-dd HH:mm, por ejemplo 2026-11-18 19:00");
        return d.getTime();
    }
    private void renderSlices(LinearLayout parent,
        java.util.Collection<T1AutoJourneyStore.Slice> slices){
        parent.removeAllViews();
        if(slices.isEmpty()){
            parent.addView(label("Sin muestras en el horario elegido.",
                13,MUTED));return;
        }
        for(T1AutoJourneyStore.Slice slice:slices){
            TextView tile=label(slice.describe(),13,WHITE);
            tile.setBackgroundColor(CARD);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin=7;parent.addView(tile,lp);
        }
    }
    private void exportRange(boolean points){
        if(selectedUntil<=selectedFrom||rangeCsv.isEmpty()){
            note("Primero generá un detalle por fecha y hora.");return;
        }
        int id=points?SAVE_RANGE_POINTS:SAVE_RANGE;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,
            "DriveLab_Viaje_Estadistico_"+
            new SimpleDateFormat("yyyyMMdd_HHmm",Locale.ROOT)
                .format(new Date(selectedFrom))+
            (points?"_GPS.csv":".csv"));
        try{startActivityForResult(intent,id);}
        catch(Exception ex){note("No se pudo abrir el guardado CSV");}
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
        if(reconciledHistorical!=null)
            reconciledHistorical.setText(
                T1RebuiltMorning.reconciledReference(this));
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
            T1ChargeStore.Record lastCharge=null;
            for(T1ChargeStore.Record record:chargeDb.latest(300)){
                if(record.open!=0||record.end<=0)continue;
                long finished=record.chargeEnded>0?
                    record.chargeEnded:record.end;
                if(finished>System.currentTimeMillis())continue;
                if(finished>chargedAt){
                    chargedAt=finished;lastCharge=record;
                }
            }
            if(lastCharge!=null){
                T1AutoJourneyStore.Summary since=db.since(chargedAt);
                T1PlugEnergyLedger.Result allocation=
                    T1PlugEnergyLedger.estimate(lastCharge.kwh,
                        lastCharge.meterKwh,since.out,since.in,
                        lastCharge.price);
                text.append("\n\n").append(since.describe(
                    "DESDE ÚLTIMA CARGA · OEM ORIGINAL, "+
                    "NO ES RECONSTRUCCIÓN T1LAUNCHER"));
                text.append("\nFecha de fin registrada: ")
                    .append(new SimpleDateFormat("dd/MM HH:mm",
                        Locale.getDefault()).format(new Date(chargedAt)))
                    .append(lastCharge.source.contains("IMPORTACIÓN MANUAL")?
                        " · informada manualmente":
                        " · última corriente HV, corte estimado");
                if(allocation!=null){
                    text.append("\nELECTRICIDAD DEL ENCHUFE · ESTIMACIÓN CONTABLE")
                        .append("\nCargados a batería: ")
                        .append(T1AutoJourneyStore.fmt(
                            allocation.fromPlugBatteryKwh,3)).append(" kWh")
                        .append("\nBatería salida: ")
                        .append(T1AutoJourneyStore.fmt(
                            allocation.hvDischargeKwh,3)).append(" kWh")
                        .append(" · entrada interna: ")
                        .append(T1AutoJourneyStore.fmt(
                            allocation.hvRechargeKwh,3)).append(" kWh")
                        .append(" (motor + frenadas)")
                        .append("\nNeto batería después de enchufar: ")
                        .append(T1AutoJourneyStore.fmt(
                            allocation.hvNetKwh,3)).append(" kWh")
                        .append("\nImputado al enchufe (no medido): ")
                        .append(T1AutoJourneyStore.fmt(
                            allocation.assignedPlugKwh,3)).append(" kWh")
                        .append(" · saldo teórico ")
                        .append(T1AutoJourneyStore.fmt(
                            allocation.remainingPlugKwh,3)).append(" kWh");
                    if(allocation.gridMeterKnown)
                        text.append("\nEquivalente imputado de red: ")
                            .append(T1AutoJourneyStore.fmt(
                                allocation.allocatedGridKwh,3))
                            .append(" kWh, pérdidas proporcionales incluidas");
                    if(allocation.priceKnown)
                        text.append("\nCosto eléctrico imputado: $")
                            .append(T1AutoJourneyStore.fmt(
                                allocation.electricCost,2))
                            .append(allocation.gridMeterKnown?
                                " (con medidor)":" (sin pérdidas de red medidas)");
                    if(allocation.unattributedKwh>0)
                        text.append("\nExcedente sin atribución a esta carga: ")
                            .append(T1AutoJourneyStore.fmt(
                                allocation.unattributedKwh,3))
                            .append(" kWh; no se factura automáticamente");
                    text.append("\nNo se puede identificar físicamente de "+
                        "qué origen es cada kWh: incluye reserva anterior, "+
                        "térmico y regeneración. El 15% protegido no "+
                        "define por sí solo su origen ni su precio.");
                }else{
                    text.append("\nCarga sin kWh válidos para atribuir costos");
                }
            }else{
                text.append("\n\nDESDE ÚLTIMA CARGA: no hay una "+
                    "carga cerrada; importala en CARGAS AUTOMÁTICAS.");
            }
            ArrayList<T1AutoJourneyStore.Journey> journeys=db.latest(100);
            // Impair only the electricity previously bought on the last
            // recorded plug charge, never incoming motor/brake HV energy.
            // This is a chronological ACCOUNTING estimate, not a claim
            // that we can identify the physical origin of the electrons.
            java.util.HashMap<Long,String> allocations=new java.util.HashMap<>();
            if(lastCharge!=null && lastCharge.kwh>0){
                ArrayList<T1AutoJourneyStore.Journey> chronological=db.latest(500);
                java.util.Collections.reverse(chronological);
                double cumulativeOut=0,cumulativeIn=0;
                double previousPlug=0,previousGrid=0;
                for(T1AutoJourneyStore.Journey j:chronological){
                    if(j.start<chargedAt)continue;
                    cumulativeOut+=j.hvOut;
                    cumulativeIn+=j.hvIn;
                    T1PlugEnergyLedger.Result account=
                        T1PlugEnergyLedger.estimate(lastCharge.kwh,
                            lastCharge.meterKwh,cumulativeOut,cumulativeIn,
                            lastCharge.price);
                    if(account==null)continue;
                    double delta=account.assignedPlugKwh-previousPlug;
                    double deltaGrid=account.allocatedGridKwh-previousGrid;
                    String detail="ENCHUFE IMPUTADO AL VIAJE: "+
                        T1AutoJourneyStore.fmt(delta,3)+" kWh batería";
                    if(delta<0)detail+=" · ajuste por energía interna";
                    if(account.priceKnown)
                        detail+=" · costo atribuido $"+
                            T1AutoJourneyStore.fmt(
                                deltaGrid*lastCharge.price,2);
                    detail+=" (estimación, no medición de origen)";
                    allocations.put(j.id,detail);
                    previousPlug=account.assignedPlugKwh;
                    previousGrid=account.allocatedGridKwh;
                }
            }
            for(T1AutoJourneyStore.Journey j:journeys){
                String estimate=allocations.get(j.id);
                boolean overlapsHistorical=j.start<T1RebuiltMorning.UNTIL&&
                    (j.end>0?j.end:j.last)>=T1RebuiltMorning.FROM;
                // Some older AUTO records had the HV IN column presented
                // as regeneration. Preserve the data for exports but do not
                // mislabel the 6.23 kWh on overlapping historical screens.
                String card=overlapsHistorical?
                    "SESIÓN AUTO OEM ANTERIOR #"+j.id+
                    " · SUPERPUESTA CON 18/11 RECONSTRUIDO"+
                    "\nDATOS HV RAW NO RECONCILIADOS · "+
                    "no interpretar HV ENTRADA como frenado regenerativo."+
                    "\nNo sumar esta sesión al parcial T1Launcher. "+
                    "Registro original conservado en EXPORTAR SESIONES."+
                    "\nResumen corregido: 6,230 kWh UTILIZADOS; "+
                    "1,590 kWh REGENERADOS (fuente T1Launcher).":
                    "SESIÓN #"+j.id+"\n"+j.describe()+
                    (estimate==null?"":"\n"+estimate);
                TextView e=label(card,14,WHITE);
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
            (req!=SAVE_SESSIONS&&req!=SAVE_POINTS&&
             req!=SAVE_RANGE&&req!=SAVE_RANGE_POINTS))return;
        Uri dest=data.getData();
        boolean samples=req==SAVE_POINTS;
        boolean intervalPoints=req==SAVE_RANGE_POINTS;
        boolean intervalSummary=req==SAVE_RANGE;
        final long from=selectedFrom,until=selectedUntil;
        final String cached=rangeCsv;
        new Thread(()->{
            String message;
            try(T1AutoJourneyStore db=new T1AutoJourneyStore(this);
                OutputStream out=getContentResolver().openOutputStream(dest,"wt")){
                if(out==null)throw new IllegalStateException("Sin archivo");
                if(intervalSummary){
                    if(from<=0||until<=from||cached.isEmpty())
                        throw new IllegalArgumentException("Sin intervalo");
                    out.write(cached.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
                    out.flush();
                }else if(intervalPoints){
                    if(from<=0||until<=from)
                        throw new IllegalArgumentException("Sin intervalo");
                    db.exportWindowPoints(out,from,until);
                }else db.export(out,samples);
                message=(intervalSummary?"Viaje estadístico":
                    intervalPoints?"GPS del intervalo":
                    samples?"Puntos GPS":"Sesiones")+" exportados en CSV";
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
