package ar.com.rango5gps.drivelabauto.receiver;

import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;

/** Visible drag readiness and drop-target indication; never changes OEM controls. */
final class T1DragCue {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final int READY=0xFF23B7AC;
    private static final int TARGET=0xFFFFC857;
    private T1DragCue(){}

    static void attach(View card,String group){
        final Runnable[] pending={null};
        card.setLongClickable(true);
        card.setOnLongClickListener(v->{
            // Android's long-press threshold (~500 ms) plus 4500 ms = ~5 s.
            Runnable start=()->{
                pending[0]=null;
                v.setAlpha(0.72f);
                v.setForeground(outline(READY,5));
                boolean started=v.startDragAndDrop(
                    ClipData.newPlainText("drivelab-reorder",group),
                    new View.DragShadowBuilder(v),v,0);
                if(!started)reset(v);
            };
            pending[0]=start;
            MAIN.postDelayed(start,4500);
            return true;
        });
        card.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_UP||
               event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                if(pending[0]!=null){
                    MAIN.removeCallbacks(pending[0]);
                    pending[0]=null;
                }
            }
            return false;
        });
    }

    static void target(View v,boolean active){
        if(active){
            v.setForeground(dropLine());
            v.setAlpha(0.88f);
        }else reset(v);
    }
    static void reset(View v){
        v.setForeground(null);
        v.setAlpha(1f);
    }
    private static Drawable dropLine(){
        return new Drawable(){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas c){
                paint.setColor(TARGET);
                paint.setStrokeWidth(8f);
                c.drawLine(4f,4f,4f,getBounds().height()-4f,paint);
                paint.setStrokeWidth(3f);
                c.drawLine(4f,4f,getBounds().width()-4f,4f,paint);
            }
            @Override public void setAlpha(int a){paint.setAlpha(a);}
            @Override public void setColorFilter(android.graphics.ColorFilter f){
                paint.setColorFilter(f);
            }
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    private static GradientDrawable outline(int color,int width){
        GradientDrawable d=new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setStroke(width,color);
        d.setCornerRadius(12f);
        return d;
    }
}
