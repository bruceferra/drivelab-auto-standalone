package ar.com.rango5gps.drivelabauto.receiver;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Historical counterfactual explorer, no automotive control and no network.
 * Outputs physical energy bounds, not uncalibrated "predicted liters" from
 * a different powertrain mode.
 */
public final class T1WhatIfActivity extends T1ImmersiveActivity {
    private static final int BG=T1JetourTheme.NIGHT,
        PANEL=T1JetourTheme.PANEL,
        WHITE=T1JetourTheme.WHITE,MUTED=T1JetourTheme.MUTED,
        INPUT=T1JetourTheme.PANEL_HI,DARK_TEXT=T1JetourTheme.WHITE,
        EDGE=T1JetourTheme.BORDER,POPUP=T1JetourTheme.PANEL;
    private Spinner trip,propulsion,strategy,regeneration,drive;
    private EditText startSoc,targetSoc,packCapacity,mass,baselineFuel;
    private TextView result,caption;
    private File[] trips=new File[0];
    @Override protected int overlayClearBackgroundColor(){return BG;}

    private TextView text(String s,int size,int color,boolean strong){
        TextView t=new TextView(this);
        t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(strong)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(5,5,5,5);return t;
    }
    private LinearLayout col(){
        LinearLayout v=new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    private LinearLayout row(){
        LinearLayout v=new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private void column(LinearLayout parent,View item){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(3,0,3,0);parent.addView(item,p);
    }
    private int dp(int x){
        return Math.round(x*getResources().getDisplayMetrics().density);
    }
    private GradientDrawable inputBackground(int color){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(9));
        bg.setStroke(dp(1),EDGE);
        return bg;
    }
    private Spinner pick(String... choices){
        Spinner s=new Spinner(this,Spinner.MODE_DROPDOWN);
        T1JetourTheme.spinner(s,choices);
        return s;
    }
    private EditText numeric(String initial,String hint){
        EditText e=new EditText(this);
        e.setText(initial);e.setHint(hint);e.setSingleLine(true);
        e.setTextSize(16);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|
            InputType.TYPE_NUMBER_FLAG_DECIMAL|
            InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setSelectAllOnFocus(true);
        e.setPadding(dp(11),dp(9),dp(11),dp(9));
        T1JetourTheme.number(e);
        e.setMinHeight(dp(48));
        return e;
    }
    private void field(LinearLayout parent,String label,View control){
        LinearLayout wrap=col();
        wrap.addView(text(label,12,WHITE,true));
        wrap.addView(control);column(parent,wrap);
    }
    private GradientDrawable panel(){
        GradientDrawable d=new GradientDrawable();
        return T1JetourTheme.surface(PANEL,15);
    }
    private void section(LinearLayout root,String label){
        root.addView(text(label,17,Color.WHITE,true));
    }
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        ScrollView screen=new ScrollView(this);
        screen.setBackground(T1JetourTheme.backdrop());
        LinearLayout root=col();
        root.setPadding(13,9,13,25);
        screen.addView(root);
        LinearLayout top=row();root.addView(top);
        TextView heading=text("SIMULADOR DE VIAJE · EXPERIMENTAL",20,WHITE,true);
        column(top,heading);
        Button back=new Button(this);
        back.setText("VOLVER");back.setAllCaps(false);
        T1JetourTheme.button(back,INPUT);
        back.setOnClickListener(v->finish());top.addView(back);
        root.addView(text("Mismo recorrido real, otras condiciones de batería. "+
            "Comparamos límites energéticos; todavía NO predecimos las "+
            "decisiones de la ECU ni los litros exactos de HEV SOC 50.",
            13,MUTED,false));

        section(root,"1 · VIAJE REAL GUARDADO");
        File[] list=T1TripRecorderService.tripDir(this).listFiles(
            f->f.isFile()&&f.getName().matches(
                "trip_[0-9]{8}_[0-9]{6}\\.csv"));
        if(list!=null)trips=list;
        Arrays.sort(trips,(a,b)->b.getName().compareTo(a.getName()));
        String[] options=new String[trips.length];
        for(int i=0;i<trips.length;i++)
            options[i]=trips[i].getName().replace("trip_","")
                .replace(".csv","");
        trip=pick(options.length==0?new String[]{"SIN VIAJES LOCALES"}:options);
        root.addView(trip);

        section(root,"2 · HIPÓTESIS DEL RECORRIDO");
        root.addView(text("Desplegables con letras blancas sobre azul " +
            "oscuro, igual que los controles Jetour. Elegí el escenario " +
            "y tocá ANALIZAR.",12,MUTED,false));
        LinearLayout modes=row();root.addView(modes);
        propulsion=pick("EV","HEV","EV MAX");
        propulsion.setSelection(1);
        field(modes,"PROPULSIÓN",propulsion);
        strategy=pick("SMART","SOC FORZADO");
        strategy.setSelection(1);
        field(modes,"ESTRATEGIA",strategy);
        drive=pick("ECO","NORMAL","SPORT","SNOW");
        field(modes,"CONDUCCIÓN",drive);
        regeneration=pick("BAJA","MEDIA","ALTA");
        regeneration.setSelection(1);
        field(modes,"REGENERACIÓN",regeneration);

        LinearLayout socs=row();root.addView(socs);
        startSoc=numeric("100","SOC");
        field(socs,"SOC INICIAL %",startSoc);
        targetSoc=numeric("50","SOC");
        field(socs,"SOC OBJETIVO % (FORZADO)",targetSoc);
        packCapacity=numeric("20","kWh");
        field(socs,"BATERÍA ÚTIL kWh · SUPUESTO",packCapacity);
        LinearLayout assumptions=row();root.addView(assumptions);
        baselineFuel=numeric("","Litros");
        field(assumptions,"LITROS REALES MEDIDOS · OPCIONAL",baselineFuel);
        mass=numeric("2100","kg");
        field(assumptions,"MASA VEHÍCULO kg · SUPUESTO",mass);

        LinearLayout actions=row();root.addView(actions);
        Button check=new Button(this);
        check.setAllCaps(false);check.setText("ANALIZAR ESCENARIO");
        T1JetourTheme.button(check,0xFF086F75);
        check.setOnClickListener(v->calculate());
        column(actions,check);
        Button actual=new Button(this);
        actual.setAllCaps(false);actual.setText("USAR SOC ORIGINAL");
        T1JetourTheme.button(actual,INPUT);
        actual.setOnClickListener(v->useHistoricalStart());
        column(actions,actual);

        result=text("Elegí un viaje y analizá los escenarios.",15,
            WHITE,false);
        result.setBackground(panel());result.setPadding(20,14,20,14);
        root.addView(result);
        caption=text("AVISO: el límite energético de mantener SOC al final "+
            "NO equivale al consumo que habría tenido otra estrategia. "+
            "El modo de conducción y la regeneración se registran, "+
            "pero sus efectos numéricos aún requieren pruebas OEM.",
            13,MUTED,false);
        root.addView(caption);
        setContentView(screen);
        if(trips.length>0)calculate();
    }
    private static String fmt(double x,int digits){
        return Double.isFinite(x)?
            String.format(Locale.ROOT,"%."+digits+"f",x):"SIN DATOS";
    }
    private double positive(EditText e,double min,double max,
                            boolean optional){
        String x=e.getText().toString().trim().replace(',','.');
        if(x.isEmpty()&&optional)return Double.NaN;
        try{
            double v=Double.parseDouble(x);
            if(Double.isFinite(v)&&v>=min&&v<=max)return v;
        }catch(Exception ignored){}
        throw new IllegalArgumentException("Revisá el campo ("+
            e.getHint()+") rango "+min+"–"+max+".");
    }
    private void useHistoricalStart(){
        if(trips.length==0)return;
        try{
            T1WhatIfEnergyModel.Trip t=T1WhatIfEnergyModel.read(
                trips[trip.getSelectedItemPosition()]);
            T1WhatIfEnergyModel.Actual a=T1WhatIfEnergyModel.inspect(t,2100);
            if(Double.isFinite(a.socStart)){
                startSoc.setText(fmt(a.socStart,1));
                result.setText("SOC inicial OEM recuperado: "+
                    fmt(a.socStart,1)+" %. Podés modificarlo para simular.");
            }else result.setText("No se pudo leer SOC inicial en ese viaje.");
        }catch(Exception e){result.setText("No se pudo recuperar viaje: "+e.getMessage());}
    }
    private void calculate(){
        if(trips.length==0){
            result.setText("Todavía no hay CSV de TRIP local. "+
                "Empezá un Viaje Total y guardalo.");
            return;
        }
        try{
            double initial=positive(startSoc,0,100,false),
                target=positive(targetSoc,0,100,false),
                capacity=positive(packCapacity,5,100,false),
                massKg=positive(mass,500,4900,false),
                measured=positive(baselineFuel,0.01,1000,true);
            T1WhatIfEnergyModel.Trip tripData=T1WhatIfEnergyModel.read(
                trips[trip.getSelectedItemPosition()]);
            T1WhatIfEnergyModel.Actual real=
                T1WhatIfEnergyModel.inspect(tripData,massKg);
            boolean forced=strategy.getSelectedItemPosition()==1;
            boolean powerChanged=propulsion.getSelectedItemPosition()!=1;
            T1WhatIfEnergyModel.Scenario scenario=
                T1WhatIfEnergyModel.compare(real,initial,forced,target,capacity,
                    Double.isFinite(measured),powerChanged);
            StringBuilder b=new StringBuilder();
            b.append("RECORRIDO OBSERVADO\n");
            b.append("Muestras: ").append(real.rows).append(" · duración: ")
                .append(fmt(real.durationMillis/60000.0,1))
                .append(" min · distancia: ").append(fmt(real.km,2))
                .append(" km\n");
            b.append("SOC real: ").append(fmt(real.socStart,1))
                .append(" % → ").append(fmt(real.socEnd,1)).append(" %\n");
            b.append("Térmico apagado: ").append(fmt(real.motorOffKm,2))
                .append(" km; encendido: ").append(fmt(real.motorOnKm,2))
                .append(" km; desconocido: ")
                .append(fmt(real.unknownMotorKm,2)).append(" km\n");
            b.append("Señales de modo 1043: ").append(real.modeRows)
                .append(" · regeneración 1046: ").append(real.regenRows)
                .append(" · freno 3011: ").append(real.brakeRows)
                .append(" · voltaje HV válido: ").append(real.voltageRows)
                .append("/").append(real.rows).append("\n");
            b.append("Máximo cinético de desaceleraciones (sin pendientes "+
                "ni pérdida por freno): ").append(fmt(real.idealBrakingKwh,2))
                .append(" kWh · NO energía regenerada realmente.\n\n");
            b.append("ESCENARIO SOLICITADO\n");
            b.append(propulsion.getSelectedItem()).append(" · ")
                .append(strategy.getSelectedItem()).append(" · conducción ")
                .append(drive.getSelectedItem()).append(" · regen ")
                .append(regeneration.getSelectedItem()).append("\n");
            b.append("SOC inicial elegido: ").append(fmt(initial,1))
                .append(" % · capacidad supuesta: ")
                .append(fmt(capacity,1)).append(" kWh\n");
            if(!scenario.hasSoc){
                b.append("NO CUANTIFICABLE: falta SOC inicial/final OEM "+
                    "o hipótesis energética utilizable.\n");
            }else{
                b.append("SOC final si solo desplazamos la curva OBSERVADA: ")
                    .append(fmt(scenario.shiftedEndpointSoc,1))
                    .append(" % (NO predicción ECU).\n");
                if(forced){
                    b.append("Energía teórica mínima para TERMINAR al SOC ")
                        .append(fmt(target,1)).append(" %: ")
                        .append(fmt(scenario.energyGapKwh,2)).append(" kWh\n");
                    b.append("Equivalente ilustrativo de nafta adicional: ")
                        .append(fmt(scenario.extraGasolineMin,2)).append("–")
                        .append(fmt(scenario.extraGasolineMax,2))
                        .append(" litros (SUPUESTO 1,8–3,2 kWh útiles/L).\n");
                }
            }
            if(Double.isFinite(measured))
                b.append("Litros reales aportados para comparar: ")
                    .append(fmt(measured,2)).append(" L. No se suman "+
                        "los equivalentes anteriores como consumo predicho.\n");
            else
                b.append("Sin litros reales verificados: el contador "+
                    "gas_l_proxy no es consumo medido.\n");
            b.append("\nINTERPRETACIÓN\n").append(scenario.note);
            if(real.modeRows==0||real.regenRows==0)
                b.append("\nEste viaje es anterior al registro detallado "+
                    "de modos 1043/1040/1046; el siguiente tendrá esos valores.");
            b.append("\nCambios de modo/regeneración aún no se cuantifican "+
                "sin modelo de potencia, fuel-rate y eficiencia calibrados. "+
                "Nunca se ordena nada al vehículo.");
            result.setText(b.toString());
        }catch(Exception ex){
            result.setText("No se pudo analizar: "+
                ex.getClass().getSimpleName()+" · "+ex.getMessage());
        }
    }
}
