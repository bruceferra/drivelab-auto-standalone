package ar.com.rango5gps.drivelabauto.boot;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.view.Display;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * NAV52 experimental physical cluster A/B host. Upgrade the EXISTING
 * NAV-owned Presentation/TextureView; NEVER replace that Presentation
 * or reparent the live Waze TextureView, which would destroy its surface.
 *
 * Full mode keeps Waze on its original full-size physical slot.
 * Split mode reserves B alongside A. The client must transfer only its
 * own VirtualDisplays and confirm new frames before displaying success.
 *
 * This source file is NOT included in any published LAB51 APK/OTA.
 */
final class NavFourSlotClusterABHost {
    interface SlotListener extends TextureView.SurfaceTextureListener {
        // Owner's listener tracks the new NAV B slot.
    }
    private final FrameLayout physicalRoot;
    private final TextureView originalWaze;
    private final FrameLayout secondContainer;
    private final TextureView second;
    private final int physicalWidth,physicalHeight;
    private boolean split;
    // The last VERIFIED visual A/B occupancy; previews are NOT commits.
    // An empty NAV host may temporarily receive an app during a swap
    // but its pixels must stay hidden behind the existing full-width app.
    private boolean visibleA=true,visibleB=false;

    NavFourSlotClusterABHost(FrameLayout root,TextureView waze,
                             int width,int height,SlotListener callback){
        if(root==null||waze==null||callback==null||
           width<1200||height<480||waze.getParent()!=root)
            throw new SecurityException(
                "Clúster NAV A/B requiere Presentation propia existente");
        Display d=root.getDisplay();
        if(d==null||d.getDisplayId()!=1||
           waze.getDisplay()==null||
           waze.getDisplay().getDisplayId()!=1)
            throw new SecurityException(
                "No se puede reutilizar un panel físico que no sea Display 1");
        physicalRoot=root;originalWaze=waze;
        physicalWidth=width;physicalHeight=height;
        // Create the second surface with a rounded PARENT, never assign
        // a background to TextureView (Android throws UOE).
        secondContainer=new FrameLayout(root.getContext());
        GradientDrawable rounded=new GradientDrawable();
        rounded.setColor(Color.BLACK);
        rounded.setCornerRadius(19f*
            root.getResources().getDisplayMetrics().density);
        secondContainer.setBackground(rounded);
        secondContainer.setClipToOutline(true);
        second=new TextureView(root.getContext());
        second.setOpaque(true);
        second.setSurfaceTextureListener(callback);
        secondContainer.addView(second,
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        secondContainer.setVisibility(View.GONE);
        root.addView(secondContainer,new FrameLayout.LayoutParams(
            width/2,height,Gravity.RIGHT|Gravity.TOP));
    }

    TextureView surfaceA(){return originalWaze;}
    TextureView surfaceB(){return second;}
    boolean isSplit(){return split;}

    /**
     * This only changes the physical OWNED host layout, not an Android
     * task or display. Call on UI thread while the Jetour is parked.
     * An unbound panel B may be black until its app is selected.
     */
    void prepareSplit(){
        if(split)return;
        if(!physicalRoot.isAttachedToWindow()||
           originalWaze.getParent()!=physicalRoot)
            throw new SecurityException("Clúster NAV original ya no existe");
        // Preserve LAB50's rounded original Presentation background.
        // Prepare an attached/available B Surface but DO NOT shrink Waze
        // merely because 4P mode was enabled. A/B split is committed only
        // after two NAV-owned streams have delivered verified frames.
        originalWaze.setLayoutParams(new FrameLayout.LayoutParams(
            physicalWidth,physicalHeight,Gravity.LEFT|Gravity.TOP));
        secondContainer.setLayoutParams(new FrameLayout.LayoutParams(
            physicalWidth-physicalWidth/2,
            physicalHeight,Gravity.RIGHT|Gravity.TOP));
        secondContainer.setVisibility(View.VISIBLE);
        secondContainer.setAlpha(0f);
        originalWaze.setAlpha(1f);
        split=true;
    }

    /** Reveal the empty physical B target during frame verification only.
     * Waze A retains its native full-size Surface and Matrix. A verified
     * commit or rollback restores the actual A/B occupancy layout.
     */
    void previewSecond(){
        if(!split||!second.isAvailable()||
           !secondContainer.isAttachedToWindow())
            throw new SecurityException("Tablero B sin superficie NAV");
        NavFourSlotParkingVisibilityPolicy.Front front=
            NavFourSlotParkingVisibilityPolicy.frontWhenParking(
                visibleA,visibleB,NavFourSlotPolicy.Slot.TABLERO_B);
        if(originalWaze.getWidth()<physicalWidth-2)
            throw new SecurityException(
                "Tablero A aún no cubre la vista temporal B");
        secondContainer.setVisibility(View.VISIBLE);
        // Keep the temporary VD attached and alpha=1 so Android can
        // render a NEW frame for LAB57's guard. Cover its transient
        // pixels with the existing, already visible A fullscreen View.
        // Previously bringToFront(B) flashed Spotify/Maps for ~1 sec.
        secondContainer.setAlpha(1f);
        second.setAlpha(1f);
        if(front==NavFourSlotParkingVisibilityPolicy.Front.A)
            originalWaze.bringToFront();
        else secondContainer.bringToFront();
    }
    /** A can be vacant while the already verified B app fills the
     * entire cluster. Park to A BEHIND B, never flash the parking app.
     */
    void previewFirst(){
        if(!split||!originalWaze.isAvailable()||
           !originalWaze.isAttachedToWindow())
            throw new SecurityException("Tablero A sin superficie NAV");
        NavFourSlotParkingVisibilityPolicy.Front front=
            NavFourSlotParkingVisibilityPolicy.frontWhenParking(
                visibleA,visibleB,NavFourSlotPolicy.Slot.TABLERO_A);
        if(secondContainer.getWidth()<physicalWidth-2)
            throw new SecurityException(
                "Tablero B aún no cubre la vista temporal A");
        originalWaze.setAlpha(1f);
        if(front==NavFourSlotParkingVisibilityPolicy.Front.B)
            secondContainer.bringToFront();
        else originalWaze.bringToFront();
    }

    /**
     * The cluster's single-app layout stays FULL WIDTH until both A and B
     * contain a verified NAV stream. The optional B TextureView remains
     * attached (alpha 0) so Android can bind it without recreation.
     */
    void layoutFor(boolean aOccupied,boolean bOccupied){
        if(!split)return;
        visibleA=aOccupied;
        visibleB=bOccupied;
        int half=physicalWidth/2;
        int aW=aOccupied&&bOccupied?half:physicalWidth;
        int bW=aOccupied&&bOccupied?physicalWidth-half:
            bOccupied?physicalWidth:physicalWidth-half;
        originalWaze.setLayoutParams(new FrameLayout.LayoutParams(
            aW,physicalHeight,Gravity.LEFT|Gravity.TOP));
        secondContainer.setLayoutParams(new FrameLayout.LayoutParams(
            bW,physicalHeight,Gravity.RIGHT|Gravity.TOP));
        originalWaze.setAlpha(aOccupied?1f:0f);
        secondContainer.setAlpha(bOccupied?1f:0f);
        // Restore normal visual layering after the pre-commit A probe.
        if(bOccupied)secondContainer.bringToFront();
        else if(aOccupied)originalWaze.bringToFront();
        if(aOccupied&&!bOccupied){
            // A failed attempt must not leave Waze looking miniature.
            // Its normal full-width 1920x720 original Surface is unchanged.
            originalWaze.setTransform(new Matrix());
        }
    }
    /**
     * Return to full-screen mode ONLY after the owner has verified that
     * no VirtualDisplay is bound to B. Never discard an active task.
     */
    void restoreFull(boolean bUnbound){
        if(!bUnbound)
            throw new SecurityException(
                "Tablero B sigue ocupado; no se puede ocultar");
        secondContainer.setAlpha(0f);
        secondContainer.setVisibility(View.GONE);
        originalWaze.setAlpha(1f);
        visibleA=true;visibleB=false;
        originalWaze.setTransform(new Matrix());
        originalWaze.setLayoutParams(new FrameLayout.LayoutParams(
            physicalWidth,physicalHeight,Gravity.BOTTOM));
        split=false;
    }

    /**
     * Content is fitted (never cropped). The real VirtualDisplay's
     * native size is kept unchanged while its host shifts from 100% to
     * A/B; a responsive resize is a separate, guarded future feature.
     */
    static void fit(TextureView texture,int displayW,int displayH){
        if(texture==null||texture.getWidth()<1||
           texture.getHeight()<1)return;
        NavMosaicGeometry.Fit f=NavMosaicGeometry.fit(
            displayW,displayH,texture.getWidth(),texture.getHeight());
        Matrix transform=new Matrix();
        transform.setScale(f.sx,f.sy,
            texture.getWidth()/2f,texture.getHeight()/2f);
        texture.setTransform(transform);
    }

    /** Explicit cleanup after the owner's cluster session has ended. */
    void closeAfterOwnerRelease(){
        if(secondContainer.getParent()==physicalRoot){
            secondContainer.setVisibility(View.GONE);
            physicalRoot.removeView(secondContainer);
        }
    }
}
