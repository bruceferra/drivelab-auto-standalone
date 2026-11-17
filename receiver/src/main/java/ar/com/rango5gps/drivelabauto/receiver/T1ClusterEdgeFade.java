package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Two INDEPENDENT, non-touchable window-context overlays, one above each
 * saved FREEFORM pane of Display 1. The older single MATCH_PARENT overlay
 * could be confined to one freeform window by Jetour's window manager.
 * Explicit per-panel overlays and Android 11 createWindowContext avoid
 * inheriting the calling Activity's half-screen window bounds.
 *
 * Decoration only. Gradient is a translucent dark mask, NOT alpha control
 * of Waze/Maps or an x-ray to the OEM instrument layer underneath.
 * No focus, key, accessibility, task, speed, or vehicle operations.
 */
final class T1ClusterEdgeFade {
    static final String WIDTH_PX="cluster_edge_fade_width_px_v1";
    static final String RESULT="cluster_edge_fade_result_v1";
    static final int MAX_PX=300;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final int DEFAULT_PX=60;

    private static WindowManager manager;
    private static PanelFadeView leftView,rightView;
    private static int displayId=-1,appliedRatio=-1;
    private T1ClusterEdgeFade(){}

    static int width(Context c){
        int stored=T1ClusterWazeRouter.prefs(c).getInt(WIDTH_PX,DEFAULT_PX);
        return Math.max(0,Math.min(MAX_PX,stored));
    }
    static void setWidth(Context c,int pixels){
        int safe=Math.max(0,Math.min(MAX_PX,pixels));
        T1ClusterWazeRouter.prefs(c).edit().putInt(WIDTH_PX,safe).apply();
        refresh(c);
    }
    private static void status(Context c,String message){
        T1ClusterWazeRouter.prefs(c).edit().putString(RESULT,message).apply();
    }
    static String last(Context c){
        return T1ClusterWazeRouter.prefs(c).getString(RESULT,
            "Desvanecido en ambos paneles todavía sin prueba física");
    }
    static void refresh(Context c){
        Context app=c.getApplicationContext();
        MAIN.post(()->apply(app));
    }
    static void hide(Context c){
        Context app=c.getApplicationContext();
        MAIN.post(()->{
            remove();
            status(app,"DESVANECIDO OFF: ambos paneles liberados");
        });
    }
    private static void remove(){
        if(manager!=null){
            if(leftView!=null){
                try{manager.removeViewImmediate(leftView);}
                catch(RuntimeException ignored){}
            }
            if(rightView!=null){
                try{manager.removeViewImmediate(rightView);}
                catch(RuntimeException ignored){}
            }
        }
        leftView=null;
        rightView=null;
        manager=null;
        displayId=-1;
        appliedRatio=-1;
    }
    private static WindowManager.LayoutParams params(int width,int height,
                                                     int x,String label){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            width,height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;
        p.x=x;
        p.y=0;
        p.setTitle(label);
        return p;
    }
    private static void apply(Context c){
        int edge=width(c);
        if(!T1ClusterWazeRouter.enabled(c)||
           !T1ClusterLayoutPolicy.savedDualSplit(c)||edge==0){
            remove();
            status(c,"DESVANECIDO OFF · 0 px o clúster sin dos paneles");
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
            status(c,"DESVANECIDO no aplicado: falta permiso superposición");
            return;
        }
        int ratio=T1ClusterLayoutPolicy.number(c,
            T1ClusterLayoutPolicy.SPLIT_PERCENT,50,30,70);
        int physicalWidth=d.getMode().getPhysicalWidth();
        int physicalHeight=d.getMode().getPhysicalHeight();
        int leftWidth=Math.round(physicalWidth*ratio/100f);
        int rightWidth=physicalWidth-leftWidth;
        if(leftWidth<=0||rightWidth<=0||physicalHeight<=0){
            remove();
            status(c,"DESVANECIDO no aplicado: dimensiones inválidas");
            return;
        }
        try{
            if(leftView!=null&&rightView!=null&&manager!=null&&
               displayId==d.getDisplayId()&&appliedRatio==ratio){
                leftView.update(edge);
                rightView.update(edge);
                status(c,"DESVANECIDO "+edge+
                    " px solicitado en AMBOS paneles de Display 1 "+
                    "(máscara visual, no transparencia OEM)");
                return;
            }
            remove();
            // Android 11's window context is tied to the PHYSICAL display
            // overlay layer, not to DriveLab's current freeform Activity
            // window. Both explicit overlay windows have independent bounds.
            Context windowContext=Build.VERSION.SDK_INT>=30?
                c.createWindowContext(d,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null):
                c.createDisplayContext(d);
            WindowManager wm=(WindowManager)windowContext.getSystemService(
                Context.WINDOW_SERVICE);
            if(wm==null)throw new IllegalStateException(
                "WindowManager no disponible");
            manager=wm;
            leftView=new PanelFadeView(windowContext,edge);
            wm.addView(leftView,params(leftWidth,physicalHeight,0,
                "DriveLab desvanecido · panel A"));
            rightView=new PanelFadeView(windowContext,edge);
            wm.addView(rightView,params(rightWidth,physicalHeight,leftWidth,
                "DriveLab desvanecido · panel B"));
            displayId=d.getDisplayId();
            appliedRatio=ratio;
            status(c,"DESVANECIDO "+edge+
                " px solicitado en panel A y panel B de Display 1. "+
                "Confirmá visualmente ambos lados; Android puede "+
                "mantener superficies OEM encima.");
        }catch(Exception ex){
            remove();
            status(c,"DESVANECIDO no aplicado: "+
                ex.getClass().getSimpleName()+" · "+
                String.valueOf(ex.getMessage()));
        }
    }

    private static final class PanelFadeView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        // Keep the gradient subtle, including at 300 px.
        private static final int VEIL=0x99151a22;
        private int widthPx;
        PanelFadeView(Context c,int px){
            super(c);
            widthPx=px;
            setBackgroundColor(Color.TRANSPARENT);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setClickable(false);
            setFocusable(false);
        }
        void update(int px){widthPx=px;invalidate();}
        private void strip(Canvas canvas,float start,float end,
                           int color0,int color1){
            if(end<=start)return;
            paint.setShader(new LinearGradient(start,0,end,0,
                color0,color1,Shader.TileMode.CLAMP));
            canvas.drawRect(start,0,end,getHeight(),paint);
            paint.setShader(null);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int panelWidth=getWidth();
            // Prior version capped at fullDisplayWidth/8: a 300px setting
            // would visibly stop changing at 240px on a 1920px cluster.
            int e=Math.min(widthPx,Math.max(0,panelWidth/2));
            if(e==0)return;
            int clear=Color.TRANSPARENT;
            // Draw BOTH edges in EACH panel. Thus the outside edges
            // and the two sides of the central seam all fade.
            strip(canvas,0,e,VEIL,clear);
            strip(canvas,panelWidth-e,panelWidth,clear,VEIL);
        }
    }
}
