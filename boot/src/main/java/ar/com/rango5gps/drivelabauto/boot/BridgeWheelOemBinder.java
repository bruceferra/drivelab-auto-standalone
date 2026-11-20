package ar.com.rango5gps.drivelabauto.boot;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

/** Proprietary Jetour read-only wheel event subscription, no car writes.
 * Register/unregister are vendor interop transactions 1/2, event=3.
 * Callback is EXCLUSIVE on some ROMs: user opt-in and visible map only.
 * No automatic bind at vehicle boot; never grabs T1Launcher's wheel
 * without explicit owner approval.
 */
final class BridgeWheelOemBinder {
    interface Listener{
        void event(int code,int action,int extra);
        void status(String message);
    }
    private static final String SERVICE_PKG="com.autolink.hardkeyservice";
    private static final String SERVICE_CLS=
        "com.autolink.hardkeyservice.core.ALHardKeyService";
    private static final String SERVER_TOKEN=
        "com.autolink.adapterinterface.IHardKeyService";
    private static final String CALLBACK_TOKEN=
        "com.autolink.adapterinterface.IHardKeyCallback";
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean requested,registered,bound;
    private long requestAt;
    private IBinder server;
    private final IBinder callback=new Binder(){
        {attachInterface(null,CALLBACK_TOKEN);}
        @Override protected boolean onTransact(int transaction,Parcel data,
                                                 Parcel reply,int flags)
                throws RemoteException{
            if(transaction==INTERFACE_TRANSACTION){
                if(reply!=null)reply.writeString(CALLBACK_TOKEN);
                return true;
            }
            if(transaction!=3)return super.onTransact(transaction,data,reply,flags);
            data.enforceInterface(CALLBACK_TOKEN);
            int code=data.readInt(),action=data.readInt(),extra=data.readInt();
            if(reply!=null)reply.writeNoException();
            if(code<1000||code>1003)return true;
            main.post(()->{
                if(requested&&registered)listener.event(code,action,extra);
            });
            return true;
        }
    };
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName component,IBinder binder){
            if(!requested)return;
            server=binder;
            try{
                transact(1);
                registered=true;
                listener.status("OEM: rueda conectada; sólo mapas visibles en Display 1");
            }catch(Exception ex){
                listener.status("OEM no permitió registrar rueda: "+
                    ex.getClass().getSimpleName());
                stop();
            }
        }
        @Override public void onServiceDisconnected(ComponentName component){
            server=null;
            registered=false;
            listener.status("OEM: enlace de volante desconectado");
        }
    };
    BridgeWheelOemBinder(Context app,Listener listener){
        this.context=app.getApplicationContext();
        this.listener=listener;
    }
    boolean active(){return requested&&registered&&bound;}
    void start(){
        // Jetour may drop a registered vendor callback without completing
        // a clean disconnect. Do not stay permanently in requested=true.
        if(requested){
            if(registered)return;
            if(bound&&SystemClock.elapsedRealtime()-requestAt<6000L)return;
            stop(); // stale unregistered connection; retry only after timeout
        }
        requested=true;
        requestAt=SystemClock.elapsedRealtime();
        Intent intent=new Intent().setComponent(
            new ComponentName(SERVICE_PKG,SERVICE_CLS));
        try{
            bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);
            if(!bound){
                requested=false;
                listener.status("Servicio OEM hardkey no disponible");
            }
        }catch(Exception ex){
            bound=false;requested=false;
            listener.status("Error conectando volante OEM: "+
                ex.getClass().getSimpleName());
        }
    }
    private void transact(int transaction)throws RemoteException{
        if(server==null)throw new RemoteException("Sin binder OEM");
        Parcel p=Parcel.obtain(),response=Parcel.obtain();
        try{
            p.writeInterfaceToken(SERVER_TOKEN);
            p.writeStrongBinder(callback);
            if(!server.transact(transaction,p,response,0))
                throw new RemoteException("Transacción OEM rechazada "+transaction);
            response.readException();
        }finally{p.recycle();response.recycle();}
    }
    void stop(){
        requested=false;
        if(registered){
            try{transact(2);}catch(Exception ignored){}
        }
        registered=false;
        if(bound){
            try{context.unbindService(connection);}catch(Exception ignored){}
        }
        bound=false;server=null;
    }
}
