package ar.com.rango5gps.drivelabauto.boot;
public final class BridgeWindowMathTest {
    static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        BridgeWindowMath.Frame[] center=BridgeWindowMath.center(1920,1080,65,0);
        ok(center.length==2,"two center windows");
        ok(center[0].width()==1248&&center[1].width()==672,
           "65/35 pixel distribution");
        ok(BridgeWindowMath.disjoint(center[0],center[1]),
           "must not overlap");
        ok(center[1].height()==1080&&center[1].width()<center[1].height(),
           "Spotify panel is tall and narrow, phone-like aspect");
        BridgeWindowMath.Frame panel=
            BridgeWindowMath.display1(1920,720,8,68,62);
        ok(panel.width()==1306&&panel.height()==446,
           "bounded Display1 map region");
        ok(panel.left>=0&&panel.top>=0&&panel.right<=1920&&
           panel.bottom<=720,"bounds cannot exceed secondary display");
        boolean invalid=false;
        try{BridgeWindowMath.center(1920,1080,5,0);}
        catch(IllegalArgumentException e){invalid=true;}
        ok(invalid,"reject too small app panel");
        System.out.println("PASS Bridge pixel geometry: 65/35 mobile Spotify and bounded Display1");
    }
}
