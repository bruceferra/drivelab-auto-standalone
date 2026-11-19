package ar.com.rango5gps.drivelabauto.receiver;

public final class T1CenterMosaicViewportTest {
    static void ck(boolean success,String message){
        if(!success)throw new AssertionError(message);
    }
    static T1AdbTaskParser.Box real(T1CenterMosaicGeometry.Tile target){
        return new T1AdbTaskParser.Box(
            target.left,target.top,target.right,target.bottom);
    }
    static void checkLayout(int mode,int ratio,int inset){
        T1CenterMosaicGeometry.Tile[] tiles=T1CenterMosaicGeometry.layout(
            1920,1080,mode,ratio);
        T1CenterMosaicViewport.Plan plan=T1CenterMosaicViewport.calculate(
            tiles,1080,inset);
        ck(plan.usableHeight==1080-inset,"Real usable screen height");
        int count=mode==T1CenterMosaicGeometry.TWO_COLUMNS?2:3;
        T1AdbTaskParser.Box[] actual=new T1AdbTaskParser.Box[count];
        long area=0;
        for(int i=0;i<count;i++){
            T1CenterMosaicGeometry.Tile displayed=plan.visible[i];
            T1CenterMosaicGeometry.Tile request=plan.commands[i];
            ck(displayed.top>=inset&&displayed.bottom<=1080,
                "Do not place any app below physical bottom of display");
            ck(displayed.top==inset?
                request.top==0&&request.bottom==
                    displayed.bottom-displayed.top:
                request.top==displayed.top&&
                    request.bottom==displayed.bottom,
                "Request y=0 with height only for top row; direct absolute lower");
            ck(T1CenterMosaicViewport.near(real(displayed),displayed),
                "Match exact physical layout");
            actual[i]=real(displayed);
            area+=(long)(displayed.right-displayed.left)*
                (displayed.bottom-displayed.top);
        }
        ck(T1CenterMosaicViewport.disjoint(actual),
            "Nonoverlapping real task geometry");
        ck(area==1920L*(1080-inset),"All visible pixels covered exactly");
    }
    public static void main(String[] args){
        checkLayout(T1CenterMosaicGeometry.TWO_COLUMNS,50,101);
        checkLayout(T1CenterMosaicGeometry.TWO_COLUMNS,60,101);
        checkLayout(T1CenterMosaicGeometry.TWO_COLUMNS,70,101);
        checkLayout(T1CenterMosaicGeometry.SIDE_STACK,50,101);
        checkLayout(T1CenterMosaicGeometry.SIDE_STACK,60,101);
        checkLayout(T1CenterMosaicGeometry.TOP_STACK,70,101);
        checkLayout(T1CenterMosaicGeometry.THREE_COLUMNS,50,101);
        checkLayout(T1CenterMosaicGeometry.SIDE_STACK,50,0);
        checkLayout(T1CenterMosaicGeometry.TWO_COLUMNS,80,0);
        T1CenterMosaicGeometry.Tile[] previous=
            T1CenterMosaicGeometry.layout(1920,1080,
                T1CenterMosaicGeometry.SIDE_STACK,50);
        ck(!T1CenterMosaicViewport.disjoint(new T1AdbTaskParser.Box[]{
            new T1AdbTaskParser.Box(0,101,960,1181),
            new T1AdbTaskParser.Box(960,101,1920,641),
            new T1AdbTaskParser.Box(960,540,1920,1080)}),
            "Actual Jetour 0.3.86 snapshot had 101px overlap");
        boolean rejected=false;
        try{T1CenterMosaicViewport.calculate(previous,1080,400);}
        catch(IllegalArgumentException e){rejected=true;}
        ck(rejected,"Unknown extreme inset fails safe");
        int width60=T1CenterMosaicGeometry.layout(1920,1080,
            T1CenterMosaicGeometry.TWO_COLUMNS,60)[0].right;
        ck(width60==1152,"60% left = 1152px");
        ck(T1CenterMosaicGeometry.layout(1920,1080,
            T1CenterMosaicGeometry.TWO_COLUMNS,70)[0].right==1344,
            "70% left = 1344px");
        System.out.println("PASS usable vertical pixels, no overlap, dynamic inset, 50/60/70 proportions");
    }
}
