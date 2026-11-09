package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;
import ar.com.rango5gps.drivelabauto.common.MapLinks;

/**
 * Receives a place explicitly shared inside the T1 and requests Waze navigation
 * on the SAME T1. No Xiaomi, network bridge, monitoring, vehicle OEM or CAN APIs.
 * Waze deeplink carries navigate=yes, unlike a generic ACTION_SEND to Waze.
 */
public final class ShareToClusterActivity extends Activity {
    private int generation;
    @Override public void onCreate(Bundle state){super.onCreate(state);handle(getIntent());}
    @Override protected void onNewIntent(Intent next){super.onNewIntent(next);setIntent(next);handle(next);}
    private void handle(Intent incoming){
        final int current=++generation;
        String raw=null;
        if(incoming!=null && Intent.ACTION_SEND.equals(incoming.getAction())){
            CharSequence share=incoming.getCharSequenceExtra(Intent.EXTRA_TEXT);
            CharSequence subject=incoming.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
            if(share!=null){
                raw=share.toString();
                // Some Maps versions send the place name separately in EXTRA_SUBJECT.
                if(subject!=null&&!subject.toString().trim().isEmpty() &&
                    MapLinks.extract(subject.toString())==null)
                    raw=subject.toString().trim()+"\n"+raw;
            }
        }else if(incoming!=null&&Intent.ACTION_VIEW.equals(incoming.getAction())&&
            incoming.getData()!=null)raw=incoming.getData().toString();
        if(raw==null||MapLinks.extract(raw)==null){
            error("Google Maps no compartió un enlace de lugar o destino.");
            return;
        }
        TextView progress=new TextView(this);
        progress.setText("Identificando destino de Google Maps para Waze del clúster…");
        progress.setTextSize(19);progress.setGravity(Gravity.CENTER);
        setContentView(progress);
        final String source=raw;
        new Thread(()->{
            MapLinks.Destination destination=MapLinks.resolve(source);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||generation!=current)return;
                if(destination==null){
                    error("No fue posible verificar el lugar compartido. "+
                        "No se enviarán coordenadas inventadas.");
                    return;
                }
                openWaze(destination);
            });
        },"T1-Maps-Share-Resolve").start();
    }
    private void openWaze(MapLinks.Destination destination){
        Intent navigation=new Intent(Intent.ACTION_VIEW,destination.wazeUri())
            .setPackage("com.waze");
        navigation.addCategory(Intent.CATEGORY_BROWSABLE);
        try{
            startActivity(navigation);
            String message="coord".equals(destination.kind)?
                "Navegación solicitada a Waze del clúster":
                "Destino enviado por nombre a Waze · verificá el resultado";
            Toast.makeText(this,message,Toast.LENGTH_LONG).show();
            finish();
        }catch(RuntimeException ex){
            error("No se pudo abrir Waze en la T1. Comprobá que esté instalado y "+
                "que el clúster tenga Waze habilitado.");
        }
    }
    private void error(String message){
        if(isFinishing()||isDestroyed())return;
        new AlertDialog.Builder(this).setTitle("DriveLab · Google Maps → Waze")
            .setMessage(message).setPositiveButton("CERRAR",(dialog,which)->finish())
            .setOnCancelListener(d->finish()).show();
    }
}
