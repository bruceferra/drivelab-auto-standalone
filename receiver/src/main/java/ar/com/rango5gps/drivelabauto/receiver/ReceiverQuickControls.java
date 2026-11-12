package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Wide top-level control strip. No submenus, no modal confirmations and no
 * activity re-render on tap. Motor actions still perform OEM safety checks.
 */
final class ReceiverQuickControls {
    private static final int TEAL=0xFF008577,BLUE=0xFF176B99,ORANGE=0xFFA15D23,
        PURPLE=0xFF6956A6,RED=0xFF9A3737,GREY=0xFF5B6872,GREEN=0xFF327340;
    private final Activity activity;
    private final MirrorPulsePilot fold;
    private final VehicleWindowGroup windows;
    private final PedestrianAlertControl avas;
    private TextView status,config;
    private Button mirrorAuto,mirrorManual,interiorMode,airAuto;
    private Button leftMinus,leftPlus,rightMinus,rightPlus;
    ReceiverQuickControls(Activity a){
        activity=a;fold=new MirrorPulsePilot(a);
        windows=new VehicleWindowGroup(a);
        avas=new PedestrianAlertControl(a);
    }
    void onResume(){
        fold.setForeground(true);windows.setForeground(true);
        avas.setForeground(true);
        ensureRecirculationGuard();
        refresh();
    }
    private void ensureRecirculationGuard(){
        android.content.SharedPreferences p=hvacPrefs();
        boolean wanted=RecirculationGuardService.MODE_INTERIOR.equals(
            p.getString(RecirculationGuardService.MODE,
                RecirculationGuardService.MODE_AUTO));
        if(!wanted||p.getBoolean(RecirculationGuardService.PAUSED,false))return;
        long age=System.currentTimeMillis()-
            p.getLong(RecirculationGuardService.LAST_POLL,0L);
        if(age>=0&&age<=8000&&
            p.getBoolean(RecirculationGuardService.MONITORING,false))return;
        try{
            activity.startForegroundService(new Intent(activity,
                RecirculationGuardService.class).setAction(
                    RecirculationGuardService.ACTION_RESTORE));
        }catch(RuntimeException e){
            p.edit().putString(RecirculationGuardService.STATUS,
                "Modo interior persistido pero Android impidió reanudar: "+
                    e.getClass().getSimpleName()).apply();
        }
    }
    void onPause(){
        fold.cancel();fold.setForeground(false);windows.setForeground(false);
        avas.setForeground(false);
    }
    private android.content.SharedPreferences mirrorPrefs(){
        return activity.getSharedPreferences(MirrorAutomationService.PREFS,
            Context.MODE_PRIVATE);
    }
    private android.content.SharedPreferences hvacPrefs(){
        return activity.getSharedPreferences(RecirculationGuardService.PREFS,
            Context.MODE_PRIVATE);
    }
    private void message(String value){
        if(status!=null)status.setText(value);
    }
    // Three visually distinct states: raised / selected (sunken) / touch-down.
    // All colors are calculated locally; no images, API or extra resources.
    private static int blend(int base,int other,float part){
        return Color.rgb(
            (int)(Color.red(base)*(1-part)+Color.red(other)*part),
            (int)(Color.green(base)*(1-part)+Color.green(other)*part),
            (int)(Color.blue(base)*(1-part)+Color.blue(other)*part));
    }
    private GradientDrawable bevel(int base,boolean selected,boolean pressed){
        int upper=pressed?blend(base,Color.BLACK,.32f):
            selected?blend(base,Color.BLACK,.14f):blend(base,Color.WHITE,.20f);
        int lower=pressed?blend(base,Color.BLACK,.12f):
            selected?blend(base,Color.BLACK,.30f):blend(base,Color.BLACK,.22f);
        GradientDrawable bg=new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,new int[]{upper,lower});
        bg.setCornerRadius(17);
        bg.setStroke(selected?5:2,selected?0xFFFFD86B:0xFFD1E3EC);
        return bg;
    }
    private void styleSurface(Button b,int base){
        StateListDrawable backgrounds=new StateListDrawable();
        backgrounds.addState(new int[]{android.R.attr.state_pressed},
            bevel(base,b.isSelected(),true));
        backgrounds.addState(new int[]{android.R.attr.state_selected},
            bevel(base,true,false));
        backgrounds.addState(new int[]{},
            bevel(base,false,false));
        b.setBackgroundTintList(null);
        b.setBackground(backgrounds);
        b.setElevation(b.isSelected()?2f:7f);
        b.setTranslationY(b.isSelected()?2f:0f);
        b.setTextColor(Color.WHITE);
        b.setTypeface(null,b.isSelected()?
            android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
    }
    private Button button(LinearLayout parent,String text,int color,Runnable cb){
        Button b=new Button(activity);b.setText(text);b.setAllCaps(false);
        b.setTextSize(14);b.setTextColor(Color.WHITE);
        styleSurface(b,color);
        b.setTag(Receiver3DButtons.QUICK_TAG);
        // Return false to preserve native clicks, long-press and accessibility.
        b.setOnTouchListener((view,event)->{
            int a=event.getActionMasked();
            if(a==MotionEvent.ACTION_DOWN){
                b.setElevation(1f);b.setTranslationY(3f);
                b.setScaleX(.985f);b.setScaleY(.96f);
            }else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
                b.setElevation(b.isSelected()?2f:7f);
                b.setTranslationY(b.isSelected()?2f:0f);
                b.setScaleX(1f);b.setScaleY(1f);
            }
            return false;
        });
        b.setOnClickListener(v->cb.run());
        parent.addView(b,new LinearLayout.LayoutParams(0,68,1));
        return b;
    }
    /** Persisted modes remain visibly depressed; transient actions only on touch. */
    private void styleSelected(Button button,boolean selected,int base){
        if(button==null)return;
        if(button.isSelected()!=selected){
            button.setSelected(selected);
            styleSurface(button,base);
        }
    }
    private LinearLayout row(LinearLayout root){
        LinearLayout r=new LinearLayout(activity);
        r.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(r,new LinearLayout.LayoutParams(-1,-2));
        return r;
    }
    void attach(LinearLayout root){
        TextView head=new TextView(activity);
        head.setText("CONTROLES RÁPIDOS · JETOUR T1");
        head.setTextSize(21);head.setTextColor(0xFF132A3D);
        head.setPadding(7,4,7,4);
        root.addView(head);

        LinearLayout a=row(root);
        button(a,"PLEGAR ESPEJOS",PURPLE,()->fold(false));
        button(a,"DESPLEGAR ESPEJOS",BLUE,()->fold(true));
        mirrorAuto=button(a,"ESPEJOS AUTO",GREEN,()->setMirrorMode(true));
        mirrorManual=button(a,"ESPEJOS MANUAL",ORANGE,()->setMirrorMode(false));

        // Put less frequent/manual OEM actions behind one expandable section.
        // They remain accessible; opening Maps does not stop the opt-in
        // recirculation guard, mirror-auto service or the Receiver server.
        LinearLayout extra=new LinearLayout(activity);
        extra.setOrientation(LinearLayout.VERTICAL);
        extra.setVisibility(android.view.View.GONE);
        Button extraToggle=new Button(activity);
        extraToggle.setAllCaps(false);
        extraToggle.setText("MOSTRAR CONTROLES AVANZADOS");
        extraToggle.setOnClickListener(v->{
            boolean reveal=extra.getVisibility()!=android.view.View.VISIBLE;
            extra.setVisibility(reveal?android.view.View.VISIBLE:android.view.View.GONE);
            extraToggle.setText(reveal?"OCULTAR CONTROLES AVANZADOS":
                "MOSTRAR CONTROLES AVANZADOS");
        });
        root.addView(extraToggle);
        root.addView(extra);
        LinearLayout b=row(extra);
        button(b,"BAJAR ESPEJOS",BLUE,()->move(false));
        button(b,"SUBIR ESPEJOS",BLUE,()->move(true));
        button(b,"VENTILACIÓN TOTAL",TEAL,()->windows(WindowGroupPolicy.VENT_QUARTER));
        button(b,"CIERRE TOTAL",ORANGE,()->windows(WindowGroupPolicy.CLOSE));

        LinearLayout c=row(root);
        button(c,"ALARMA PEATÓN ON/OFF",RED,()->avas());
        interiorMode=button(c,"AIRE INTERIOR · AUTO",GREEN,()->setAir(false));
        airAuto=button(c,"AIRE EXTERIOR · OEM",GREY,()->setAir(true));
        button(c,"TABLERO / TRIP",BLUE,()->activity.startActivity(
            new Intent(activity,T1TankDashboardActivity.class)));

        LinearLayout d=row(extra);
        leftMinus=button(d,"IZQ −",GREY,()->adjust(MirrorAutomationService.LEFT,-1));
        leftPlus=button(d,"IZQ +",TEAL,()->adjust(MirrorAutomationService.LEFT,1));
        rightMinus=button(d,"DER −",GREY,()->adjust(MirrorAutomationService.RIGHT,-1));
        rightPlus=button(d,"DER +",TEAL,()->adjust(MirrorAutomationService.RIGHT,1));

        LinearLayout e=row(extra);
        button(e,"RECUPERAR ESPEJOS · 10 PASOS ARRIBA",ORANGE,this::restoreMirrors);

        config=new TextView(activity);config.setTextSize(14);
        config.setPadding(8,8,8,4);root.addView(config);
        status=new TextView(activity);status.setTextSize(13);
        status.setPadding(8,6,8,12);root.addView(status);
        refresh();
    }
    void refresh(){
        if(config==null||status==null)return;
        android.content.SharedPreferences m=mirrorPrefs();
        android.content.SharedPreferences a=hvacPrefs();
        int l=Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,m.getInt(MirrorAutomationService.LEFT,2)));
        int r=Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,m.getInt(MirrorAutomationService.RIGHT,2)));
        boolean on=m.getBoolean(MirrorAutomationService.AUTO,false);
        boolean interior=RecirculationGuardService.MODE_INTERIOR.equals(
            a.getString(RecirculationGuardService.MODE,
                RecirculationGuardService.MODE_AUTO));
        styleSelected(mirrorAuto,on,GREEN);
        styleSelected(mirrorManual,!on,ORANGE);
        styleSelected(interiorMode,interior&&
            !a.getBoolean(RecirculationGuardService.PAUSED,false),GREEN);
        styleSelected(airAuto,!interior,GREY);
        if(leftMinus!=null)leftMinus.setText("IZQ − ("+l+")");
        if(leftPlus!=null)leftPlus.setText("IZQ + ("+l+")");
        if(rightMinus!=null)rightMinus.setText("DER − ("+r+")");
        if(rightPlus!=null)rightPlus.setText("DER + ("+r+")");
        boolean serviceActive=m.getBoolean(MirrorAutomationService.SERVICE_ACTIVE,false);
        config.setText("Espejos: "+(on?"AUTO":"MANUAL")+
            " · servicio de fondo: "+(serviceActive?"ACTIVO":
                (on?"SIN SERVICIO · revisar estado":"detenido"))+
            "  · pasos por lado: IZQ "+l+" / DER "+r+
            "  · aire: "+(interior?"INTERIOR VIGILADO 1004=1":"CLIMATIZADOR ORIGINAL")+
            (a.getBoolean(RecirculationGuardService.PAUSED,false)?
                " (HVAC PAUSADO POR OEM)":
                interior?(a.getBoolean(RecirculationGuardService.MONITORING,false)&&
                    System.currentTimeMillis()-a.getLong(
                        RecirculationGuardService.LAST_POLL,0L)<9000?
                    " (SERVICIO ACTIVO EN SEGUNDO PLANO)":
                    " (SIN MONITOREO RECIENTE; RECUPERANDO)"):""));
        // Display the most recent OEM action without altering scroll position.
        String current=m.getString(MirrorAutomationService.STATUS,"");
        String recirc=a.getString(RecirculationGuardService.STATUS,"");
        if(current.length()>130)current=current.substring(0,130);
        if(recirc.length()>130)recirc=recirc.substring(0,130);
        if(!current.isEmpty()||!recirc.isEmpty())
            status.setText(current+"\n"+recirc);
    }
    private boolean busy(){
        return fold.busy()||windows.busy()||avas.busy();
    }
    private void fold(boolean unfold){
        if(busy()){message("Otra orden de control sigue en curso");return;}
        // A fold/unfold tap is also an explicit request to stop automatic
        // mirror tilting: never ask the driver to change modes first.
        if(mirrorPrefs().getBoolean(MirrorAutomationService.AUTO,false)){
            try{
                mirrorPrefs().edit().putBoolean(MirrorAutomationService.AUTO,false)
                    .putString(MirrorAutomationService.STATUS,
                        "AUTO detenido por plegado/desplegado manual").commit();
                activity.startService(new Intent(activity,MirrorAutomationService.class)
                    .setAction(MirrorAutomationService.ACTION_MANUAL));
            }catch(Exception ex){
                message("No se pudo detener AUTO: "+ex.getMessage());return;
            }
        }
        refresh();
        if(!fold.testFold2003(unfold,this::message))
            message("No se inició plegado: comprobá marcha/velocidad y conexión OEM");
        else message("Verificando 2003 y enviando una orden si hace falta…");
    }
    private void windows(int operation){
        if(busy()){message("Otra orden está en curso");return;}
        if(!windows.start(operation,p->message(p.message)))
            message("No se inició la orden de ventanillas");
        else message("Verificando ventanas antes de enviar comandos…");
    }
    private void avas(){
        // AVAS has its own single-request guard. Window/mirror background
        // work must never block the driver's manual acoustic-alert button.
        if(!avas.toggle(r->message(r.summary())))
            message("ALARMA PEATÓN: control ocupado o sin pantalla activa");
        else message("Alarma 2074: cambio manual ON/OFF solicitado…");
    }
    private void setMirrorMode(boolean auto){
        try{
            Intent intent=new Intent(activity,MirrorAutomationService.class)
                .setAction(auto?MirrorAutomationService.ACTION_AUTO:
                    MirrorAutomationService.ACTION_MANUAL);
            if(auto)activity.startForegroundService(intent);
            else activity.startService(intent);
            message(auto?"AUTO armado: espejo normal ajustado por el conductor. "+
                "Al entrar en R bajará una sola vez.":
                "MANUAL seleccionado: sin movimientos automáticos");
            refresh();
        }catch(Exception ex){message("Modo espejo rechazado: "+ex.getMessage());}
    }
    private void restoreMirrors(){
        try{
            activity.startForegroundService(new Intent(activity,
                MirrorAutomationService.class)
                .setAction(MirrorAutomationService.ACTION_RECOVER));
            message("Recuperación manual solicitada: 10 pasos arriba por lado. "+
                "Solo detenido; posición final no medida. Verificar visualmente.");
            refresh();
        }catch(Exception ex){message("Recuperación: "+ex.getMessage());}
    }
    private void move(boolean up){
        try{
            activity.startForegroundService(new Intent(activity,
                MirrorAutomationService.class).setAction(up?
                MirrorAutomationService.ACTION_UP:MirrorAutomationService.ACTION_DOWN));
            message((up?"Subiendo":"Bajando")+
                " ambos espejos · requiere velocidad 0 · "+
                "desactiva AUTO y registra resultado");
            refresh();
        }catch(Exception ex){message("Espejos: "+ex.getMessage());}
    }
    private void adjust(String key,int delta){
        if(mirrorPrefs().getBoolean(MirrorAutomationService.AUTO,false)){
            message("Primero cambiá a MANUAL para modificar pasos");return;
        }
        int old=mirrorPrefs().getInt(key,2);
        int value=Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,old+delta));
        mirrorPrefs().edit().putInt(key,value).apply();
        refresh();message((MirrorAutomationService.LEFT.equals(key)?"Izquierdo":"Derecho")+
            " configurado a "+value+" movimientos por ciclo");
    }
    private void setAir(boolean auto){
        try{
            Intent intent=new Intent(activity,RecirculationGuardService.class)
                .setAction(auto?RecirculationGuardService.ACTION_AUTO:
                    RecirculationGuardService.ACTION_INTERIOR);
            if(auto)activity.startService(intent);
            else activity.startForegroundService(intent);
            message(auto?
                "AIRE OEM para esta sesión; al reabrir DriveLab se "+
                    "restablecerá la preferencia automática de aire interior":
                "AIRE INTERIOR: supervisor de fondo activado; "+
                    "1004=1 se verifica cada 3 segundos");
            refresh();
        }catch(Exception ex){message("Climatización: "+ex.getMessage());}
    }
}
