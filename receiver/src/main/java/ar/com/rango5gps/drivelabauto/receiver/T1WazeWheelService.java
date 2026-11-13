package ar.com.rango5gps.drivelabauto.receiver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.SystemClock;
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
        LAST_STATUS="status",LAST_TIME="last_time";
    static final String NEXT="wheel_next",PREV="wheel_prev",
        OK="wheel_ok",BACK="wheel_back";
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
    private long lastButtonAt;
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
           !OK.equals(action)&&!BACK.equals(action))return;
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(PENDING,action)
            .putLong(PENDING_TIME,System.currentTimeMillis())
            .putString(LAST_STATUS,"Capturando "+action+
                ": pulsá una tecla física del volante").apply();
    }
    static String describe(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        return p.getString(LAST_STATUS,"Sin teclas capturadas")+
            "\nSIGUIENTE="+p.getInt(NEXT,KeyEvent.KEYCODE_DPAD_RIGHT)+
            " · ANTERIOR="+p.getInt(PREV,KeyEvent.KEYCODE_DPAD_LEFT)+
            " · OK="+p.getInt(OK,KeyEvent.KEYCODE_DPAD_CENTER)+
            " · ATRÁS="+p.getInt(BACK,KeyEvent.KEYCODE_BACK);
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
        status("Accesibilidad Waze activa; esperando KeyEvent real del volante");
    }
    @Override public boolean onKeyEvent(KeyEvent event){
        if(event==null||event.getAction()!=KeyEvent.ACTION_DOWN||
            event.getRepeatCount()>0)return false;
        final int code=event.getKeyCode();
        final long now=System.currentTimeMillis();
        SharedPreferences p=prefs();
        String pending=p.getString(PENDING,"");
        if(!pending.isEmpty()){
            if(now-p.getLong(PENDING_TIME,0)<45000){
                p.edit().putInt(pending,code).remove(PENDING)
                    .putInt(LAST,code)
                    .putString(LAST_STATUS,"Capturado "+pending+" = "+code+
                        " ("+KeyEvent.keyCodeToString(code)+")")
                    .putLong(LAST_TIME,now).apply();
                return false; // do not hijack OEM behavior during calibration
            }
            p.edit().remove(PENDING).apply();
        }
        p.edit().putInt(LAST,code).putLong(LAST_TIME,now).apply();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        String pkg=root==null?"":String.valueOf(root.getPackageName());
        if(!WAZE.equals(pkg)||!p.getBoolean(ENABLED,false)){
            if(!WAZE.equals(pkg))hideHint();
            return false;
        }
        int next=p.getInt(NEXT,KeyEvent.KEYCODE_DPAD_RIGHT);
        int prev=p.getInt(PREV,KeyEvent.KEYCODE_DPAD_LEFT);
        int ok=p.getInt(OK,KeyEvent.KEYCODE_DPAD_CENTER);
        int back=p.getInt(BACK,KeyEvent.KEYCODE_BACK);
        boolean forward=code==next||
            (next==KeyEvent.KEYCODE_DPAD_RIGHT&&code==KeyEvent.KEYCODE_DPAD_DOWN);
        boolean backward=code==prev||
            (prev==KeyEvent.KEYCODE_DPAD_LEFT&&code==KeyEvent.KEYCODE_DPAD_UP);
        boolean confirm=code==ok||(ok==KeyEvent.KEYCODE_DPAD_CENTER&&
            code==KeyEvent.KEYCODE_ENTER);
        boolean cancel=code==back;
        if(!forward&&!backward&&!confirm&&!cancel)return false;
        if(SystemClock.elapsedRealtime()-lastButtonAt<150)return true;
        if(!collectPromptChoices(root)){
            status("Waze sin diálogo accesible compatible. "+
                "No se consumió la tecla "+code);
            hideHint();
            return false;
        }
        lastButtonAt=SystemClock.elapsedRealtime();
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
            // Only send BACK when we detected a modal choice set.
            hideHint();
            status("ATRÁS: diálogo visible, sin cancelar accesible. "+
                "Se solicitó Android BACK.");
            return performGlobalAction(GLOBAL_ACTION_BACK);
        }
        return clickSelected("OK");
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
        String text="VOLANTE  "+(selected+1)+"/"+choices.size()+
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
        if(event!=null&&event.getPackageName()!=null){
            String pkg=String.valueOf(event.getPackageName());
            if(!WAZE.equals(pkg)&&!pkg.equals(getPackageName()))
                hideHint();
        }
    }
    @Override public void onInterrupt(){hideHint();}
    @Override public void onDestroy(){hideHint();super.onDestroy();}
}