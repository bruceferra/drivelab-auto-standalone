package ar.com.rango5gps.drivelabauto.boot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Exclusive wheel owner for Bridge. This class is independent from
 * all legacy Receiver controls, T1Launcher and the map window launcher.
 *
 * STRICT SAFETY CONTRACT:
 * - Manual Android Accessibility authorization and Bridge opt-in BOTH needed.
 * - OEM Binder may register after manual owner opt-in even when Jetour
 *   hides the map's accessibility root; controls remain fail-closed unless
 *   an exact Waze/Maps APPLICATION window is verified on chosen Display.
 * - Separate manual 20s OEM key recapture logs raw wheel codes only.
 * - Never captures MODE, voice, hazard, ADAS or other OEM buttons.
 * - Never sends global BACK, ADB commands or vehicle property writes.
 * - OK/CANCEL acts only on own accessible Waze/Maps node, never OEM dialogs.
 * - Scroll moves a visible accessibility selection; never zooms or injects gestures.
 * - If the map disappears, never send actions; an opted-in listener may
 *   remain registered read-only as Receiver does, until manually disabled.
 * - Can coexist installed with T1Launcher, but owner must NOT enable
 *   two hard-key callbacks simultaneously; UI makes that explicit.
 */
public final class BridgeWheelAccessibilityService extends AccessibilityService
    implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String PREFS="drivelab_bridge_wheel_v1";
    static final String ENABLED="enabled",TARGET="target",
        REVERSE="reverse",STATUS="status",LAST_KEY="last_key",
        WINDOW_DISPLAY="window_display",
        MIC_ENABLED="mic_enabled",
        CAPTURE_STATUS="capture_status",CAPTURE_COUNT="capture_count",
        CAPTURE_LAST="capture_last",
        LAST_TIME="last_event",SERVICE_CONNECTED="connected";
    static final String MAPS="com.google.android.apps.maps";
    static final String WAZE="com.waze";
    private static final long COOLDOWN_MS=180;
    private static final long CAPTURE_DURATION_MS=20000L;
    private static volatile long captureEndElapsed;
    private static volatile BridgeWheelAccessibilityService connectedInstance;
    static boolean isCaptureActive(){
        long until=captureEndElapsed;
        return until>0&&SystemClock.elapsedRealtime()<until;
    }
    static boolean beginKeyCapture(Context c){
        BridgeWheelAccessibilityService live=connectedInstance;
        if(live==null||!live.serviceReady){
            prefs(c).edit().putString(CAPTURE_STATUS,
                "NO SE PUDO CAPTURAR: accesibilidad Bridge no conectada. "+
                "Usá RECONECTAR ACCESIBILIDAD · ADB LOCAL 5556/5555.")
                .apply();
            return false;
        }
        captureEndElapsed=SystemClock.elapsedRealtime()+CAPTURE_DURATION_MS;
        prefs(c).edit().putInt(CAPTURE_COUNT,0)
            .putString(CAPTURE_LAST,"Sin tecla todavía")
            .putString(CAPTURE_STATUS,
                "REINSCRIBIENDO BINDER OEM · CAPTURA 20 S · sólo lectura; "+
                "sin zoom/OK/atrás ni acciones en Maps")
            .apply();
        live.main.post(()->{
            // Receiver parity: a stale but reportedly registered OEM
            // callback can be silent after Jetour changes ownership.
            // This explicit parked-only test does ONE local unregister/
            // re-register, not ADB, force-stop or OEM property writes.
            if(live.oem!=null){
                live.oem.stop();
                live.oem.start();
            }
            live.reconcileWheel();
        });
        // No map actions during capture. The Binder may be held by
        // another installed service, which is reported, not bypassed.
        return true;
    }
    static void navigationIntentArrived(Context c){
        BridgeWheelAccessibilityService live=connectedInstance;
        if(live==null||!live.serviceReady)return;
        live.requestRefresh();
    }
    static String captureState(Context c){
        SharedPreferences p=prefs(c);
        String value=p.getString(CAPTURE_STATUS,"Aún no se ejecutó recaptura");
        return value+" · PULSACIONES "+p.getInt(CAPTURE_COUNT,0)+
            " · "+p.getString(CAPTURE_LAST,"Sin tecla");
    }
    private static final int MAX_NODES=550;
    // Only labels from a VISIBLE actionable node owned by the chosen map.
    // Exclude the map's own +/- zoom buttons, which the driver does not
    // want the steering wheel to operate.
    private static final String[] EXCLUDED_LABELS={
        "acercar","alejar","zoom","zoom in","zoom out",
        "mas zoom","menos zoom","ampliar mapa","reducir mapa"
    };
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Choice> choices=new ArrayList<>();
    private BridgeWheelOemBinder oem;
    private volatile boolean serviceReady=false;
    private static volatile boolean connectedNow=false;
    // Process-local manual opt-in; never re-register OEM callback at ignition.
    private static volatile boolean manuallyArmed=false;
    private static volatile boolean microphoneArmed=false;
    static void setManuallyArmed(boolean value){manuallyArmed=value;}
    static void setMicrophoneArmed(boolean value){microphoneArmed=value;}
    static boolean isMicrophoneArmed(){return microphoneArmed;}
    static boolean isManuallyArmed(){return manuallyArmed;}
    static boolean isConnected(){return connectedNow;}
    static String oemState(Context c){
        return BridgeWheelOemBinder.diagnostic(c);
    }
    private long lastKeyAt=0;
    private int lastKey=-1,selected=-1;
    private boolean lastCaptureRunning;
    private String selectedName="";
    private final Runnable refresh=new Runnable(){
        @Override public void run(){reconcileWheel();}
    };
    private static final class Choice{
        final AccessibilityNodeInfo node;
        final String text;
        final Rect bounds;
        Choice(AccessibilityNodeInfo n,String name,Rect b){
            node=n;text=name;bounds=new Rect(b);
        }
    }
    static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private void status(String s){
        prefs(this).edit().putString(STATUS,s)
            .putLong(LAST_TIME,System.currentTimeMillis()).apply();
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        serviceReady=true;
        connectedNow=true;
        connectedInstance=this;
        AccessibilityServiceInfo info=getServiceInfo();
        if(info!=null){
            info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|
                AccessibilityEvent.TYPE_WINDOWS_CHANGED|
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            setServiceInfo(info);
        }
        prefs(this).registerOnSharedPreferenceChangeListener(this);
        prefs(this).edit().putBoolean(SERVICE_CONNECTED,true).apply();
        oem=new BridgeWheelOemBinder(this,new BridgeWheelOemBinder.Listener(){
            @Override public void event(int code,int action,int extra){
                onOemEvent(code,action,extra);
            }
            @Override public void status(String message){
                BridgeWheelAccessibilityService.this.status(message);
            }
        });
        status("Accesibilidad Bridge conectada · volante "+
            (prefs(this).getBoolean(ENABLED,false)?
                "habilitado por usuario":"sin habilitar"));
        requestRefresh();
    }
    private void requestRefresh(){
        main.removeCallbacks(refresh);
        main.postDelayed(refresh,180);
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){
        if(ENABLED.equals(key)||MIC_ENABLED.equals(key)||
            TARGET.equals(key)||REVERSE.equals(key)||
            WINDOW_DISPLAY.equals(key))
            requestRefresh();
    }
    private String target(){
        String result=prefs(this).getString(TARGET,WAZE);
        return MAPS.equals(result)?MAPS:WAZE;
    }
    private static String normalized(String input){
        String s=Normalizer.normalize(input,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        return s.replaceAll("\\s+"," ").trim();
    }
    private static String label(AccessibilityNodeInfo n){
        CharSequence desc=n.getContentDescription(),text=n.getText();
        if(desc!=null&&desc.length()>0)return desc.toString();
        return text==null?"":text.toString();
    }
    private static boolean permitted(String input){
        String clean=normalized(input);
        if(clean.isEmpty()||clean.length()>110||
           clean.equals("+")||clean.equals("-")||
           clean.equals("−")||clean.equals("＋")||
           clean.equals("–"))return false;
        for(String item:EXCLUDED_LABELS)
            if(clean.equals(item))return false;
        if(clean.contains("zoom")||clean.contains("acercar")||
           clean.contains("alejar")||clean.contains("ampliar mapa")||
           clean.contains("reducir mapa"))return false;
        return true;
    }
    private AccessibilityNodeInfo findOwnNode(
            AccessibilityNodeInfo root,String packageName,int depth){
        if(root==null||depth>15)return null;
        CharSequence pkg=root.getPackageName();
        if(pkg!=null&&packageName.equals(pkg.toString())&&
           root.isVisibleToUser())return root;
        for(int i=0;i<root.getChildCount();i++){
            AccessibilityNodeInfo found=findOwnNode(
                root.getChild(i),packageName,depth+1);
            if(found!=null)return found;
        }
        return null;
    }
    private static final class MapWindow {
        final AccessibilityNodeInfo root;
        final Rect area;
        final int displayId;
        MapWindow(AccessibilityNodeInfo r,Rect a,int d){
            root=r;area=a;displayId=d;
        }
    }
    private MapWindow visibleMap(){
        return visibleMap(target());
    }
    private MapWindow visibleMap(String expected){
        return visibleMap(expected,prefs(this).getInt(WINDOW_DISPLAY,1));
    }
    private MapWindow visibleMap(String expected,int selectedDisplay){
        if(!serviceReady||(
            selectedDisplay!=0&&selectedDisplay!=1))return null;
        try{
            SparseArray<List<AccessibilityWindowInfo>> all=
                getWindowsOnAllDisplays();
            List<AccessibilityWindowInfo> one=
                all==null?null:all.get(selectedDisplay);
            if(one==null)return null;
            // WindowManager returns from top Z to bottom Z. Only the
            // highest APPLICATION window may own the wheel. A Jetour
            // firmware activity above Maps means release the callback.
            for(AccessibilityWindowInfo window:one){
                if(window==null||window.getDisplayId()!=selectedDisplay||
                   window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)
                    continue;
                AccessibilityNodeInfo raw=window.getRoot();
                if(raw==null)continue;
                AccessibilityNodeInfo own=findOwnNode(raw,expected,0);
                if(own==null)return null;
                Rect area=new Rect();
                window.getBoundsInScreen(area);
                Rect child=new Rect();
                own.getBoundsInScreen(child);
                if(child.width()>300&&child.height()>160)
                    area.intersect(child);
                if(area.width()<300||area.height()<160)
                    return null;
                return new MapWindow(own,area,selectedDisplay);
            }
        }catch(Exception ignored){}
        return null;
    }
    private AccessibilityNodeInfo bridgeMapsRoot(){
        MapWindow verified=visibleMap(MAPS,
            BridgeGoogleMapsReplacement.displayId(this));
        return verified==null?null:verified.root;
    }
    /** Explicit new Sender/voice destination authorizes replacing the old
     * Maps route. Only the one labelled STOP-confirmation in Maps is clickable,
     * within a short intent-scoped window; no global back, generic YES or taps.
     */
    private static final class StopPrompt {
        boolean question,exactExitQuestion,genericAction;
        AccessibilityNodeInfo button;
        Rect bounds;
        int differentButtons;
    }
    private void findStopPrompt(AccessibilityNodeInfo n,StopPrompt prompt,
                                int depth,int[] visits){
        if(n==null||depth>16||visits[0]++>400||!n.isVisibleToUser())return;
        String label=label(n);
        if(BridgeGoogleMapsReplacement.stopQuestion(label))prompt.question=true;
        if(BridgeGoogleMapsReplacement.exitQuestion(label))prompt.exactExitQuestion=true;
        boolean generic=BridgeGoogleMapsReplacement.genericExitAction(label);
        if(BridgeGoogleMapsReplacement.stopAction(label)||generic){
            AccessibilityNodeInfo clickable=n.isClickable()?n:null;
            if(clickable==null){
                AccessibilityNodeInfo parent=n.getParent();
                if(parent!=null&&parent.isClickable())clickable=parent;
            }
            if(clickable!=null){
                Rect b=new Rect();clickable.getBoundsInScreen(b);
                if(b.width()>28&&b.height()>20&&b.width()<700&&b.height()<260){
                    if(prompt.bounds==null||!prompt.bounds.equals(b)){
                        prompt.differentButtons++;
                        prompt.genericAction=generic;
                        prompt.bounds=b;
                        prompt.button=clickable;
                    }
                }
            }
        }
        for(int i=0;i<n.getChildCount()&&visits[0]<400;i++)
            findStopPrompt(n.getChild(i),prompt,depth+1,visits);
    }
    private void maybeConfirmMapsReplacement(){
        if(!BridgeGoogleMapsReplacement.pending(this))return;
        AccessibilityNodeInfo root=bridgeMapsRoot();
        if(root==null||!MAPS.equals(String.valueOf(root.getPackageName())))
            return;
        StopPrompt prompt=new StopPrompt();
        findStopPrompt(root,prompt,0,new int[]{0});
        if(!prompt.question||prompt.differentButtons!=1||
           prompt.button==null||
           (prompt.genericAction&&!prompt.exactExitQuestion))return;
        // Consume the permission BEFORE clicking so repeated AccessibilityEvents
        // cannot click twice. If Maps rejects it, leave a diagnostic instead.
        BridgeGoogleMapsReplacement.consume(this);
        boolean clicked=false;
        try{clicked=prompt.button.performAction(
            AccessibilityNodeInfo.ACTION_CLICK);}
        catch(Exception ignored){}
        BridgeGoogleMapsReplacement.result(this,clicked?
            "Maps: confirmación de salir de la ruta anterior SOLICITADA":
            "Maps: confirmación identificada, clic rechazado");
    }
    /** Start turn-by-turn navigation only after a NEW authenticated
     * Sender/voice destination, with a unique, visible, clickable START
     * action owned by Google Maps on the verified cluster display.
     * Never click generic OK, permissions, system controls or guessed pixels.
     */
    private static final class MapsStart {
        AccessibilityNodeInfo button;
        Rect bounds;
        int unique;
    }
    private void inspectMapsStart(AccessibilityNodeInfo node,
                                  MapsStart found,int depth,int[] visited){
        if(node==null||depth>18||visited[0]++>450||
           !node.isVisibleToUser())return;
        if(BridgeGoogleMapsReplacement.startAction(label(node))){
            AccessibilityNodeInfo clickable=node.isClickable()?node:null;
            if(clickable==null){
                AccessibilityNodeInfo parent=node.getParent();
                if(parent!=null&&parent.isClickable())clickable=parent;
            }
            if(clickable!=null&&clickable.isEnabled()){
                Rect rect=new Rect();clickable.getBoundsInScreen(rect);
                if(rect.width()>28&&rect.height()>20&&
                   rect.width()<800&&rect.height()<300){
                    if(found.bounds==null||!found.bounds.equals(rect)){
                        found.unique++;
                        found.bounds=rect;
                        found.button=clickable;
                    }
                }
            }
        }
        for(int i=0;i<node.getChildCount()&&visited[0]<450;i++)
            inspectMapsStart(node.getChild(i),found,depth+1,visited);
    }
    private void maybeStartMapsNavigation(){
        if(!BridgeGoogleMapsReplacement.startPending(this))return;
        AccessibilityNodeInfo root=bridgeMapsRoot();
        if(root==null||!MAPS.equals(String.valueOf(root.getPackageName())))
            return;
        // Never click an underlying Start button while a STOP/exit dialog
        // is covering the route. The stop path has its own narrow consent.
        StopPrompt stop=new StopPrompt();
        findStopPrompt(root,stop,0,new int[]{0});
        if(stop.question)return;
        MapsStart start=new MapsStart();
        inspectMapsStart(root,start,0,new int[]{0});
        if(start.unique!=1||start.button==null)return;
        // Consume BEFORE click to prevent duplicate click on redraw/events.
        BridgeGoogleMapsReplacement.consumeStart(this);
        boolean clicked=false;
        try{
            clicked=start.button.performAction(
                AccessibilityNodeInfo.ACTION_CLICK);
        }catch(Exception ignored){}
        BridgeGoogleMapsReplacement.result(this,clicked?
            "Google Maps · Iniciar navegación: clic accesible solicitado":
            "Google Maps · botón Iniciar detectado, clic rechazado por Android");
    }
    private void reconcileWheel(){
        if(oem==null)return;
        // Ported from DriveLab Auto Receiver: fresh Sender intent arms
        // a 12-second, one-shot Maps route replacement/start flow.
        boolean routePending=
            BridgeGoogleMapsReplacement.pending(this)||
            BridgeGoogleMapsReplacement.startPending(this);
        if(routePending){
            maybeConfirmMapsReplacement();
            maybeStartMapsNavigation();
        }
        boolean optIn=isManuallyArmed()&&prefs(this).getBoolean(ENABLED,false);
        boolean mic=isMicrophoneArmed()&&prefs(this).getBoolean(MIC_ENABLED,false);
        boolean capture=isCaptureActive();
        if(!capture&&lastCaptureRunning){
            captureEndElapsed=0L;
            int count=prefs(this).getInt(CAPTURE_COUNT,0);
            prefs(this).edit().putString(CAPTURE_STATUS,
                "CAPTURA FINALIZADA A LOS 20 S · "+count+
                " pulsaciones recibidas · sin ejecutar controles").apply();
        }
        lastCaptureRunning=capture;
        if(!capture&&!optIn&&!mic){
            oem.stop();
            status("Volante Bridge APAGADO; controla el sistema Jetour");
            choices.clear();selected=-1;selectedName="";
            main.removeCallbacks(refresh);
            // Route auto-start must operate even if driver chose
            // VOLANTE OFF. Still no OEM callback when wheel is off.
            if(routePending)main.postDelayed(refresh,650L);
            return;
        }
        // Crucial Receiver parity: receiving OEM events must NOT depend
        // on Jetour publishing the Maps root as an Accessibility window.
        // Controls are still gated by visibleMap() in onOemEvent below.
        if(!oem.active())oem.start();
        MapWindow visible=optIn?visibleMap():null;
        if(optIn&&visible==null){
            choices.clear();selected=-1;selectedName="";
            status("RECEPTOR OEM SOLICITADO · mapa no verificable: "+
                "teclas en sólo lectura (Display "+
                prefs(this).getInt(WINDOW_DISPLAY,1)+")");
        }else if(optIn&&!capture&&visible!=null){
            status("RECEPTOR OEM SOLICITADO · mapa verificable en Display "+
                visible.displayId+" · OK/ATRÁS/OPCIONES sin zoom");
        }else if(mic&&!optIn&&!capture){
            status("RECEPTOR OEM SOLICITADO · micrófono 1010 habilitado "+
                "manualmente; rueda no configurada");
        }
        // The vendor binder may disconnect while the app's screen is
        // unchanged. Recheck with bounded polling while manually armed.
        main.removeCallbacks(refresh);
        main.postDelayed(refresh,700L);
    }
    private void onOemEvent(int code,int action,int extra){
        if(isCaptureActive()){
            SharedPreferences p=prefs(this);
            int count=p.getInt(CAPTURE_COUNT,0)+(action==100?1:0);
            p.edit().putInt(CAPTURE_COUNT,count)
                .putString(CAPTURE_LAST,
                    "TECLA "+code+" · ACCIÓN "+action+
                    " · EXTRA "+extra)
                .putString(CAPTURE_STATUS,
                    "BINDER OEM RECIBE EVENTOS · 20 S sólo lectura")
                .apply();
            return; // No selection/click/back/gesture during recapture.
        }
        if(code==1010){
            if(isMicrophoneArmed()&&prefs(this).getBoolean(MIC_ENABLED,false)){
                BridgeOemVoiceLauncher.trigger(this,action);
            }else{
                status("MICRÓFONO OEM 1010 recibido por Bridge · "+
                    "asistente de voz no habilitado manualmente");
            }
            return;
        }
        if(!isManuallyArmed()||
           !prefs(this).getBoolean(ENABLED,false))return;
        boolean reverse=prefs(this).getBoolean(REVERSE,false);
        int cmd=BridgeWheelPolicy.translate(code,action,reverse);
        if(cmd==BridgeWheelPolicy.NONE)return;
        MapWindow map=visibleMap();
        if(map==null){
            status("TECLA OEM "+code+" recibida, pero Maps/Waze no está "+
                "en la ventana superior verificable del Display "+
                prefs(this).getInt(WINDOW_DISPLAY,1)+
                " · ninguna acción ejecutada");
            return;
        }
        long now=SystemClock.elapsedRealtime();
        if(code==lastKey&&now-lastKeyAt<COOLDOWN_MS)return;
        lastKey=code;lastKeyAt=now;
        prefs(this).edit().putString(LAST_KEY,
            code+" / "+BridgeWheelPolicy.name(cmd)).apply();
        switch(cmd){
            case BridgeWheelPolicy.NEXT:
            case BridgeWheelPolicy.PREVIOUS:
                if(promptChoices(map.root))
                    moveChoice(cmd==BridgeWheelPolicy.NEXT);
                else status("RUEDA: el mapa no muestra opciones seleccionables; "+
                    "no se ejecutó ningún gesto");
                break;
            case BridgeWheelPolicy.CONFIRM:
                confirm(map.root);break;
            case BridgeWheelPolicy.CANCEL:
                cancel(map.root);break;
            default:break;
        }
    }
    @Override public boolean onKeyEvent(KeyEvent event){
        // Never consume arbitrary Android input. Vendor events are received
        // only by our explicitly opted-in Binder callback above.
        return false;
    }
    private void visit(AccessibilityNodeInfo node,int depth,int[] n){
        if(node==null||depth>14||n[0]++>MAX_NODES||
           !node.isVisibleToUser())return;
        if(permitted(label(node))){
            AccessibilityNodeInfo button=node;
            if(!button.isClickable()){
                AccessibilityNodeInfo parent=button.getParent();
                for(int level=0;level<3&&parent!=null;level++){
                    // Never traverse into a clickable Jetour/Android wrapper.
                    if(!target().equals(String.valueOf(
                        parent.getPackageName())))break;
                    if(parent.isClickable()){button=parent;break;}
                    parent=parent.getParent();
                }
            }
            if(button.isClickable()&&
               target().equals(String.valueOf(button.getPackageName()))){
                Rect r=new Rect();button.getBoundsInScreen(r);
                if(r.width()>24&&r.height()>18&&r.width()<850&&
                   r.height()<400){
                    String text=normalized(label(node));
                    boolean exists=false;
                    for(Choice item:choices)
                        if(item.text.equals(text))exists=true;
                    if(!exists)choices.add(new Choice(button,text,r));
                }
            }
        }
        for(int i=0;i<node.getChildCount()&&n[0]<MAX_NODES;i++)
            visit(node.getChild(i),depth+1,n);
    }
    private boolean promptChoices(AccessibilityNodeInfo root){
        choices.clear();
        visit(root,0,new int[]{0});
        Collections.sort(choices,Comparator
            .comparingInt((Choice a)->a.bounds.top)
            .thenComparingInt(a->a.bounds.left));
        if(choices.isEmpty()||choices.size()>25){
            choices.clear();selected=-1;selectedName="";
            return false;
        }
        // This is a navigation list of visible buttons, not a zoom mode.
        // A single valid map option is selectable with the wheel too.
        if(!selectedName.isEmpty()){
            selected=-1;
            for(int i=0;i<choices.size();i++)
                if(selectedName.equals(choices.get(i).text))selected=i;
        }
        return true;
    }
    private void moveChoice(boolean forward){
        if(choices.isEmpty())return;
        selected=Math.floorMod(selected+(forward?1:-1),choices.size());
        selectedName=choices.get(selected).text;
        Choice active=choices.get(selected);
        // Accessibility focus highlights the chosen map button when Android
        // supports it. It does NOT click or operate any OEM control.
        active.node.performAction(
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        status("VOLANTE: opción "+(selected+1)+"/"+choices.size()+
            " · "+selectedName+" (OK confirma · ATRÁS cancela)");
    }
    private void confirm(AccessibilityNodeInfo root){
        if(!promptChoices(root)||selected<0||selected>=choices.size()){
            status("OK: primero seleccioná una opción del mapa con la rueda");
            return;
        }
        Choice selection=choices.get(selected);
        boolean clicked=selection.node.isVisibleToUser()&&
            target().equals(String.valueOf(selection.node.getPackageName()))&&
            selection.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        status(clicked?"OK: acción del mapa solicitada: "+selection.text:
            "OK: el mapa rechazó la pulsación");
        selected=-1;selectedName="";choices.clear();
    }
    private void cancel(AccessibilityNodeInfo root){
        if(promptChoices(root)){
            Choice dismiss=null;
            for(Choice choice:choices){
                if(choice.text.equals("cancelar")||
                   choice.text.equals("cancel")||
                   choice.text.equals("atras")||
                   choice.text.equals("volver")||
                   choice.text.equals("back")||
                   choice.text.equals("no gracias")){
                    if(dismiss!=null){
                        status("ATRÁS: opciones ambiguas, no se pulsó nada");
                        return;
                    }
                    dismiss=choice;
                }
            }
            if(dismiss!=null){
                boolean click=dismiss.node.isVisibleToUser()&&
                    target().equals(String.valueOf(dismiss.node.getPackageName()))&&
                    dismiss.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                status(click?"ATRÁS: botón del mapa solicitado":
                    "ATRÁS: el mapa rechazó la pulsación");
                selected=-1;selectedName="";return;
            }
        }
        selected=-1;selectedName="";choices.clear();
        status("ATRÁS: selección cancelada; NUNCA se envió BACK global");
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        int type=event.getEventType();
        if(type==AccessibilityEvent.TYPE_WINDOWS_CHANGED||
           type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||
           type==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED){
            // NAV response is limited to the encrypted Sender request;
            // no generic map UI events alone can arm a navigation click.
            if(BridgeGoogleMapsReplacement.pending(this)||
               BridgeGoogleMapsReplacement.startPending(this))
                requestRefresh();
            else if(isManuallyArmed()||isCaptureActive())
                requestRefresh();
        }
    }
    @Override public void onInterrupt(){release();}
    @Override public boolean onUnbind(android.content.Intent intent){
        release();return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        release();super.onDestroy();
    }
    private void release(){
        serviceReady=false;
        connectedNow=false;
        if(connectedInstance==this)connectedInstance=null;
        captureEndElapsed=0L;
        lastCaptureRunning=false;
        main.removeCallbacks(refresh);
        try{prefs(this).unregisterOnSharedPreferenceChangeListener(this);}
        catch(Exception ignored){}
        if(oem!=null){oem.stop();oem=null;}
        prefs(this).edit().putBoolean(SERVICE_CONNECTED,false).apply();
    }
}
