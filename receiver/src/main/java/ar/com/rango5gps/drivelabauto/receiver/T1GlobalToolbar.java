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
import android.widget.NumberPicker;
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
    private Button optimizer,mirrorButton,mirrorAutoButton,airButton,avsButton,roadModeButton;
    // Permanent first-row read-only OEM 1046 / 1040 / 1041 indicators.
    private Button regenIndicator,batteryIndicator,socTargetIndicator;
    private TextView info;
    private boolean foreground;
    private long lastPoll=0, lastDevicesPoll=0;
    private int mirrorState=-1; // OEM 2003: 0 folded, 1 unfolded
    private int pedestrianState=-1; // OEM 2074: 1 ON, 0 OFF, -1 unknown
    private final Runnable poll=new Runnable(){
        @Override public void run(){
            if(!foreground)return;
            refreshAutoAndAir();
            long now=android.os.SystemClock.elapsedRealtime();
            if(now-lastPoll>=15000){
                lastPoll=now;
                readDisplayMode();
            }
            // Re-read OEM fold/AVAS states in case the driver changed them
            // from the original Jetour screen, not just from DriveLab.
            if(now-lastDevicesPoll>=12000){
                lastDevicesPoll=now;
                if(!mirrors.busy())readMirrorState();
                if(!avas.busy())readAvasState();
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
    private Button readOnlyIndicator(LinearLayout row,String title,int width){
        Button b=button(row,title,T1JetourTheme.PANEL_HI,width,()->{});
        b.setOnClickListener(null);
        b.setClickable(false);
        b.setLongClickable(false);
        b.setFocusable(false);
        b.setContentDescription(title+" · lectura OEM pendiente");
        return b;
    }
    private void paintReadOnly(Button b,String title,boolean confirmed,String detail){
        if(b==null)return;
        b.setText(title);
        T1JetourTheme.button(b,confirmed?
            T1JetourTheme.GREEN_BUTTON:T1JetourTheme.PANEL_HI);
        b.setContentDescription(detail);
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
        // Same permanent top row as EV/HEV/EV MAX and the applied road profile.
        regenIndicator=readOnlyIndicator(header,"REGEN · SIN LECTURA",138);
        batteryIndicator=readOnlyIndicator(header,"BATERÍA · SIN LECTURA",158);
        socTargetIndicator=readOnlyIndicator(header,"SOC OBJ. · S/L",130);
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
        // Reverse parking automation is NOT the fold/unfold control.
        // Keep AUTO/MANUAL and configuration always visible on every Receiver screen.
        mirrorAutoButton=button(actions,"REVERSA · ESTADO",T1JetourTheme.PANEL_HI,166,
            this::toggleMirrorAuto);
        button(actions,"CONFIG. ESPEJOS R",T1JetourTheme.PANEL_HI,168,
            this::configureReverseMirrors);
        mirrorButton=button(actions,"ESPEJOS · LEER",T1JetourTheme.PANEL_HI,150,
            this::toggleMirror);
        avsButton=button(actions,"PEATÓN · LEYENDO",T1JetourTheme.PANEL_HI,135,
            this::toggleAvas);
        button(actions,"VENTILACIÓN TOTAL",T1JetourTheme.PANEL_HI,165,
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
        // Re-observe the hardware instead of carrying a stale button label.
        readMirrorState();
        readAvasState();
        lastPoll=0;
        lastDevicesPoll=android.os.SystemClock.elapsedRealtime();
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
        refreshMirrorAuto();
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
    /** The green chip is an APPLIED optimized profile, not the GPS
     * classification. At SOC 13% with a CITY minimum of 15% it must show
     * SOC NO CUMPLE (gray), even if the GPS reports a city street.
     */
    private void refreshRoadMode(boolean automatic){
        if(roadModeButton==null)return;
        T1OptimizedModePolicy.Road applied=automatic?
            T1OptimizedAutoService.verifiedAppliedRoad(owner):null;
        if(applied!=null){
            String road=T1OptimizedModePolicy.roadName(applied);
            String subprofile=T1OptimizedAutoService.verifiedAppliedBranchLabel(owner);
            roadModeButton.setText("MODO "+road+
                (subprofile.isEmpty()?"":" "+subprofile));
            T1JetourTheme.button(roadModeButton,T1JetourTheme.GREEN_BUTTON);
            roadModeButton.setContentDescription(
                "Perfil "+road+" "+subprofile+" aplicado y confirmado. "+
                "Tres lecturas de vía, condición SOC, OEM 1043 y SOC/regeneración verificados. Solo lectura.");
        }else{
            String reason=T1OptimizedAutoService.roadDisplayReason(owner);
            roadModeButton.setText(reason);
            T1JetourTheme.button(roadModeButton,T1JetourTheme.PANEL_HI);
            roadModeButton.setContentDescription(
                "Modo optimizado NO confirmado: "+reason+
                ". El GPS por sí solo no demuestra la activación del perfil.");
        }
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
                paintReadOnly(regenIndicator,
                    T1TopRowEnergyStatus.regeneration(snapshot.regen1046),
                    snapshot.regen1046>=1&&snapshot.regen1046<=3,
                    "Regeneración OEM 1046="+snapshot.regen1046+
                    " (1 baja, 2 media, 3 alta)");
                paintReadOnly(batteryIndicator,
                    T1TopRowEnergyStatus.battery(snapshot.socMode),
                    snapshot.socMode==T1TopRowEnergyStatus.SMART||
                        snapshot.socMode==T1TopRowEnergyStatus.FORCED,
                    "Gestión batería OEM 1040="+snapshot.socMode);
                paintReadOnly(socTargetIndicator,
                    T1TopRowEnergyStatus.targetSoc(
                        snapshot.socMode,snapshot.socTargetInt),
                    snapshot.socMode==T1TopRowEnergyStatus.SMART||
                        (snapshot.socMode==T1TopRowEnergyStatus.FORCED&&
                         T1SocTargetCodec.observedPercentForRaw(
                             snapshot.socTargetInt)>=0),
                    T1TopRowEnergyStatus.description(
                        snapshot.socMode,snapshot.socTargetInt));
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
    /** Auto tilt is a distinct feature from OEM folding. Preferences are
     * read every refresh; green is reserved for an actual live heartbeat. */
    private void refreshMirrorAuto(){
        if(mirrorAutoButton==null)return;
        SharedPreferences p=owner.getSharedPreferences(
            MirrorAutomationService.PREFS,Context.MODE_PRIVATE);
        boolean auto=p.getBoolean(MirrorAutomationService.AUTO,false);
        boolean service=p.getBoolean(MirrorAutomationService.SERVICE_ACTIVE,false);
        long age=System.currentTimeMillis()-p.getLong(
            MirrorAutomationService.LAST_POLL,0L);
        boolean healthy=auto&&service&&age>=0&&age<=8000;
        mirrorAutoButton.setText(healthy?"REVERSA AUTO ✓":
            auto?"REVERSA AUTO · REVISAR":"REVERSA MANUAL");
        T1JetourTheme.button(mirrorAutoButton,healthy?
            T1JetourTheme.GREEN_BUTTON:auto?
            T1JetourTheme.AMBER_BUTTON:T1JetourTheme.PANEL_HI);
        mirrorAutoButton.setContentDescription(auto?
            (healthy?"Espejos reversa AUTO activo. Tocar para desactivar":
                "Espejos reversa AUTO solicitado, servicio sin confirmar. Tocar para desactivar"):
            "Espejos reversa MANUAL. Tocar para activar AUTO R");
    }
    private void toggleMirrorAuto(){
        SharedPreferences p=owner.getSharedPreferences(
            MirrorAutomationService.PREFS,Context.MODE_PRIVATE);
        if(p.getBoolean(MirrorAutomationService.AUTO,false)){
            try{
                owner.startService(new Intent(owner,MirrorAutomationService.class)
                    .setAction(MirrorAutomationService.ACTION_MANUAL));
                note("ESPEJOS REVERSA · desactivación solicitada");
                main.postDelayed(this::refreshMirrorAuto,500);
            }catch(Exception ex){
                note("Espejos: no se pudo desactivar AUTO · "+
                    ex.getClass().getSimpleName());
            }
            return;
        }
        // The driver has explicitly requested AUTO R by touching the toggle.
        // No redundant confirmation dialog. Keep uncertainty guard: a prior
        // interrupted actuator motion still requires physical inspection.
        if(p.getString(MirrorAutomationService.POSITION,"")
                .startsWith("INCIERTA")){
            note("ESPEJOS · POSICIÓN INCIERTA. Revisá los espejos "+
                "antes de rearmar; no se moverán automáticamente.");
            refreshMirrorAuto();
            return;
        }
        try{
            owner.startForegroundService(new Intent(owner,
                MirrorAutomationService.class)
                .setAction(MirrorAutomationService.ACTION_AUTO));
            note("ESPEJOS REVERSA · AUTO solicitado; verificando servicio");
            main.postDelayed(this::refreshMirrorAuto,650);
        }catch(Exception ex){
            note("Espejos: AUTO no iniciado · "+
                ex.getClass().getSimpleName());
        }
    }
    private void configureReverseMirrors(){
        SharedPreferences p=owner.getSharedPreferences(
            MirrorAutomationService.PREFS,Context.MODE_PRIVATE);
        LinearLayout body=new LinearLayout(owner);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(9),dp(18),dp(9));
        TextView diagnostic=new TextView(owner);
        boolean on=p.getBoolean(MirrorAutomationService.AUTO,false);
        long age=System.currentTimeMillis()-p.getLong(
            MirrorAutomationService.LAST_POLL,0L);
        boolean live=p.getBoolean(MirrorAutomationService.SERVICE_ACTIVE,false)
            &&age>=0&&age<=8000;
        diagnostic.setText("MODO: "+(on?"AUTO":"MANUAL")+
            " · SERVICIO: "+(live?"ACTIVO":"SIN LATIDO RECIENTE")+
            "\nÚLTIMA MARCHA: "+p.getInt(MirrorAutomationService.LAST_GEAR,-1)+
            " · VELOCIDAD: "+p.getString(MirrorAutomationService.LAST_SPEED,"SIN DATO")+
            "\nESTADO: "+p.getString(MirrorAutomationService.STATUS,"SIN ESTADO")+
            "\nPOSICIÓN: "+p.getString(MirrorAutomationService.POSITION,"NO MEDIDA")+
            "\nÚLTIMO ERROR: "+p.getString(MirrorAutomationService.LAST_ERROR,"NINGUNO")+
            "\nÚLTIMA TRANSICIÓN: "+
                p.getString(MirrorAutomationService.LAST_TRANSITION,"NINGUNA")+
            "\n\nPasos relativos (1 a 20) por espejo, sin sensor de ángulo. "+
            "Para modificar, primero desactivá AUTO R desde la barra.");
        diagnostic.setTextSize(14);
        diagnostic.setTextColor(android.graphics.Color.DKGRAY);
        body.addView(diagnostic);
        LinearLayout pickers=new LinearLayout(owner);
        pickers.setGravity(Gravity.CENTER);
        body.addView(pickers);
        NumberPicker left=new NumberPicker(owner);
        left.setMinValue(1);left.setMaxValue(MirrorParkingPolicy.MAX_PULSES_PER_SIDE);
        left.setValue(Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,
            p.getInt(MirrorAutomationService.LEFT,2))));
        left.setContentDescription("Pasos espejo izquierdo");
        NumberPicker right=new NumberPicker(owner);
        right.setMinValue(1);right.setMaxValue(MirrorParkingPolicy.MAX_PULSES_PER_SIDE);
        right.setValue(Math.max(1,Math.min(MirrorParkingPolicy.MAX_PULSES_PER_SIDE,
            p.getInt(MirrorAutomationService.RIGHT,2))));
        right.setContentDescription("Pasos espejo derecho");
        TextView labelLeft=new TextView(owner);
        labelLeft.setText("IZQ");
        labelLeft.setTextColor(android.graphics.Color.DKGRAY);
        TextView labelRight=new TextView(owner);
        labelRight.setText("DER");
        labelRight.setTextColor(android.graphics.Color.DKGRAY);
        pickers.addView(labelLeft);
        pickers.addView(left,new LinearLayout.LayoutParams(dp(90),dp(115)));
        pickers.addView(labelRight);
        pickers.addView(right,new LinearLayout.LayoutParams(dp(90),dp(115)));
        android.widget.ScrollView scroll=new android.widget.ScrollView(owner);
        scroll.addView(body);
        new AlertDialog.Builder(owner).setTitle("CONFIGURAR ESPEJOS · REVERSA R")
            .setView(scroll).setNegativeButton("CERRAR",null)
            .setPositiveButton("GUARDAR PASOS",(d,w)->{
                if(p.getBoolean(MirrorAutomationService.AUTO,false)){
                    note("Primero desactivá REVERSA AUTO para cambiar los pasos");
                    return;
                }
                p.edit()
                    .putInt(MirrorAutomationService.LEFT,left.getValue())
                    .putInt(MirrorAutomationService.RIGHT,right.getValue())
                    .apply();
                note("ESPEJOS R · guardados IZQ "+left.getValue()+
                    " / DER "+right.getValue()+" pasos");
            }).show();
    }

    private void showMirrorState(){
        // The label states the NEXT action, never the current position.
        mirrorButton.setText(mirrorState==0?"DESPLEGAR ESPEJOS":
            mirrorState==1?"PLEGAR ESPEJOS":"ESPEJOS · SIN ESTADO");
        T1JetourTheme.button(mirrorButton,T1JetourTheme.PANEL_HI);
        mirrorButton.setContentDescription(mirrorState==0?
            "Espejos plegados según OEM 2003. Tocar para desplegar":
            mirrorState==1?
            "Espejos desplegados según OEM 2003. Tocar para plegar":
            "Estado de plegado sin lectura OEM; el botón no indica un estado");
    }
    private void readMirrorState(){
        if(!foreground||mirrors.busy())return;
        // Mark as pending; never display a stale cached action as confirmed.
        mirrorState=-1;
        mirrorButton.setText("ESPEJOS · LEYENDO");
        if(!mirrors.readFold2003(status->{
            if(!foreground)return;
            if(status.startsWith("LECTURA 2003=0 "))mirrorState=0;
            else if(status.startsWith("LECTURA 2003=1 "))mirrorState=1;
            else mirrorState=-1;
            showMirrorState();
        }))showMirrorState();
    }
    private void showAvasState(){
        avsButton.setText(pedestrianState==1?"PEATÓN ON":
            pedestrianState==0?"PEATÓN OFF":"PEATÓN · SIN LECTURA");
        T1JetourTheme.button(avsButton,pedestrianState==1?
            T1JetourTheme.GREEN_BUTTON:pedestrianState==0?
            T1JetourTheme.RED_BUTTON:T1JetourTheme.PANEL_HI);
        avsButton.setContentDescription("Alerta peatonal 2074: "+
            (pedestrianState==1?"activada":pedestrianState==0?
                "desactivada":"sin lectura OEM")+
            ". Tocar para alternar tras leer el estado del vehículo.");
    }
    private void readAvasState(){
        if(!foreground||avas.busy())return;
        pedestrianState=-1;
        avsButton.setText("PEATÓN · LEYENDO");
        if(!avas.readState(result->{
            if(!foreground)return;
            pedestrianState=result.after==null?-1:result.after;
            showAvasState();
        }))showAvasState();
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
        mirrorState=-1; // Do not pre-commit the inverted state.
        mirrorButton.setText("ESPEJOS · CONFIRMANDO");
        if(!mirrors.toggleFold2003(status->{
            if(!foreground)return;
            note(status);
            readMirrorState();
        })){
            note("Espejos: control ocupado o sin lectura");
            showMirrorState();
        }
    }
    private void toggleAvas(){
        if(!foreground)return;
        if(avas.busy()){
            note("Peatón: lectura u orden OEM en curso");return;
        }
        pedestrianState=-1;
        avsButton.setText("PEATÓN · CONFIRMANDO");
        if(!avas.toggle(result->{
            if(!foreground)return;
            // Use ONLY the final getter, not a guessed inverted UI state.
            pedestrianState=result.after==null?-1:result.after;
            showAvasState();
            note(result.summary());
            // Resync on the following poll if 2074 had delayed feedback.
            if(result.after==null)readAvasState();
        })){
            note("Peatón: control ocupado o sin lectura OEM");
            showAvasState();
        }
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