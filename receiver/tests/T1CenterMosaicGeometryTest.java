package ar.com.rango5gps.drivelabauto.receiver;

/** Geometry invariants for the center screen, independent of Android. */
public final class T1CenterMosaicGeometryTest {
    private static void check(boolean yes,String msg){
        if(!yes)throw new AssertionError(msg);
    }
    public static void main(String[] args){
        for(int mode=0;mode<4;mode++){
            for(int pct:new int[]{30,40,50,60,70}){
                T1CenterMosaicGeometry.Tile[] tiles=
                    T1CenterMosaicGeometry.layout(1920,1080,mode,pct);
                int active=mode==3?2:3;
                long area=0;
                for(int i=0;i<active;i++){
                    T1CenterMosaicGeometry.Tile t=tiles[i];
                    check(t.left>=0&&t.top>=0&&t.right<=1920&&t.bottom<=1080,
                        "Tile outside center display");
                    check(t.right>t.left&&t.bottom>t.top,"Empty tile");
                    area+=(long)(t.right-t.left)*(t.bottom-t.top);
                    for(int j=0;j<i;j++){
                        T1CenterMosaicGeometry.Tile o=tiles[j];
                        int dx=Math.max(0,Math.min(t.right,o.right)-Math.max(t.left,o.left));
                        int dy=Math.max(0,Math.min(t.bottom,o.bottom)-Math.max(t.top,o.top));
                        check(dx*dy==0,"Overlapping center windows");
                    }
                }
                check(area==1920L*1080L,"Mosaic must fill screen exactly");
            }
        }
        System.out.println("PASS center 2/3-window geometry: bounds, no overlap, full coverage");
    }
}
