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
 * - No hard-key registration unless a real Waze/Maps APPLICATION window
 *   is visible at Android Display 1 (not simply a successful startActivity).
 * - Never captures MODE, voice, hazard, ADAS or other OEM buttons.
 * - Never sends global BACK, ADB commands or vehicle property writes.
 * - OK/CANCEL acts only on own accessible Waze/Maps node, never OEM dialogs.
 * - Scroll moves a visible accessibility selection; never zooms or injects gestures.
 * - If the map disappears, immediately unregister the OEM callback.
 * - Can coexist installed with T1Launcher, but owner must NOT enable
 *   two hard-key callbacks simultaneously; UI makes that explicit.
 */
public final class BridgeWheelAccessibilityService extends AccessibilityService
    implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String PREFS="drivelab_bridge_wheel_v1";
    static final String ENABLED="enabled",TARGET="target",
        REVERSE="reverse",STATUS="status",LAST_KEY="last_key",
        LAST_TIME="last_event",SERVICE_CONNECTED="connected";
    static final String MAPS="com.google.android.apps.maps";
    static final String WAZE="com.waze";
    private static final long COOLDOWN_MS=180;
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
    static boolean isConnected(){return connectedNow;}
    private long lastKeyAt=0;
    private int lastKey=-1,selected=-1;
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
                onOemEvent(code,action);
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
        if(ENABLED.equals(key)||TARGET.equals(key)||REVERSE.equals(key))
            requestRefresh();
    }
    private String target(){
        String result=prefs(this).getString(TARGET,MAPS);
        return WAZE.equals(result)?WAZE:MAPS;
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
        if(!serviceReady)return null;
        String expected=target();
        try{
            SparseArray<List<AccessibilityWindowInfo>> all=
                getWindowsOnAllDisplays();
            List<AccessibilityWindowInfo> one=all==null?null:all.get(1);
            if(one==null)return null;
            // WindowManager returns from top Z to bottom Z. Only the
            // highest APPLICATION window may own the wheel. A Jetour
            // firmware activity above Maps means release the callback.
            for(AccessibilityWindowInfo window:one){
                if(window==null||window.getDisplayId()!=1||
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
                return new MapWindow(own,area,1);
            }
        }catch(Exception ignored){}
        return null;
    }
    private void reconcileWheel(){
        if(oem==null)return;
        boolean optIn=prefs(this).getBoolean(ENABLED,false);
        MapWindow visible=optIn?visibleMap():null;
        if(!optIn||visible==null){
            if(oem.active())oem.stop();
            if(!optIn)status("Volante Bridge APAGADO; controla el sistema Jetour");
            else status("SIN MAPA VERIFICADO en Display 1; rueda OEM liberada");
            choices.clear();selected=-1;selectedName="";
            return;
        }
        if(!oem.active())oem.start();
    }
    private void onOemEvent(int code,int action){
        if(!prefs(this).getBoolean(ENABLED,false))return;
        boolean reverse=prefs(this).getBoolean(REVERSE,false);
        int cmd=BridgeWheelPolicy.translate(code,action,reverse);
        if(cmd==BridgeWheelPolicy.NONE)return;
        MapWindow map=visibleMap();
        if(map==null){
            reconcileWheel();return;
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
           type==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            requestRefresh();
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
        main.removeCallbacks(refresh);
        try{prefs(this).unregisterOnSharedPreferenceChangeListener(this);}
        catch(Exception ignored){}
        if(oem!=null){oem.stop();oem=null;}
        prefs(this).edit().putBoolean(SERVICE_CONNECTED,false).apply();
    }
}
