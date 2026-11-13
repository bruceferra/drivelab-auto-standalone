package ar.com.rango5gps.drivelabauto.receiver;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.view.View;
import android.view.ViewGroup;
import java.util.Locale;

/**
 * Jetour T1-inspired original UI primitives, derived from vehicle screen photos.
 * No copyrighted Jetour icons, typeface, raster images, OEM processes or assets.
 * Drawables are lightweight local Android vector strokes; no dependencies.
 */
final class T1JetourTheme {
    static final int NIGHT=0xFF080F20;
    static final int DEEP=0xFF0B192D;
    static final int PANEL=0xFF14283E;
    static final int PANEL_HI=0xFF1A3651;
    static final int CYAN=0xFF65E6EC;
    static final int TURQUOISE=0xFF28CFDC;
    static final int GREEN=0xFF86E85D;
    static final int MUTED=0xFFBBD3E3;
    static final int WHITE=0xFFF6FBFF;
    static final int RED=0xFFEF686D;
    static final int AMBER=0xFFFFC66C;
    static final int BORDER=0xFF39607B;
    private T1JetourTheme(){}

    static int dp(Context c,float value){
        return Math.round(value*c.getResources().getDisplayMetrics().density);
    }
    private static int mix(int a,int b,float ratio){
        return Color.rgb(Math.round(Color.red(a)*(1-ratio)+Color.red(b)*ratio),
            Math.round(Color.green(a)*(1-ratio)+Color.green(b)*ratio),
            Math.round(Color.blue(a)*(1-ratio)+Color.blue(b)*ratio));
    }
    static GradientDrawable backdrop(){
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{NIGHT,DEEP,0xFF0D2640});
    }
    static GradientDrawable surface(int color,int radius){
        GradientDrawable d=new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{mix(color,Color.WHITE,.09f),color,
                mix(color,Color.BLACK,.12f)});
        d.setCornerRadius(radius);
        d.setStroke(1,mix(BORDER,color,.28f));
        return d;
    }
    static Drawable buttonBackground(int color){
        GradientDrawable fill=surface(color,12);
        return new RippleDrawable(ColorStateList.valueOf(0x4488F6FF),fill,null);
    }
    static void button(Button b,int color){
        b.setBackgroundTintList(null);
        b.setBackground(buttonBackground(color));
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(b.getContext(),44));
        b.setPadding(dp(b.getContext(),12),dp(b.getContext(),7),
            dp(b.getContext(),12),dp(b.getContext(),7));
        b.setCompoundDrawablePadding(dp(b.getContext(),8));
        String name=iconName(b.getText()==null?"":b.getText().toString());
        if(name!=null){
            Icon icon=new Icon(name,CYAN);
            int size=dp(b.getContext(),19);
            icon.setBounds(0,0,size,size);
            b.setCompoundDrawables(icon,null,null,null);
        }
    }
    static void number(EditText e){
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setBackground(surface(PANEL_HI,10));
        e.setPadding(dp(e.getContext(),10),dp(e.getContext(),8),
            dp(e.getContext(),10),dp(e.getContext(),8));
    }
    static void spinner(Spinner s,String[] choices){
        Context c=s.getContext();
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(
            c,android.R.layout.simple_spinner_item,choices){
            @Override public View getView(int position,View convert,ViewGroup parent){
                TextView t=(TextView)super.getView(position,convert,parent);
                t.setText(choices[position]+"  ▾");
                t.setTextColor(WHITE);
                t.setTextSize(15);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));
                t.setBackground(surface(PANEL_HI,10));
                t.setMinHeight(dp(c,45));
                return t;
            }
            @Override public View getDropDownView(int position,View convert,
                                                   ViewGroup parent){
                TextView t=(TextView)super.getDropDownView(position,convert,parent);
                t.setTextColor(WHITE);t.setTextSize(16);
                t.setSingleLine(false);
                t.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));
                t.setMinHeight(dp(c,48));
                t.setBackgroundColor(position%2==0?PANEL_HI:PANEL);
                return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setPopupBackgroundDrawable(surface(PANEL,12));
    }
    static void title(TextView t){
        t.setTextColor(WHITE);
        t.setLetterSpacing(.035f);
    }
    private static String iconName(String value){
        String v=value.toUpperCase(Locale.ROOT);
        if(v.contains("MICRÓFONO")||v.contains("DESTINO")||
           v.contains("HABLAR"))return "mic";
        if(v.contains("PENDIENT")||v.contains("VALIDAC")||
           v.contains("PRUEBA"))return "list";
        if(v.contains("COCKPIT")||v.contains("TABLERO")||
           v.contains("SIMULADOR"))return "gauge";
        if(v.contains("VEHÍCULO")||v.contains("ESPEJO")||
           v.contains("MODO OPTIMIZADO"))return "car";
        if(v.contains("ENERGÍA")||v.contains("SOC")||
           v.contains("EV MAX"))return "battery";
        if(v.contains("VOLANTE"))return "steer";
        if(v.contains("DIAGN")||v.contains("CODE"))return "pulse";
        if(v.contains("ACTUALIZAR"))return "update";
        return null;
    }
    static final class Icon extends Drawable{
        private final String type;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(String name,int tint){
            type=name;paint.setColor(tint);
            paint.setStrokeWidth(1.8f);paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();
            canvas.save();
            canvas.translate(b.left,b.top);
            canvas.scale(b.width()/24f,b.height()/24f);
            switch(type){
                case "mic":
                    canvas.drawRoundRect(9,3,15,15,3,3,paint);
                    canvas.drawArc(6,7,18,19,0,180,false,paint);
                    canvas.drawLine(12,19,12,22,paint);
                    canvas.drawLine(9,22,15,22,paint);break;
                case "list":
                    for(int y=6;y<=18;y+=6){
                        canvas.drawCircle(5,y,1,paint);
                        canvas.drawLine(9,y,21,y,paint);
                    }
                    break;
                case "gauge":
                    canvas.drawArc(3,3,21,21,180,180,false,paint);
                    canvas.drawLine(12,12,18,8,paint);
                    canvas.drawCircle(12,12,1,paint);
                    canvas.drawLine(4,19,20,19,paint);break;
                case "steer":
                    canvas.drawCircle(12,12,9,paint);
                    canvas.drawCircle(12,13,2,paint);
                    canvas.drawLine(4,10,10,12,paint);
                    canvas.drawLine(20,10,14,12,paint);
                    canvas.drawLine(12,15,12,20,paint);break;
                case "battery":
                    canvas.drawRoundRect(3,6,19,18,2,2,paint);
                    canvas.drawLine(21,10,21,14,paint);
                    canvas.drawLine(7,12,10,12,paint);
                    canvas.drawLine(13,9,13,15,paint);
                    canvas.drawLine(10,12,16,12,paint);break;
                case "car":
                    Path p=new Path();
                    p.moveTo(3,15);p.lineTo(5,10);p.lineTo(8,7);
                    p.lineTo(16,7);p.lineTo(19,10);p.lineTo(21,15);
                    p.lineTo(21,19);p.lineTo(3,19);p.close();
                    canvas.drawPath(p,paint);
                    canvas.drawLine(5,12,19,12,paint);
                    canvas.drawCircle(7,19,1.7f,paint);
                    canvas.drawCircle(17,19,1.7f,paint);break;
                case "pulse":
                    Path wave=new Path();
                    wave.moveTo(2,12);wave.lineTo(7,12);
                    wave.lineTo(10,5);wave.lineTo(14,19);
                    wave.lineTo(17,12);wave.lineTo(22,12);
                    canvas.drawPath(wave,paint);break;
                case "update":
                    canvas.drawArc(4,4,20,20,65,290,false,paint);
                    canvas.drawLine(19,6,20,11,paint);
                    canvas.drawLine(20,11,15,10,paint);break;
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){
            paint.setColorFilter(filter);invalidateSelf();
        }
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return 24;}
        @Override public int getIntrinsicHeight(){return 24;}
    }
}