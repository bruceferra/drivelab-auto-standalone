package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
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
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Draw all four navigation panel edge gradients in ONE NON-FOCUSABLE
 * window on the secondary Display 1. The former v0.3.43 implementation
 * called WindowManager.addView twice while releasing the SeekBar: on
 * the Jetour ROM this disturbed the active settings screen and did not
 * show the right panel. Never create two window tokens for this feature.
 *
 * Decoration only, not transparency of foreign Waze/Maps surfaces.
 * No OEM/car operations, Android activity relaunch, task changes,
 * focus/key injection or access to the navigation apps.
 */
final class T1ClusterEdgeFade {
    static final String WIDTH_PX="cluster_edge_fade_width_px_v1";
    static final String RESULT="cluster_edge_fade_result_v1";
    static final int MAX_PX=300;
    private static final int DEFAULT_PX=60;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static WindowManager manager;
    private static FadeView view;
    private static int displayId=-1;
    private static int attachedW=-1,attachedH=-1;
    private static Runnable queued;
    private T1ClusterEdgeFade(){}

    static int width(Context c){
        int stored=T1ClusterWazeRouter.prefs(c).getInt(WIDTH_PX,DEFAULT_PX);
        return Math.max(0,Math.min(MAX_PX,stored));
    }
    static void setWidth(Context c,int px){
        Context app=c.getApplicationContext();
        int safe=Math.max(0,Math.min(MAX_PX,px));
        T1ClusterWazeRouter.prefs(app).edit().putInt(WIDTH_PX,safe).apply();
        // Updating an existing View never calls WindowManager.addView and
        // cannot change the Activity or the selected navigation tasks.
        MAIN.post(()->{
            if(view!=null&&manager!=null&&displayId==1){
                if(safe==0)remove();
                else view.update(safe,T1ClusterLayoutPolicy.number(app,
                    T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70));
                status(app,safe==0?"DESVANECIDO OFF · 0 px":
                    "DESVANECIDO AJUSTADO: "+safe+
                    " px; misma ventana, sin recreación");
                return;
            }
            if(safe==0){
                remove();
                status(app,"DESVANECIDO OFF · 0 px");
                return;
            }
            // Jetour's vendor WindowManager is sensitive to windows
            // being inserted from a slider touch-up callback. Defer only
            // the first overlay creation, never replace it on dragging.
            if(queued!=null)MAIN.removeCallbacks(queued);
            queued=()->{queued=null;apply(app);};
            MAIN.postDelayed(queued,350L);
            status(app,"DESVANECIDO "+safe+
                " px guardado; preparando una sola capa para ambos paneles");
        });
    }
    static String last(Context c){
        return T1ClusterWazeRouter.prefs(c).getString(RESULT,
            "Desvanecido pendiente de comprobación en Display 1");
    }
    private static void status(Context c,String text){
        T1ClusterWazeRouter.prefs(c).edit().putString(RESULT,text).apply();
    }
    static void refresh(Context c){
        Context app=c.getApplicationContext();
        MAIN.post(()->{
            if(queued!=null){MAIN.removeCallbacks(queued);queued=null;}
            apply(app);
        });
    }
    static void hide(Context c){
        Context app=c.getApplicationContext();
        MAIN.post(()->{
            if(queued!=null){MAIN.removeCallbacks(queued);queued=null;}
            remove();
            status(app,"DESVANECIDO OFF: capa retirada del Display 1");
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
        attachedW=-1;
        attachedH=-1;
    }
    private static void apply(Context c){
        int edge=width(c);
        if(!T1ClusterWazeRouter.enabled(c)||
           !T1ClusterLayoutPolicy.savedDualSplit(c)||edge==0){
            remove();
            status(c,"DESVANECIDO OFF · clúster sin dos mapas o 0 px");
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
        int physicalWidth=d.getMode().getPhysicalWidth();
        int physicalHeight=d.getMode().getPhysicalHeight();
        if(physicalWidth<2||physicalHeight<1){
            remove();
            status(c,"DESVANECIDO no aplicado: tamaño físico desconocido");
            return;
        }
        try{
            if(view!=null&&manager!=null&&displayId==d.getDisplayId()&&
                attachedW==physicalWidth&&attachedH==physicalHeight){
                view.update(edge,ratio);
                status(c,"DESVANECIDO "+edge+
                    " px actualizado en la misma capa de Display 1");
                return;
            }
            remove();
            // Restore the original v0.3.42 WindowManager path, which
            // did not make the settings Activity disappear on this ROM.
            // Unlike MATCH_PARENT, use the full PHYSICAL 1920x720 size
            // to request both saved panels, never a freeform half-width.
            Context displayContext=c.createDisplayContext(d);
            WindowManager wm=(WindowManager)displayContext.getSystemService(
                Context.WINDOW_SERVICE);
            if(wm==null)throw new IllegalStateException(
                "WindowManager no disponible");
            FadeView overlay=new FadeView(displayContext,edge,ratio);
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(
                physicalWidth,physicalHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            p.gravity=Gravity.TOP|Gravity.LEFT;
            p.x=0;p.y=0;
            p.setTitle("DriveLab · un solo desvanecido para paneles A/B");
            wm.addView(overlay,p);
            manager=wm;
            view=overlay;
            displayId=d.getDisplayId();
            attachedW=physicalWidth;attachedH=physicalHeight;
            status(c,"DESVANECIDO "+edge+
                " px solicitado a ambos paneles con una sola ventana. "+
                "Pendiente de comprobar el resultado visual en la T1");
        }catch(Exception ex){
            remove();
            status(c,"DESVANECIDO NO APLICADO · "+
                ex.getClass().getSimpleName()+": "+
                String.valueOf(ex.getMessage()));
        }
    }
    private static final class FadeView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final int VEIL=0x99151a22;
        private int edge,ratio;
        FadeView(Context c,int px,int pct){
            super(c);
            edge=px;ratio=pct;
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        void update(int px,int pct){edge=px;ratio=pct;invalidate();}
        private void strip(Canvas canvas,float a,float b,
                           int c0,int c1){
            if(b<=a)return;
            paint.setShader(new LinearGradient(a,0,b,0,
                c0,c1,Shader.TileMode.CLAMP));
            canvas.drawRect(a,0,b,getHeight(),paint);
            paint.setShader(null);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            final int w=getWidth();
            if(w<=0||edge<=0)return;
            float seam=w*Math.max(30,Math.min(70,ratio))/100f;
            // Four independent gradients are painted in one single View:
            // left panel external, left panel seam, right panel seam,
            // right panel external. No half-display 240px ceiling.
            float left=Math.min(edge,seam/2f);
            float right=Math.min(edge,(w-seam)/2f);
            int clear=Color.TRANSPARENT;
            strip(canvas,0,left,VEIL,clear);
            strip(canvas,seam-left,seam,clear,VEIL);
            strip(canvas,seam,seam+right,VEIL,clear);
            strip(canvas,w-right,w,clear,VEIL);
        }
    }
}
