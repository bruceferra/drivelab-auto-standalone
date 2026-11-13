package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.EnumMap;
import java.util.Locale;

/**
 * Offline four-profile editor and EXPLICIT opt-in automatic EV/HEV/EV MAX
 * controller. SOC/regen writes remain available after explicit AUTO ON
 * with guarded P/0 or D eligibility, verified against the NEW objective.
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
    static class RuleEditor {
        Spinner power,strategy,regen,forced,minPct,maxPct;
        CheckBox minEnabled,maxEnabled;
    }
    static final class Profile extends RuleEditor {
        CheckBox blockEnabled,secondEnabled;
        RuleEditor second;
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
        Spinner s=new Spinner(this,Spinner.MODE_DROPDOWN);
        T1JetourTheme.spinner(s,names);
        s.setSelection(initial);
        return s;
    }
    /** The battery CONDITION is free 5%-100% in 5% increments.
     * SOC FORZADO uses the separate OEM 1041 allowlist, never arbitrary
     * percentages that would fail on the setter.
     */
    private static final int PCT_MIN=5,PCT_STEP=5,PCT_COUNT=20;
    private Spinner percentageSelector(int previouslySaved){
        String[] options=new String[PCT_COUNT];
        for(int i=0;i<PCT_COUNT;i++)
            options[i]=((i+1)*PCT_STEP)+" %";
        int selected=Math.min(PCT_COUNT-1,
            Math.max(0,(int)Math.round((previouslySaved-PCT_MIN)/
                (double)PCT_STEP)));
        return selector(options,selected);
    }
    private Spinner forcedSocSelector(int oldValue){
        int[] allowed=T1SocRegenWritePolicy.forcedSocPresets();
        String[] choices=new String[allowed.length];
        for(int i=0;i<allowed.length;i++)
            choices[i]=allowed[i]+" %";
        return selector(choices,
            T1SocRegenWritePolicy.nearestForcedSocPresetIndex(oldValue));
    }
    private int forcedSocValue(Spinner source){
        int[] allowed=T1SocRegenWritePolicy.forcedSocPresets();
        if(source==null||source.getSelectedItemPosition()<0||
            source.getSelectedItemPosition()>=allowed.length)
            throw new IllegalArgumentException(
                "Elegí un SOC FORZADO permitido por la Jetour.");
        int chosen=allowed[source.getSelectedItemPosition()];
        if(!T1SocRegenWritePolicy.accepted(
            T1SocRegenWritePolicy.TARGET,chosen))
            throw new IllegalArgumentException(
                "Este valor SOC no está autorizado para escribir.");
        return chosen;
    }
    private void card(LinearLayout parent,T1OptimizedModePolicy.Road which){
        T1OptimizedModePolicy.Rule existing=T1OptimizedModeStore.get(this,which);
        LinearLayout box=vertical();
        box.setPadding(15,6,15,9);box.setBackground(background(CARD,15));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(5,5,5,5);parent.addView(box,cp);
        LinearLayout top=horizontal();box.addView(top);
        TextView title=text(T1OptimizedModePolicy.roadName(which),
            17,0xFF08384C,true);
        column(top,title,1);
        Profile p=new Profile();
        p.blockEnabled=new CheckBox(this);
        p.blockEnabled.setText("ACTIVADO");
        p.blockEnabled.setTextColor(T1JetourTheme.WHITE);
        p.blockEnabled.setButtonTintList(
            android.content.res.ColorStateList.valueOf(T1JetourTheme.CYAN));
        p.blockEnabled.setChecked(T1OptimizedModeStore.isEnabled(this,which));
        top.addView(p.blockEnabled);
        final TextView blockNotice=text(
            "ACTIVADO · el optimizador puede aplicar este perfil",
            11,0xFF40566B,false);
        box.addView(blockNotice);
        // This switch is effective immediately, even if another field is
        // edited but not yet saved. It does not write an OEM command.
        p.blockEnabled.setOnCheckedChangeListener((button,checked)->{
            T1OptimizedModeStore.setEnabled(this,which,checked);
            p.blockEnabled.setText(checked?"ACTIVADO":"DESACTIVADO");
            blockNotice.setText(checked?
                "ACTIVADO · se habilitan órdenes de este bloque":
                "DESACTIVADO · sin órdenes OEM de este bloque");
            blockNotice.setTextColor(checked?
                T1JetourTheme.CYAN:T1JetourTheme.MUTED);
            if(preview!=null)preview.setText(
                T1OptimizedModePolicy.roadName(which)+
                (checked?" · perfil ACTIVADO":" · perfil DESACTIVADO")+
                "\nEl cambio ya está guardado y funciona sin tocar GUARDAR.");
        });
        boolean initiallyOn=p.blockEnabled.isChecked();
        p.blockEnabled.setText(initiallyOn?"ACTIVADO":"DESACTIVADO");
        blockNotice.setText(initiallyOn?
            "ACTIVADO · el optimizador puede aplicar este perfil":
            "DESACTIVADO · sin órdenes OEM de este bloque");
        if(which==T1OptimizedModePolicy.Road.UNKNOWN)
            box.addView(text("Respaldo configurable cuando el GPS o mapa no "+
                "identifican una vía confiable.",12,0xFF40566B,false));
        box.addView(text(T1OptimizedModePolicy.roadName(which)+
            " 1 · CONDICIÓN PRINCIPAL",15,T1JetourTheme.CYAN,true));
        LinearLayout options=horizontal();box.addView(options);
        p.power=selector(new String[]{"EV","HEV","EV MAX"},existing.power.ordinal());
        p.strategy=selector(new String[]{"SMART","SOC FORZADO"},
            existing.strategy.ordinal());
        p.regen=selector(new String[]{"BAJA","MEDIA","ALTA"},
            existing.regeneration.ordinal());
        LinearLayout power=vertical();power.addView(text("PROPULSIÓN",12,0xFF334B5A,true));
        power.addView(p.power);column(options,power,1);
        LinearLayout soc=vertical();soc.addView(text("GESTIÓN SOC",12,0xFF334B5A,true));
        soc.addView(p.strategy);
        soc.addView(text("EV: SMART primero si estaba FORZADO; "+
            "EV MAX: el auto desactiva FORZADO solo. "+
            "FORZADO se configura únicamente con HEV.",
            10,0xFF40566B,false));
        column(options,soc,1);
        LinearLayout regen=vertical();regen.addView(text("REGENERACIÓN",12,0xFF334B5A,true));
        regen.addView(p.regen);column(options,regen,1);

        LinearLayout second=horizontal();box.addView(second);
        // Two independent AND conditions on ACTUAL OEM battery SOC.
        // Disable either limit to get an open-ended minimum or maximum.
        // A missing SOC reading never satisfies enabled bounds.
        LinearLayout threshold=vertical();
        threshold.addView(text("RANGO DE BATERÍA · SOC ACTUAL",
            12,0xFF334B5A,true));
        threshold.addView(text("Activá uno o ambos límites. Con ambos activos "+
            "se usa el rango inclusivo: SOC ≥ mínimo Y SOC ≤ máximo. "+
            "Si apagás uno, el otro funciona por sí solo.",
            11,0xFF40566B,false));
        LinearLayout lower=horizontal();
        p.minEnabled=new CheckBox(this);
        p.minEnabled.setText("MÍNIMO ≥");
        p.minEnabled.setTextColor(T1JetourTheme.WHITE);
        p.minEnabled.setButtonTintList(android.content.res.ColorStateList.valueOf(
            T1JetourTheme.CYAN));
        p.minEnabled.setChecked(existing.minEnabled);
        column(lower,p.minEnabled,2);
        p.minPct=percentageSelector(existing.minBattery);
        column(lower,p.minPct,1);
        threshold.addView(lower);
        LinearLayout upper=horizontal();
        p.maxEnabled=new CheckBox(this);
        p.maxEnabled.setText("MÁXIMO ≤");
        p.maxEnabled.setTextColor(T1JetourTheme.WHITE);
        p.maxEnabled.setButtonTintList(android.content.res.ColorStateList.valueOf(
            T1JetourTheme.CYAN));
        p.maxEnabled.setChecked(existing.maxEnabled);
        column(upper,p.maxEnabled,2);
        p.maxPct=percentageSelector(existing.maxBattery);
        column(upper,p.maxPct,1);
        threshold.addView(upper);
        column(second,threshold,1);

        LinearLayout target=vertical();
        target.addView(text("OBJETIVO SOC FORZADO",12,0xFF334B5A,true));
        target.addView(text("Solo objetivos admitidos por la lista OEM "+
            "1041: 20, 30, 40, 50, 60 y 70 % (múltiplos de diez). "+
            "No se muestran porcentajes arbitrarios ni 56 %. "+
            "DriveLab comprueba la lectura de vuelta del vehículo.",
            11,0xFF40566B,false));
        LinearLayout targetEditor=horizontal();
        p.forced=forcedSocSelector(existing.forcedSoc);
        column(targetEditor,p.forced,1);
        targetEditor.addView(text("%",15,0xFF334B5A,true));
        target.addView(targetEditor);
        column(second,target,1);
        profiles.put(which,p);
        p.minEnabled.setOnCheckedChangeListener((b,checked)->
            p.minPct.setEnabled(checked));
        p.maxEnabled.setOnCheckedChangeListener((b,checked)->
            p.maxPct.setEnabled(checked));
        p.minPct.setEnabled(existing.minEnabled);
        p.maxPct.setEnabled(existing.maxEnabled);
        final Runnable syncSocAvailability=()->{
            boolean electric=p.power.getSelectedItemPosition()!=
                T1OptimizedModePolicy.Power.HEV.ordinal();
            // EV needs SMART to exit HEV FORZADO; EV MAX itself turns
            // FORZADO off in this T1. Do not offer contradictory EV+
            // FORZADO settings or re-apply FORZADO after switching.
            if(electric&&p.strategy.getSelectedItemPosition()!=
                    T1OptimizedModePolicy.Strategy.SMART.ordinal())
                p.strategy.setSelection(
                    T1OptimizedModePolicy.Strategy.SMART.ordinal());
            p.strategy.setEnabled(!electric);
            p.forced.setEnabled(!electric&&p.strategy.getSelectedItemPosition()==
                T1OptimizedModePolicy.Strategy.FORCED.ordinal());
        };
        p.power.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view,int position,long id){syncSocAvailability.run();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        p.strategy.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view,int position,long id){syncSocAvailability.run();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        syncSocAvailability.run();
        addSecondRuleEditor(box,p,which,
            T1OptimizedModeStore.getSecond(this,which));
    }

    /** Each road contains two independent editable conditions. Rule B is
     * optional and stays OFF until the user saves it explicitly.
     */
    private void addSecondRuleEditor(LinearLayout parent,Profile parentProfile,
                                     T1OptimizedModePolicy.Road road,
                                     T1OptimizedModePolicy.Rule existing){
        LinearLayout heading=horizontal();parent.addView(heading);
        column(heading,text(T1OptimizedModePolicy.roadName(road)+
            " 2 · SEGUNDA CONDICIÓN",15,T1JetourTheme.CYAN,true),1);
        parentProfile.secondEnabled=new CheckBox(this);
        parentProfile.secondEnabled.setText("ACTIVAR "+
            T1OptimizedModePolicy.roadName(road)+" 2");
        parentProfile.secondEnabled.setTextColor(Color.WHITE);
        parentProfile.secondEnabled.setButtonTintList(
            android.content.res.ColorStateList.valueOf(T1JetourTheme.CYAN));
        parentProfile.secondEnabled.setChecked(
            T1OptimizedModeStore.secondEnabled(this,road));
        heading.addView(parentProfile.secondEnabled);
        LinearLayout section=vertical();parent.addView(section);
        section.addView(text("Cuando SOC cumple la regla 2, aplica "+
            "sus propios ajustes. Entre los dos umbrales se MANTIENE "+
            "la última regla 1 o 2 confirmada por la ECU, sin nuevas "+
            "escrituras OEM. Si todavía no hubo coincidencias, no cambia "+
            "nada. Los rangos de 1 y 2 no pueden superponerse.",
            11,MUTED,false));
        RuleEditor q=new RuleEditor();parentProfile.second=q;
        LinearLayout options=horizontal();section.addView(options);
        q.power=selector(new String[]{"EV","HEV","EV MAX"},
            existing.power.ordinal());
        q.strategy=selector(new String[]{"SMART","SOC FORZADO"},
            existing.strategy.ordinal());
        q.regen=selector(new String[]{"BAJA","MEDIA","ALTA"},
            existing.regeneration.ordinal());
        LinearLayout power=vertical();
        power.addView(text("PROPULSIÓN B",12,Color.WHITE,true));
        power.addView(q.power);column(options,power,1);
        LinearLayout management=vertical();
        management.addView(text("GESTIÓN SOC B",12,Color.WHITE,true));
        management.addView(q.strategy);column(options,management,1);
        LinearLayout regen=vertical();
        regen.addView(text("REGENERACIÓN B",12,Color.WHITE,true));
        regen.addView(q.regen);column(options,regen,1);

        LinearLayout battery=horizontal();section.addView(battery);
        LinearLayout condition=vertical();
        condition.addView(text("BATERÍA REAL · RANGO B",
            12,Color.WHITE,true));
        LinearLayout low=horizontal();condition.addView(low);
        q.minEnabled=new CheckBox(this);
        q.minEnabled.setText("MÍNIMO ≥");
        q.minEnabled.setTextColor(Color.WHITE);
        q.minEnabled.setButtonTintList(
            android.content.res.ColorStateList.valueOf(T1JetourTheme.CYAN));
        q.minEnabled.setChecked(existing.minEnabled);
        column(low,q.minEnabled,2);
        q.minPct=percentageSelector(existing.minBattery);
        column(low,q.minPct,1);
        LinearLayout high=horizontal();condition.addView(high);
        q.maxEnabled=new CheckBox(this);
        q.maxEnabled.setText("MÁXIMO ≤");
        q.maxEnabled.setTextColor(Color.WHITE);
        q.maxEnabled.setButtonTintList(
            android.content.res.ColorStateList.valueOf(T1JetourTheme.CYAN));
        q.maxEnabled.setChecked(existing.maxEnabled);
        column(high,q.maxEnabled,2);
        q.maxPct=percentageSelector(existing.maxBattery);
        column(high,q.maxPct,1);
        column(battery,condition,2);
        LinearLayout forced=vertical();
        forced.addView(text("SOC FORZADO B · SOLO 20 A 70",
            12,Color.WHITE,true));
        q.forced=forcedSocSelector(existing.forcedSoc);
        forced.addView(q.forced);
        column(battery,forced,1);
        q.minPct.setEnabled(existing.minEnabled);
        q.maxPct.setEnabled(existing.maxEnabled);
        q.minEnabled.setOnCheckedChangeListener(
            (button,checked)->q.minPct.setEnabled(checked));
        q.maxEnabled.setOnCheckedChangeListener(
            (button,checked)->q.maxPct.setEnabled(checked));
        final Runnable syncStrategy=()->{
            boolean electric=q.power.getSelectedItemPosition()!=
                T1OptimizedModePolicy.Power.HEV.ordinal();
            if(electric&&q.strategy.getSelectedItemPosition()!=
                    T1OptimizedModePolicy.Strategy.SMART.ordinal())
                q.strategy.setSelection(
                    T1OptimizedModePolicy.Strategy.SMART.ordinal());
            q.strategy.setEnabled(!electric);
            q.forced.setEnabled(!electric&&
                q.strategy.getSelectedItemPosition()==
                    T1OptimizedModePolicy.Strategy.FORCED.ordinal());
        };
        q.power.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> a,
                    View v,int position,long id){syncStrategy.run();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> a){}
        });
        q.strategy.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> a,
                    View v,int position,long id){syncStrategy.run();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> a){}
        });
        parentProfile.secondEnabled.setOnCheckedChangeListener(
            (button,checked)->{
                section.setVisibility(checked?View.VISIBLE:View.GONE);
                if(preview!=null)preview.setText(
                    T1OptimizedModePolicy.roadName(road)+" 2 "+
                    (checked?"habilitada":"deshabilitada")+
                    " · tocá GUARDAR LOS 4 PERFILES para aplicar");
            });
        section.setVisibility(parentProfile.secondEnabled.isChecked()?
            View.VISIBLE:View.GONE);
        syncStrategy.run();
    }

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackground(T1JetourTheme.backdrop());
        LinearLayout root=vertical();root.setPadding(12,6,12,26);
        scroll.addView(root);
        LinearLayout title=horizontal();root.addView(title);
        column(title,text("MODO OPTIMIZADO · CIUDAD 1/2 · CARRETERA 1/2 · "+
            "AUTOPISTA 1/2 · DESCONOCIDO 1/2",
            18,Color.WHITE,true),1);
        Button close=new Button(this);close.setAllCaps(false);close.setText("VOLVER");
        T1JetourTheme.button(close,T1JetourTheme.PANEL_HI);
        close.setOnClickListener(v->finish());title.addView(close);
        root.addView(text("Cada vía tiene reglas independientes 1 y 2. "+
            "Pueden ejecutarse sucesivamente: por ejemplo AUTOPISTA 1 "+
            "con SOC ≥70%, mantener EV mientras cae a 60, 50 y 40%, "+
            "y recién pasar a AUTOPISTA 2 con SOC ≤30%. Una vez en "+
            "regla 2 se conserva hasta una nueva condición. "+
            "Sin coincidencia previa no se cambia el vehículo. "+
            "MODO OPTIMIZADO automático según el camino: "+
            "EV/HEV/EV MAX (1043), SMART/FORZADO (1040), objetivo SOC (1041) "+
            "y regeneración (1046). Los perfiles cambian mientras circulás "+
            "en D sin obligarte a detenerte. Cada bloque de vía tiene "+
            "ACTIVADO/DESACTIVADO independiente: apagado significa cero "+
            "órdenes del bloque, incluso el modo HEV de respaldo. "+
            "GPS y telemetría deben ser "+
            "válidos; cada orden OEM se confirma por lectura posterior. "+
            "Si un comando falla se detiene la automatización, y vos "+
            "podés comprobar el resultado en el menú original Jetour.",
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
        Button socDiag=new Button(this);
        socDiag.setAllCaps(false);
        socDiag.setText("VER / SUBIR DIAGNÓSTICO SOC · 1/2/3");
        T1JetourTheme.button(socDiag,T1JetourTheme.PANEL_HI);
        socDiag.setOnClickListener(v->showSocDiagnostic());
        root.addView(socDiag);
        root.addView(text("Paso 1: seguridad OEM. Paso 2: envío del "+
            "nuevo SOC. Paso 3: confirmación por getters 1041. "+
            "SUBIR envía directamente al servidor privado ya vinculado "+
            "por QR; sin menú de otras aplicaciones ni GPS/VIN. "+
            "Si falla la red, conserva el CSV local.",12,MUTED,false));
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
    private int percentage(Spinner spinner){
        if(spinner==null||spinner.getSelectedItemPosition()<0||
            spinner.getSelectedItemPosition()>=PCT_COUNT)
            throw new IllegalArgumentException(
                "Elegí un porcentaje del menú (5 a 100 %).");
        return PCT_MIN+PCT_STEP*spinner.getSelectedItemPosition();
    }
    private T1OptimizedModePolicy.Rule fromControls(RuleEditor p){
        T1OptimizedModePolicy.Power power=T1OptimizedModePolicy.Power.values()[
            p.power.getSelectedItemPosition()];
        T1OptimizedModePolicy.Strategy strategy=
            T1OptimizedModePolicy.Strategy.values()[
                p.strategy.getSelectedItemPosition()];
        T1OptimizedModePolicy.Regen regen=T1OptimizedModePolicy.Regen.values()[
            p.regen.getSelectedItemPosition()];
        int lower=percentage(p.minPct),upper=percentage(p.maxPct);
        boolean minimumEnabled=p.minEnabled.isChecked(),
            maximumEnabled=p.maxEnabled.isChecked();
        if(minimumEnabled&&maximumEnabled&&lower>upper)
            throw new IllegalArgumentException(
                "Rango de batería inválido: mínimo "+lower+
                " % mayor al máximo "+upper+
                " %. Ajustá uno de los desplegables.");
        return new T1OptimizedModePolicy.Rule(
            power,strategy,forcedSocValue(p.forced),
            minimumEnabled,lower,maximumEnabled,upper,regen);
    }
    private boolean saveProfiles(){
        try{
            EnumMap<T1OptimizedModePolicy.Road,T1OptimizedModePolicy.Rule> first=
                new EnumMap<>(T1OptimizedModePolicy.Road.class);
            EnumMap<T1OptimizedModePolicy.Road,T1OptimizedModePolicy.Rule> second=
                new EnumMap<>(T1OptimizedModePolicy.Road.class);
            // Validate ALL eight rules before changing stored preferences.
            for(T1OptimizedModePolicy.Road r:T1OptimizedModePolicy.Road.values()){
                Profile editor=profiles.get(r);
                T1OptimizedModePolicy.Rule a=fromControls(editor);
                T1OptimizedModePolicy.Rule c=fromControls(editor.second);
                if(editor.secondEnabled.isChecked()&&
                   T1OptimizedDualRulePolicy.overlaps(a,c))
                    throw new IllegalArgumentException(
                        T1OptimizedModePolicy.roadName(r)+
                        ": los rangos de REGLA 1 y REGLA 2 se superponen. "+
                        "Separá los umbrales para evitar órdenes contradictorias.");
                first.put(r,a);
                second.put(r,c);
            }
            for(T1OptimizedModePolicy.Road r:first.keySet()){
                Profile editor=profiles.get(r);
                T1OptimizedModeStore.save(this,r,first.get(r));
                T1OptimizedModeStore.saveSecond(this,r,second.get(r),
                    editor.secondEnabled.isChecked());
                T1OptimizedModeStore.setEnabled(this,r,
                    editor.blockEnabled.isChecked());
            }
            preview.setText("GUARDADAS LAS 8 REGLAS (1 Y 2 POR VÍA) · "+
                "cada activación se recuerda hasta el siguiente umbral; "+
                "sin coincidencia inicial no se envía ninguna orden OEM. "+
                "1043/1040/1041/1046 automáticos en P o D con "+
                "lectura OEM confirmada.\n"+
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
            T1JetourTheme.button(automaticButton,
                enabled?T1JetourTheme.GREEN_BUTTON:T1JetourTheme.RED_BUTTON);
        }
        if(automaticState!=null)
            automaticState.setText(T1OptimizedAutoService.status(this));
    }
    private void showSocDiagnostic(){
        String report=T1OemDiagnosticLog.report(this);
        new android.app.AlertDialog.Builder(this)
            .setTitle("DIAGNÓSTICO · MODO OPTIMIZADO / SOC")
            .setMessage("1: marcha y velocidad. 2: escritura Binder. "+
                "3: lectura del NUEVO objetivo, entero y flotante. "+
                "Si no confirma, no se repite automáticamente.\n\n"+
                report+"\n\nSUBIR: servidor privado DriveLab "+
                "(vinculación QR, no otras aplicaciones).")
            .setNegativeButton("CERRAR",null)
            .setPositiveButton("SUBIR AL SERVIDOR",
                (d,w)->uploadSocDiagnostic())
            .show();
    }
    /** Explicit upload only: never ACTION_SEND, no system app chooser. */
    private void uploadSocDiagnostic(){
        if(!T1CloudDiagnostics.active(this)){
            new android.app.AlertDialog.Builder(this)
                .setTitle("NUBE PRIVADA NO VINCULADA")
                .setMessage("Este informe sigue guardado en la T1. "+
                    "Vinculá primero este Receiver con tu Xiaomi mediante "+
                    "ACTIVAR NUBE CON QR. Después repetí SUBIR AL SERVIDOR. "+
                    "No hace falta compartir con otra aplicación.")
                .setNegativeButton("CERRAR",null)
                .setPositiveButton("VINCULAR QR",(d,w)->{
                    Intent open=new Intent(this,ReceiverVehicleActivity.class);
                    open.putExtra("open_tab",3);
                    startActivity(open);
                }).show();
            return;
        }
        final java.io.File cloudCsv;
        try{
            cloudCsv=T1OemDiagnosticLog.exportCloudCsv(this);
        }catch(Exception failure){
            new android.app.AlertDialog.Builder(this)
                .setTitle("INFORME NO PREPARADO")
                .setMessage(failure.getMessage()==null?
                    "No se pudo conservar el informe local":
                    failure.getMessage())
                .setPositiveButton("CERRAR",null).show();
            return;
        }
        if(preview!=null)
            preview.setText("SUBIENDO DIAGNÓSTICO SOC · "+
                cloudCsv.getName()+" · servidor privado HTTPS…");
        T1CloudDiagnostics.upload(this,cloudCsv,(message,qrUrl)->{
            // T1CloudDiagnostics invokes this callback on Android main thread
            // only after server responses have been verified.
            if(isFinishing()||isDestroyed())return;
            if(preview!=null)preview.setText(message);
            boolean received=message.startsWith("SERVIDOR OK");
            new android.app.AlertDialog.Builder(this)
                .setTitle(received?
                    "DIAGNÓSTICO RECIBIDO EN SERVIDOR":
                    "DIAGNÓSTICO NO SUBIDO")
                .setMessage(message+"\n\n"+
                    (received?"También quedó una copia privada en la T1.":
                        "El CSV quedó guardado. Verificá QR/internet "+
                        "y repetí SUBIR AL SERVIDOR."))
                .setPositiveButton("CERRAR",null).show();
        });
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
                "resultado del comando 1043. Se intentan también los "+
                "cambios 1040/1041/1046 en D, sin detenerte, cuando el "+
                "perfil y las lecturas OEM son confiables. Son escrituras "+
                "experimentales: se comprueba cada una con readback. "+
                "Ante un rechazo OEM se desactiva AUTO. Podés apagar "+
                "el optimizador desde este botón o la notificación.")
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
        if(!T1OptimizedModeStore.isEnabled(this,road))
            return "VÍA: "+T1OptimizedModePolicy.roadName(road)+
                " · PERFIL DESACTIVADO. No se ejecutan órdenes OEM "+
                "ni respaldo automático de este bloque.";
        T1OptimizedModePolicy.Road actuallyApplied=
            T1OptimizedAutoService.verifiedAppliedRoad(this);
        String appliedLabel=T1OptimizedAutoService.verifiedAppliedBranchLabel(this);
        int previous=actuallyApplied==road&&"1".equals(appliedLabel)?
            T1OptimizedDualRulePolicy.PRIMARY:
            actuallyApplied==road&&"2".equals(appliedLabel)?
                T1OptimizedDualRulePolicy.SECONDARY:
                T1OptimizedDualRulePolicy.NONE;
        T1OptimizedDualRulePolicy.Choice choice=
            T1OptimizedDualRulePolicy.selectWithHold(
                T1OptimizedModeStore.get(this,road),
                T1OptimizedModeStore.getSecond(this,road),
                T1OptimizedModeStore.secondEnabled(this,road),soc,previous);
        String outcome=choice.held?
            "MANTENER "+choice.title(road)+" · sin cambios OEM en el rango intermedio":
            choice.matched()?choice.title(road)+" · "+
                T1OptimizedModePolicy.evaluate(road,choice.rule,soc).summary():
                choice.reason+" · mantener modo actual sin cambios";
        return "VÍA: "+T1OptimizedModePolicy.roadName(road)+
            " · "+info+"\n"+outcome+
            "\nAUTO: 1043/1040/1041/1046 por tipo de vía. "+
            "Los cambios de regeneración pueden ocurrir en movimiento "+
            "cuando el perfil cambia, con verificación OEM. "+
            "La condición SOC usa batería ACTUAL, no objetivo forzado. "+
            " · optimización SOC/regeneración experimental ON/OFF";
    }
    private void showCurrent(){
        preview.setText(currentRecommendation()+"\n"+
            T1OptimizedAutoService.status(this));
        refreshAutoUi();
    }
}
