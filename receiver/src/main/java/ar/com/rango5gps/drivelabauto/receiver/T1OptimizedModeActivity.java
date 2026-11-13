package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.EnumMap;
import java.util.Locale;

/**
 * Offline four-profile editor and EXPLICIT opt-in automatic EV/HEV/EV MAX
 * controller. Only OEM 1043 is writable automatically. Forced SOC and
 * regenerative braking remain preferences/suggestions.
 */
public final class T1OptimizedModeActivity extends T1ImmersiveActivity {
    private static final int BG=T1JetourTheme.NIGHT,
        CARD=T1JetourTheme.PANEL,
        MUTED=T1JetourTheme.MUTED, AQUA=T1JetourTheme.CYAN;
    private final EnumMap<T1OptimizedModePolicy.Road,Profile> profiles=
        new EnumMap<>(T1OptimizedModePolicy.Road.class);
    private TextView preview,automaticState;
    private Button automaticButton;
    private T1OptimizedModePolicy.Road road=T1OptimizedModePolicy.Road.UNKNOWN;
    @Override protected int overlayClearBackgroundColor(){return BG;}
    static final class Profile {
        Spinner power,strategy,regen,comparison;
        CheckBox conditional;
        EditText forced,targetMinimum;
    }
    private GradientDrawable background(int color,int radius){
        return T1JetourTheme.surface(color,radius);
    }
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout horizontal(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);
        t.setTextColor(color==0xFF08384C||color==0xFF334B5A?
            T1JetourTheme.WHITE:color==0xFF40566B?T1JetourTheme.MUTED:color);
        t.setPadding(5,7,5,7);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private void column(LinearLayout into,View widget,float weight){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,weight);
        p.setMargins(4,0,4,0);into.addView(widget,p);
    }
    private Spinner selector(String[] names,int initial){
        Spinner s=new Spinner(this);
        T1JetourTheme.spinner(s,names);
        s.setSelection(initial);
        return s;
    }
    private EditText number(int x,String label){
        EditText t=new EditText(this);
        t.setText(String.valueOf(x));
        t.setHint(label);t.setSingleLine(true);
        t.setTextSize(16);
        t.setInputType(InputType.TYPE_CLASS_NUMBER);
        T1JetourTheme.number(t);
        return t;
    }
    private void card(LinearLayout parent,T1OptimizedModePolicy.Road which){
        T1OptimizedModePolicy.Rule existing=T1OptimizedModeStore.get(this,which);
        LinearLayout box=vertical();
        box.setPadding(15,6,15,9);box.setBackground(background(CARD,15));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(5,5,5,5);parent.addView(box,cp);
        TextView title=text(T1OptimizedModePolicy.roadName(which),17,0xFF08384C,true);
        box.addView(title);
        if(which==T1OptimizedModePolicy.Road.UNKNOWN)
            box.addView(text("Respaldo configurable cuando el GPS o mapa no "+
                "identifican una vía confiable.",12,0xFF40566B,false));
        LinearLayout options=horizontal();box.addView(options);
        Profile p=new Profile();
        p.power=selector(new String[]{"EV","HEV","EV MAX"},existing.power.ordinal());
        p.strategy=selector(new String[]{"SMART","SOC FORZADO"},
            existing.strategy.ordinal());
        p.regen=selector(new String[]{"BAJA","MEDIA","ALTA"},
            existing.regeneration.ordinal());
        LinearLayout power=vertical();power.addView(text("PROPULSIÓN",12,0xFF334B5A,true));
        power.addView(p.power);column(options,power,1);
        LinearLayout soc=vertical();soc.addView(text("GESTIÓN SOC",12,0xFF334B5A,true));
        soc.addView(p.strategy);column(options,soc,1);
        LinearLayout regen=vertical();regen.addView(text("REGENERACIÓN",12,0xFF334B5A,true));
        regen.addView(p.regen);column(options,regen,1);

        LinearLayout second=horizontal();box.addView(second);
        // Left: a CONDITION on actual observed battery percentage.
        // Right: separate target percentage requested in FORCED mode.
        LinearLayout threshold=vertical();
        threshold.addView(text("CONDICIÓN DE BATERÍA (SOC ACTUAL)",
            12,0xFF334B5A,true));
        p.conditional=new CheckBox(this);
        p.conditional.setText("ACTIVAR CONDICIÓN");
        p.conditional.setTextColor(T1JetourTheme.WHITE);
        p.conditional.setButtonTintList(android.content.res.ColorStateList.valueOf(
            T1JetourTheme.CYAN));
        p.conditional.setChecked(existing.thresholdEnabled);
        threshold.addView(p.conditional);
        LinearLayout thresholdEditor=horizontal();
        p.comparison=selector(new String[]{
                "Mayor o igual (≥)","Menor o igual (≤)"},
            existing.comparison.ordinal());
        column(thresholdEditor,p.comparison,2);
        p.targetMinimum=number(existing.minBattery,"Porcentaje");
        column(thresholdEditor,p.targetMinimum,1);
        thresholdEditor.addView(text("%",15,0xFF334B5A,true));
        threshold.addView(thresholdEditor);
        column(second,threshold,1);

        LinearLayout target=vertical();
        target.addView(text("OBJETIVO SOC FORZADO",12,0xFF334B5A,true));
        target.addView(text("Porcentaje que se le pediría mantener al auto "+
            "si elegís SOC FORZADO (distinto de la condición).",
            11,0xFF40566B,false));
        LinearLayout targetEditor=horizontal();
        p.forced=number(existing.forcedSoc,"Porcentaje objetivo");
        column(targetEditor,p.forced,1);
        targetEditor.addView(text("%",15,0xFF334B5A,true));
        target.addView(targetEditor);
        column(second,target,1);
        profiles.put(which,p);
        p.conditional.setOnCheckedChangeListener((b,checked)->{
            p.targetMinimum.setEnabled(checked);
            p.comparison.setEnabled(checked);
        });
        p.targetMinimum.setEnabled(existing.thresholdEnabled);
        p.comparison.setEnabled(existing.thresholdEnabled);
        p.forced.setEnabled(existing.strategy==
            T1OptimizedModePolicy.Strategy.FORCED);
        p.strategy.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view,int position,long id){
                p.forced.setEnabled(position==T1OptimizedModePolicy.Strategy.FORCED.ordinal());
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackground(T1JetourTheme.backdrop());
        LinearLayout root=vertical();root.setPadding(12,6,12,26);
        scroll.addView(root);
        LinearLayout title=horizontal();root.addView(title);
        column(title,text("MODO OPTIMIZADO · 4 PERFILES",20,Color.WHITE,true),1);
        Button close=new Button(this);close.setAllCaps(false);close.setText("VOLVER");
        T1JetourTheme.button(close,T1JetourTheme.PANEL_HI);
        close.setOnClickListener(v->finish());title.addView(close);
        root.addView(text("MODO OPTIMIZADO ON aplica EV / HEV / EV MAX "+
            "(señal 1043) automáticamente según el mapa OFFLINE y la "+
            "condición SOC de cada perfil. Solo se hacen cambios con GPS "+
            "preciso, clasificación estable y doble comprobación OEM de "+
            "marcha/velocidad y modo. SOC SMART/FORZADO (1040/1041) y "+
            "REGENERACIÓN (1046) siguen SOLO COMO PREFERENCIAS: sus "+
            "comandos todavía no están validados. Si OEM rechaza un "+
            "cambio, AUTO se desactiva; no repite órdenes ciegamente.",
            13,MUTED,false));
        LinearLayout switchRow=horizontal();root.addView(switchRow);
        automaticButton=new Button(this);
        automaticButton.setAllCaps(false);
        automaticButton.setTextSize(18);
        T1JetourTheme.button(automaticButton,0xFF24576B);
        automaticButton.setOnClickListener(v->toggleAutomatic());
        column(switchRow,automaticButton,1);
        automaticState=text("AUTO: verificando estado…",14,Color.WHITE,true);
        root.addView(automaticState);
        refreshAutoUi();
        for(T1OptimizedModePolicy.Road r:T1OptimizedModePolicy.Road.values())
            card(root,r);
        LinearLayout actions=horizontal();root.addView(actions);
        Button save=new Button(this);
        save.setAllCaps(false);save.setText("GUARDAR LOS 4 PERFILES");
        T1JetourTheme.button(save,0xFF0D7078);
        save.setOnClickListener(v->saveProfiles());
        column(actions,save,1);
        Button simulate=new Button(this);
        simulate.setAllCaps(false);simulate.setText("VER RECOMENDACIÓN");
        T1JetourTheme.button(simulate,T1JetourTheme.PANEL_HI);
        simulate.setOnClickListener(v->showCurrent());
        column(actions,simulate,1);
        preview=text("Se mostrará el último tipo de vía detectado por el "+
            "mapa offline y el perfil configurado.",15,Color.WHITE,false);
        root.addView(preview);
        setContentView(scroll);
        showCurrent();
    }
    private int percentage(EditText e){
        try{
            String x=e.getText().toString().trim();
            if(x.isEmpty())throw new IllegalArgumentException();
            int value=Integer.parseInt(x);
            if(value<0||value>100)throw new IllegalArgumentException();
            return value;
        }catch(Exception ex){
            throw new IllegalArgumentException("Los porcentajes deben "+
                "estar entre 0 y 100.");
        }
    }
    private T1OptimizedModePolicy.Rule fromControls(Profile p){
        T1OptimizedModePolicy.Power power=T1OptimizedModePolicy.Power.values()[
            p.power.getSelectedItemPosition()];
        T1OptimizedModePolicy.Strategy strategy=
            T1OptimizedModePolicy.Strategy.values()[
                p.strategy.getSelectedItemPosition()];
        T1OptimizedModePolicy.Regen regen=T1OptimizedModePolicy.Regen.values()[
            p.regen.getSelectedItemPosition()];
        // Even disabled fields must be validated to prevent hidden bad prefs.
        return new T1OptimizedModePolicy.Rule(
            power,strategy,percentage(p.forced),p.conditional.isChecked(),
            percentage(p.targetMinimum),
            T1OptimizedModePolicy.Comparison.values()[
                p.comparison.getSelectedItemPosition()],regen);
    }
    private boolean saveProfiles(){
        try{
            EnumMap<T1OptimizedModePolicy.Road,T1OptimizedModePolicy.Rule> next=
                new EnumMap<>(T1OptimizedModePolicy.Road.class);
            for(T1OptimizedModePolicy.Road r:T1OptimizedModePolicy.Road.values())
                next.put(r,fromControls(profiles.get(r)));
            for(T1OptimizedModePolicy.Road r:next.keySet())
                T1OptimizedModeStore.save(this,r,next.get(r));
            preview.setText("GUARDADOS LOS CUATRO PERFILES · "+
                "el modo automático solo controla 1043 si está ON.\n"+
                currentRecommendation());
            return true;
        }catch(IllegalArgumentException ex){
            preview.setText("NO GUARDADO: "+ex.getMessage());
            return false;
        }
    }
    private void refreshAutoUi(){
        boolean enabled=T1OptimizedAutoService.enabled(this);
        if(automaticButton!=null){
            automaticButton.setText(enabled?
                "MODO OPTIMIZADO · ON · TOCAR PARA APAGAR":
                "MODO OPTIMIZADO · OFF · TOCAR PARA ACTIVAR");
            automaticButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    enabled?0xFF087B71:0xFF7B4541));
        }
        if(automaticState!=null)
            automaticState.setText(T1OptimizedAutoService.status(this));
    }
    private void toggleAutomatic(){
        if(T1OptimizedAutoService.enabled(this)){
            T1OptimizedAutoService.disable(this);
            refreshAutoUi();
            showCurrent();
            return;
        }
        if(!saveProfiles())return;
        new android.app.AlertDialog.Builder(this)
            .setTitle("ACTIVAR MODO OPTIMIZADO")
            .setMessage("Al activarlo, DriveLab cambiará automáticamente "+
                "EV, HEV o EV MAX según la vía y el SOC, incluso con Maps "+
                "en pantalla. La app necesita GPS preciso y el mapa "+
                "offline PBA+CABA. Verifica marcha P/0 o D con velocidad "+
                "OEM válida, tres lecturas de vía consecutivas y el "+
                "resultado del comando 1043. No aplica todavía SOC "+
                "Forzado/Smart ni regeneración, aunque los hayas "+
                "configurado. Ante un rechazo OEM se desactiva "+
                "automáticamente. Podés apagarlo con este mismo botón "+
                "o con la notificación del sistema.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                String message=T1OptimizedAutoService.activate(this);
                refreshAutoUi();
                preview.setText(message+"\n"+currentRecommendation());
            }).show();
    }
    @Override protected void onResume(){
        super.onResume();
        refreshAutoUi();
    }
    private String currentRecommendation(){
        road=T1OptimizedModeStore.lastRoad(this);
        // A contemporaneous OEM SOC snapshot is handed from the cockpit.
        // Never reuse stale readings or infer battery state from a mode label.
        long at=getIntent().getLongExtra("soc_at",0L);
        double soc=System.currentTimeMillis()-at<15000&&
            System.currentTimeMillis()-at>=0?
            getIntent().getDoubleExtra("soc",Double.NaN):Double.NaN;
        String info=T1OptimizedModeStore.lastInfo(this);
        T1OptimizedModePolicy.Decision result=T1OptimizedModePolicy.evaluate(
            road,T1OptimizedModeStore.get(this,road),soc);
        return "VÍA: "+T1OptimizedModePolicy.roadName(road)+
            " · "+info+"\n"+result.summary()+
            "\nEl modo automático aplica solo 1043 si está encendido. "+
            "Una condición SOC necesita batería OEM válida. "+
            "Si la lectura está ausente, se recomienda HEV SMART. "+
            "La condición usa el SOC actual, no el objetivo SOC forzado.";
    }
    private void showCurrent(){
        preview.setText(currentRecommendation()+"\n"+
            T1OptimizedAutoService.status(this));
        refreshAutoUi();
    }
}
