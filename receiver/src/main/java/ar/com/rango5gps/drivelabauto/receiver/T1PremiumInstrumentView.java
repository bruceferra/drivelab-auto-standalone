package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.util.Locale;

/** Vector, zero-image dashboard. No speed/SoC placeholders pretending to be OEM readings. */
public final class T1PremiumInstrumentView extends View {
    private static final int BG=0xFF0A1424,CARD=0xFF15263B,WHITE=0xFFF4FAFF,
        MUTED=0xFF93A9BC,TEAL=0xFF48DFD2,BLUE=0xFF67B9FF,GOLD=0xFFFFBF62;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Float speed,fuel,soc,rpm,currentA;private int powerMode=-1;
    private float animatedSpeed=0f;
    private boolean compactWithoutSpeed;
    public T1PremiumInstrumentView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    /** Preserve all OEM instrument cards without the large speedometer. */
    public void setCompactWithoutSpeed(boolean enabled){
        if(compactWithoutSpeed==enabled)return;
        compactWithoutSpeed=enabled;
        requestLayout();invalidate();
    }
    public void update(Double speed,Double fuel,Double soc,int powerMode,
                       Double rpm,Double currentA){
        this.speed=good(speed,0,220);this.fuel=good(fuel,0,70);
        this.soc=good(soc,0,100);
        this.rpm=good(rpm,0,12000);
        this.currentA=good(currentA,-1500,1500);
        this.powerMode=powerMode;
        invalidate();
    }
    private static Float good(Double n,double min,double max){
        return n!=null&&Double.isFinite(n)&&n>=min&&n<=max?n.floatValue():null;
    }
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=(int)(width*(compactWithoutSpeed?178f:466f)/1200f);
        setMeasuredDimension(width,resolveSize(height,h));
    }
    private void fill(int color){p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(color);}
    private void stroke(int color,float width){
        fill(color);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);
    }
    private void label(Canvas c,String s,float x,float y,int color,float size,boolean bold){
        fill(color);p.setTextAlign(Paint.Align.LEFT);p.setTextSize(size);
        p.setTypeface(android.graphics.Typeface.create(
            bold?"sans-serif-medium":"sans-serif",bold?1:0));
        c.drawText(s,x,y,p);
    }
    private void round(Canvas c,float left,float top,float right,float bottom,
                       float radius,int color){
        fill(color);c.drawRoundRect(left,top,right,bottom,radius,radius,p);
    }
    private void track(Canvas c,float l,float t,float r,float b,float frac,int color){
        round(c,l,t,r,b,8,0xFF294158);
        if(frac>0)round(c,l,t,l+(r-l)*Math.min(1f,frac),b,8,color);
    }
    private void speedGauge(Canvas c){
        float cx=216,cy=191,r=128;
        RectF arc=new RectF(cx-r,cy-r,cx+r,cy+r);
        stroke(0xFF2D4359,17);
        c.drawArc(arc,135,270,false,p);
        if(speed!=null){
            float target=Math.max(0,Math.min(200,speed));
            if(Math.abs(target-animatedSpeed)>0.15f){
                animatedSpeed+=(target-animatedSpeed)*0.24f;
                postInvalidateDelayed(24);
            }else animatedSpeed=target;
            p.setShader(new LinearGradient(cx-r,cy,cx+r,cy,TEAL,BLUE,
                Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(17);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(arc,135,270*(animatedSpeed/200),false,p);
            p.setShader(null);
            double angle=Math.toRadians(135+270*animatedSpeed/200);
            stroke(WHITE,4);
            c.drawLine(cx,cy,(float)(cx+82*Math.cos(angle)),
                (float)(cy+82*Math.sin(angle)),p);
            fill(TEAL);c.drawCircle(cx,cy,6,p);
        }
        for(int i=0;i<=10;i++){
            double angle=Math.toRadians(135+i*27);
            float x1=(float)(cx+102*Math.cos(angle));
            float y1=(float)(cy+102*Math.sin(angle));
            float x2=(float)(cx+(i%2==0?114:108)*Math.cos(angle));
            float y2=(float)(cy+(i%2==0?114:108)*Math.sin(angle));
            stroke(0xFFB0C5D5,i%2==0?2.5f:1.5f);
            c.drawLine(x1,y1,x2,y2,p);
        }
        fill(WHITE);p.setTypeface(android.graphics.Typeface.create(
            "sans-serif-light",0));p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(78);c.drawText(speed==null?"—":
            String.format(Locale.ROOT,"%.0f",speed),cx,cy+19,p);
        p.setTextSize(20);p.setColor(MUTED);c.drawText("km/h",cx,cy+52,p);
        p.setTypeface(android.graphics.Typeface.DEFAULT);
        p.setTextSize(15);c.drawText("VELOCIDAD OEM",cx,cy-67,p);
    }
    /** Lightweight real OEM status cards, without inventing a battery power kW. */
    private void engineAndBattery(Canvas c){
        round(c,430,342,792,449,19,CARD);
        round(c,806,342,1182,449,19,CARD);
        T1PowerFlowPolicy.Engine engine=T1PowerFlowPolicy.engine(
            rpm==null?Double.NaN:rpm);
        T1PowerFlowPolicy.Battery battery=T1PowerFlowPolicy.battery(
            currentA==null?Double.NaN:currentA);
        label(c,"MOTOR TÉRMICO · 1035",449,370,MUTED,16,false);
        int engineColor=engine==T1PowerFlowPolicy.Engine.ON?GOLD:
            engine==T1PowerFlowPolicy.Engine.OFF?TEAL:MUTED;
        fill(engineColor);c.drawCircle(460,399,9,p);
        label(c,T1PowerFlowPolicy.engineLabel(engine),482,408,engineColor,26,true);
        label(c,rpm==null?"RPM: SIN LECTURA":
            String.format(Locale.ROOT,"RPM: %.0f",rpm),449,437,WHITE,19,false);
        // Battery icon and pole. The fill displays SOC, the label displays
        // instantaneous current polarity (a provisional vehicle mapping).
        stroke(0xFFB9D5DE,3);
        c.drawRoundRect(830,384,896,423,8,8,p);
        round(c,897,395,905,412,2,0xFFB9D5DE);
        if(soc!=null){
            float filled=(62f*Math.max(0,Math.min(100,soc))/100f);
            round(c,833,387,833+filled,420,5,
                battery==T1PowerFlowPolicy.Battery.CHARGING?TEAL:BLUE);
        }
        label(c,"BATERÍA HV · 1064",920,372,MUTED,15,false);
        int flowColor=battery==T1PowerFlowPolicy.Battery.CHARGING?TEAL:
            battery==T1PowerFlowPolicy.Battery.DISCHARGING?GOLD:MUTED;
        label(c,T1PowerFlowPolicy.batteryLabel(battery),
            921,407,flowColor,23,true);
        label(c,currentA==null?"A: SIN LECTURA · ESTADO NO VALIDADO":
            String.format(Locale.forLanguageTag("es-AR"),"%.1f A · signo por validar",
                Math.abs(currentA)),921,435,MUTED,14,false);
    }
    /** Same instruments as the full display: fuel, SOC, thermal engine,
     * HV battery flow, and drive-mode readback. Only speedometer omitted.
     * True four-card layout (not stretching/cropping the old speed image).
     */
    private void compactInstruments(Canvas c){
        round(c,0,0,1200,178,18,BG);
        round(c,4,5,296,172,17,CARD);
        round(c,304,5,596,172,17,CARD);
        round(c,604,5,896,172,17,CARD);
        round(c,904,5,1196,172,17,CARD);

        label(c,"COMBUSTIBLE · CI186",18,35,MUTED,17,false);
        label(c,fuel==null?"SIN LECTURA":
            String.format(Locale.forLanguageTag("es-AR"),"%.2f L",fuel),
            18,98,WHITE,fuel==null?26:48,true);
        track(c,20,116,278,136,fuel==null?0:fuel/67,GOLD);
        label(c,"LITROS OEM INDICADOS",20,161,MUTED,14,false);

        label(c,"BATERÍA · SOC TABLERO",318,35,MUTED,17,false);
        label(c,soc==null?"SIN LECTURA":
            String.format(Locale.ROOT,"%.1f %%",soc),
            318,94,WHITE,soc==null?26:46,true);
        track(c,319,109,577,125,soc==null?0:soc/100,TEAL);
        int modeColor=powerMode==1?TEAL:powerMode==2?GOLD:BLUE;
        label(c,"MODO · "+T1ZonePolicy.name(powerMode),
            319,159,modeColor,18,true);

        T1PowerFlowPolicy.Engine engine=T1PowerFlowPolicy.engine(
            rpm==null?Double.NaN:rpm);
        int engineColor=engine==T1PowerFlowPolicy.Engine.ON?GOLD:
            engine==T1PowerFlowPolicy.Engine.OFF?TEAL:MUTED;
        label(c,"MOTOR TÉRMICO · 1035",618,35,MUTED,17,false);
        fill(engineColor);c.drawCircle(634,84,10,p);
        label(c,T1PowerFlowPolicy.engineLabel(engine),
            656,93,engineColor,29,true);
        label(c,rpm==null?"RPM: SIN LECTURA":
            String.format(Locale.ROOT,"RPM: %.0f",rpm),
            618,137,WHITE,20,false);
        label(c,"ESTADO POR RPM · EXP.",618,160,MUTED,14,false);

        T1PowerFlowPolicy.Battery battery=T1PowerFlowPolicy.battery(
            currentA==null?Double.NaN:currentA);
        int flowColor=battery==T1PowerFlowPolicy.Battery.CHARGING?TEAL:
            battery==T1PowerFlowPolicy.Battery.DISCHARGING?GOLD:MUTED;
        label(c,"BATERÍA HV · 1064",918,35,MUTED,17,false);
        stroke(0xFFB9D5DE,3);
        c.drawRoundRect(920,59,974,92,6,6,p);
        round(c,975,69,981,83,2,0xFFB9D5DE);
        if(soc!=null)round(c,923,62,923+48f*Math.max(0,
            Math.min(100,soc))/100f,89,3,
            battery==T1PowerFlowPolicy.Battery.CHARGING?TEAL:BLUE);
        label(c,T1PowerFlowPolicy.batteryLabel(battery),
            994,84,flowColor,19,true);
        label(c,currentA==null?"A: SIN LECTURA":
            String.format(Locale.forLanguageTag("es-AR"),
                "%.1f A · signo por validar",Math.abs(currentA)),
            918,126,WHITE,currentA==null?18:16,false);
        label(c,"FLUJO OEM EXPERIMENTAL",918,160,MUTED,14,false);
    }
    @Override protected void onDraw(Canvas real){
        super.onDraw(real);
        Canvas c=real;float ratio=getWidth()/1200f;
        c.save();c.scale(ratio,ratio);
        if(compactWithoutSpeed){
            compactInstruments(c);
            c.restore();
            return;
        }
        round(c,0,0,1200,466,30,BG);
        round(c,430,55,1182,178,20,CARD);
        round(c,430,192,1182,325,20,CARD);
        label(c,"D R I V E L A B     /     J E T O U R  T 1",445,32,
            0xFFD7E6F1,18,true);
        speedGauge(c);
        label(c,"COMBUSTIBLE · CI186",457,90,MUTED,17,false);
        label(c,fuel==null?"SIN LECTURA":
            String.format(Locale.forLanguageTag("es-AR"),"%.2f L",fuel),
            457,139,WHITE,39,true);
        label(c,"LITROS INDICADOS",750,94,MUTED,14,false);
        track(c,753,115,1140,132,fuel==null?0:fuel/67,GOLD);
        label(c,"BATERÍA · SOC TABLERO",457,227,MUTED,17,false);
        label(c,soc==null?"SIN LECTURA":
            String.format(Locale.ROOT,"%.1f %%",soc),
            457,284,WHITE,42,true);
        label(c,"PROPULSIÓN",750,226,MUTED,14,false);
        int modeColor=powerMode==1?TEAL:powerMode==2?GOLD:BLUE;
        round(c,752,245,913,291,14,0xFF243F54);
        label(c,T1ZonePolicy.name(powerMode),768,278,modeColor,25,true);
        label(c,"Lectura 1043",944,276,MUTED,16,false);
        engineAndBattery(c);
        c.restore();
    }
}
