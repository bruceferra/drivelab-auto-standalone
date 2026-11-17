package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

/** Decorative, touch-through gradient over ONLY the user's selected
 * secondary navigation Display 1. Width 0..120 physical pixels.
 *
 * This is NOT true transparency of foreign app surfaces: Android does not
 * expose Waze/Maps SurfaceControl alpha to a normal third-party APK.
 * The dark edge veil softens square panel cuts but cannot reveal OEM
 * content underneath or repair steering-wheel focus. Never infer overlay
 * success from an ActivityOptions launch or hide vehicle instruments.
 */
final class T1ClusterEdgeFade {
    static final String WIDTH_PX="cluster_edge_fade_width_px_v1";
    static final String RESULT="cluster_edge_fade_result_v1";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final int DEFAULT_PX=60;
    private static WindowManager manager;
    private static FadeView view;
    private static int displayId=-1;
    private T1ClusterEdgeFade(){}

    static int width(Context c){
        int saved=T1ClusterWazeRouter.prefs(c).getInt(WIDTH_PX,DEFAULT_PX);
        return Math.max(0,Math.min(120,saved));
    }
    static void setWidth(Context c,int px){
        int safe=Math.max(0,Math.min(120,px));
        T1ClusterWazeRouter.prefs(c).edit().putInt(WIDTH_PX,safe).apply();
        refresh(c);
    }
    private static void status(Context c,String value){
        T1ClusterWazeRouter.prefs(c).edit().putString(RESULT,value).apply();
    }
    static String last(Context c){
        return T1ClusterWazeRouter.prefs(c).getString(RESULT,
            "Degradado sin aplicar aún en la pantalla real");
    }
    static void refresh(Context c){
        Context app=c.getApplicationContext();
        MAIN.post(()->apply(app));
    }
    static void hide(Context c){
        Context app=c.getApplicationContext();
        MAIN.post(()->{
            remove();
            status(app,"DESVANECIDO OFF: superposición retirada");
        });
    }
    private static void remove(){
        if(view!=null&&manager!=null){
            try{manager.removeViewImmediate(view);}
            catch(RuntimeException ignored){}
        }
        view=null;
        manager=null;
        displayId=-1;
    }
    private static void apply(Context c){
        int px=width(c);
        if(!T1ClusterWazeRouter.enabled(c)||
           !T1ClusterLayoutPolicy.savedDualSplit(c)||px==0){
            remove();
            status(c,"DESVANECIDO OFF · 0 px o clúster sin dos mapas");
            return;
        }
        Display d=T1ClusterWazeRouter.selected(c);
        if(d==null||d.getDisplayId()!=1){
            remove();
            status(c,"DESVANECIDO no aplicado: Display 1 no disponible");
            return;
        }
        if(!Settings.canDrawOverlays(c)){
            remove();
            status(c,"DESVANECIDO no aplicado: falta permiso de superposición");
            return;
        }
        int ratio=T1ClusterLayoutPolicy.number(c,
            T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70);
        try{
            if(view!=null&&displayId==d.getDisplayId()){
                view.update(px,ratio);
                status(c,"DESVANECIDO solicitado · "+px+
                    " px (máscara visual, no transparencia OEM)");
                return;
            }
            remove();
            Context displayContext=c.createDisplayContext(d);
            WindowManager wm=(WindowManager)displayContext.getSystemService(
                Context.WINDOW_SERVICE);
            if(wm==null)throw new IllegalStateException("WindowManager no disponible");
            FadeView overlay=new FadeView(displayContext,px,ratio);
            WindowManager.LayoutParams params=new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            params.gravity=android.view.Gravity.TOP|android.view.Gravity.LEFT;
            params.setTitle("DriveLab mapas · bordes degradados");
            wm.addView(overlay,params);
            view=overlay;
            manager=wm;
            displayId=d.getDisplayId();
            status(c,"DESVANECIDO solicitado en Display 1: "+px+
                " px a cada borde; comprobar visualmente");
        }catch(Exception ex){
            remove();
            status(c,"DESVANECIDO no aplicado: "+ex.getClass().getSimpleName()+
                " · "+String.valueOf(ex.getMessage()));
        }
    }
    private static final class FadeView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int widthPx,ratio;
        private static final int VEIL=0x99151a22;
        FadeView(Context c,int px,int pct){
            super(c);
            widthPx=px;ratio=pct;
            setBackgroundColor(Color.TRANSPARENT);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setClickable(false);
            setFocusable(false);
        }
        void update(int px,int pct){
            widthPx=px;ratio=pct;invalidate();
        }
        private void strip(Canvas canvas,float left,float right,
                           int startColor,int endColor){
            if(right<=left)return;
            paint.setShader(new LinearGradient(left,0,right,0,
                startColor,endColor,Shader.TileMode.CLAMP));
            canvas.drawRect(left,0,right,getHeight(),paint);
            paint.setShader(null);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int w=getWidth();
            int e=Math.min(widthPx,Math.max(0,w/8));
            if(e==0)return;
            // Left/right outside edges and a symmetric fade around
            // the split seam. The transparent interior remains untouched.
            float seam=w*Math.max(30,Math.min(70,ratio))/100f;
            int transparent=Color.TRANSPARENT;
            strip(canvas,0,e,VEIL,transparent);
            strip(canvas,w-e,w,transparent,VEIL);
            strip(canvas,Math.max(0,seam-e),seam,transparent,VEIL);
            strip(canvas,seam,Math.min(w,seam+e),VEIL,transparent);
        }
    }
}
