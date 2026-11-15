package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** On-device diagram only; it never draws over real cluster instruments. */
final class T1ClusterLayoutPreview extends View {
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String mode="FULL";
    private int split=50,width=70,height=70,x=0,y=0;
    private String first="Waze",second="Google Maps";
    T1ClusterLayoutPreview(Context c){
        super(c);setMinimumHeight(160);
    }
    void update(String layout,int ratio,int widthPct,int heightPct,
                int leftPct,int topPct,String app,String secondApp){
        mode=layout;split=ratio;width=widthPct;height=heightPct;
        x=leftPct;y=topPct;first=app;second=secondApp;
        invalidate();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec);
        int h=Math.max(156,Math.min(230,w*720/1920+80));
        setMeasuredDimension(w,resolveSize(h,heightSpec));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float left=18,top=14,right=getWidth()-18,bottom=getHeight()-18;
        pen.setColor(0xFF122331);pen.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(left,top,right,bottom),10,10,pen);
        pen.setColor(0xFF6C96A5);pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(2f);
        canvas.drawRoundRect(new RectF(left,top,right,bottom),10,10,pen);
        pen.setStyle(Paint.Style.FILL);
        int pxW=1920,pxH=720;
        float cw=right-left,ch=bottom-top;
        if("SPLIT".equals(mode)){
            area(canvas,left,top,cw,ch,
                T1ClusterLayoutMath.left(pxW,pxH,split),pxW,pxH,
                0xFF0F756F,"Waze");
            area(canvas,left,top,cw,ch,
                T1ClusterLayoutMath.right(pxW,pxH,split),pxW,pxH,
                0xFF1E557E,second);
        }else if("WINDOW".equals(mode)){
            area(canvas,left,top,cw,ch,
                T1ClusterLayoutMath.window(pxW,pxH,width,height,x,y),
                pxW,pxH,0xFF0F756F,first);
        }else{
            area(canvas,left,top,cw,ch,
                new T1ClusterLayoutMath.Frame(0,0,pxW,pxH),
                pxW,pxH,0xFF0F756F,first);
        }
    }
    private void area(Canvas c,float lx,float ty,float w,float h,
            T1ClusterLayoutMath.Frame f,int sw,int sh,int color,String label){
        float a=lx+w*f.left/sw,b=ty+h*f.top/sh;
        float z=lx+w*f.right/sw,t=ty+h*f.bottom/sh;
        pen.setColor(color);
        c.drawRoundRect(new RectF(a+1,b+1,z-1,t-1),5,5,pen);
        pen.setColor(Color.WHITE);pen.setTextSize(13f);
        String title=label==null?"App":label;
        if(title.length()>18)title=title.substring(0,16)+"…";
        c.drawText(title,a+9,Math.min(t-8,b+22),pen);
    }
}