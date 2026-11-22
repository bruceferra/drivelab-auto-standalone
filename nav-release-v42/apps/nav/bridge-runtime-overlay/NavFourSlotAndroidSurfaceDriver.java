package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * SOURCE-ONLY experimental Android backend for NAV's four-slot controller.
 * Not copied to the LAB51 APK, and NOT wired to the working cluster.
 *
 * This object can bind only ALREADY-CREATED VirtualDisplays. It never
 * creates displays, launches Android apps, moves Android tasks, or targets
 * physical display 0/1 with an ADB command.
 *
 * The owner MUST install a frame notification into each existing NAV
 * TextureView listener and provide a UI adapter for changing its aspect
 * ratio and touch routing. The driver refuses to proceed without both.
 *
 * Calls to bind()/awaitFrame()/unchanged() run off the Android UI thread.
 * onFrame() is called from the UI thread's TextureView callback.
 */
final class NavFourSlotAndroidSurfaceDriver
        implements NavFourSlotSwapController.Driver {
    private static final String LAB=
        "ar.com.rango5gps.drivelabauto.boot.lab";

    interface Ownership {
        boolean liveSession(long session);
        boolean ownsDisplay(NavFourSlotPolicy.Stream source);
    }
    interface Inventory {
        /** Read-only, bounded shell("am stack list") with no mutations. */
        String readStackList();
    }
    interface GeometryAndInput {
        /**
         * Called BEFORE the source is attached to the destination:
         * apply aspect-preserving transform, set the dynamic touch VID,
         * and keep input DISABLED until coordinator commits.
         * Implementations marshal to UI thread and return only on success.
         */
        boolean prepare(NavFourSlotPolicy.Stream source,
                        NavFourSlotPolicy.Slot target);
    }

    static final class RegisteredDisplay {
        final NavFourSlotPolicy.Stream stream;
        final VirtualDisplay virtual;
        final String expectedName;
        RegisteredDisplay(NavFourSlotPolicy.Stream identity,
                          VirtualDisplay display,String name){
            if(identity==null||display==null||name==null||
               !name.startsWith("DriveLabNAV"))
                throw new SecurityException("Display no declarado por NAV");
            stream=identity;virtual=display;expectedName=name;
        }
    }
    private static final class Host {
        final NavFourSlotPolicy.Slot slot;
        final TextureView texture;
        final int expectedHardwareId;
        Surface ownedSurface;
        SurfaceTexture originalTexture;
        final boolean ownerProvided;
        long frames;
        Host(NavFourSlotPolicy.Slot slot,TextureView texture,
             Surface fromOwner){
            this.slot=slot;this.texture=texture;
            expectedHardwareId=slot.touch?0:1;
            ownerProvided=fromOwner!=null;
            if(fromOwner!=null){
                if(!fromOwner.isValid()||texture==null||
                   texture.getSurfaceTexture()==null)
                    throw new SecurityException(
                        "El Surface original NAV no es válido");
                ownedSurface=fromOwner;
                originalTexture=texture.getSurfaceTexture();
            }
        }
        boolean ready(){
            Display host=texture==null?null:texture.getDisplay();
            return texture!=null&&texture.isAttachedToWindow()&&
                texture.isAvailable()&&
                texture.getSurfaceTexture()!=null&&
                host!=null&&host.getDisplayId()==expectedHardwareId&&
                texture.getWidth()>=200&&texture.getHeight()>=200;
        }
        Surface surface(){
            if(!ready())throw new SecurityException(
                "Superficie NAV no disponible para "+slot);
            SurfaceTexture st=texture.getSurfaceTexture();
            if(ownedSurface!=null&&originalTexture!=st){
                throw new SecurityException(
                    "Textura cambió sin una nueva sesión NAV");
            }
            if(ownedSurface==null){
                originalTexture=st;
                ownedSurface=new Surface(st);
            }
            if(!ownedSurface.isValid())
                throw new SecurityException("Surface NAV inválida");
            return ownedSurface;
        }
        void release(){
            if(ownedSurface!=null){
                if(!ownerProvided)ownedSurface.release();
                ownedSurface=null;
            }
            originalTexture=null;
        }
    }

    private final Object monitor=new Object();
    private final long session;
    private final Context app;
    private final Ownership proof;
    private final Inventory inventory;
    private final GeometryAndInput geometry;
    private final EnumMap<NavFourSlotPolicy.Slot,Host> hosts=
        new EnumMap<>(NavFourSlotPolicy.Slot.class);
    private final Map<String,RegisteredDisplay> running=new HashMap<>();
    private final Map<String,NavFourSlotPolicy.Slot> bound=new HashMap<>();
    private final Map<String,Long> baseline=new HashMap<>();
    private volatile boolean failedClosed;

    NavFourSlotAndroidSurfaceDriver(
            Context context,NavFourSlotPolicy.State initial,
            Map<NavFourSlotPolicy.Slot,TextureView> textureSlots,
            Map<String,RegisteredDisplay> displays,
            Ownership ownerProof,Inventory readOnlyInventory,
            GeometryAndInput layoutAndInput){
        this(context,initial,textureSlots,displays,ownerProof,
            readOnlyInventory,layoutAndInput,
            java.util.Collections.emptyMap());
    }
    NavFourSlotAndroidSurfaceDriver(
            Context context,NavFourSlotPolicy.State initial,
            Map<NavFourSlotPolicy.Slot,TextureView> textureSlots,
            Map<String,RegisteredDisplay> displays,
            Ownership ownerProof,Inventory readOnlyInventory,
            GeometryAndInput layoutAndInput,
            Map<NavFourSlotPolicy.Slot,Surface> originalSurfaces){
        if(context==null||!LAB.equals(context.getPackageName())||
           initial==null||textureSlots==null||displays==null||
           originalSurfaces==null||
           ownerProof==null||readOnlyInventory==null||
           layoutAndInput==null)
            throw new SecurityException("NAV driver sin propietario");
        NavFourSlotPolicy.validate(initial);
        if(initial.streams.size()!=displays.size()||
           textureSlots.size()!=4||
           !ownerProof.liveSession(initial.session))
            throw new SecurityException("Inventario cuatro-paneles incompleto");
        app=context.getApplicationContext();
        session=initial.session;proof=ownerProof;
        inventory=readOnlyInventory;geometry=layoutAndInput;
        for(NavFourSlotPolicy.Slot slot:NavFourSlotPolicy.Slot.values()){
            TextureView tv=textureSlots.get(slot);
            if(tv==null)throw new SecurityException(
                "Falta una de las cuatro superficies");
            Host h=new Host(slot,tv,originalSurfaces.get(slot));
            if(!h.ready())throw new SecurityException(
                "El panel no pertenece a la pantalla NAV esperada: "+slot);
            hosts.put(slot,h);
        }
        for(Map.Entry<String,NavFourSlotPolicy.Stream> e:
                initial.streams.entrySet()){
            RegisteredDisplay r=displays.get(e.getKey());
            if(r==null||r.stream!=e.getValue()||
               !checkDisplay(r)||!proof.ownsDisplay(r.stream))
                throw new SecurityException(
                    "VirtualDisplay/tarea no es propiedad de NAV");
            running.put(e.getKey(),r);
        }
        for(NavFourSlotPolicy.Slot slot:NavFourSlotPolicy.Slot.values()){
            String name=initial.idAt(slot);
            if(name!=null)bound.put(name,slot);
        }
    }
    private boolean checkDisplay(RegisteredDisplay r){
        DisplayManager dm=(DisplayManager)
            app.getSystemService(Context.DISPLAY_SERVICE);
        Display d=r.virtual.getDisplay();
        Display live=dm==null?null:dm.getDisplay(r.stream.displayId);
        return d!=null&&live!=null&&
            d.getDisplayId()==r.stream.displayId&&
            live.getDisplayId()==d.getDisplayId()&&
            r.expectedName.equals(d.getName())&&
            r.expectedName.equals(live.getName())&&
            d.getState()!=Display.STATE_OFF&&
            (d.getFlags()&Display.FLAG_PRIVATE)==0;
    }
    private void requireWorker(){
        if(Looper.myLooper()==Looper.getMainLooper())
            throw new SecurityException(
                "Swap NAV no puede bloquear el hilo táctil/UI");
    }
    private boolean current(){
        return !failedClosed&&proof.liveSession(session);
    }
    @Override public boolean ownsSlot(
            NavFourSlotPolicy.Slot slot,long token){
        if(slot==null||token!=session||!current())return false;
        Host h=hosts.get(slot);
        return h!=null&&h.ready();
    }
    @Override public boolean unchanged(NavFourSlotPolicy.Stream expected){
        requireWorker();
        if(expected==null||expected.session!=session||!current())
            return false;
        RegisteredDisplay r=running.get(expected.id);
        return r!=null&&r.stream==expected&&checkDisplay(r)&&
            proof.ownsDisplay(expected)&&
            NavFourSlotTaskInventory.exact(
                inventory.readStackList(),expected);
    }

    /**
     * The current occupant of an occupied destination is detached FIRST.
     * This prevents two separate VirtualDisplays from writing into the
     * same TextureView Surface during an A↔B swap. The controller
     * restores both original slots on any failure.
     */
    @Override public void bind(
            NavFourSlotPolicy.Stream stream,
            NavFourSlotPolicy.Slot target)throws Exception{
        requireWorker();
        if(stream==null||target==null||!ownsSlot(target,session))
            throw new SecurityException("Destino NAV fuera de sesión");
        RegisteredDisplay selected=running.get(stream.id);
        if(selected==null||selected.stream!=stream||
           !checkDisplay(selected)||!proof.ownsDisplay(stream))
            throw new SecurityException("Flujo NAV no reconocido");
        Host destination=hosts.get(target);
        // Geometry/touch callback must update its per-slot VID and fit.
        if(!geometry.prepare(stream,target))
            throw new SecurityException(
                "No se aplicó la geometría/tacto del destino");
        synchronized(monitor){
            if(!current()||!destination.ready())
                throw new SecurityException("Surface NAV retirada");
            String other=null;
            for(Map.Entry<String,NavFourSlotPolicy.Slot> entry:
                    bound.entrySet())
                if(entry.getValue()==target&&
                   !entry.getKey().equals(stream.id)){
                    other=entry.getKey();break;
                }
            if(other!=null){
                RegisteredDisplay occupant=running.get(other);
                if(occupant==null||!checkDisplay(occupant))
                    throw new SecurityException("Ocupante no pertenece a NAV");
                // No force-stop, no task relaunch.
                occupant.virtual.setSurface(null);
                bound.remove(other);
            }
            selected.virtual.setSurface(null);
            bound.remove(stream.id);
            Surface surface=destination.surface();
            SurfaceTexture texture=destination.texture.getSurfaceTexture();
            texture.setDefaultBufferSize(stream.width,stream.height);
            baseline.put(stream.id,destination.frames);
            selected.virtual.setSurface(surface);
            bound.put(stream.id,target);
        }
    }

    @Override public boolean awaitFrame(
            NavFourSlotPolicy.Stream stream,
            NavFourSlotPolicy.Slot target)throws Exception{
        requireWorker();
        if(stream==null||target==null||!current())return false;
        final long deadline=SystemClock.elapsedRealtime()+1800L;
        synchronized(monitor){
            while(SystemClock.elapsedRealtime()<deadline){
                if(!ownsSlot(target,session)||
                   bound.get(stream.id)!=target)return false;
                Host h=hosts.get(target);
                Long start=baseline.get(stream.id);
                if(start!=null&&h.frames>start)return true;
                monitor.wait(Math.min(125L,
                    Math.max(1L,deadline-SystemClock.elapsedRealtime())));
            }
        }
        return false;
    }
    /** Wire this into the ALREADY-EXISTING surface callback in its owner. */
    void onFrame(NavFourSlotPolicy.Slot slot){
        synchronized(monitor){
            Host h=hosts.get(slot);
            if(h!=null){h.frames++;monitor.notifyAll();}
        }
    }
    /**
     * Called only after the owner has independently released its own
     * displays/session. Never release or detach external T1 surfaces.
     */
    void releaseAfterOwnerClose(){
        synchronized(monitor){
            failedClosed=true;
            for(Host host:hosts.values())host.release();
            baseline.clear();bound.clear();running.clear();
            monitor.notifyAll();
        }
    }
}
