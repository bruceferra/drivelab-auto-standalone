package ar.com.rango5gps.drivelabauto.receiver;

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

/**
 * Independent interoperability implementation for T1 OEM hard-key Binder.
 * Discovered from the owner's original T1Launcher T1N134 APK:
 * service com.autolink.hardkeyservice.core.ALHardKeyService,
 * IHardKeyService transaction 1 register / 2 unregister,
 * IHardKeyCallback transaction 3 with (key,action,extra) integers.
 *
 * Registration can be EXCLUSIVE on vendor ROMs: NEVER start at boot or
 * without an explicit user opt-in, and unbind immediately outside Waze.
 * Neither sends car-property writes nor runs ADB. No event interception
 * outside codes 1000..1003. Any failure leaves stock controls intact.
 */
final class T1OemHardKeyBridge {
    interface Receiver {
        void onHardKey(int key,int action,int extra);
        void onStatus(String text);
    }
    private static final String SERVICE_PKG="com.autolink.hardkeyservice";
    private static final String SERVICE_NAME=
        "com.autolink.hardkeyservice.core.ALHardKeyService";
    private static final String SERVER_TOKEN=
        "com.autolink.adapterinterface.IHardKeyService";
    private static final String CALLBACK_TOKEN=
        "com.autolink.adapterinterface.IHardKeyCallback";
    private static final int REGISTER=1,UNREGISTER=2,EVENT=3;
    private final Context app;
    private final Receiver receiver;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean bound,requested,registered;
    private IBinder service;
    private final IBinder callback=new Binder(){
        { attachInterface(null,CALLBACK_TOKEN); }
        @Override protected boolean onTransact(int code,Parcel data,
                                                Parcel reply,int flags)
            throws RemoteException{
            if(code==INTERFACE_TRANSACTION){
                if(reply!=null)reply.writeString(CALLBACK_TOKEN);
                return true;
            }
            if(code==EVENT){
                data.enforceInterface(CALLBACK_TOKEN);
                int key=data.readInt(),action=data.readInt(),extra=data.readInt();
                if(reply!=null)reply.writeNoException();
                if(key>=1000&&key<=1003)
                    main.post(()->{
                        if(requested&&registered)
                            receiver.onHardKey(key,action,extra);
                    });
                return true;
            }
            if(code==1||code==2){
                data.enforceInterface(CALLBACK_TOKEN);
                if(reply!=null)reply.writeNoException();
                return true;
            }
            return super.onTransact(code,data,reply,flags);
        }
    };
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(!requested)return;
            service=binder;
            try{
                call(REGISTER);
                registered=true;
                status("Binder OEM enlazado · códigos 1000–1003 · "+
                    "verificá los controles de T1Launcher");
            }catch(Exception e){
                status("Binder OEM no autorizado o rechazado: "+
                    e.getClass().getSimpleName());
                stop();
            }
        }
        @Override public void onServiceDisconnected(ComponentName name){
            service=null;
            registered=false;
            status("Binder OEM desconectado; control Waze suspendido");
        }
    };
    T1OemHardKeyBridge(Context context,Receiver receiver){
        this.app=context.getApplicationContext();
        this.receiver=receiver;
    }
    private void status(String text){receiver.onStatus(text);}
    boolean registered(){return registered&&bound&&requested;}
    void start(){
        if(requested)return;
        requested=true;
        Intent intent=new Intent();
        intent.setComponent(new ComponentName(SERVICE_PKG,SERVICE_NAME));
        try{
            bound=app.bindService(intent,connection,Context.BIND_AUTO_CREATE);
            if(!bound){
                requested=false;
                status("Servicio OEM hardkey no disponible en esta ROM");
            }else status("Conectando Binder OEM · solo mientras Waze esté visible");
        }catch(Exception ex){
            requested=false;bound=false;
            status("Acceso Binder no permitido: "+ex.getClass().getSimpleName());
        }
    }
    private void call(int transaction)throws RemoteException{
        if(service==null)throw new RemoteException("Sin Binder OEM");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(SERVER_TOKEN);
            data.writeStrongBinder(callback);
            if(!service.transact(transaction,data,reply,0))
                throw new RemoteException("OEM rechazó transact "+transaction);
            reply.readException();
        }finally{data.recycle();reply.recycle();}
    }
    void stop(){
        if(!requested&&!bound)return;
        requested=false;
        if(registered){
            try{call(UNREGISTER);}
            catch(Exception ignored){}
        }
        registered=false;
        if(bound){
            try{app.unbindService(connection);}catch(Exception ignored){}
        }
        bound=false;service=null;
        status("Binder Waze desconectado; no se toca Google Maps");
    }
}
