package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
        refresh();
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
    private Button button(LinearLayout parent,String text,int color,Runnable cb){
        Button b=new Button(activity);b.setText(text);b.setAllCaps(false);
        b.setTextSize(14);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setOnClickListener(v->cb.run());
        parent.addView(b,new LinearLayout.LayoutParams(0,68,1));
        return b;
    }
    /** Distinct pressed/unpressed styling on actual persisted selected state. */
    private void styleSelected(Button button,boolean selected,int base){
        if(button==null)return;
        GradientDrawable bg=new GradientDrawable();
        bg.setCornerRadius(17);
        bg.setColor(selected?base:0xFF7A8690);
        bg.setStroke(selected?6:2,selected?0xFFFFE08A:0xFFC4CED6);
        button.setBackgroundTintList(null);
        button.setBackground(bg);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null,selected?
            android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
        button.setSelected(selected);
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

        LinearLayout b=row(root);
        button(b,"BAJAR ESPEJOS",BLUE,()->move(false));
        button(b,"SUBIR ESPEJOS",BLUE,()->move(true));
        button(b,"VENTILACIÓN TOTAL",TEAL,()->windows(WindowGroupPolicy.VENT_QUARTER));
        button(b,"CIERRE TOTAL",ORANGE,()->windows(WindowGroupPolicy.CLOSE));

        LinearLayout c=row(root);
        button(c,"ALARMA PEATÓN ON/OFF",RED,()->avas());
        interiorMode=button(c,"SIEMPRE AIRE INTERIOR",GREEN,()->setAir(false));
        airAuto=button(c,"AIRE AUTO",GREY,()->setAir(true));
        button(c,"TABLERO / TRIP",BLUE,()->activity.startActivity(
            new Intent(activity,T1TankDashboardActivity.class)));

        LinearLayout d=row(root);
        leftMinus=button(d,"IZQ −",GREY,()->adjust(MirrorAutomationService.LEFT,-1));
        leftPlus=button(d,"IZQ +",TEAL,()->adjust(MirrorAutomationService.LEFT,1));
        rightMinus=button(d,"DER −",GREY,()->adjust(MirrorAutomationService.RIGHT,-1));
        rightPlus=button(d,"DER +",TEAL,()->adjust(MirrorAutomationService.RIGHT,1));

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
        styleSelected(interiorMode,interior,GREEN);
        styleSelected(airAuto,!interior,GREY);
        if(leftMinus!=null)leftMinus.setText("IZQ − ("+l+")");
        if(leftPlus!=null)leftPlus.setText("IZQ + ("+l+")");
        if(rightMinus!=null)rightMinus.setText("DER − ("+r+")");
        if(rightPlus!=null)rightPlus.setText("DER + ("+r+")");
        config.setText("Espejos: "+(on?"AUTO":"MANUAL")+
            "  · pasos por lado: IZQ "+l+" / DER "+r+
            "  · aire: "+(interior?"SIEMPRE INTERIOR":"AUTO")+
            (a.getBoolean(RecirculationGuardService.PAUSED,false)?
                " (HVAC PAUSADO)":""));
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
        if(mirrorPrefs().getBoolean(MirrorAutomationService.AUTO,false)){
            message("Elegí ESPEJOS MANUAL antes de plegar: evita que AUTO "+
                "mueva los cristales mientras están plegados");return;
        }
        if(busy()){message("Otra orden de control sigue en curso");return;}
        if(!fold.testFold2003(unfold,this::message))
            message("No se inició plegado: comprobá la marcha y la velocidad baja");
        else message("Verificando 2003 y enviando una orden si hace falta…");
    }
    private void windows(int operation){
        if(busy()){message("Otra orden está en curso");return;}
        if(!windows.start(operation,p->message(p.message)))
            message("No se inició la orden de ventanillas");
        else message("Verificando ventanas antes de enviar comandos…");
    }
    private void avas(){
        if(busy()){message("Otra operación está en curso");return;}
        if(!avas.toggle(r->message(r.summary())))
            message("Alarma peatón: espera OEM/no se inició");
        else message("Verificando si se puede cambiar alarma peatonal…");
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
            message(auto?"Aire AUTO: el climatizador decide":"Modo interior: "+
                "Receiver mantendrá 1004=1 y vigilará cambios OEM");
            refresh();
        }catch(Exception ex){message("Climatización: "+ex.getMessage());}
    }
}
