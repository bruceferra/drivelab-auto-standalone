package ar.com.rango5gps.drivelabauto.boot;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * NAV52 SOURCE-ONLY session routing for the two central touch panels.
 *
 * The historical touch adapter assumed Central A always showed Maps and
 * Central B always showed Spotify. A four-panel swap MUST route gestures
 * to the app's current owned VirtualDisplay instead.
 *
 * Never register physical display 0/1 or an unidentified VirtualDisplay.
 * Publishing is only permitted after the transaction coordinator verified
 * its frame AND task identities.
 */
final class NavFourSlotLiveRouting {
    private static final String LAB=
        "ar.com.rango5gps.drivelabauto.boot.lab";
    static final class Snapshot {
        final NavFourSlotPolicy.State state;
        final Map<String,String> displayNames;
        final NavFourSlotAndroidSurfaceDriver.Ownership owner;
        Snapshot(NavFourSlotPolicy.State fresh,Map<String,String> names,
                 NavFourSlotAndroidSurfaceDriver.Ownership proof){
            state=fresh;displayNames=Collections.unmodifiableMap(
                new HashMap<>(names));owner=proof;
        }
    }
    static final class TouchTarget {
        final NavFourSlotPolicy.Stream stream;
        final String displayName;
        TouchTarget(NavFourSlotPolicy.Stream s,String n){
            stream=s;displayName=n;
        }
    }
    private static volatile Snapshot current;
    private NavFourSlotLiveRouting(){}

    private static boolean supported(Context app,Snapshot record){
        if(app==null||record==null||!LAB.equals(app.getPackageName()))
            return false;
        NavMosaicSessionRegistry.Session center=
            NavMosaicSessionRegistry.snapshot(app);
        if(center==null||center.token!=record.state.session||
           !record.owner.liveSession(center.token))return false;
        DisplayManager dm=(DisplayManager)
            app.getSystemService(Context.DISPLAY_SERVICE);
        if(dm==null)return false;
        for(NavFourSlotPolicy.Stream stream:record.state.streams.values()){
            String expected=record.displayNames.get(stream.id);
            if(expected==null||!expected.startsWith("DriveLabNAV")||
               !record.owner.ownsDisplay(stream))return false;
            Display d=dm.getDisplay(stream.displayId);
            if(d==null||d.getDisplayId()<2||d.getDisplayId()>99||
               d.getState()==Display.STATE_OFF||
               (d.getFlags()&Display.FLAG_PRIVATE)!=0||
               !expected.equals(d.getName()))return false;
        }
        return true;
    }
    static synchronized void publish(
            Context app,NavFourSlotPolicy.State verified,
            Map<String,String> displayNames,
            NavFourSlotAndroidSurfaceDriver.Ownership proof){
        if(verified==null||displayNames==null||proof==null||
           verified.streams.size()!=displayNames.size())
            throw new SecurityException("Estado de cuatro paneles incompleto");
        // Make a private defensive copy: never permit external mutation of
        // the mutable EnumMap inside the source State after publication.
        NavFourSlotPolicy.State copy=new NavFourSlotPolicy.State(
            verified.session,verified.revision,verified.streams,
            verified.bindings);
        Snapshot snapshot=new Snapshot(copy,displayNames,proof);
        if(!supported(app,snapshot))
            throw new SecurityException(
                "Display/rutas NAV cambiaron durante el intercambio");
        current=snapshot;
    }
    static Snapshot snapshot(Context app,long token){
        Snapshot record=current;
        return record!=null&&record.state.session==token&&
            supported(app,record)&&current==record?record:null;
    }
    static boolean armed(long token){
        Snapshot found=current;
        return found!=null&&found.state.session==token;
    }
    static TouchTarget central(Context app,long token,int panel){
        if(panel<0||panel>1)return null;
        Snapshot record=snapshot(app,token);
        if(record==null)return null;
        NavFourSlotPolicy.Stream stream=record.state.at(
            panel==0?NavFourSlotPolicy.Slot.CENTRAL_A:
                     NavFourSlotPolicy.Slot.CENTRAL_B);
        if(stream==null)return null;
        String name=record.displayNames.get(stream.id);
        return name==null?null:new TouchTarget(stream,name);
    }
    static synchronized void clear(long token){
        Snapshot record=current;
        if(record!=null&&record.state.session==token)current=null;
    }
}
