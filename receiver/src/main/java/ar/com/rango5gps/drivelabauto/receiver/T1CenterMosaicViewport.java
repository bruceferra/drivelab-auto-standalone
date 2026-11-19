package ar.com.rango5gps.drivelabauto.receiver;

/** Jetour T1 Display 0 task-window viewport.
 * The ROM forces any window requested at y=0 to actual y≈101 while keeping
 * its height. Lower tiles begin at the requested nonzero y instead.
 * Compute true usable height [inset, displayHeight] before tiled launches.
 * No task/OEM work happens in this pure geometry helper.
 */
final class T1CenterMosaicViewport {
    static final class Plan {
        final int inset,usableHeight;
        final T1CenterMosaicGeometry.Tile[] commands,visible;
        Plan(int inset,int height,T1CenterMosaicGeometry.Tile[] commands,
                T1CenterMosaicGeometry.Tile[] visible){
            this.inset=inset;this.usableHeight=height-inset;
            this.commands=commands;this.visible=visible;
        }
    }
    private T1CenterMosaicViewport(){}
    static Plan calculate(T1CenterMosaicGeometry.Tile[] original,
            int displayHeight,int inset){
        if(original==null||original.length<1||original.length>3||
            displayHeight<480||inset<0||inset>220||inset>=displayHeight/3)
            throw new IllegalArgumentException("Viewport de pantalla no válido");
        int usable=displayHeight-inset;
        T1CenterMosaicGeometry.Tile[] commands=
            new T1CenterMosaicGeometry.Tile[original.length];
        T1CenterMosaicGeometry.Tile[] visible=
            new T1CenterMosaicGeometry.Tile[original.length];
        for(int i=0;i<original.length;i++){
            T1CenterMosaicGeometry.Tile t=original[i];
            int top=inset+Math.round(t.top*usable/(float)displayHeight);
            int bottom=inset+Math.round(t.bottom*usable/(float)displayHeight);
            if(bottom-top<110||t.right-t.left<110)
                throw new IllegalArgumentException(
                    "Panel demasiado estrecho para Android: "+(i+1)+
                    ". Ajustá el deslizador.");
            visible[i]=new T1CenterMosaicGeometry.Tile(
                t.left,top,t.right,bottom);
            // y=0 is special in Jetour; the ROM snaps to the inset and
            // preserves the supplied height, so request height (not bottom).
            boolean snapped=t.top==0;
            commands[i]=new T1CenterMosaicGeometry.Tile(
                t.left,snapped?0:top,t.right,
                snapped?bottom-top:bottom);
        }
        return new Plan(inset,displayHeight,commands,visible);
    }
    static boolean near(T1AdbTaskParser.Box measured,
            T1CenterMosaicGeometry.Tile target){
        if(measured==null||target==null)return false;
        return Math.abs(measured.left-target.left)<=20&&
            Math.abs(measured.top-target.top)<=20&&
            Math.abs(measured.right-target.right)<=20&&
            Math.abs(measured.bottom-target.bottom)<=20;
    }
    static boolean disjoint(T1AdbTaskParser.Box[] panels){
        if(panels==null||panels.length<1)return false;
        for(int i=0;i<panels.length;i++){
            T1AdbTaskParser.Box a=panels[i];
            if(a==null)return false;
            for(int j=i+1;j<panels.length;j++){
                T1AdbTaskParser.Box b=panels[j];
                if(b==null)return false;
                int width=Math.min(a.right,b.right)-Math.max(a.left,b.left);
                int height=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top);
                if(width>20&&height>20)return false;
            }
        }
        return true;
    }
}
