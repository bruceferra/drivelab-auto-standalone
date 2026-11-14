package ar.com.rango5gps.drivelabauto.receiver;
public final class T1ClusterLayoutMathTest {
    private static void check(boolean condition,String name){
        if(!condition)throw new AssertionError(name);
    }
    public static void main(String[] args){
        T1ClusterLayoutMath.Frame a=T1ClusterLayoutMath.left(1920,720,60);
        T1ClusterLayoutMath.Frame b=T1ClusterLayoutMath.right(1920,720,60);
        check(a.width()==1152&&b.width()==768&&a.right==b.left,
            "60/40 split exactly fills 1920");
        check(a.height()==720&&b.height()==720,
            "split fills all vertical pixels");
        for(int ratio=0;ratio<=100;ratio++){
            a=T1ClusterLayoutMath.left(1920,720,ratio);
            b=T1ClusterLayoutMath.right(1920,720,ratio);
            check(a.left==0&&b.right==1920&&a.right==b.left,
                "no gaps or overlap at "+ratio);
        }
        T1ClusterLayoutMath.Frame win=T1ClusterLayoutMath.window(
            1920,720,70,75,0,0);
        check(win.left==0&&win.top==0&&win.width()==1344&&
            win.height()==540,"70% width,75% height");
        win=T1ClusterLayoutMath.window(1920,720,70,75,100,100);
        check(win.right==1920&&win.bottom==720,"right-bottom positioning");
        for(int x=-50;x<=150;x+=10)
          for(int y=-50;y<=150;y+=10){
            win=T1ClusterLayoutMath.window(1920,720,58,60,x,y);
            check(win.left>=0&&win.top>=0&&win.right<=1920&&
                win.bottom<=720,"clamped xy "+x+","+y);
          }
        check(T1ClusterLayoutMath.bounded(-10,0,100)==0&&
            T1ClusterLayoutMath.bounded(190,0,100)==100,
            "clamp user sliders");
        System.out.println("PASS T1 freeform/split percent math");
    }
}