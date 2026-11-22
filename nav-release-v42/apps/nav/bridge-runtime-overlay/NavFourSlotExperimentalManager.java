package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.view.Surface;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * LAB53 PARKED-ONLY EXPERIMENTAL integration of the 4-slot controller
 * with the 3 ACTUAL existing NAV display tasks, not a simulated UI.
 *
 * Central A/B host Maps/Spotify, cluster A initially Waze, cluster B
 * is empty. Exactly those 3 apps can move between 4 physical slots
 * without rerunning, killing or moving an Android task.
 *
 * A fourth independent app/VD is NOT created by this class. It is a
 * separate integration step; never claim 4 live apps are already present.
 *
 * All options are initiated from NAV's central touchscreen; no steering
 * wheel, OEM Binder, system task, or remote sender changes.
 */
final class NavFourSlotExperimentalManager {
    interface Reporter {
        void status(String message);
        void stateChanged(boolean active);
    }
    private final Activity owner;
    private final long token;
    private final VirtualDisplay mapsDisplay,spotifyDisplay;
    private final Surface mapsSink,spotifySink;
    private final TextureView mapsTexture,spotifyTexture;
    private final FrameLayout mapsHost,spotifyHost;
    private final LinearLayout toolbar;
    private final Reporter reporter;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean starting,initializing,active,closed,touchPaused;
    private int readinessTries;
    private NavFourSlotAndroidSurfaceDriver driver;
    private NavFourSlotSessionCoordinator coordinator;
    private NavFourSlotControls controls;
    private final EnumMap<NavFourSlotPolicy.Slot,TextureView> surfaces=
        new EnumMap<>(NavFourSlotPolicy.Slot.class);

    NavFourSlotExperimentalManager(Activity activity,long session,
            VirtualDisplay maps,VirtualDisplay media,
            Surface mapsOwnedSink,Surface mediaOwnedSink,
            TextureView mapsView,TextureView mediaView,
            FrameLayout panelA,FrameLayout panelB,
            LinearLayout topBar,Reporter callback){
        if(activity==null||session<=0||maps==null||media==null||
           mapsOwnedSink==null||mediaOwnedSink==null||
           !mapsOwnedSink.isValid()||!mediaOwnedSink.isValid()||
           mapsView==null||mediaView==null||panelA==null||
           panelB==null||topBar==null||callback==null)
            throw new SecurityException("NAV cuatro paneles sin propietario");
        owner=activity;token=session;
        mapsDisplay=maps;spotifyDisplay=media;
        mapsSink=mapsOwnedSink;spotifySink=mediaOwnedSink;
        mapsTexture=mapsView;spotifyTexture=mediaView;
        mapsHost=panelA;spotifyHost=panelB;toolbar=topBar;
        reporter=callback;
        surfaces.put(NavFourSlotPolicy.Slot.CENTRAL_A,mapsTexture);
        surfaces.put(NavFourSlotPolicy.Slot.CENTRAL_B,spotifyTexture);
    }

    boolean isActive(){return active&&!closed;}
    boolean isPreparing(){return (starting||initializing)&&!closed;}
    boolean blockedInput(){return touchPaused||starting||initializing;}
    boolean canSafelyClose(){
        // If a rollback was unconfirmed, central input remains blocked,
        // but the owner must still be able to CLOSE its own verified
        // Android tasks. Do not strand a Waze/Maps session indefinitely.
        return !starting&&!initializing&&
            (coordinator==null||!coordinator.isSwapRunning());
    }
    void activate(){
        if(closed||starting||active)return;
        if(NavOwnClusterProbe.fourSlotAnchor(owner,token)!=null){
            fail("Ya hay un compositor de cuatro paneles activo");
            return;
        }
        NavMosaicSessionRegistry.Session live=
            NavMosaicSessionRegistry.snapshot(owner);
        if(live==null||live.token!=token||
           live.displayId!=mapsDisplay.getDisplay().getDisplayId()||
           live.mediaDisplayId!=spotifyDisplay.getDisplay().getDisplayId()||
           !NavMosaicSessionRegistry.MAPS.equals(live.mapPackage)||
           !NavOwnClusterProbe.hasActiveWazeTrial()){
            fail("Para cuatro paneles abrí primero Maps, Spotify y "+
                "Waze del clúster con CLÚSTER ON");
            return;
        }
        starting=true;
        reporter.status("Preparando Tablero A/B sin detener Maps, "+
            "Spotify ni Waze. Solo estacionado.");
        boolean created=NavOwnClusterProbe.prepareFourSlots(
            owner,token,new NavFourSlotClusterABHost.SlotListener(){
                @Override public void onSurfaceTextureAvailable(
                    SurfaceTexture st,int w,int h){
                    main.postDelayed(()->tryInitialize(),250L);
                }
                @Override public void onSurfaceTextureSizeChanged(
                    SurfaceTexture st,int w,int h){}
                @Override public void onSurfaceTextureUpdated(
                    SurfaceTexture st){
                    if(driver!=null)
                        driver.onFrame(NavFourSlotPolicy.Slot.TABLERO_B);
                }
                @Override public boolean onSurfaceTextureDestroyed(
                    SurfaceTexture st){
                    if(active||starting)
                        fail("Android destruyó el segundo panel del tablero");
                    return true;
                }
            },
            ()->{if(driver!=null)
                driver.onFrame(NavFourSlotPolicy.Slot.TABLERO_A);},
            ()->{if(driver!=null)
                driver.onFrame(NavFourSlotPolicy.Slot.TABLERO_B);});
        if(!created){
            starting=false;
            fail("Android no pudo preparar la segunda superficie "+
                "de Waze sin alterar su capa actual");
            return;
        }
        main.postDelayed(()->tryInitialize(),900L);
    }
    private void tryInitialize(){
        if(closed||!starting||active||initializing)return;
        NavOwnClusterProbe.FourSlotAnchor cluster=
            NavOwnClusterProbe.fourSlotAnchor(owner,token);
        if(cluster==null||!cluster.slotA.isAvailable()||
           !cluster.slotB.isAvailable()||
           cluster.slotB.getWidth()<200||
           cluster.slotB.getHeight()<200){
            if(++readinessTries>16){
                starting=false;
                fail("El segundo panel del tablero no generó una "+
                    "superficie Android utilizable.");
                NavOwnClusterProbe.restoreSingleFourSlot(owner,token);
                return;
            }
            main.postDelayed(()->{
                if(!closed&&starting)tryInitialize();
            },500L);
            return;
        }
        initializing=true; // only one Android inventory read; still preparing
        surfaces.put(NavFourSlotPolicy.Slot.TABLERO_A,cluster.slotA);
        surfaces.put(NavFourSlotPolicy.Slot.TABLERO_B,cluster.slotB);
        new Thread(()->{
            NavFourSlotPolicy.State snapshot=null;
            String failure=null;
            try{
                final NavMosaicSessionRegistry.Session live=
                    NavMosaicSessionRegistry.snapshot(owner);
                if(live==null||live.token!=token||
                   cluster.token!=token)
                    throw new SecurityException(
                        "La sesión NAV cambió al dividir tablero");
                String a=BridgeLocalAdb.shell(owner,"am stack list");
                String b=BridgeLocalAdb.shell(owner,"am stack list");
                NavMosaicExitPolicy.Snapshot one=
                    NavMosaicExitPolicy.parse(
                        a,live.displayId,live.mapPackage,
                        live.mediaDisplayId);
                NavMosaicExitPolicy.Snapshot two=
                    NavMosaicExitPolicy.parse(
                        b,live.displayId,live.mapPackage,
                        live.mediaDisplayId);
                if(!one.same(two)||one.map==null||
                   one.spotify==null)
                    throw new SecurityException(
                        "Maps o Spotify no tienen una tarea única verificable");
                Map<String,NavFourSlotPolicy.Stream> streams=
                    new HashMap<>();
                streams.put("maps",new NavFourSlotPolicy.Stream(
                    "maps",NavMosaicSessionRegistry.MAPS,
                    live.displayId,two.map.taskId,two.map.stackId,
                    mapsTexture.getWidth(),mapsTexture.getHeight(),token));
                streams.put("spotify",new NavFourSlotPolicy.Stream(
                    "spotify",NavMosaicExitPolicy.SPOTIFY,
                    live.mediaDisplayId,two.spotify.taskId,
                    two.spotify.stackId,
                    spotifyTexture.getWidth(),
                    spotifyTexture.getHeight(),token));
                streams.put("waze",new NavFourSlotPolicy.Stream(
                    "waze",NavMosaicSessionRegistry.WAZE,
                    cluster.display,cluster.task,cluster.stack,
                    cluster.width,cluster.height,token));
                if(!NavFourSlotTaskInventory.exact(b,streams.get("maps"))||
                   !NavFourSlotTaskInventory.exact(b,streams.get("spotify"))||
                   !NavFourSlotTaskInventory.exact(b,streams.get("waze"))||
                   !NavFourSlotTaskInventory.exact(a,streams.get("waze")))
                    throw new SecurityException(
                        "Android no confirmó las tres tareas originales");
                EnumMap<NavFourSlotPolicy.Slot,String> slots=
                    new EnumMap<>(NavFourSlotPolicy.Slot.class);
                slots.put(NavFourSlotPolicy.Slot.CENTRAL_A,"maps");
                slots.put(NavFourSlotPolicy.Slot.CENTRAL_B,"spotify");
                slots.put(NavFourSlotPolicy.Slot.TABLERO_A,"waze");
                snapshot=new NavFourSlotPolicy.State(
                    token,0L,streams,slots);
            }catch(Exception denied){
                failure=denied.getClass().getSimpleName()+": "+
                    String.valueOf(denied.getMessage());
            }
            final NavFourSlotPolicy.State result=snapshot;
            final String problem=failure;
            main.post(()->{
                if(closed)return;
                initializing=false;starting=false;
                if(problem!=null){
                    fail("Los cuatro paneles no se activaron: "+problem);
                    NavOwnClusterProbe.restoreSingleFourSlot(owner,token);
                }else{
                    startOnUi(result,cluster);
                }
            });
        },"NAV-FourSlot-ReadOnly-Inventory").start();
    }
    private void startOnUi(NavFourSlotPolicy.State state,
                           NavOwnClusterProbe.FourSlotAnchor cluster){
        if(closed||active)return;
        try{
            final Map<String,NavFourSlotAndroidSurfaceDriver.RegisteredDisplay>
                records=new HashMap<>();
            records.put("maps",
                new NavFourSlotAndroidSurfaceDriver.RegisteredDisplay(
                    state.streams.get("maps"),mapsDisplay,
                    "DriveLabNAVMapa"));
            records.put("spotify",
                new NavFourSlotAndroidSurfaceDriver.RegisteredDisplay(
                    state.streams.get("spotify"),spotifyDisplay,
                    "DriveLabNAVSpotify"));
            records.put("waze",
                new NavFourSlotAndroidSurfaceDriver.RegisteredDisplay(
                    state.streams.get("waze"),cluster.virtual,
                    "DriveLabNAVClusterOwn"));
            final Map<String,String> displayNames=new HashMap<>();
            displayNames.put("maps","DriveLabNAVMapa");
            displayNames.put("spotify","DriveLabNAVSpotify");
            displayNames.put("waze","DriveLabNAVClusterOwn");
            NavFourSlotAndroidSurfaceDriver.Ownership proof=
                new NavFourSlotAndroidSurfaceDriver.Ownership(){
                    @Override public boolean liveSession(long nonce){
                        NavMosaicSessionRegistry.Session live=
                            NavMosaicSessionRegistry.snapshot(owner);
                        NavOwnClusterProbe.FourSlotAnchor waze=
                            NavOwnClusterProbe.fourSlotAnchor(owner,token);
                        return !closed&&live!=null&&live.token==token&&
                            nonce==token&&waze!=null&&
                            waze.display==cluster.display&&
                            waze.task==cluster.task;
                    }
                    @Override public boolean ownsDisplay(
                            NavFourSlotPolicy.Stream stream){
                        VirtualDisplay owned="maps".equals(stream.id)?
                            mapsDisplay:"spotify".equals(stream.id)?
                            spotifyDisplay:"waze".equals(stream.id)?
                            cluster.virtual:null;
                        return liveSession(stream.session)&&owned!=null&&
                            owned.getDisplay()!=null&&
                            stream.displayId==
                                owned.getDisplay().getDisplayId();
                    }
                };
            NavFourSlotAndroidSurfaceDriver.GeometryAndInput geo=
                (stream,slot)->{
                    CountDownLatch done=new CountDownLatch(1);
                    final boolean[] success={false};
                    main.post(()->{
                        try{
                            TextureView host=surfaces.get(slot);
                            if(host!=null&&host.isAvailable()){
                                if(slot==NavFourSlotPolicy.Slot.TABLERO_A||
                                   slot==NavFourSlotPolicy.Slot.TABLERO_B)
                                    NavOwnClusterProbe.layoutFourSlots(
                                        owner,token,true,true);
                                // Re-enable the destination before waiting
                                // for fresh frames after an empty-slot move.
                                host.setAlpha(1f);
                                NavFourSlotClusterABHost.fit(
                                    host,stream.width,stream.height);
                                success[0]=true;
                            }
                        }catch(RuntimeException ignored){}
                        finally{done.countDown();}
                    });
                    try{
                        return done.await(1800L,TimeUnit.MILLISECONDS)&&
                            success[0];
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();return false;
                    }
                };
            Map<NavFourSlotPolicy.Slot,Surface> borrowed=
                new EnumMap<>(NavFourSlotPolicy.Slot.class);
            borrowed.put(NavFourSlotPolicy.Slot.CENTRAL_A,mapsSink);
            borrowed.put(NavFourSlotPolicy.Slot.CENTRAL_B,spotifySink);
            borrowed.put(NavFourSlotPolicy.Slot.TABLERO_A,
                cluster.originalSink);
            driver=new NavFourSlotAndroidSurfaceDriver(
                owner,state,surfaces,records,proof,
                ()->{
                    try{
                        return BridgeLocalAdb.shell(owner,"am stack list");
                    }catch(Exception denied){
                        // ADB unavailable => no task proof => fail closed.
                        return null;
                    }
                },geo,
                borrowed);
            // Refresh both central slots' source geometry on swaps.
            NavFourSlotClusterABHost.fit(
                cluster.slotA,cluster.width,cluster.height);
            controls=new NavFourSlotControls(owner,
                mapsHost,spotifyHost,toolbar,
                (from,to,nonce,revision)->{
                    if(coordinator!=null)
                        coordinator.request(from,to,nonce,revision);
                });
            coordinator=new NavFourSlotSessionCoordinator(
                owner,state,displayNames,proof,driver,
                new NavFourSlotSessionCoordinator.TouchGate(){
                    @Override public boolean pauseAllCentralInput(){
                        if(closed||touchPaused)return false;
                        touchPaused=true;
                        return true;
                    }
                    @Override public void resumeCentralInput(){
                        if(!closed){
                            updateVisibleSlots();
                            touchPaused=false;
                        }
                    }
                },controls);
            active=true;
            updateVisibleSlots();
            reporter.stateChanged(true);
            reporter.status("CUATRO ESPACIOS preparados: Central A/B y "+
                "Tablero A/B. Tres aplicaciones existentes; el cuarto "+
                "espacio queda libre. Tocá ↗ para mover o intercambiar.");
        }catch(Exception failed){
            fail("Android rechazó cuatro paneles: "+
                failed.getClass().getSimpleName()+": "+
                String.valueOf(failed.getMessage()));
        }
    }
    private void updateVisibleSlots(){
        NavFourSlotLiveRouting.Snapshot s=
            NavFourSlotLiveRouting.snapshot(owner,token);
        if(s==null)return;
        NavFourSlotPolicy.Stream clusterA=s.state.at(
            NavFourSlotPolicy.Slot.TABLERO_A);
        NavFourSlotPolicy.Stream clusterB=s.state.at(
            NavFourSlotPolicy.Slot.TABLERO_B);
        NavOwnClusterProbe.layoutFourSlots(owner,token,
            clusterA!=null,clusterB!=null);
        for(NavFourSlotPolicy.Slot slot:NavFourSlotPolicy.Slot.values()){
            TextureView tv=surfaces.get(slot);
            if(tv!=null){
                NavFourSlotPolicy.Stream content=s.state.at(slot);
                // The cluster B host is kept ATTACHED when empty. When an
                // app moves in, prepare() makes its texture visible before
                // awaiting frames; after commit reflect the exact binding.
                tv.setAlpha(content==null?0f:1f);
                if(content!=null&&(slot==
                    NavFourSlotPolicy.Slot.TABLERO_A||
                    slot==NavFourSlotPolicy.Slot.TABLERO_B)){
                    final int sourceW=content.width,sourceH=content.height;
                    tv.post(()->NavFourSlotClusterABHost.fit(
                        tv,sourceW,sourceH));
                }
            }
        }
    }
    NavFourSlotPolicy.Stream centralStream(int panel){
        if(!isActive()||panel<0||panel>1)return null;
        NavFourSlotLiveRouting.TouchTarget t=
            NavFourSlotLiveRouting.central(owner,token,panel);
        return t==null?null:t.stream;
    }
    void frameCentral(int index){
        if(driver!=null&&index>=0&&index<=1)
            driver.onFrame(index==0?
                NavFourSlotPolicy.Slot.CENTRAL_A:
                NavFourSlotPolicy.Slot.CENTRAL_B);
    }
    private void fail(String message){
        starting=false;initializing=false;
        reporter.status(message);
        if(!active)NavOwnClusterProbe.restoreSingleFourSlot(owner,token);
        if(!owner.isFinishing()&&!owner.isDestroyed())
            new AlertDialog.Builder(owner)
                .setTitle("CUATRO PANELES · NO ACTIVADOS")
                .setMessage(message+"\n\nLAB 52 se mantiene sin cambios.")
                .setPositiveButton("ENTENDIDO",null).show();
    }
    /** The owner must already be shutting down its own verified tasks. */
    void closeAfterOwnerCleanup(){
        closed=true;starting=false;active=false;
        touchPaused=true;
        if(coordinator!=null){
            try{coordinator.closeAfterOwnerRelease();}
            catch(RuntimeException ignored){}
            coordinator=null;
        }else{
            NavFourSlotLiveRouting.clear(token);
            if(driver!=null)driver.releaseAfterOwnerClose();
        }
        driver=null;
        reporter.stateChanged(false);
    }
}
