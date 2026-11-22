package ar.com.rango5gps.drivelabauto.boot;

/**
 * LAB48: no-crop virtual panel geometry. A VirtualDisplay is born with
 * exactly the host TextureView's size, so the app itself fills the slot.
 * If the host later changes during settings, show ALL content with
 * center-inside scaling and invert those coordinates for touch gestures.
 * No task/display recreation or global screen resize.
 */
final class NavMosaicGeometry {
    static final class Size {
        final int w,h;
        Size(int width,int height){
            if(width<1||height<1||width>3000||height>2200)
                throw new SecurityException("Tamaño de panel virtual fuera de rango");
            w=width;h=height;
        }
    }
    static final class Fit {
        final float sx,sy;
        final Size source,host;
        Fit(Size sourceSize,Size hostSize){
            source=sourceSize;host=hostSize;
            float src=(float)source.w/source.h;
            float dst=(float)host.w/host.h;
            // CENTER_INSIDE, no cropping, no aspect-ratio distortion.
            sx=Math.min(1f,src/dst);
            sy=Math.min(1f,dst/src);
        }
        float x(float pixels){
            return ((pixels/(float)host.w)-.5f)/sx+.5f;
        }
        float y(float pixels){
            return ((pixels/(float)host.h)-.5f)/sy+.5f;
        }
        boolean contains(float pixelsX,float pixelsY){
            float px=x(pixelsX),py=y(pixelsY);
            return !Float.isNaN(px)&&!Float.isNaN(py)&&
                px>=0f&&px<=1f&&py>=0f&&py<=1f;
        }
    }
    private NavMosaicGeometry(){}
    static Size fromSurface(int w,int h){
        if(w<240||h<240)throw new SecurityException(
            "Panel NAV demasiado pequeño para abrir una app");
        return new Size(w,h);
    }
    static Fit fit(int sourceW,int sourceH,int hostW,int hostH){
        return new Fit(new Size(sourceW,sourceH),new Size(hostW,hostH));
    }
}
