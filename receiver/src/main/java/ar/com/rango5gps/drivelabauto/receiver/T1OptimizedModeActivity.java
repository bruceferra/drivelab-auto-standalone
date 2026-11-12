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
 * Offline per-road rule editor. All controls SAVE PREFERENCES ONLY.
 * No automatic powertrain, SOC or regenerative-braking commands are issued.
 */
public final class T1OptimizedModeActivity extends T1ImmersiveActivity {
    private static final int BG=0xFF0A1623, CARD=0xFFF4F8FA,
        MUTED=0xFF9DB7CA, AQUA=0xFF22BDB7;
    private final EnumMap<T1OptimizedModePolicy.Road,Profile> profiles=
        new EnumMap<>(T1OptimizedModePolicy.Road.class);
    private TextView preview;
    private T1OptimizedModePolicy.Road road=T1OptimizedModePolicy.Road.UNKNOWN;
    @Override protected int overlayClearBackgroundColor(){return BG;}
    static final class Profile {
        Spinner power,strategy,regen;
        CheckBox conditional;
        EditText forced,targetMinimum;
    }
    private GradientDrawable background(int color,int radius){
        GradientDrawable b=new GradientDrawable();
        b.setColor(color);b.setCornerRadius(radius);
        return b;
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
        t.setTextColor(color);
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
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setSelection(initial);
        return s;
    }
    private EditText number(int x,String label){
        EditText t=new EditText(this);
        t.setText(String.valueOf(x));
        t.setHint(label);t.setSingleLine(true);
        t.setTextSize(16);
        t.setInputType(InputType.TYPE_CLASS_NUMBER);
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
        LinearLayout target=vertical();
        target.addView(text("SOC FORZADO % (solo si se eligió Forzado)",
            11,0xFF334B5A,false));
        p.forced=number(existing.forcedSoc,"50");target.addView(p.forced);
        column(second,target,1);
        LinearLayout threshold=vertical();
        p.conditional=new CheckBox(this);
        p.conditional.setText("EXIGIR SOC >");
        p.conditional.setChecked(existing.thresholdEnabled);
        threshold.addView(p.conditional);
        p.targetMinimum=number(existing.minBattery,"30");
        threshold.addView(p.targetMinimum);
        column(second,threshold,1);
        profiles.put(which,p);
        p.conditional.setOnCheckedChangeListener((b,checked)->
            p.targetMinimum.setEnabled(checked));
        p.targetMinimum.setEnabled(existing.thresholdEnabled);
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=vertical();root.setPadding(12,6,12,26);
        scroll.addView(root);
        LinearLayout title=horizontal();root.addView(title);
        column(title,text("MODO OPTIMIZADO · 4 PERFILES",20,Color.WHITE,true),1);
        Button close=new Button(this);close.setAllCaps(false);close.setText("VOLVER");
        close.setOnClickListener(v->finish());title.addView(close);
        root.addView(text("CONFIGURACIÓN LOCAL · SOLO SUGERENCIAS. "+
            "No cambia automáticamente EV/HEV/EV MAX, SOC ni regeneración. "+
            "Falta validar las escrituras OEM y transiciones antes de "+
            "habilitar automatización del vehículo.",13,MUTED,false));
        for(T1OptimizedModePolicy.Road r:T1OptimizedModePolicy.Road.values())
            card(root,r);
        LinearLayout actions=horizontal();root.addView(actions);
        Button save=new Button(this);
        save.setAllCaps(false);save.setText("GUARDAR LOS 4 PERFILES");
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(AQUA));
        save.setOnClickListener(v->saveProfiles());
        column(actions,save,1);
        Button simulate=new Button(this);
        simulate.setAllCaps(false);simulate.setText("VER RECOMENDACIÓN");
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
            percentage(p.targetMinimum),regen);
    }
    private void saveProfiles(){
        try{
            EnumMap<T1OptimizedModePolicy.Road,T1OptimizedModePolicy.Rule> next=
                new EnumMap<>(T1OptimizedModePolicy.Road.class);
            for(T1OptimizedModePolicy.Road r:T1OptimizedModePolicy.Road.values())
                next.put(r,fromControls(profiles.get(r)));
            for(T1OptimizedModePolicy.Road r:next.keySet())
                T1OptimizedModeStore.save(this,r,next.get(r));
            preview.setText("GUARDADOS LOS CUATRO PERFILES · sin comandos al "+
                "vehículo.\n"+currentRecommendation());
        }catch(IllegalArgumentException ex){
            preview.setText("NO GUARDADO: "+ex.getMessage());
        }
    }
    private String currentRecommendation(){
        road=T1OptimizedModeStore.lastRoad(this);
        double soc=Double.NaN;
        // Vehicle read is bounded to the user-opened diagnostic screen.
        String info=T1OptimizedModeStore.lastInfo(this);
        T1OptimizedModePolicy.Decision result=T1OptimizedModePolicy.evaluate(
            road,T1OptimizedModeStore.get(this,road),soc);
        return "VÍA: "+T1OptimizedModePolicy.roadName(road)+
            " · "+info+"\n"+result.summary()+
            "\nUna condición SOC mínima necesita batería OEM válida. "+
            "Si la lectura está ausente, se recomienda HEV SMART.";
    }
    private void showCurrent(){
        preview.setText(currentRecommendation());
    }
}
