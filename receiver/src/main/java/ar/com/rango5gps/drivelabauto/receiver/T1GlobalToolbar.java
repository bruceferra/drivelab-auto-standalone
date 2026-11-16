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
import java.util.Date;

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
    private Button clusterButton,optimizer,mirrorButton,mirrorAutoButton,mirrorRecoverButton,airButton,avsButton,roadModeButton,ventilationButton;
    // Permanent first-row read-only OEM 1046 / 1040 / 1041 indicators.
    private Button regenIndicator,batteryIndicator,socTargetIndicator;
    private TextView info,clockDate,clockTime;
    private final Runnable clockTick=new Runnable(){
        @Override public void run(){
            if(!foreground)return;
            refreshClock();
            main.postDelayed(this,1000L);
        }
    };
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
                if(!windows.busy())readWindowVentilationState();
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
        // Read-only clock stays at upper right, outside scrolling controls.
        LinearLayout clockBox=new LinearLayout(owner);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        clockBox.setPadding(dp(4),0,dp(14),0);
        clockDate=new TextView(owner);
        clockDate.setGravity(Gravity.RIGHT);
        clockDate.setSingleLine(true);
        clockDate.setTextColor(T1JetourTheme.MUTED);
        clockDate.setTextSize(14f);
        clockTime=new TextView(owner);
        clockTime.setGravity(Gravity.RIGHT);
        clockTime.setSingleLine(true);
        clockTime.setTypeface(null,Typeface.BOLD);
        clockTime.setTextColor(T1JetourTheme.WHITE);
        clockTime.setTextSize(19f);
        clockBox.addView(clockDate);
        clockBox.addView(clockTime);
        header.addView(clockBox,new LinearLayout.LayoutParams(dp(255),-1));
        refreshClock();

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
        // First control on the SECOND row, before OPTIMIZADO ON/OFF.
        // ON routes Waze/selected app to the verified Display 1.
        // OFF disables future routing and closes only known Waze/Maps
        // tasks on that secondary display if local ADB is authorized.
        clusterButton=button(actions,"CLÚSTER OFF",
            T1JetourTheme.PANEL_HI,140,this::toggleCluster);
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
        mirrorRecoverButton=button(actions,"RECUPERAR ESPEJOS",
            T1JetourTheme.PANEL_HI,170,this::showMirrorRecovery);
        mirrorButton=button(actions,"ESPEJOS · LEER",T1JetourTheme.PANEL_HI,150,
            this::toggleMirror);
        avsButton=button(actions,"PEATÓN · LEYENDO",T1JetourTheme.PANEL_HI,135,
            this::toggleAvas);
        ventilationButton=button(actions,"MODO VENTILACIÓN · LEER",
            T1JetourTheme.PANEL_HI,180,this::smartVentilation);
        button(actions,"CIERRE TOTAL",T1JetourTheme.PANEL_HI,135,
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
        readWindowVentilationState();
        lastPoll=0;
        lastDevicesPoll=android.os.SystemClock.elapsedRealtime();
        main.removeCallbacks(poll);
        main.post(poll);
        main.removeCallbacks(clockTick);
        main.post(clockTick);
    }
    void onPause(){
        foreground=false;
        main.removeCallbacks(poll);
        main.removeCallbacks(clockTick);
        mirrors.cancel();
        mirrors.setForeground(false);
        windows.cancel();
        windows.setForeground(false);
        avas.setForeground(false);
    }
    private void note(String text){
        if(foreground&&info!=null)info.setText(text);
    }
    private void refreshClock(){
        if(clockDate==null||clockTime==null)return;
        Date now=new Date();
        clockDate.setText(T1HeaderClockFormat.date(now));
        clockTime.setText(T1HeaderClockFormat.time(now));
    }
    private void refreshAutoAndAir(){
        if(!foreground)return;
        boolean automatic=T1OptimizedAutoService.enabled(owner);
        optimizer.setText(automatic?"OPTIMIZADO ON":"OPTIMIZADO OFF");
        T1JetourTheme.button(optimizer,automatic?
            T1JetourTheme.GREEN_BUTTON:T1JetourTheme.RED_BUTTON);
        refreshRoadMode(automatic);
        refreshCluster();
        refreshMirrorAuto();
        SharedPreferences air=owner.getSharedPreferences(
            RecirculationGuardService.PREFS,Context.MODE_PRIVATE);
        boolean interior=RecirculationGuardService.MODE_INTERIOR.equals(
            air.getString(RecirculationGuardService.MODE,RecirculationGuardService.MODE_AUTO));
        boolean paused=air.getBoolean(RecirculationGuardService.PAUSED,false);
        boolean running=air.getBoolean(RecirculationGuardService.MONITORING,false);
        long age=System.currentTimeMillis()-air.getLong(RecirculationGuardService.LAST_POLL,0);
        long verifiedAge=System.currentTimeMillis()-
            air.getLong(RecirculationGuardService.LAST_CONFIRMED,0);
        boolean verified=interior&&!paused&&running&&age>=0&&age<12000&&
            verifiedAge>=0&&verifiedAge<12000&&
            air.getInt(RecirculationGuardService.LAST_VALUE,-1)==1;
        if(interior&&paused){
            airButton.setText("AIRE AUTO · PAUSA OEM");
            T1JetourTheme.button(airButton,T1JetourTheme.AMBER_BUTTON);
        }else if(verified){
            airButton.setText("AIRE INTERIOR AUTO ✓");
            T1JetourTheme.button(airButton,T1JetourTheme.GREEN_BUTTON);
        }else if(interior){
            // Green reflects selected preference; the word SOLICITADO
            // distinguishes it from OEM value 1004=1 verified success.
            // OEM PAUSE/defog status remains independently visible in amber.
            airButton.setText("AIRE INTERIOR · SOLICITADO");
            T1JetourTheme.button(airButton,T1JetourTheme.GREEN_BUTTON);
        }else{
            airButton.setText("AIRE INTERIOR AUTO");
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
            // No prompt: show actual OEM state or reconnect cause.
            note(state);
            return;
        }
        // Driver's deliberate tap is an explicit re-arm; avoid repeating the
        // confirmation modal on every ignition. OEM repeated-reversion
        // protection remains in RecirculationGuardService.
        try{
            owner.startForegroundService(new Intent(owner,
                RecirculationGuardService.class)
                .setAction(RecirculationGuardService.ACTION_INTERIOR));
            note("Reintento manual solicitado · "+state+
                " · Recirculación sin confirmar hasta leer 1004=1. "+
                "El desempañado OEM tiene prioridad.");
        }catch(Exception ex){note("Aire sin reactivar: "+
            ex.getClass().getSimpleName());}
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
    /** OFF is not the same as the OEM dashboard being visible:
     * Android cannot remove Waze's foreign task without authorized ADB.
     * Mark an unverified close in amber until task inspection succeeds.
     */
    private void refreshCluster(){
        if(clusterButton==null)return;
        SharedPreferences p=T1ClusterWazeRouter.prefs(owner);
        boolean active=T1ClusterWazeRouter.enabled(owner);
        boolean pending=p.getBoolean(T1ClusterWazeRouter.CLOSE_PENDING,false);
        clusterButton.setText(active?"CLÚSTER ON":
            pending?"CLÚSTER OFF · SIN CIERRE":"CLÚSTER OFF");
        T1JetourTheme.button(clusterButton,active?
            T1JetourTheme.GREEN_BUTTON:pending?
            T1JetourTheme.AMBER_BUTTON:T1JetourTheme.PANEL_HI);
        clusterButton.setContentDescription(active?
            "Clúster de navegación habilitado. Tocar para cerrar Waze "+
                "y volver al tablero OEM cuando Android permita.":
            pending?"Clúster desactivado pero cierre de apps pendiente; "+
                "tocá para volver a CLÚSTER ON; Waze puede seguir visible sin ADB":
            "Clúster desactivado. Tocar para enviar Waze al Display elegido");
    }
    private void toggleCluster(){
        SharedPreferences p=T1ClusterWazeRouter.prefs(owner);
        if(T1ClusterWazeRouter.enabled(owner)){
            // OFF stops routing but does not claim the Waze task disappears.
            // The OEM dashboard is not verified without a physical test.
            p.edit().putBoolean(T1ClusterWazeRouter.ENABLED,false)
                .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false)
                // Preserve the already-verified split layout preference
                // across OFF/ON. Tasks are closed; geometry will be
                // rechecked before ADB applies the saved bounds again.
                .putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,true)
                .putString(T1ClusterWazeRouter.STATUS,
                    T1ClusterAdbResize.enabled(owner)?
                    "CLÚSTER OFF: destinos detenidos. Intentando cerrar "+
                    "Waze/Maps con ADB autorizado.":
                    "CLÚSTER OFF: destinos detenidos. Waze puede seguir "+
                    "visible en el tablero; sin ADB no se cerró.")
                .apply();
            refreshCluster();
            if(!T1ClusterAdbResize.enabled(owner)){
                note("RUTAS OFF · Waze puede seguir visible sin ADB. "+
                    "Tocá nuevamente para CLÚSTER ON.");
                return;
            }
            T1ClusterAdbResize.closeClusterApps(owner,(message,confirmed)->{
                // Ignore stale OFF callbacks after the user returned to ON.
                if(T1ClusterWazeRouter.enabled(owner))return;
                // Persist the result even when DriveLab is temporarily
                // backgrounded during the task removal on Display 1.
                T1ClusterWazeRouter.prefs(owner).edit()
                    .putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,!confirmed)
                    .putString(T1ClusterWazeRouter.STATUS,message).apply();
                if(foreground){
                    refreshCluster();
                    note(message);
                }
            });
            return;
        }
        if(T1ClusterWazeRouter.selected(owner)==null){
            note("CLÚSTER: Display 1 no disponible o no elegido. "+
                "Elegilo en configuración.");
            navigate(T1ClusterWazeSettingsActivity.class);
            return;
        }
        // Pending ADB close does not block returning to CLÚSTER ON.
        p.edit().putBoolean(T1ClusterWazeRouter.ENABLED,true)
            .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,true)
            .putBoolean(T1ClusterWazeRouter.CLOSE_PENDING,false)
            .putString(T1ClusterWazeRouter.STATUS,
                "CLÚSTER ON: abriendo navegación en el display elegido")
            .apply();
        // Keep ON enabled for the parked layout confirmation, but never
        // launch full-screen Waze as a fallback over the chosen split.
        boolean pendingLayout=T1ClusterLayoutPolicy.pendingUnverified(owner);
        String result;
        if(pendingLayout)
            result=T1ClusterLayoutPolicy.pendingMessage(owner);
        else if(T1ClusterLayoutPolicy.active(owner))
            result=T1ClusterLayoutPolicy.boot(owner);
        else result=T1ClusterWazeRouter.launchSelectedApp(owner,true,false);
        boolean launched=result.startsWith("Waze SOLICITADO")||
            result.startsWith("APP SOLICITADA:")||
            result.startsWith("APP SOLICITADA EN VENTANA:");
        if(!launched&&!pendingLayout){
            // Never show green when Android refused the display.
            p.edit().putBoolean(T1ClusterWazeRouter.ENABLED,false)
                .putBoolean(T1ClusterWazeRouter.AUTO_BOOT,false).apply();
        }
        T1ClusterWazeRouter.status(owner,result);
        refreshCluster();
        note(result);
        // Boot launches both apps and resizes them asynchronously (1.8 s).
        // A recent explicit destination can then be reapplied in the
        // PREVIOUSLY confirmed 60/40 layout; never surprise the center display.
        if(launched&&!pendingLayout){
            final long onAt=android.os.SystemClock.elapsedRealtime();
            main.postDelayed(()->{
                if(!T1ClusterWazeRouter.enabled(owner)||
                   T1ClusterLayoutPolicy.pendingUnverified(owner))return;
                String restored=T1LastRoute.restore(owner);
                if(foreground)note(restored);
            },3000L);
        }
    }

    private void refreshMirrorAuto(){
        if(mirrorAutoButton==null)return;
        SharedPreferences p=owner.getSharedPreferences(
            MirrorAutomationService.PREFS,Context.MODE_PRIVATE);
        boolean auto=p.getBoolean(MirrorAutomationService.AUTO,false);
        boolean safetyHold=p.getBoolean(MirrorAutomationService.SAFETY_HOLD,true);
        int pendingLeft=Math.max(0,p.getInt(
            MirrorAutomationService.PENDING_LEFT,0));
        int pendingRight=Math.max(0,p.getInt(
            MirrorAutomationService.PENDING_RIGHT,0));
        boolean inFlight=p.getBoolean(
            MirrorAutomationService.PULSE_IN_FLIGHT,false);
        boolean service=p.getBoolean(MirrorAutomationService.SERVICE_ACTIVE,false);
        long age=System.currentTimeMillis()-p.getLong(
            MirrorAutomationService.LAST_POLL,0L);
        boolean healthy=auto&&!safetyHold&&service&&age>=0&&age<=8000;
        String pose=p.getString(MirrorAutomationService.POSITION,"");
        // Tilted during a healthy live AUTO run is EXPECTED; uncertainty
        // matters when the service is gone and physical return was not seen.
        boolean unknown=(pose.startsWith("INCIERTA")||
            pose.startsWith("R · inclinados"))&&!healthy;
        mirrorAutoButton.setText(unknown?"ESPEJOS · REVISAR POSICIÓN":
            healthy?"REVERSA AUTO ✓":
            auto?"REVERSA AUTO · REVISAR":"REVERSA MANUAL");
        T1JetourTheme.button(mirrorAutoButton,unknown?
            T1JetourTheme.AMBER_BUTTON:healthy?
            T1JetourTheme.GREEN_BUTTON:auto?
            T1JetourTheme.AMBER_BUTTON:T1JetourTheme.PANEL_HI);
        // This recovery shortcut used to be hard-coded AMBER forever.
        // Clear the warning once the owner acknowledged NORMAL in P/0
        // and the exact pending ledger is empty. The button remains
        // accessible in neutral gray for future manual recovery.
        if(mirrorRecoverButton!=null){
            boolean needsRecovery=safetyHold||unknown||inFlight||
                pendingLeft>0||pendingRight>0;
            T1JetourTheme.button(mirrorRecoverButton,needsRecovery?
                T1JetourTheme.AMBER_BUTTON:T1JetourTheme.PANEL_HI);
            mirrorRecoverButton.setText(needsRecovery?
                "RECUPERAR ESPEJOS":"ESPEJOS · SIN PENDIENTES");
            mirrorRecoverButton.setContentDescription(needsRecovery?
                "Recuperación de espejos disponible. Pendientes izquierda "+
                    pendingLeft+", derecha "+pendingRight:
                "Espejos sin pulsos pendientes. Posición "+
                    "normal confirmada o sin recuperación requerida.");
        }
        mirrorAutoButton.setContentDescription(unknown?
            "Posición física no comprobada; tocar para ver recuperación segura":
            auto?(healthy?"Espejos reversa AUTO activo. Tocar para desactivar":
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
        String pose=p.getString(MirrorAutomationService.POSITION,"");
        if(p.getBoolean(MirrorAutomationService.SAFETY_HOLD,true)||
                pose.startsWith("INCIERTA")||
                pose.startsWith("R · inclinados")){
            showMirrorRecovery();
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
    /** No angle sensor: the only safe way out of a lost relative-motion
     * ledger is for the driver to set the actual pose using OEM controls.
     * The app then verifies P + 0 km/h but never moves the mirrors here.
     */
    private void showMirrorRecovery(){
        SharedPreferences p=owner.getSharedPreferences(
            MirrorAutomationService.PREFS,Context.MODE_PRIVATE);
        String pose=p.getString(MirrorAutomationService.POSITION,"SIN MEDICIÓN");
        int left=Math.max(0,p.getInt(MirrorAutomationService.PENDING_LEFT,0));
        int right=Math.max(0,p.getInt(MirrorAutomationService.PENDING_RIGHT,0));
        boolean unknown=p.getBoolean(MirrorAutomationService.PULSE_IN_FLIGHT,false);
        boolean exact=(left+right)>0&&!unknown;
        AlertDialog.Builder dialog=new AlertDialog.Builder(owner)
            .setTitle("ESPEJOS · RECUPERAR POSICIÓN")
            .setMessage("Estado: "+pose+
                "\nPulsos pendientes: IZQ "+left+" · DER "+right+
                (unknown?"\nHay un pulso sin cierre comprobado. "+
                    "No se puede compensar automáticamente.":"")+
                "\n\nEn P, DriveLab puede subir solo los pulsos "+
                "que quedaron registrados. No repetirá una cantidad fija."+
                "\nSi esta cuenta se perdió en la versión anterior, "+
                "acomodá los espejos con los mandos ORIGINALES."+
                "\nCuando queden a tu gusto, confirmá la referencia. "+
                "El modo AUTO seguirá apagado hasta que lo actives.")
            .setNegativeButton("CERRAR",null)
            .setPositiveButton("YA ESTÁN NORMALES · CONFIRMAR",(d,w)->{
                try{
                    owner.startForegroundService(new Intent(owner,
                        MirrorAutomationService.class)
                        .setAction(MirrorAutomationService.ACTION_CONFIRM_NORMAL));
                    note("Confirmando referencia visual en P sin mover espejos");
                    main.postDelayed(this::refreshMirrorAuto,800L);
                }catch(Exception ex){
                    note("No se pudo confirmar referencia · "+
                        ex.getClass().getSimpleName());
                }
            });
        if(exact){
            dialog.setNeutralButton("SUBIR "+left+" IZQ / "+right+" DER",
                (d,w)->{
                    try{
                        owner.startForegroundService(new Intent(owner,
                            MirrorAutomationService.class)
                            .setAction(MirrorAutomationService.ACTION_RECOVER));
                        note("Recuperando solo pulsos pendientes en P");
                        main.postDelayed(this::refreshMirrorAuto,1000L);
                    }catch(Exception ex){
                        note("No se pudo recuperar espejos: "+
                            ex.getClass().getSimpleName());
                    }
                });
        }
        dialog.show();
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
            " · REARME AUTOMÁTICO: "+
                (p.getBoolean(MirrorAutomationService.SAFETY_HOLD,true)?
                    "BLOQUEADO HASTA CONFIRMACIÓN EN P":"PERMITIDO SOLO A PEDIDO")+
            " · SERVICIO: "+(live?"ACTIVO":"SIN LATIDO RECIENTE")+
            "\nÚLTIMA MARCHA: "+p.getInt(MirrorAutomationService.LAST_GEAR,-1)+
            " · VELOCIDAD: "+p.getString(MirrorAutomationService.LAST_SPEED,"SIN DATO")+
            "\nESTADO: "+p.getString(MirrorAutomationService.STATUS,"SIN ESTADO")+
            "\nPOSICIÓN: "+p.getString(MirrorAutomationService.POSITION,"NO MEDIDA")+
            "\nPULSOS SIN COMPENSAR: IZQ "+
                p.getInt(MirrorAutomationService.PENDING_LEFT,0)+
                " / DER "+p.getInt(MirrorAutomationService.PENDING_RIGHT,0)+
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
    private void showVentilation(int[] states,String error){
        if(!foreground||ventilationButton==null)return;
        if(states==null||error!=null){
            ventilationButton.setText("VENTILACIÓN · SIN LECTURA");
            T1JetourTheme.button(ventilationButton,T1JetourTheme.AMBER_BUTTON);
            return;
        }
        boolean quarter=WindowGroupPolicy.smartOperation(states)==WindowGroupPolicy.CLOSE;
        ventilationButton.setText(quarter?"MODO VENTILACIÓN ON":"MODO VENTILACIÓN OFF");
        T1JetourTheme.button(ventilationButton,quarter?
            T1JetourTheme.GREEN_BUTTON:T1JetourTheme.RED_BUTTON);
    }
    private void readWindowVentilationState(){
        if(!foreground||windows.busy())return;
        windows.inspectState(this::showVentilation);
    }
    private void smartVentilation(){
        if(!foreground)return;
        if(windows.busy()||mirrors.busy()){
            note("Espejos/ventanillas: orden en curso");return;
        }
        if(!windows.smartVentilation(progress->{
            if(!foreground)return;
            note(progress.message);
            if(!progress.running)readWindowVentilationState();
        }))note("Modo ventilación: no se inició la lectura OEM");
        else note("Leyendo las cuatro ventanillas: a 1/4 o cierre automático…");
    }
    private void windows(int command){
        if(!foreground)return;
        if(windows.busy()||mirrors.busy()){
            note("Espejos/ventanillas: orden todavía en curso");
            return;
        }
        if(!windows.start(command,p->{
            if(foreground)note(p.message);
            if(foreground&&!p.running)readWindowVentilationState();
        }))note("Ventanillas: no se inició (comprobar OEM)");
        else note("Ventanillas: verificando estado OEM…");
    }
}