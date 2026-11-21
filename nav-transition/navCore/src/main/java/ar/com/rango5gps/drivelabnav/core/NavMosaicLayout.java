package ar.com.rango5gps.drivelabnav.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolution-independent layout geometry for NAV on Jetour central/cluster displays.
 * No Android, OEM or ADB operations. Never infers that a window actually appeared.
 *
 * Tiles are ordered: primary navigation, secondary navigation, optional media.
 * Insets and density are provided by the real display/window, not hardcoded.
 */
public final class NavMosaicLayout {
    public enum Mode { SINGLE, DUAL, THREE_COLUMNS, MAIN_STACK, MEDIA_RAIL }
    public enum Axis { HORIZONTAL, VERTICAL }

    public static final class Rect {
        public final int left, top, right, bottom;
        public Rect(int left, int top, int right, int bottom) {
            if (right <= left || bottom <= top) throw new IllegalArgumentException("Empty tile");
            this.left=left; this.top=top; this.right=right; this.bottom=bottom;
        }
        public int width() { return right-left; }
        public int height() { return bottom-top; }
        public boolean intersects(Rect other) {
            return other!=null && left<other.right && right>other.left &&
                top<other.bottom && bottom>other.top;
        }
        @Override public String toString() {
            return left+","+top+" - "+right+","+bottom;
        }
    }

    public static final class Request {
        public final int width, height, safeLeft, safeTop, safeRight, safeBottom;
        public final float density;
        public final Mode mode;
        public final int primaryPercent;
        public final boolean reversed;
        public Request(int width,int height,int safeLeft,int safeTop,
                       int safeRight,int safeBottom,float density,Mode mode,
                       int primaryPercent,boolean reversed) {
            this.width=width; this.height=height;
            this.safeLeft=safeLeft; this.safeTop=safeTop;
            this.safeRight=safeRight; this.safeBottom=safeBottom;
            this.density=density; this.mode=mode; this.primaryPercent=primaryPercent;
            this.reversed=reversed;
        }
    }

    public static final class Plan {
        public final Rect safeArea;
        public final List<Rect> tiles;
        public final Axis axis;
        public final int gapPx;
        public final Mode mode;
        private Plan(Rect safeArea,List<Rect> tiles,Axis axis,int gapPx,Mode mode) {
            this.safeArea=safeArea; this.tiles=Collections.unmodifiableList(tiles);
            this.axis=axis; this.gapPx=gapPx; this.mode=mode;
        }
    }

    private NavMosaicLayout() {}
    private static int clamp(int value,int min,int max) {
        return Math.max(min,Math.min(max,value));
    }
    private static int divider(int length,int gap,int percent,float density) {
        int room=length-gap;
        // Each selected pane retains a reasonable share on small displays.
        int min=clamp(Math.round(148f*density),1,Math.max(1,room/3));
        return clamp(Math.round(room*clamp(percent,10,90)/100f),min,room-min);
    }
    public static Plan calculate(Request req) {
        if (req==null || req.mode==null || !Float.isFinite(req.density) ||
            req.density<0.5f || req.density>6f || req.width<240 || req.height<240 ||
            req.width>10000 || req.height>10000 ||
            req.safeLeft<0 || req.safeTop<0 || req.safeRight<0 || req.safeBottom<0)
            throw new IllegalArgumentException("Invalid display or insets");
        final int left=req.safeLeft, top=req.safeTop;
        final int right=req.width-req.safeRight, bottom=req.height-req.safeBottom;
        if (right-left<200 || bottom-top<200)
            throw new IllegalArgumentException("Insufficient safe content area");
        Rect safe=new Rect(left,top,right,bottom);
        int w=safe.width(),h=safe.height();
        boolean wide=w>=h*1.14f;
        Axis axis=wide?Axis.HORIZONTAL:Axis.VERTICAL;
        int gap=clamp(Math.round(req.density*8f),0,Math.max(1,Math.min(w,h)/40));
        List<Rect> tiles=new ArrayList<>();
        if (req.mode==Mode.SINGLE) {
            tiles.add(safe);
        } else if (req.mode==Mode.DUAL || req.mode==Mode.MEDIA_RAIL) {
            int pct=req.mode==Mode.MEDIA_RAIL?clamp(req.primaryPercent,62,82):
                req.primaryPercent;
            if (wide) {
                int first=divider(w,gap,pct,req.density);
                tiles.add(new Rect(left,top,left+first,bottom));
                tiles.add(new Rect(left+first+gap,top,right,bottom));
            } else {
                int first=divider(h,gap,pct,req.density);
                tiles.add(new Rect(left,top,right,top+first));
                tiles.add(new Rect(left,top+first+gap,right,bottom));
            }
        } else if (req.mode==Mode.THREE_COLUMNS && wide) {
            // A three-column layout expands independently with the display.
            int content=w-2*gap;
            int first=content/3, second=content/3;
            tiles.add(new Rect(left,top,left+first,bottom));
            tiles.add(new Rect(left+first+gap,top,left+first+gap+second,bottom));
            tiles.add(new Rect(left+first+gap+second+gap,top,right,bottom));
        } else if (wide) {
            // Main navigation + two stacked secondary tiles.
            int first=divider(w,gap,req.primaryPercent,req.density);
            int otherStart=left+first+gap;
            int second=(h-gap)/2;
            tiles.add(new Rect(left,top,left+first,bottom));
            tiles.add(new Rect(otherStart,top,right,top+second));
            tiles.add(new Rect(otherStart,top+second+gap,right,bottom));
        } else {
            // Tall/portrait viewport: stack panes, never create tiny side tiles.
            int content=h-2*gap;
            int main=divider(h,gap,req.primaryPercent,req.density);
            int mainCapped=Math.min(main,content-2*Math.max(1,content/6));
            int rest=content-mainCapped;
            tiles.add(new Rect(left,top,right,top+mainCapped));
            tiles.add(new Rect(left,top+mainCapped+gap,right,
                top+mainCapped+gap+rest/2));
            tiles.add(new Rect(left,top+mainCapped+gap+rest/2+gap,right,bottom));
        }
        if (req.reversed && tiles.size()>1) {
            List<Rect> reflected=new ArrayList<>();
            for (Rect r:tiles) {
                if(wide)
                    reflected.add(new Rect(left+right-r.right,r.top,
                        left+right-r.left,r.bottom));
                else
                    reflected.add(new Rect(r.left,top+bottom-r.bottom,
                        r.right,top+bottom-r.top));
            }
            tiles=reflected;
        }
        for(int i=0;i<tiles.size();i++) {
            Rect r=tiles.get(i);
            if(r.left<left||r.right>right||r.top<top||r.bottom>bottom)
                throw new IllegalStateException("Pane outside safe area");
            for(int j=0;j<i;j++)if(r.intersects(tiles.get(j)))
                throw new IllegalStateException("Overlapping panes");
        }
        return new Plan(safe,tiles,axis,gap,req.mode);
    }
}
