package ar.com.rango5gps.drivelabauto.sender;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import ar.com.rango5gps.drivelabauto.common.MapLinks;

/** Handles user-chosen Uber / Waze / Google Maps links without monitoring other apps. */
public final class RouteActivity extends Activity {
    private int generation=0;
    @Override public void onCreate(Bundle state){super.onCreate(state);route(getIntent());}
    @Override protected void onNewIntent(Intent incoming){super.onNewIntent(incoming);setIntent(incoming);route(incoming);}
    private void route(Intent intent){
        final int current=++generation;
        final String raw;
        if(intent!=null&&Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null)
            raw=intent.getData().toString();
        else if(intent!=null&&Intent.ACTION_SEND.equals(intent.getAction())){
            CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            raw=text==null?null:text.toString();
        }else raw=null;
        if(raw==null){dialog("No se recibió ningún enlace de navegación");return;}
        progress("Identificando destino…");
        new Thread(()->{
            MapLinks.Destination destination=MapLinks.resolve(raw);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||current!=generation)return;
                if(destination==null){dialog("Google Maps o Uber no entregó un destino verificable. "+
                    "No voy a inventar coordenadas.");return;}
                deliver(destination,current);
            });
        },"Sender-Resolve-Link").start();
    }
    private void deliver(MapLinks.Destination d,int current){
        boolean only=SenderClient.prefs(this).getBoolean("only_cluster",true);
        if(!SenderClient.configured(this)){
            fallback(d,"El Receiver no está vinculado. No se abrió Waze automáticamente.");return;
        }
        if(only)progress("Enviando destino cifrado al clúster…");
        else openWaze(d);
        new Thread(()->{
            SenderClient.Delivery result=SenderClient.send(this,d.kind,d.value);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||generation!=current)return;
                if(!only){finish();return;}
                if(result.accepted){
                    Toast.makeText(this,"Destino recibido por T1",Toast.LENGTH_SHORT).show();finish();
                }else fallback(d,result.status);
            });
        },"Sender-Deliver").start();
    }
    private void openWaze(MapLinks.Destination dest){
        Intent view=new Intent(Intent.ACTION_VIEW,dest.wazeUri());
        view.setPackage("com.waze");view.addCategory(Intent.CATEGORY_BROWSABLE);
        try{startActivity(view);}catch(Exception e){
            Toast.makeText(this,"Waze no se abrió en Xiaomi",Toast.LENGTH_LONG).show();
        }
    }
    private void fallback(MapLinks.Destination d,String reason){
        new AlertDialog.Builder(this).setTitle("Destino no confirmado en T1")
            .setMessage(reason+"\n\n¿Querés abrir Waze en el Xiaomi?")
            .setPositiveButton("ABRIR WAZE EN XIAOMI",(dialog,which)->{openWaze(d);finish();})
            .setNegativeButton("CERRAR",(dialog,which)->finish())
            .setOnCancelListener(x->finish()).show();
    }
    private void dialog(String reason){new AlertDialog.Builder(this)
        .setMessage(reason).setPositiveButton("OK",(d,w)->finish())
        .setOnCancelListener(d->finish()).show();}
    private void progress(String s){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(19);
        t.setGravity(Gravity.CENTER);setContentView(t);
    }
}
