package ar.com.rango5gps.drivelabauto.receiver;

/** Pure percent-based geometry for the cluster (testable without Android).
 * Percentages describe fractions of the available secondary display.
 * x/y position is a percentage of the REMAINING available travel, so
 * windows always stay fully inside the display.
 */
final class T1ClusterLayoutMath {
    static final class Frame {
        final int left,top,right,bottom;
        Frame(int l,int t,int r,int b){
            left=l;top=t;right=r;bottom=b;
        }
        int width(){return right-left;}
        int height(){return bottom-top;}
    }
    private T1ClusterLayoutMath(){}
    static int bounded(int n,int min,int max){
        return Math.max(min,Math.min(max,n));
    }
    static Frame window(int displayWidth,int displayHeight,
                        int widthPercent,int heightPercent,
                        int xPercent,int yPercent){
        int w=Math.max(1,displayWidth),h=Math.max(1,displayHeight);
        int ww=bounded((int)Math.round(w*
            bounded(widthPercent,30,100)/100.0),1,w);
        int hh=bounded((int)Math.round(h*
            bounded(heightPercent,35,100)/100.0),1,h);
        int left=(int)Math.round((w-ww)*bounded(xPercent,0,100)/100.0);
        int top=(int)Math.round((h-hh)*bounded(yPercent,0,100)/100.0);
        return new Frame(left,top,left+ww,top+hh);
    }
    static Frame left(int displayWidth,int displayHeight,int leftPercent){
        int w=Math.max(2,displayWidth),h=Math.max(1,displayHeight);
        int split=bounded((int)Math.round(
            w*bounded(leftPercent,30,70)/100.0),1,w-1);
        return new Frame(0,0,split,h);
    }
    static Frame right(int displayWidth,int displayHeight,int leftPercent){
        Frame first=left(displayWidth,displayHeight,leftPercent);
        return new Frame(first.right,0,Math.max(2,displayWidth),
            Math.max(1,displayHeight));
    }
}