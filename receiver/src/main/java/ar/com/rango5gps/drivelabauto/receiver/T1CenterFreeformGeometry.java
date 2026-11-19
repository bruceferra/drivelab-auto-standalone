package ar.com.rango5gps.drivelabauto.receiver;

/** Recognizes Android 11 task geometry with a window-decor offset.
 * Jetour Display 0 moves freeform windows down by ~101 pixels, while
 * preserving the requested 960 x 1080 size. Do not compare exact top Y.
 */
final class T1CenterFreeformGeometry {
    private T1CenterFreeformGeometry(){}
    static boolean half(T1AdbTaskParser.Box actual,
            T1AdbTaskParser.Box desired){
        if(actual==null||desired==null)return false;
        int aw=actual.right-actual.left;
        int ah=actual.bottom-actual.top;
        int dw=desired.right-desired.left;
        int dh=desired.bottom-desired.top;
        return Math.abs(aw-dw)<=20&&Math.abs(ah-dh)<=20&&
            Math.abs(actual.left-desired.left)<=20&&
            actual.top>=desired.top-20&&actual.top<=desired.top+200;
    }
    /** General tile match for configurable two/three-panel layouts.
     * Android 11's Freeform task Y origin may be shifted +101px. Require
     * exact X and dimensions, and a bounded nonnegative common Y offset.
     */
    static boolean tile(T1AdbTaskParser.Box actual,
            T1CenterMosaicGeometry.Tile desired){
        if(actual==null||desired==null)return false;
        return Math.abs((actual.right-actual.left)-
                (desired.right-desired.left))<=20&&
            Math.abs((actual.bottom-actual.top)-
                (desired.bottom-desired.top))<=20&&
            Math.abs(actual.left-desired.left)<=20&&
            actual.top>=desired.top-20&&actual.top<=desired.top+180;
    }
    static boolean twoColumns(T1AdbTaskParser.Box left,
            T1AdbTaskParser.Box right,int centerWidth,int height){
        if(left==null||right==null||centerWidth<600||height<480)return false;
        int half=centerWidth/2;
        T1AdbTaskParser.Box leftTarget=new T1AdbTaskParser.Box(
            0,0,half,height);
        T1AdbTaskParser.Box rightTarget=new T1AdbTaskParser.Box(
            half,0,centerWidth,height);
        return half(left,leftTarget)&&half(right,rightTarget)&&
            Math.abs(left.top-right.top)<=30&&
            left.right<=right.left+20;
    }
}
