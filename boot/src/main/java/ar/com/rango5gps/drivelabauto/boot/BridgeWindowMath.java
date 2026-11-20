package ar.com.rango5gps.drivelabauto.boot;

/** Device-pixel geometry only; no ownership of OEM surfaces. */
final class BridgeWindowMath {
    static final class Frame {
        final int left,top,right,bottom;
        Frame(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
        int width(){return right-left;}
        int height(){return bottom-top;}
    }
    static Frame[] center(int width,int height,int leftPct,int topMargin){
        if(width<400||height<300)throw new IllegalArgumentException("Display 0 inválido");
        if(leftPct<40||leftPct>80||topMargin<0||topMargin>height/4)
            throw new IllegalArgumentException("Distribución fuera de rango");
        int division=Math.max(1,Math.min(width-1,Math.round(width*leftPct/100f)));
        return new Frame[]{new Frame(0,topMargin,division,height),
            new Frame(division,topMargin,width,height)};
    }
    static Frame display1(int width,int height,int marginPct,
                          int widthPct,int heightPct){
        if(width<400||height<200)throw new IllegalArgumentException("Display 1 inválido");
        if(marginPct<0||marginPct>20||widthPct<30||widthPct>100||
           heightPct<30||heightPct>100)
            throw new IllegalArgumentException("Geometría inválida");
        int w=Math.round(width*widthPct/100f);
        int h=Math.round(height*heightPct/100f);
        int x=Math.min(width-w,Math.round(width*marginPct/100f));
        int y=Math.max(0,(height-h)/2);
        return new Frame(x,y,x+w,y+h);
    }
    static boolean disjoint(Frame a,Frame b){
        return a.right<=b.left||b.right<=a.left||
            a.bottom<=b.top||b.bottom<=a.top;
    }
    private BridgeWindowMath(){}
}
