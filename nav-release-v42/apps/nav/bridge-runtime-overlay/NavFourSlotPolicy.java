package ar.com.rango5gps.drivelabauto.boot;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * LAB47 SOURCE-ONLY prototype, NOT installed or staged in an Android APK.
 *
 * A/B of central touch display and A/B of physical cluster share a set of
 * NAV-owned VirtualDisplay content streams. Swaps change ONLY their render
 * slot bindings; do not use am display move-stack, force-stop, or relaunch.
 *
 * Android implementation must call VirtualDisplay.setSurface on each
 * verified NAV-owned stream and roll back before committing a new State.
 * Android does not provide an atomic two-display swap primitive.
 */
final class NavFourSlotPolicy {
    enum Slot {
        CENTRAL_A(true), CENTRAL_B(true),
        TABLERO_A(false), TABLERO_B(false);
        final boolean touch;
        Slot(boolean acceptsTouch){touch=acceptsTouch;}
    }
    enum ClusterLayout { EMPTY, SINGLE_FULL, SPLIT_AB }

    static final class Stream {
        final String id,pkg;
        final int displayId,taskId,stackId,width,height;
        final long session;
        Stream(String key,String packageName,int d,int task,int stack,
               int w,int h,long ownerSession){
            id=key;pkg=packageName;displayId=d;taskId=task;stackId=stack;
            width=w;height=h;session=ownerSession;
            if(id==null||!id.matches("[A-Za-z0-9_]{1,40}")||
               pkg==null||!pkg.matches(
                   "[a-z][a-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){1,8}")||
               pkg.equals("android")||pkg.startsWith("com.android.")||
               pkg.startsWith("com.jetour.")||pkg.startsWith("com.chery.")||
               d<2||d>99||task<=10||stack<=10||
               task>9999999||stack>9999999||
               w<240||h<240||w>4096||h>4096||ownerSession<=0)
                throw new SecurityException(
                    "Flujo o display ajeno a NAV / identificador inválido");
        }
    }
    static final class State {
        final long session,revision;
        final Map<String,Stream> streams;
        final EnumMap<Slot,String> bindings;
        State(long token,long rev,Map<String,Stream> owned,
              Map<Slot,String> position){
            if(token<=0||rev<0||owned==null||position==null||
               owned.size()>4)
                throw new SecurityException("Inventario NAV inválido");
            session=token;revision=rev;
            streams=Collections.unmodifiableMap(new HashMap<>(owned));
            bindings=new EnumMap<>(Slot.class);
            bindings.putAll(position);
            validate(this);
        }
        String idAt(Slot slot){return bindings.get(slot);}
        Stream at(Slot slot){return streams.get(idAt(slot));}
    }
    static final class Bind {
        final String stream;
        final Slot slot;
        Bind(String content,Slot target){stream=content;slot=target;}
    }
    static final class Swap {
        final State before,after;
        final Bind[] apply;
        final Bind[] rollback;
        final Slot from,to;
        Swap(State old,State next,Bind[] forward,Bind[] backward,
             Slot source,Slot destination){
            before=old;after=next;apply=forward;rollback=backward;
            from=source;to=destination;
        }
    }
    private NavFourSlotPolicy(){}

    static void validate(State state){
        if(state==null)throw new SecurityException("Sin sesión NAV");
        Set<Integer> displays=new HashSet<>();
        Set<Integer> tasks=new HashSet<>();
        Set<String> ids=new HashSet<>();
        for(Map.Entry<String,Stream> e:state.streams.entrySet()){
            Stream v=e.getValue();
            if(v==null||!e.getKey().equals(v.id)||
               v.session!=state.session||
               !displays.add(v.displayId)||!tasks.add(v.taskId))
                throw new SecurityException(
                    "VD/tarea duplicada o no perteneciente a NAV");
        }
        for(Slot slot:state.bindings.keySet())
            if(slot==null)throw new SecurityException("Panel desconocido");
        for(String stream:state.bindings.values()){
            if(stream!=null&&
               (!state.streams.containsKey(stream)||!ids.add(stream)))
                throw new SecurityException(
                    "Misma app visible dos veces o tarea ajena");
        }
        if(ids.size()!=state.streams.size())
            throw new SecurityException(
                "Cada flujo NAV debe estar en un único panel");
    }

    /** Operation plans are rejected rather than retargeting stale sessions. */
    static Swap swap(State before,Slot from,Slot to,
                     long expectedSession,long expectedRevision){
        validate(before);
        if(from==null||to==null||
           expectedSession!=before.session||
           expectedRevision!=before.revision||
           before.revision==Long.MAX_VALUE)
            throw new SecurityException(
                "Solicitud de intercambio desactualizada");
        if(from==to)
            return new Swap(before,before,new Bind[0],new Bind[0],from,to);
        String a=before.idAt(from),b=before.idAt(to);
        if(a==null)throw new SecurityException(
            "El panel origen no tiene app");
        EnumMap<Slot,String> next=new EnumMap<>(before.bindings);
        next.put(from,b);
        next.put(to,a);
        State after=new State(before.session,before.revision+1,
            before.streams,next);
        // Rebind surfaces, NOT task identities. No shell or foreign display.
        Bind[] apply=b==null?
            new Bind[]{new Bind(a,to)}:
            new Bind[]{new Bind(a,to),new Bind(b,from)};
        Bind[] rollback=b==null?
            new Bind[]{new Bind(a,from)}:
            new Bind[]{new Bind(a,from),new Bind(b,to)};
        return new Swap(before,after,apply,rollback,from,to);
    }

    static ClusterLayout clusterLayout(State state){
        validate(state);
        boolean a=state.at(Slot.TABLERO_A)!=null;
        boolean b=state.at(Slot.TABLERO_B)!=null;
        return a&&b?ClusterLayout.SPLIT_AB:
            a||b?ClusterLayout.SINGLE_FULL:ClusterLayout.EMPTY;
    }

    /**
     * The physical cluster has no touch input. Central touch must resolve
     * via CURRENT slot -> stream's own VirtualDisplay rather than fixed
     * legacy mapDisplay/mediaDisplay fields.
     */
    static int touchDisplay(State state,Slot slot){
        validate(state);
        if(slot==null||!slot.touch)
            throw new SecurityException("Tablero no táctil");
        Stream v=state.at(slot);
        if(v==null)throw new SecurityException("Panel central vacío");
        return v.displayId;
    }

    /** If the same app runs twice, require an exact, owner-chosen target. */
    static Stream senderTarget(State state,String pkg,Slot requested){
        validate(state);
        if(pkg==null)throw new SecurityException("Destino nulo");
        if(requested!=null){
            Stream v=state.at(requested);
            if(v==null||!pkg.equals(v.pkg))
                throw new SecurityException(
                    "La app no está en el destino explícito");
            return v;
        }
        Stream found=null;
        for(Stream v:state.streams.values()){
            if(pkg.equals(v.pkg)){
                if(found!=null)
                    throw new SecurityException(
                        "Hay dos instancias; elegí el panel de destino");
                found=v;
            }
        }
        if(found==null)throw new SecurityException(
            "App Sender no está en los paneles NAV");
        return found;
    }

    static String summary(State state){
        validate(state);
        StringBuilder out=new StringBuilder();
        for(Slot s:Slot.values()){
            Stream v=state.at(s);
            if(out.length()>0)out.append(" | ");
            out.append(s.name()).append("=")
                .append(v==null?"LIBRE":v.pkg+"#"+v.displayId);
        }
        return out.toString();
    }
}
