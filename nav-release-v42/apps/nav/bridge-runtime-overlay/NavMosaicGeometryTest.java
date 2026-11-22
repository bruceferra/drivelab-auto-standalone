package ar.com.rango5gps.drivelabauto.boot;

/** Pure Java layout and touch-coordinate regression for LAB48. */
public final class NavMosaicGeometryTest {
    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError(what);
    }
    private static void near(float actual,float wanted,String what){
        if(Math.abs(actual-wanted)>0.0002f)
            throw new AssertionError(what+": "+actual+" expected "+wanted);
    }
    private static void denied(Runnable runnable,String what){
        try{runnable.run();throw new AssertionError("unsafe accepted: "+what);}
        catch(SecurityException expected){}
    }
    public static void main(String[] args){
        // Native display matches host: no crop, letterbox, pixel offset.
        NavMosaicGeometry.Size left=NavMosaicGeometry.fromSurface(1250,980);
        NavMosaicGeometry.Size right=NavMosaicGeometry.fromSurface(620,980);
        NavMosaicGeometry.Fit maps=NavMosaicGeometry.fit(
            left.w,left.h,1250,980);
        NavMosaicGeometry.Fit spotify=NavMosaicGeometry.fit(
            right.w,right.h,620,980);
        near(maps.sx,1f,"full native Maps width");
        near(maps.sy,1f,"full native Maps height");
        near(spotify.sx,1f,"full native Spotify width");
        near(spotify.sy,1f,"full native Spotify height");
        near(maps.x(312.5f),.25f,"Maps quarter width touch");
        near(maps.y(735f),.75f,"Maps three-quarter height touch");
        near(spotify.x(465f),.75f,"Spotify right-side button touch");
        near(spotify.y(490f),.5f,"Spotify center height touch");
        check(maps.contains(1250,980),"all Maps content visible");
        check(spotify.contains(0,0),"Spotify edges visible");

        // Reopening controls shrinks host; center-inside must never
        // enlarge or cut source content. Out-of-content taps are rejected.
        NavMosaicGeometry.Fit wide=NavMosaicGeometry.fit(
            1280,720,900,700);
        check(wide.sx<=1f&&wide.sy<=1f,"never magnify/crop");
        near(wide.sx,1f,"wide source fills host width");
        near(wide.y(350),.5f,"wide source center correct");
        check(!wide.contains(450,20),"top letterbox cannot tap app");
        check(wide.contains(450,350),"content center maps to center");

        NavMosaicGeometry.Fit tall=NavMosaicGeometry.fit(
            480,800,620,950);
        check(tall.sx<=1f&&tall.sy<=1f,"portrait contain");
        near(tall.y(475),.5f,"Spotify tall center correct");
        check(!tall.contains(4,475),"left letterbox cannot tap app");
        check(tall.contains(310,475),"Spotify center touch valid");

        // Extremely small transient advanced menu viewport is allowed as
        // a viewer, but NEVER as a brand-new virtual display for apps.
        NavMosaicGeometry.Fit settings=NavMosaicGeometry.fit(
            1250,980,120,50);
        check(settings.sx<=1f&&settings.sy<=1f,
            "settings aspect fit never crops");
        denied(()->NavMosaicGeometry.fromSurface(120,50),
            "no tiny virtual display");
        denied(()->NavMosaicGeometry.fromSurface(3300,2500),
            "no runaway virtual display");
        System.out.println("PASS NavMosaicGeometryTest: two full native displays, no crop, inverse touch and fit");
    }
}
