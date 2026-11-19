package ar.com.rango5gps.drivelabauto.receiver;

public final class T1CenterFreeformGeometryTest {
    private static void yes(boolean v,String message){
        if(!v)throw new AssertionError(message);
    }
    private static T1AdbTaskParser.Box box(int l,int t,int r,int b){
        return new T1AdbTaskParser.Box(l,t,r,b);
    }
    public static void main(String[] args){
        T1AdbTaskParser.Box expected=box(0,0,960,1080);
        T1AdbTaskParser.Box actual=box(0,101,960,1181);
        yes(T1CenterFreeformGeometry.half(actual,expected),
            "Actual Jetour screenshot: 960x1080 at Y+101 is 50% success");
        yes(!T1CenterFreeformGeometry.half(
            box(0,0,1920,1080),expected),
            "Full-width windows must never pass 50% verification");
        yes(!T1CenterFreeformGeometry.half(
            box(0,101,640,1181),expected),
            "Wrong window width must not pass");
        yes(!T1CenterFreeformGeometry.half(
            box(0,101,960,950),expected),
            "Wrong window height must not pass");
        yes(T1CenterFreeformGeometry.twoColumns(
            actual,box(960,101,1920,1181),1920,1080),
            "Two aligned panes may share Android's 101px vertical offset");
        yes(!T1CenterFreeformGeometry.twoColumns(
            actual,box(0,101,960,1181),1920,1080),
            "Overlapping panes must not be mistaken for a mosaic");
        yes(!T1CenterFreeformGeometry.twoColumns(
            actual,box(960,350,1920,1430),1920,1080),
            "Incorrect vertical alignment must not pass");
        System.out.println("PASS half-screen geometry and +101px Android freeform offset");
    }
}
