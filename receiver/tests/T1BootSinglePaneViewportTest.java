package ar.com.rango5gps.drivelabauto.receiver;
public final class T1BootSinglePaneViewportTest {
    static void yes(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args){
        T1CenterMosaicGeometry.Tile t=new T1CenterMosaicGeometry.Tile(
            0,0,1920,1080);
        T1CenterMosaicViewport.Plan p=T1CenterMosaicViewport.calculate(
            new T1CenterMosaicGeometry.Tile[]{t},1080,101);
        yes(p.visible.length==1,"1 panel");
        yes(p.commands[0].top==0&&p.commands[0].bottom==979,
            "Freeform y-zero snaps to top 101 with 979px height");
        yes(p.visible[0].top==101&&p.visible[0].bottom==1080,
            "100% of safe visible height, without exceeding bottom");
        yes(T1CenterMosaicViewport.disjoint(new T1AdbTaskParser.Box[]{
            new T1AdbTaskParser.Box(0,101,1920,1080)}),
            "One rectangle has no overlap");
        System.out.println("PASS DriveLab Boot 100% safe-area Freeform geometry");
    }
}
