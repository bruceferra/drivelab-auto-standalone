package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.Locale;

/**
 * Live animated custom view for the T1 center screen / T1Launcher projection.
 * OEM speed, CI186 and SOC are represented without claiming OEM cluster takeover.
 */
public final class T1InstrumentView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Float speed=null,fuel=null,soc=null;
    private float shownSpeed=0,shownFuel=0,shownSoc=0;
    private static final int NAVY=0xFF102231,BLUE=0xFF57B8F4,
        TEAL=0xFF35D5C3,GOLD=0xFFFFBF64,MUTED=0xFF647F95,WHITE=Color.WHITE;
    public T1InstrumentView(Context context){super(context);}
    public void setTelemetry(Double speed,Double oemFuel,Double batterySoc){
        this.speed=finite(speed,0,220);
        this.fuel=finite(oemFuel,0,70);
        this.soc=finite(batterySoc,0,100);
        // Keep needle smooth on successive real OEM samples; never fabricate
        // unavailable readings or interpolate across a missing source.
        if(this.speed!=null)shownSpeed=this.speed;
        if(this.fuel!=null)shownFuel=this.fuel;
        if(this.soc!=null)shownSoc=this.soc;
        invalidate();
    }
    private static Float finite(Double v,double min,double max){
        return v!=null&&Double.isFinite(v)&&v>=min&&v<=max?v.floatValue():null;
    }
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w);
        int height=(int)(width/2.45f);
        setMeasuredDimension(width,resolveSize(height,h));
    }
    private void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);}
    private void stroke(int color,float width){
        p.setColor(color);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);
    }
    private void text(Canvas c,String t,float x,float y,float size,int color){
        fill(color);p.setTypeface(android.graphics.Typeface.create(
            "sans-serif",android.graphics.Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size);c.drawText(t,x,y,p);
    }
    private void dial(Canvas c,float cx,float cy,float radius,String label,
                      Float actual,float drawn,float min,float max,int color,
                      String suffix,String detail){
        stroke(0xFF29465A,19);
        RectF r=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
        c.drawArc(r,145,250,false,p);
        if(actual!=null){
            float f=Math.max(0,Math.min(1,(drawn-min)/(max-min)));
            stroke(color,19);c.drawArc(r,145,250*f,false,p);
            double a=Math.toRadians(145+250*f);
            stroke(WHITE,5);
            float length=radius*0.73f;
            c.drawLine(cx,cy,(float)(cx+length*Math.cos(a)),
                (float)(cy+length*Math.sin(a)),p);
            fill(color);c.drawCircle(cx,cy,8,p);
        }
        text(c,label,cx,cy-radius-21,23,0xFFD3E7F1);
        text(c,actual==null?"--":String.format(Locale.US,"%.0f",actual),
            cx,cy+31,58,actual==null?MUTED:WHITE);
        text(c,suffix,cx,cy+64,20,color);
        text(c,detail,cx,cy+radius+27,18,MUTED);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float scale=getWidth()/1200f;
        canvas.save();canvas.scale(scale,scale);
        fill(NAVY);canvas.drawRoundRect(0,0,1200,490,32,32,p);
        text(canvas,"DRIVELAB  ·  JETOUR T1",600,40,29,WHITE);
        dial(canvas,220,255,145,"VELOCIDAD OEM",speed,shownSpeed,0,200,BLUE,
            "km/h · señal OEM","SIN SENSOR = --");
        dial(canvas,600,255,145,"NAFTA · CI186",fuel,shownFuel,0,67,GOLD,
            "L indicados","No son litros físicos");
        dial(canvas,980,255,145,"BATERÍA PHEV",soc,shownSoc,0,100,TEAL,
            "% SOC OEM","No implica kWh de red");
        text(canvas,"Los tres indicadores requieren datos reales; sin lectura muestran --",
            600,475,16,MUTED);
        canvas.restore();
    }
}
