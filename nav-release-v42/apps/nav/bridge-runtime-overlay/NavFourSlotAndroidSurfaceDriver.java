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
        long lastFrameTime;
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
    private final Map<String,Long> baselineTime=new HashMap<>();
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
        if(!surfaceBindingsExact())throw new SecurityException(
            "LAB56: Surface/VirtualDisplay inicial no coincide");
    }
    private boolean checkIdentity(RegisteredDisplay r){
        // VirtualDisplay.setSurface(null) temporarily suspends video
        // output and may report Display.STATE_OFF. That does NOT mean
        // the exact owner display was released; rejecting a detached
        // VD prevents the second half of a swap AND its rollback.
        DisplayManager dm=(DisplayManager)
            app.getSystemService(Context.DISPLAY_SERVICE);
        Display d=r.virtual.getDisplay();
        Display live=dm==null?null:dm.getDisplay(r.stream.displayId);
        return d!=null&&live!=null&&
            d.getDisplayId()==r.stream.displayId&&
            live.getDisplayId()==d.getDisplayId()&&
            r.expectedName.equals(d.getName())&&
            r.expectedName.equals(live.getName())&&
            (d.getFlags()&Display.FLAG_PRIVATE)==0;
    }
    private boolean checkDisplay(RegisteredDisplay r){
        if(!checkIdentity(r))return false;
        Display d=r.virtual.getDisplay();
        return d!=null&&d.getState()!=Display.STATE_OFF;
    }
    /**
     * A callback on a TextureView is NOT proof that its pixels came from
     * the declared application. LAB55 previously accepted any next frame
     * while the controls still labeled the original stream.
     *
     * First verify the exact Surface held by EVERY original owned VD,
     * and ensure there is only one producer for each of the four hosts.
     * Never alter an OEM Surface or any Android task.
     */
    private boolean surfaceBindingsExact(){
        synchronized(monitor){
            java.util.HashSet<Surface> seen=new java.util.HashSet<>();
            if(bound.size()!=running.size())return false;
            for(Map.Entry<String,RegisteredDisplay> entry:
                    running.entrySet()){
                NavFourSlotPolicy.Slot slot=bound.get(entry.getKey());
                Host host=hosts.get(slot);
                if(host==null||host.ownedSurface==null||
                   !host.ownedSurface.isValid())return false;
                Surface attached=entry.getValue().virtual.getSurface();
                if(attached==null||attached!=host.ownedSurface||
                   !seen.add(attached))return false;
            }
            return true;
        }
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
            proof.ownsDisplay(expected)&&surfaceBindingsExact()&&
            NavFourSlotTaskInventory.exact(
                inventory.readStackList(),expected);
    }

    /**
     * LAB55: direct Surface rebind ONLY to an empty NAV-owned host.
     * Android treats setSurface(null) as a paused virtual display;
     * an occupied-slot swap must first park the occupant in the fourth
     * unoccupied NAV slot, keeping all original app tasks alive.
     * A foreign or occupied destination is NEVER evicted.
     */
    @Override public void bind(
            NavFourSlotPolicy.Stream stream,
            NavFourSlotPolicy.Slot target)throws Exception{
        requireWorker();
        if(stream==null||target==null||!ownsSlot(target,session))
            throw new SecurityException("Destino NAV fuera de sesión");
        RegisteredDisplay selected=running.get(stream.id);
        if(selected==null||selected.stream!=stream||
           !checkIdentity(selected)||!proof.ownsDisplay(stream))
            throw new SecurityException("Flujo NAV no reconocido");
        Host destination=hosts.get(target);
        if(!surfaceBindingsExact())
            throw new SecurityException(
                "Superficie real y etiqueta NAV no coinciden");
        // Before making a temporary slot visible, prove it is vacant.
        synchronized(monitor){
            if(!current()||!destination.ready()||
               !bound.containsKey(stream.id)||bound.get(stream.id)==target)
                throw new SecurityException(
                    "Origen o destino NAV no disponible");
            for(Map.Entry<String,NavFourSlotPolicy.Slot> entry:
                    bound.entrySet())
                if(entry.getValue()==target)
                    throw new SecurityException(
                        "Destino NAV ocupado: usar cuarto espacio libre");
        }
        // Prepare the (empty) host on the UI thread, not the live source.
        if(!geometry.prepare(stream,target))
            throw new SecurityException(
                "No se habilitó el panel destino para verificación");
        synchronized(monitor){
            if(!current()||!destination.ready()||
               !bound.containsKey(stream.id))
                throw new SecurityException("Surface NAV retirada");
            for(Map.Entry<String,NavFourSlotPolicy.Slot> entry:
                    bound.entrySet())
                if(entry.getValue()==target)
                    throw new SecurityException(
                        "Destino NAV ocupado tras preparar");
            Surface surface=destination.surface();
            SurfaceTexture texture=destination.texture.getSurfaceTexture();
            texture.setDefaultBufferSize(stream.width,stream.height);
            baseline.put(stream.id,destination.frames);
            baselineTime.put(stream.id,destination.lastFrameTime);
            // Rebind DIRECTLY. No temporary OFF state, no second writer.
            selected.virtual.setSurface(surface);
            if(selected.virtual.getSurface()!=surface)
                throw new SecurityException(
                    "Android no confirmó Surface del flujo "+stream.id);
            bound.put(stream.id,target);
            if(!surfaceBindingsExact())
                throw new SecurityException(
                    "Dos displays NAV comparten pantalla o etiqueta");
        }
    }

    @Override public boolean awaitFrame(
            NavFourSlotPolicy.Stream stream,
            NavFourSlotPolicy.Slot target)throws Exception{
        requireWorker();
        if(stream==null||target==null||!current())return false;
        final long deadline=SystemClock.elapsedRealtime()+5000L;
        synchronized(monitor){
            while(SystemClock.elapsedRealtime()<deadline){
                if(!ownsSlot(target,session)||
                   bound.get(stream.id)!=target||
                   !surfaceBindingsExact())return false;
                Host h=hosts.get(target);
                Long start=baseline.get(stream.id);
                Long timestamp=baselineTime.get(stream.id);
                // Count alone can accept a queued OLD Maps frame while
                // Spotify is already labeled as the new occupant.
                // Confirm a newly latched TextureView buffer timestamp.
                if(start!=null&&timestamp!=null&&h.frames>start&&
                   h.lastFrameTime>timestamp)return true;
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
            if(h!=null){
                h.frames++;
                SurfaceTexture st=h.texture==null?
                    null:h.texture.getSurfaceTexture();
                if(st!=null){
                    try{h.lastFrameTime=st.getTimestamp();}
                    catch(RuntimeException ignored){}
                }
                monitor.notifyAll();
            }
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
            baseline.clear();baselineTime.clear();
            bound.clear();running.clear();
            monitor.notifyAll();
        }
    }
}
