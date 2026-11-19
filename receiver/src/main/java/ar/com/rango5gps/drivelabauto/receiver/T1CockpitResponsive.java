package ar.com.rango5gps.drivelabauto.receiver;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Reflow the EXISTING Cockpit controls when Android gives the same activity
 * 70%, 60%, 50%, or 33% of Display 0. Do not recreate activities, reset
 * trips, affect OEM controls, or modify the permanently pinned top toolbar.
 *
 * All original views and listeners are reused. Row order is captured AFTER
 * DriveLab's persisted card reordering is applied; switching back to a wide
 * window restores the original direct-child arrangement.
 */
final class T1CockpitResponsive {
    private static final class Group {
        final LinearLayout holder;
        final ArrayList<View> items=new ArrayList<>();
        final ArrayList<LinearLayout.LayoutParams> params=new ArrayList<>();
        final int wide,medium,narrow;
        Group(LinearLayout holder,int wide,int medium,int narrow){
            this.holder=holder;this.wide=wide;this.medium=medium;
            this.narrow=narrow;
            for(int i=0;i<holder.getChildCount();i++){
                View child=holder.getChildAt(i);
                items.add(child);
                params.add(new LinearLayout.LayoutParams(
                    (LinearLayout.LayoutParams)child.getLayoutParams()));
            }
        }
        void reflow(int mode,int gapPx){
            holder.removeAllViews();
            int columns=T1CockpitResponsivePolicy.columns(
                mode,wide,medium,narrow);
            if(columns>=items.size()){
                holder.setOrientation(LinearLayout.HORIZONTAL);
                for(int i=0;i<items.size();i++){
                    View child=items.get(i);
                    holder.addView(child,new LinearLayout.LayoutParams(
                        params.get(i)));
                }
                return;
            }
            holder.setOrientation(LinearLayout.VERTICAL);
            LinearLayout line=null;
            for(int i=0;i<items.size();i++){
                if(i%columns==0){
                    line=new LinearLayout(holder.getContext());
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    holder.addView(line,new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                View child=items.get(i);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
                lp.setMargins(gapPx,gapPx,gapPx,gapPx);
                line.addView(child,lp);
            }
        }
    }
    private final ArrayList<Group> groups=new ArrayList<>();
    private final IdentityHashMap<TextView,Float> naturalTextPx=
        new IdentityHashMap<>();
    private final ScrollView viewport;
    private int lastMode=-1;
    T1CockpitResponsive(ScrollView viewport){
        this.viewport=viewport;
    }
    void register(LinearLayout row,int wide,int medium,int narrow){
        if(row!=null&&row.getChildCount()>1)
            groups.add(new Group(row,wide,medium,narrow));
    }
    void attach(){
        viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->
            adapt(r-l));
        viewport.post(()->adapt(viewport.getWidth()));
    }
    private void adapt(int widthPx){
        if(widthPx<=0)return;
        float density=viewport.getResources().getDisplayMetrics().density;
        if(density<=0)return;
        int widthDp=Math.round(widthPx/density);
        int mode=T1CockpitResponsivePolicy.mode(widthDp);
        if(mode==lastMode)return;
        lastMode=mode;
        int gap=Math.max(1,Math.round(2*density));
        for(Group group:groups)group.reflow(mode,gap);
        float factor=T1CockpitResponsivePolicy.fontScale(mode);
        scaleText(viewport,factor);
    }
    private void scaleText(View view,float scale){
        if(view instanceof TextView){
            TextView text=(TextView)view;
            Float base=naturalTextPx.get(text);
            if(base==null){
                base=text.getTextSize();
                naturalTextPx.put(text,base);
            }
            // Small labels remain readable. Do not shrink touch targets or
            // change widget minimum height, click bindings or OEM semantics.
            float min=TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,9f,
                text.getResources().getDisplayMetrics());
            float newSize=Math.max(min,base*scale);
            if(Math.abs(text.getTextSize()-newSize)>0.5f)
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX,newSize);
            if(modeIsNarrow()){
                text.setMaxLines(text instanceof android.widget.Button?3:6);
            }
        }
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)
                scaleText(group.getChildAt(i),scale);
        }
    }
    private boolean modeIsNarrow(){
        return lastMode>=T1CockpitResponsivePolicy.NARROW;
    }
}
