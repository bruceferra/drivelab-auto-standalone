package ar.com.rango5gps.drivelabauto.receiver;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/** Shared tactile 3D appearance for all Receiver menus, not vehicle commands. */
final class Receiver3DButtons {
    private Receiver3DButtons(){}
    static final String QUICK_TAG="receiver_quick_3d";
    private static final int DEFAULT=0xFF176B99;
    private static int mix(int a,int b,float f){
        return Color.rgb((int)(Color.red(a)*(1-f)+Color.red(b)*f),
            (int)(Color.green(a)*(1-f)+Color.green(b)*f),
            (int)(Color.blue(a)*(1-f)+Color.blue(b)*f));
    }
    private static GradientDrawable face(int color,boolean down,boolean selected){
        int hi=down?mix(color,Color.BLACK,.35f):
            selected?mix(color,Color.BLACK,.16f):mix(color,Color.WHITE,.21f);
        int lo=down?mix(color,Color.BLACK,.10f):
            selected?mix(color,Color.BLACK,.31f):mix(color,Color.BLACK,.22f);
        GradientDrawable d=new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,new int[]{hi,lo});
        d.setCornerRadius(16);
        d.setStroke(selected?5:2,selected?0xFFFFD46A:0xFFD9E8F2);
        return d;
    }
    static void apply(View root){
        if(root instanceof Button) {
            Button b=(Button)root;
            if(QUICK_TAG.equals(b.getTag()))return;
            int base=DEFAULT;
            ColorStateList tint=b.getBackgroundTintList();
            if(tint!=null)base=tint.getDefaultColor();
            boolean active=b.isSelected()||b.isActivated();
            b.setSelected(active);
            StateListDrawable states=new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed},
                face(base,true,active));
            states.addState(new int[]{android.R.attr.state_selected},
                face(base,false,true));
            states.addState(new int[]{},face(base,false,false));
            b.setBackgroundTintList(null);
            b.setBackground(states);
            b.setTextColor(Color.WHITE);
            b.setAllCaps(false);
            b.setElevation(active?2f:7f);
            b.setTranslationY(active?2f:0f);
            b.setOnTouchListener((view,event)->{
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        b.setElevation(1f);b.setTranslationY(3f);
                        b.setScaleX(.985f);b.setScaleY(.96f);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        b.setElevation(b.isSelected()?2f:7f);
                        b.setTranslationY(b.isSelected()?2f:0f);
                        b.setScaleX(1f);b.setScaleY(1f);
                        break;
                    default:break;
                }
                return false; // leave clicks, accessibility and actions alone
            });
        } else if(root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i));
        }
    }
}
