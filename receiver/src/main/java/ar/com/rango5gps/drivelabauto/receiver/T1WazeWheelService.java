package ar.com.rango5gps.drivelabauto.receiver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityWindowInfo;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * User-enabled accessibility experiment for safe Waze prompt choices.
 * Does NOT register with OEM steering Binder or send global key injection.
 * On Android 11 it sees ONLY Android KeyEvent dispatches allowed by the ROM.
 * Never clicks coordinates. Never performs unattended confirmations.
 */
public final class T1WazeWheelService extends AccessibilityService {
    static final String PREFS="t1_waze_wheel_v1";
    static final String ENABLED="enabled",PENDING="capture_pending",
        PENDING_TIME="capture_started",LAST="last_key",
        LAST_STATUS="status",LAST_TIME="last_time",
        SERVICE_AT="service_connected_at",WINDOW="last_capture_window",
        OEM_BINDER_ENABLED="oem_binder_enabled",
        OEM_BINDER_STATUS="oem_binder_status",
        OEM_LAST="oem_last_key";
    private static final long LEARN_MS=45000L;
    static final String NEXT="wheel_next",PREV="wheel_prev",
        OK="wheel_ok",BACK="wheel_back",ROUTES="wheel_routes";
    private static final String WAZE="com.waze";
    private static final String[] DECISION={
        "aceptar","cancelar","confirmar","continuar","omitir","descartar",
        "agregar","anadir","nuevo viaje","nuevo trayecto","iniciar",
        "guardar","listo","cerrar","entendido","no","si","volver",
        "terminar","seguir","reportar","compartir reporte",
        "accept","cancel","confirm","continue","dismiss","add",
        "save","done","close","yes","no","skip","start"
    };
    private static final String[] MODAL_CUES={
        "aceptar","cancelar","confirmar","continuar","descartar",
        "no","si","accept","cancel","confirm","continue","yes","no",
        "dismiss"
    };
    private final ArrayList<Candidate> choices=new ArrayList<>();
    private TextView hint;
    private WindowManager windows;
    private int selected=0;
    private String selectedLabel="",seenPackage="";
    private boolean alternateList,mapMenuArmed;
    private long lastButtonAt;
    private T1OemHardKeyBridge oemBridge;
    private final Handler main=new Handler(Looper.getMainLooper());
    private long lastOemKeyAt;
    private int lastOemCode=-1;
    private final Runnable oemHeartbeat=new Runnable(){
        @Override public void run(){
            refreshOemBridge();
            main.postDelayed(this,2500L);
        }
    };
    private static final class Candidate{
        final AccessibilityNodeInfo node;
        final String label;
        final Rect bounds;
        Candidate(AccessibilityNodeInfo n,String label){
            this.node=n;this.label=label;
            bounds=new Rect();n.getBoundsInScreen(bounds);
        }
    }
    private SharedPreferences prefs(){
        return getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static void learn(Context c,String action){
        if(!NEXT.equals(action)&&!PREV.equals(action)&&
           !OK.equals(action)&&!BACK.equals(action)&&
           !ROUTES.equals(action))return;
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(PENDING,action)
            .putLong(PENDING_TIME,System.currentTimeMillis())
            .putString(LAST_STATUS,"Capturando "+action+
                ": pulsá una tecla física; no hace falta abrir Waze")
            .putString(WINDOW,"sin datos todavía").apply();
    }
    static String describe(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        long connected=p.getLong(SERVICE_AT,0);
        String state=connected>0&&connected<=now?
            "Accesibilidad: el servicio arrancó.":"Accesibilidad: sin inicio registrado.";
        String pending=p.getString(PENDING,"");
        if(!pending.isEmpty()){
            long remaining=LEARN_MS-(now-p.getLong(PENDING_TIME,0));
            state+="\nAPRENDIZAJE "+pending+": "+
                (remaining>0?"restan "+((remaining+999)/1000)+" s":
                    "TIEMPO TERMINADO; no llegó una tecla capturable");
        }
        return state+"\n"+
            p.getString(LAST_STATUS,"Sin teclas capturadas")+
            "\nPUENTE OEM WAZE: "+
            (p.getBoolean(OEM_BINDER_ENABLED,false)?
                "HABILITADO POR USUARIO":"APAGADO")+
            " · "+p.getString(OEM_BINDER_STATUS,"sin enlace iniciado")+
            "\nÚltima señal OEM: "+p.getString(OEM_LAST,"ninguna")+
            "\nÚltima ventana durante captura: "+
            p.getString(WINDOW,"sin datos")+
            "\nSIGUIENTE="+p.getInt(NEXT,KeyEvent.KEYCODE_DPAD_RIGHT)+
            " · ANTERIOR="+p.getInt(PREV,KeyEvent.KEYCODE_DPAD_LEFT)+
            " · OK="+p.getInt(OK,KeyEvent.KEYCODE_DPAD_CENTER)+
            " · ATRÁS="+p.getInt(BACK,KeyEvent.KEYCODE_BACK)+
            " · RUTAS="+p.getInt(ROUTES,-1);
    }
    /** Local in-screen keyboard/key diagnosis, independent of accessibility.
     * Runs ONLY during an explicit 45-second user calibration. Never
     * consumes the KeyEvent: existing OEM/T1Launcher handling remains intact.
     * If the wheel uses a private CAN / Binder callback, this will not see
     * it, which is exactly what the diagnostic helps establish.
     */
    static void observeActivityKey(Context c,KeyEvent event){
        if(event==null||event.getAction()!=KeyEvent.ACTION_DOWN||
           event.getRepeatCount()!=0)return;
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String pending=p.getString(PENDING,"");
        if(pending.isEmpty())return;
        long now=System.currentTimeMillis();
        long age=now-p.getLong(PENDING_TIME,0L);
        if(age<0||age>=LEARN_MS)return;
        int code=event.getKeyCode();
        String message="Capturado "+pending+" = "+code+
            " ("+KeyEvent.keyCodeToString(code)+")"+
            " · por pantalla DriveLab (sin Waze)";
        p.edit().putInt(pending,code).remove(PENDING)
            .putInt(LAST,code).putString(LAST_STATUS,message)
            .putString(WINDOW,"DRIVELAB.ACTIVITY")
            .putLong(LAST_TIME,now).apply();
        Log.i("DriveLabWheel",message);
    }

    private void status(String message){
        prefs().edit().putString(LAST_STATUS,message)
            .putLong(LAST_TIME,System.currentTimeMillis()).apply();
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        AccessibilityServiceInfo info=getServiceInfo();
        if(info!=null){
            info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                |AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                |AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC;
            setServiceInfo(info);
        }
        windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        prefs().edit().putLong(SERVICE_AT,System.currentTimeMillis())
            .putString(LAST_STATUS,
                "Servicio de accesibilidad conectado; listo para APRENDER")
            .apply();
        oemBridge=new T1OemHardKeyBridge(this,new T1OemHardKeyBridge.Receiver(){
            @Override public void onHardKey(int code,int action,int extra){
                handleOemKey(code,action,extra);
            }
            @Override public void onStatus(String message){
                prefs().edit().putString(OEM_BINDER_STATUS,message).apply();
                Log.i("DriveLabWheel",message);
            }
        });
        main.removeCallbacks(oemHeartbeat);
        main.post(oemHeartbeat);
        Log.i("DriveLabWheel","AccessibilityService connected; OEM bridge is opt-in");
    }
    /** Waze can run on Display 2 while another app stays focused on Display 0.
     * T1Launcher itself uses getWindowsOnAllDisplays: do not limit this service
     * to getRootInActiveWindow() or main screen package.
     */
    private AccessibilityNodeInfo wazeRoot(){
        try{
            SparseArray<List<AccessibilityWindowInfo>> across=getWindowsOnAllDisplays();
            if(across!=null)for(int d=0;d<across.size();d++){
                List<AccessibilityWindowInfo> ws=across.valueAt(d);
                if(ws==null)continue;
                for(AccessibilityWindowInfo win:ws){
                    if(win==null)continue;
                    AccessibilityNodeInfo root=win.getRoot();
                    if(root!=null&&WAZE.contentEquals(root.getPackageName())&&
                            root.isVisibleToUser())return root;
                }
            }
        }catch(Exception e){
            prefs().edit().putString(OEM_BINDER_STATUS,
                "No se pudo consultar pantalla 2: "+e.getClass().getSimpleName())
                .apply();
        }
        AccessibilityNodeInfo current=getRootInActiveWindow();
        if(current!=null&&WAZE.contentEquals(current.getPackageName()))
            return current;
        return null;
    }
    private void refreshOemBridge(){
        if(oemBridge==null)return;
        boolean enabled=prefs().getBoolean(OEM_BINDER_ENABLED,false);
        if(enabled&&wazeRoot()!=null)oemBridge.start();
        else oemBridge.stop();
    }
    /** Vendor hard-key callback IDs confirmed in the owner's T1N134 APK.
     * 1000,1001 wheel rotation; 1002 BACK; 1003 OK.
     * OEM states 100/101 may represent down/up: debounce same physical key,
     * avoiding duplicate confirmations. Other IDs, especially voice, ignored.
     */
    private void handleOemKey(int code,int action,int extra){
        if(!prefs().getBoolean(OEM_BINDER_ENABLED,false)||
                oemBridge==null||!oemBridge.registered())return;
        if(action!=100&&action!=101)return;
        long now=SystemClock.elapsedRealtime();
        prefs().edit().putString(OEM_LAST,
            code+" · estado "+action+" · tercer dato "+extra).apply();
        if(code==lastOemCode&&now-lastOemKeyAt<225)return;
        lastOemCode=code;lastOemKeyAt=now;
        int mapped;
        switch(code){
            case 1000:mapped=KeyEvent.KEYCODE_DPAD_RIGHT;break;
            case 1001:mapped=KeyEvent.KEYCODE_DPAD_LEFT;break;
            case 1002:mapped=KeyEvent.KEYCODE_BACK;break;
            case 1003:mapped=KeyEvent.KEYCODE_DPAD_CENTER;break;
            default:return;
        }
        // Reuse the SAME Waze-only accessibility selection policy as ordinary
        // Android keys; never fabricate clicks on Google Maps or OEM windows.
        onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,mapped));
    }
    @Override public boolean onKeyEvent(KeyEvent event){
        if(event==null||event.getAction()!=KeyEvent.ACTION_DOWN||
            event.getRepeatCount()>0)return false;
        final int code=event.getKeyCode();
        final long now=System.currentTimeMillis();
        SharedPreferences p=prefs();
        String pending=p.getString(PENDING,"");
        // IMPORTANT: explicit 45-second LEARNING must run BEFORE the
        // com.waze foreground check. The T1 may display an OEM window
        // or T1Launcher Zoom overlay when the physical wheel turns.
        // In calibration we only observe one Android KeyEvent, locally.
        // We NEVER consume it and never click anything during learning.
        if(!pending.isEmpty()){
            if(now-p.getLong(PENDING_TIME,0)>=0&&
               now-p.getLong(PENDING_TIME,0)<LEARN_MS){
                AccessibilityNodeInfo captureRoot=getRootInActiveWindow();
                String foreground=captureRoot==null?"sin ventana":
                    String.valueOf(captureRoot.getPackageName());
                String kind=WAZE.equals(foreground)?"Waze":
                    getPackageName().equals(foreground)?"DriveLab":
                        "T1Launcher / sistema / otra";
                String message="Capturado "+pending+" = "+code+
                    " ("+KeyEvent.keyCodeToString(code)+")"+
                    " · ventana "+kind;
                p.edit().putInt(pending,code).remove(PENDING)
                    .putInt(LAST,code).putString(LAST_STATUS,message)
                    .putString(WINDOW,foreground)
                    .putLong(LAST_TIME,now).apply();
                Log.i("DriveLabWheel",message);
                return false; // OEM gets same key, no double action
            }
            p.edit().remove(PENDING)
                .putString(LAST_STATUS,
                    "APRENDER venció sin recibir una tecla Android")
                .apply();
        }
        // Normal Waze controls remain strictly restricted to com.waze.
        // Absolutely no key logging or interception in unrelated apps.
        AccessibilityNodeInfo root=wazeRoot();
        if(root==null){
            mapMenuArmed=false;
            hideHint();
            return false;
        }
        p.edit().putInt(LAST,code).putLong(LAST_TIME,now).apply();
        if(!p.getBoolean(ENABLED,false))return false;
        int next=p.getInt(NEXT,KeyEvent.KEYCODE_DPAD_RIGHT);
        int prev=p.getInt(PREV,KeyEvent.KEYCODE_DPAD_LEFT);
        int ok=p.getInt(OK,KeyEvent.KEYCODE_DPAD_CENTER);
        int back=p.getInt(BACK,KeyEvent.KEYCODE_BACK);
        int routes=p.getInt(ROUTES,-1);
        boolean routeShortcut=routes>0&&code==routes;
        boolean forward=code==next||
            (next==KeyEvent.KEYCODE_DPAD_RIGHT&&code==KeyEvent.KEYCODE_DPAD_DOWN);
        boolean backward=code==prev||
            (prev==KeyEvent.KEYCODE_DPAD_LEFT&&code==KeyEvent.KEYCODE_DPAD_UP);
        boolean confirm=code==ok||(ok==KeyEvent.KEYCODE_DPAD_CENTER&&
            code==KeyEvent.KEYCODE_ENTER);
        boolean cancel=code==back;
        if(!forward&&!backward&&!confirm&&!cancel&&!routeShortcut)return false;
        if(SystemClock.elapsedRealtime()-lastButtonAt<150)return true;

        // First priority: list of actual Waze route alternatives. Not a
        // coordinate-based selector; the candidates are real clickable nodes.
        if(collectAlternativeRoutes(root)){
            lastButtonAt=SystemClock.elapsedRealtime();
            if(cancel){
                hideHint();
                selectedLabel="";
                status("Waze: volver de rutas alternativas");
                return performGlobalAction(GLOBAL_ACTION_BACK);
            }
            if(routeShortcut){
                highlight();
                return true;
            }
            if(forward||backward){
                selected=Math.floorMod(selected+(forward?1:-1),choices.size());
                selectedLabel=choices.get(selected).label;
                highlight();
                return true;
            }
            return clickSelected("RUTA ALTERNATIVA · OK");
        }
        // Second priority: genuine confirmation/cancel prompts on Waze.
        if(collectPromptChoices(root)){
            lastButtonAt=SystemClock.elapsedRealtime();
            if(routeShortcut)return false;
            if(forward||backward){
                selected=Math.floorMod(selected+(forward?1:-1),choices.size());
                selectedLabel=choices.get(selected).label;
                highlight();
                return true;
            }
            if(cancel){
                for(int i=0;i<choices.size();i++){
                    String norm=normalize(choices.get(i).label);
                    if(norm.equals("cancelar")||norm.equals("cancel")||
                        norm.equals("no")||norm.equals("volver")||
                        norm.equals("descartar")){
                        selected=i;selectedLabel=choices.get(i).label;
                        return clickSelected("ATRÁS");
                    }
                }
                hideHint();
                status("ATRÁS: diálogo Waze detectado sin cancelar etiquetado");
                return performGlobalAction(GLOBAL_ACTION_BACK);
            }
            return clickSelected("OK");
        }
        // A wheel turn focuses REAL labelled buttons. OK can click one only
        // AFTER such an explicit selection: no random initial actions.
        if((forward||backward)&&collectMapChoices(root)){
            lastButtonAt=SystemClock.elapsedRealtime();
            selected=Math.floorMod(selected+(forward?1:-1),choices.size());
            selectedLabel=choices.get(selected).label;
            mapMenuArmed=true;
            highlight();
            return true;
        }
        if(cancel&&mapMenuArmed){
            mapMenuArmed=false;
            selectedLabel="";
            hideHint();
            status("Waze: selección cancelada sin tocar pantalla");
            return true;
        }
        if(confirm&&mapMenuArmed&&collectMapChoices(root)){
            mapMenuArmed=false;
            lastButtonAt=SystemClock.elapsedRealtime();
            return clickSelected("BOTÓN DE WAZE · OK");
        }
        // Previous behaviour: OK with no highlighted map button attempts
        // only the labelled "Rutas" accessibility control.
        if(confirm||routeShortcut){
            AccessibilityNodeInfo routesButton=findRoutesButton(root,0,new int[]{0});
            if(routesButton==null){
                status("RUTAS: Waze no expone el botón de rutas accesible. "+
                    "No se hizo ningún toque.");
                hideHint();
                return false;
            }
            String label=labelOf(routesButton);
            boolean opened=routesButton.performAction(
                AccessibilityNodeInfo.ACTION_CLICK);
            lastButtonAt=SystemClock.elapsedRealtime();
            status("ABRIR RUTAS: "+label+" → "+
                (opened?"clic enviado":"rechazado por Waze"));
            hideHint();selectedLabel="";mapMenuArmed=false;
            return opened;
        }
        return false;
    }
    /** Exact accessible button match, no screen coordinates or guessed tap. */
    private AccessibilityNodeInfo findRoutesButton(
            AccessibilityNodeInfo root,int depth,int[] visited){
        if(root==null||depth>18||visited[0]++>450||
            !root.isVisibleToUser())return null;
        String id=root.getViewIdResourceName();
        String label=normalize(labelOf(root));
        String key=id==null?"":normalize(id);
        boolean route=(label.equals("rutas")||label.equals("routes")||
            label.contains("ruta alternativa")||
            label.contains("rutas alternativas")||
            label.equals("ver rutas")||label.equals("view routes")||
            label.equals("alternate routes")||
            label.equals("alternative routes")||
            key.contains("alternativeroute")||
            key.contains("alternativeroute")||
            key.contains("routealternatives"));
        if(route&&root.isClickable())return root;
        if(route&&root.getParent()!=null&&root.getParent().isClickable())
            return root.getParent();
        for(int i=0;i<root.getChildCount();i++){
            AccessibilityNodeInfo found=findRoutesButton(
                root.getChild(i),depth+1,visited);
            if(found!=null)return found;
        }
        return null;
    }
    private static boolean isRouteLabel(String label){
        String n=normalize(label);
        // Restrict to route ETA or explicit alternatives descriptions.
        return n.matches(".*\\b[0-9]{1,3}\\s*(min|minutos|minutes)\\b.*")||
            n.contains("ruta via ")||n.contains("route via ")||
            n.startsWith("ruta alternativa ")||
            n.startsWith("alternative route ");
    }
    private static final class RouteRead {
        int titleCount=0,etaCards=0;
    }
    private void inspectRouteCards(AccessibilityNodeInfo node,int depth,
                                   int[] visited,RouteRead found){
        if(node==null||depth>18||visited[0]++>450||
            !node.isVisibleToUser())return;
        String label=labelOf(node),normal=normalize(label);
        if(normal.equals("rutas alternativas")||
            normal.equals("alternate routes")||
            normal.equals("alternative routes")||
            normal.equals("routes")||normal.equals("rutas"))
            found.titleCount++;
        if(node.isClickable()&&isRouteLabel(label)){
            Rect region=new Rect();node.getBoundsInScreen(region);
            if(region.height()>25&&region.width()>40){
                choices.add(new Candidate(node,label));
                found.etaCards++;
            }
        }
        for(int i=0;i<node.getChildCount();i++)
            inspectRouteCards(node.getChild(i),depth+1,visited,found);
    }
    private boolean collectAlternativeRoutes(AccessibilityNodeInfo root){
        choices.clear();
        RouteRead found=new RouteRead();
        inspectRouteCards(root,0,new int[]{0},found);
        // Require the routes dialog/panel AND >=2 real choice nodes.
        // If Waze has unlabelled custom-drawn cards, do not guess.
        if(found.titleCount==0||found.etaCards<2||found.etaCards>6){
            choices.clear();
            return false;
        }
        Collections.sort(choices,Comparator
            .comparingInt((Candidate c)->c.bounds.top)
            .thenComparingInt(c->c.bounds.left));
        alternateList=true;mapMenuArmed=false;
        if(!selectedLabel.isEmpty()){
            for(int i=0;i<choices.size();i++)
                if(selectedLabel.equals(choices.get(i).label)){
                    selected=i;return true;
                }
        }
        selected=Math.max(0,Math.min(selected,choices.size()-1));
        selectedLabel=choices.get(selected).label;
        return true;
    }
    /** General Waze map controls accessible on Display 2. Exclude unlabelled
     * custom Canvas elements and inaccessible controls. The visible label
     * is required before physical wheel navigation can act on it.
     */
    private void visitMapChoices(AccessibilityNodeInfo node,int depth,int[] count){
        if(node==null||depth>16||count[0]++>420||!node.isVisibleToUser())return;
        String label=labelOf(node);
        String norm=normalize(label);
        if(node.isClickable()&&!norm.isEmpty()&&
            norm.length()<=95&&!norm.equals("mapa")&&!norm.equals("map")&&
            !norm.equals("waze")){
            Rect r=new Rect();
            node.getBoundsInScreen(r);
            if(r.width()>28&&r.height()>22)
                choices.add(new Candidate(node,label));
        }
        for(int i=0;i<node.getChildCount()&&count[0]<420;i++)
            visitMapChoices(node.getChild(i),depth+1,count);
    }
    private boolean collectMapChoices(AccessibilityNodeInfo root){
        choices.clear();alternateList=false;
        visitMapChoices(root,0,new int[]{0});
        if(choices.isEmpty())return false;
        Collections.sort(choices,Comparator
            .comparingInt((Candidate c)->c.bounds.top)
            .thenComparingInt(c->c.bounds.left));
        if(!selectedLabel.isEmpty())
            for(int i=0;i<choices.size();i++)
                if(selectedLabel.equals(choices.get(i).label)){
                    selected=i;return true;
                }
        selected=Math.max(0,Math.min(selected,choices.size()-1));
        selectedLabel=choices.get(selected).label;
        return true;
    }
    private static String normalize(String value){
        String s=Normalizer.normalize(value,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();
        return s.replaceAll("\\s+"," ");
    }
    private static boolean matched(String text,String[] allowed){
        String n=normalize(text);
        for(String term:allowed)
            if(n.equals(term)||n.startsWith(term+" ")||n.endsWith(" "+term))
                return true;
        return false;
    }
    private static String labelOf(AccessibilityNodeInfo node){
        CharSequence text=node.getText(),desc=node.getContentDescription();
        if(text!=null&&text.length()>0)return text.toString().trim();
        if(desc!=null&&desc.length()>0)return desc.toString().trim();
        return "";
    }
    private void visit(AccessibilityNodeInfo root,int depth,int[] count){
        if(root==null||depth>15||count[0]++>350||!root.isVisibleToUser())return;
        String label=labelOf(root);
        if(root.isClickable()&&matched(label,DECISION)){
            Rect r=new Rect();root.getBoundsInScreen(r);
            if(r.width()>20&&r.height()>15)
                choices.add(new Candidate(root,label));
        }
        for(int i=0;i<root.getChildCount()&&count[0]<350;i++)
            visit(root.getChild(i),depth+1,count);
    }
    private boolean collectPromptChoices(AccessibilityNodeInfo root){
        choices.clear();
        alternateList=false;mapMenuArmed=false;
        visit(root,0,new int[]{0});
        Collections.sort(choices,Comparator
            .comparingInt((Candidate c)->c.bounds.top)
            .thenComparingInt(c->c.bounds.left));
        if(choices.size()<2)return false;
        boolean modal=false;
        for(Candidate c:choices)if(matched(c.label,MODAL_CUES))modal=true;
        if(!modal)return false;
        if(!selectedLabel.isEmpty()){
            for(int i=0;i<choices.size();i++)
                if(selectedLabel.equals(choices.get(i).label)){
                    selected=i;return true;
                }
        }
        selected=Math.max(0,Math.min(selected,choices.size()-1));
        selectedLabel=choices.get(selected).label;
        return true;
    }
    private boolean clickSelected(String action){
        if(selected<0||selected>=choices.size())return false;
        Candidate c=choices.get(selected);
        boolean success=c.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        status("Waze "+action+" · "+c.label+" → "+(success?"solicitado":"rechazado"));
        hideHint();
        selectedLabel="";
        return success;
    }
    private void highlight(){
        if(choices.isEmpty())return;
        Candidate c=choices.get(selected);
        c.node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        String text=(alternateList?"RUTAS A/B/C  ":"VOLANTE  ")+
            (selected+1)+"/"+choices.size()+
            "  ·  "+c.label+"  ·  OK / ATRÁS";
        if(hint==null){
            hint=new TextView(this);
            hint.setTextColor(Color.WHITE);
            hint.setBackgroundColor(0xE51C3540);
            hint.setTextSize(17);
            hint.setPadding(18,10,18,10);
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            lp.y=22;
            try{windows.addView(hint,lp);}
            catch(Exception ex){hint=null;status("Waze: sin overlay "+ex.getClass().getSimpleName());}
        }
        if(hint!=null)hint.setText(text);
        status("Waze seleccionado: "+text);
    }
    private void hideHint(){
        if(hint==null)return;
        try{windows.removeView(hint);}catch(Exception ignored){}
        hint=null;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        // Waze can be visible ONLY on the cluster display. A different
        // foreground package does NOT prove that projected Waze disappeared.
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED||
           event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            refreshOemBridge();
            if(wazeRoot()==null)hideHint();
        }
    }
    @Override public void onInterrupt(){
        hideHint();
        if(oemBridge!=null)oemBridge.stop();
    }
    @Override public void onDestroy(){
        hideHint();
        main.removeCallbacks(oemHeartbeat);
        if(oemBridge!=null)oemBridge.stop();
        super.onDestroy();
    }
}