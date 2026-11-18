package ar.com.rango5gps.drivelabauto.receiver;

/** Pure center-display tiling; no OEM or cluster operations. */
final class T1CenterMosaicGeometry {
    static final int SIDE_STACK=0, TOP_STACK=1, THREE_COLUMNS=2, TWO_COLUMNS=3;
    static final class Tile {
        final int left,top,right,bottom;
        Tile(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
        String bounds(){return left+" "+top+" "+right+" "+bottom;}
    }
    static Tile[] layout(int width,int height,int mode,int mainPercent){
        if(width<3||height<3)throw new IllegalArgumentException("Invalid display dimensions");
        int ratio=Math.max(30,Math.min(70,mainPercent));
        int x=Math.max(1,Math.min(width-2,Math.round(width*ratio/100f)));
        int y=Math.max(1,Math.min(height-2,Math.round(height*ratio/100f)));
        if(mode==SIDE_STACK){
            int half=height/2;
            return new Tile[]{new Tile(0,0,x,height),new Tile(x,0,width,half),
                new Tile(x,half,width,height)};
        }
        if(mode==TOP_STACK){
            int half=width/2;
            return new Tile[]{new Tile(0,0,width,y),new Tile(0,y,half,height),
                new Tile(half,y,width,height)};
        }
        if(mode==THREE_COLUMNS){
            int first=width/3,second=width*2/3;
            return new Tile[]{new Tile(0,0,first,height),new Tile(first,0,second,height),
                new Tile(second,0,width,height)};
        }
        if(mode==TWO_COLUMNS){
            return new Tile[]{new Tile(0,0,x,height),new Tile(x,0,width,height)};
        }
        throw new IllegalArgumentException("Unknown layout");
    }
    private T1CenterMosaicGeometry(){}
}
