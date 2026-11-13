package ar.com.rango5gps.drivelabauto.receiver;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One owner-requested fixed toolbar shared by ALL Receiver screens, outside
 * every screen's ScrollView. No overlays, Accessibility or system privileges.
 * The OEM still enforces every individual control's safety gates.
 */
final class T1GlobalToolbar {
    private final T1ImmersiveActivity owner;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final T1ManualThreeModePilot drive;
    private final MirrorPulsePilot mirrors;
    private final VehicleWindowGroup windows;
    private final PedestrianAlertControl avas;
    private final LinearLayout container;
    private final Button[] modes=new Button[3];
    private Button optimizer,mirrorButton,airButton,avsButton,roadModeButton;
    private TextView info;
    private boolean foreground;
    private long lastPoll=0;
    private int mirrorState=-1; // OEM 2003: 0 folded, 1 unfolded
    private final Runnable poll=new Runnable(){
        @Override public void run(){
            if(!foreground)return;
            refreshAutoAndAir();
            long now=android.os.SystemClock.elapsedRealtime();
            if(now-lastPoll>=15000){
                lastPoll=now;
                readDisplayMode();
            }
            main.postDelayed(this,3000L);
        }
    };
    T1GlobalToolbar(T1ImmersiveActivity owner){
        this.owner=owner;
        drive=new T1ManualThreeModePilot(owner);
        mirrors=new MirrorPulsePilot(owner);
        windows=new VehicleWindowGroup(owner);
        avas=new PedestrianAlertControl(owner);
        container=new LinearLayout(owner);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(T1JetourTheme.NIGHT);
        build();
    }
    View view(){return container;}
    private int dp(int n){return T1JetourTheme.dp(owner,n);}
    private Button button(LinearLayout r,String title,int tint,int width,Runnable task){
        Button b=new Button(owner);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        T1JetourTheme.button(b,tint);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(width),dp(49));
        p.setMargins(dp(2),dp(2),dp(2),dp(2));
        r.addView(b,p);
        b.setOnClickListener(v->task.run());
        return b;
    }
    private void build(){
        LinearLayout header=new LinearLayout(owner);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView title=new TextView(owner);
        title.setText("DRIVELAB / T1");
        title.setTextSize(15);
        title.setTypeface(null,Typeface.BOLD);
        title.setTextColor(T1JetourTheme.WHITE);
        title.setPadding(dp(9),0,dp(12),0);
        header.addView(title);
        modes[0]=button(header,"EV",T1JetourTheme.PANEL_HI,85,()->changeMode(1));
        modes[1]=button(header,"HEV",T1JetourTheme.PANEL_HI,85,()->changeMode(2));
        modes[2]=button(header,"EV MAX",T1JetourTheme.PANEL_HI,100,()->changeMode(3));
        // Read-only dynamic road-mode chip: CITY/ROAD/MOTORWAY/UNKNOWN.
        // The OEM powertrain buttons above remain active manual controls;
        // this one never sends vehicle commands and cannot be tapped.
        roadModeButton=button(header,"MODO DESCONOCIDO",
            T1JetourTheme.PANEL_HI,183,()->{});
        roadModeButton.setOnClickListener(null);
        roadModeButton.setClickable(false);
        roadModeButton.setLongClickable(false);
        roadModeButton.setFocusable(false);
        info=new TextView(owner);
        info.setText("Modo OEM: verificando…");
        info.setTextColor(T1JetourTheme.CYAN);
        info.setTextSize(11);
        info.setSingleLine(true);
        info.setEllipsize(TextUtils.TruncateAt.END);
        info.setPadding(dp(12),0,dp(5),0);
        header.addView(info,new LinearLayout.LayoutParams(0,-2,1));

        HorizontalScrollView strip=new HorizontalScrollView(owner);
        strip.setFillViewport(false);
        strip.setHorizontalScrollBarEnabled(true);
        strip.setHorizontalFadingEdgeEnabled(true);
        strip.setFadingEdgeLength(dp(22));
        container.addView(strip,new LinearLayout.LayoutParams(-1,dp(57)));
        LinearLayout actions=new LinearLayout(owner);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(4),0,dp(7),0);
        strip.addView(actions);
        optimizer=button(actions,"OPTIMIZADO OFF",T1JetourTheme.RED_BUTTON,144,
            this::toggleAuto);
        button(actions,"CONFIGURAR OPT.",T1JetourTheme.PANEL_HI,140,
            ()->navigate(T1OptimizedModeActivity.class));
        mirrorButton=button(actions,"ESPEJOS · LEER",T1JetourTheme.PANEL_HI,150,
            this::toggleMirror);
        avsButton=button(actions,"PEATÓN ON/OFF",T1JetourTheme.PANEL_HI,130,
            this::toggleAvas);
        button(actions,"VENTILACIÓN TOTAL",T1JetourTheme.GREEN_BUTTON,165,
            ()->windows(WindowGroupPolicy.VENT_QUARTER));
        button(actions,"CIERRE TOTAL",T1JetourTheme.PANEL_HI,133,
            ()->windows(WindowGroupPolicy.CLOSE));
        airButton=button(actions,"AIRE INTERIOR AUTO",T1JetourTheme.GREEN_BUTTON,170,
            this::airStatus);
        button(actions,"COCKPIT",T1JetourTheme.PANEL_HI,110,
            ()->navigate(T1PremiumCockpitActivity.class));
        button(actions,"VEHÍCULO",T1JetourTheme.PANEL_HI,115,
            ()->navigate(ReceiverVehicleActivity.class));
        button(actions,"PENDIENTES",T1JetourTheme.PANEL_HI,115,
            ()->navigate(T1ValidationWizardActivity.class));
        button(actions,"IA / WAZE",T1JetourTheme.PANEL_HI,115,
            ()->navigate(T1VoiceRouteActivity.class));
        button(actions,"RUTAS · VOLANTE",T1JetourTheme.PANEL_HI,150,
            ()->navigate(T1WheelSettingsActivity.class));
        button(actions,"INICIO",T1JetourTheme.PANEL_HI,95,
            ()->navigate(ReceiverActivity.class));
    }
    private void navigate(Class<?> target){
        if(!foreground)return;
        if(owner.getClass()==target)return;
        Intent i=new Intent(owner,target);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        owner.startActivity(i);
    }
    void onResume(){
        foreground=true;
        mirrors.setForeground(true);
        windows.setForeground(true);
        avas.setForeground(true);
        refreshAutoAndAir();
        readMirrorState();
        lastPoll=0;
        main.removeCallbacks(poll);
        main.post(poll);
    }
    void onPause(){
        foreground=false;
        main.removeCallbacks(poll);
        mirrors.cancel();
        mirrors.setForeground(false);
        windows.cancel();
        windows.setForeground(false);
        avas.setForeground(false);
    }
    private void note(String text){
        if(foreground&&info!=null)info.setText(text);
    }
    private void refreshAutoAndAir(){
        if(!foreground)return;
        boolean automatic=T1OptimizedAutoService.enabled(owner);
        optimizer.setText(automatic?"OPTIMIZADO ON":"OPTIMIZADO OFF");
        T1JetourTheme.button(optimizer,automatic?
            T1JetourTheme.GREEN_BUTTON:T1JetourTheme.RED_BUTTON);
        refreshRoadMode(automatic);
        SharedPreferences air=owner.getSharedPreferences(
            RecirculationGuardService.PREFS,Context.MODE_PRIVATE);
        boolean interior=RecirculationGuardService.MODE_INTERIOR.equals(
            air.getString(RecirculationGuardService.MODE,RecirculationGuardService.MODE_AUTO));
        boolean paused=air.getBoolean(RecirculationGuardService.PAUSED,false);
        boolean running=air.getBoolean(RecirculationGuardService.MONITORING,false);
        long age=System.currentTimeMillis()-air.getLong(RecirculationGuardService.LAST_POLL,0);
        if(interior&&paused){
            airButton.setText("AIRE AUTO · PAUSADO");
            T1JetourTheme.button(airButton,T1JetourTheme.AMBER_BUTTON);
        }else if(interior&&running&&age>=0&&age<12000){
            airButton.setText("AIRE INTERIOR AUTO ✓");
            T1JetourTheme.button(airButton,T1JetourTheme.GREEN_BUTTON);
        }else{
            airButton.setText(interior?"AIRE AUTO · REVISAR":"AIRE INTERIOR AUTO");
            T1JetourTheme.button(airButton,T1JetourTheme.PANEL_HI);
        }
    }
    /** The road state is sourced from the same fresh offline OSM cache as
     * the optimizer, never from a saved preference for a selected profile.
     * With AUTO OFF the chip is neutral: green means the optimizer is ON,
     * not that a mode command has necessarily been applied by the OEM.
     */
    private void refreshRoadMode(boolean automatic){
        if(roadModeButton==null)return;
        T1OptimizedModePolicy.Road active=
            T1OptimizedModeStore.lastRoad(owner);
        String road=T1OptimizedModePolicy.roadName(active);
        roadModeButton.setText("MODO "+road);
        T1JetourTheme.button(roadModeButton,automatic?
            T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL_HI);
        roadModeButton.setContentDescription(
            "Clasificación de vía "+road+", solo lectura. "+
            "Modo optimizado "+(automatic?"activado":"desactivado")+
            ". No confirma un cambio OEM.");
    }
    private void airStatus(){
        SharedPreferences air=owner.getSharedPreferences(
            RecirculationGuardService.PREFS,Context.MODE_PRIVATE);
        boolean paused=air.getBoolean(RecirculationGuardService.PAUSED,false);
        String state=air.getString(RecirculationGuardService.STATUS,"Sin lectura OEM");
        if(!paused){
            new AlertDialog.Builder(owner).setTitle("RECIRCULACIÓN AUTOMÁTICA")
                .setMessage("DriveLab supervisa 1004 cada 3 segundos en segundo "+
                    "plano e intenta restaurar aire interior si la ECU habilita "+
                    "aire exterior. Estado: "+state+
                    "\n\nNo sustituye los controles de desempañado OEM.")
                .setPositiveButton("CERRAR",null).show();
            return;
        }
        new AlertDialog.Builder(owner).setTitle("AIRE INTERIOR PAUSADO POR SEGURIDAD")
            .setMessage(state+"\n\nRevisá primero si el desempañado necesita "+
                "aire exterior. Solo reactivá manualmente cuando sea seguro.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("REACTIVAR AIRE INTERIOR",(d,w)->{
                try{
                    owner.startForegroundService(new Intent(owner,
                        RecirculationGuardService.class)
                        .setAction(RecirculationGuardService.ACTION_INTERIOR));
                    note("Reactivación de aire solicitada; confirmando OEM…");
                }catch(Exception ex){note("Aire: "+ex.getClass().getSimpleName());}
            }).show();
    }
    private void readDisplayMode(){
        new Thread(()->{
            T1TripTelemetry.Sample snapshot=new T1TripTelemetry(owner).snapshot();
            main.post(()->{
                if(!foreground)return;
                int mode=snapshot.powerMode;
                for(int i=0;i<3;i++)
                    T1JetourTheme.button(modes[i],mode==i+1?
                        T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL_HI);
                if(mode>=1&&mode<=3)
                    note("MODO OEM: "+T1ManualHevPolicy.currentMode(mode));
                else note("MODO OEM: SIN LECTURA");
            });
        },"T1-Global-Mode-ReadOnly").start();
    }
    private void changeMode(int target){
        if(!foreground)return;
        if(drive.busy()){note("Modo OEM: otra orden está en curso");return;}
        note("Modo "+T1ManualHevPolicy.currentMode(target)+" · preflight OEM…");
        if(!drive.request(target,()->foreground&&owner.hasWindowFocus(),result->{
            main.post(()->{
                if(!foreground)return;
                note(result);
                readDisplayMode();
            });
        }))note("Modo OEM: no se pudo iniciar");
    }
    private void toggleAuto(){
        if(T1OptimizedAutoService.enabled(owner)){
            T1OptimizedAutoService.disable(owner);
            refreshAutoAndAir();
            note("MODO OPTIMIZADO OFF");
            return;
        }
        new AlertDialog.Builder(owner)
            .setTitle("MODO OPTIMIZADO · ACTIVAR")
            .setMessage("Aplica automáticamente EV/HEV/EV MAX según vías offline "+
                "y perfiles configurados. Los cambios de tren motriz dependen "+
                "de comprobación OEM. Usalo solo después de validar la unidad; "+
                "podés apagarlo desde esta barra.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR",(d,w)->{
                String result=T1OptimizedAutoService.activate(owner);
                refreshAutoAndAir();
                note(result);
            }).show();
    }
    private void readMirrorState(){
        mirrorState=-1;
        mirrorButton.setText("ESPEJOS · LEYENDO");
        if(!mirrors.readFold2003(status->{
            if(!foreground)return;
            // A 2003 state is only accepted after the OEM foreground read.
            if(status.startsWith("LECTURA 2003=0"))mirrorState=0;
            else if(status.startsWith("LECTURA 2003=1"))mirrorState=1;
            else mirrorState=-1;
            mirrorButton.setText(mirrorState==0?"DESPLEGAR ESPEJOS":
                mirrorState==1?"PLEGAR ESPEJOS":"ESPEJOS · SIN ESTADO");
        }))mirrorButton.setText("ESPEJOS · SIN ESTADO");
    }
    private void toggleMirror(){
        if(!foreground)return;
        if(mirrors.busy()){note("Espejos: lectura u orden en curso");return;}
        // Manual fold/unfold must not compete with the reverse-tilt service.
        if(owner.getSharedPreferences(MirrorAutomationService.PREFS,Context.MODE_PRIVATE)
            .getBoolean(MirrorAutomationService.AUTO,false)){
            try{
                owner.startService(new Intent(owner,MirrorAutomationService.class)
                    .setAction(MirrorAutomationService.ACTION_MANUAL));
            }catch(Exception ex){
                note("Espejos: no se detuvo AUTO; sin orden");return;
            }
        }
        mirrorButton.setText("ESPEJOS · CONFIRMANDO");
        if(!mirrors.toggleFold2003(status->{
            if(!foreground)return;
            note(status);
            readMirrorState();
        }))note("Espejos: control ocupado o sin lectura");
    }
    private void toggleAvas(){
        if(!foreground)return;
        if(!avas.toggle(result->{
            if(!foreground)return;
            note(result.summary());
        }))note("Peatón: control ocupado o sin lectura OEM");
    }
    private void windows(int command){
        if(!foreground)return;
        if(windows.busy()||mirrors.busy()){
            note("Espejos/ventanillas: orden todavía en curso");
            return;
        }
        if(!windows.start(command,p->{
            if(foreground)note(p.message);
        }))note("Ventanillas: no se inició (comprobar OEM)");
        else note("Ventanillas: verificando estado OEM…");
    }
}