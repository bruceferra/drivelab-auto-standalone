package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * PARKED-ONLY, manual and separately consented MediaProjection experiment.
 * The Jetour Center Display0 is mirrored (not Waze in a separate task)
 * to the already-proven lower-band Presentation on physical Display1.
 */
public final class BridgeProjectionLabActivity extends Activity {
    private static final int REQUEST_CAPTURE=8129;
    private boolean consentPending;
    private TextView status;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        BridgeImmersive.apply(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20,20,20,20);
        root.setBackgroundColor(0xFF101D27);
        TextView title=new TextView(this);
        title.setText("BRIDGE LAB · CAPTURA AUTORIZADA · DISPLAY 1");
        title.setTextSize(20);
        title.setTextColor(0xFF40DFCD);
        root.addView(title);
        TextView message=new TextView(this);
        message.setText("SÓLO CON LA JETOUR ESTACIONADA. Android pedirá permiso "+
            "explícito para compartir la pantalla CENTRAL COMPLETA durante "+
            "20 segundos. Bridge mostrará sólo una COPIA en la franja "+
            "inferior del tablero, no Waze independiente. "+
            "Cualquier dato o notificación visible en la central podría "+
            "verse en el tablero. No se graba, transmite, guarda ni "+
            "comparte por Internet. NO autorizamos Waze en un Display "+
            "virtual. Si Android rechaza el permiso, no se captura nada. "+
            "No tapa a propósito la franja superior de avisos del vehículo.");
        message.setTextColor(Color.WHITE);
        message.setTextSize(15);
        root.addView(message);
        status=new TextView(this);
        status.setTextColor(0xFF40DFCD);
        status.setTextSize(15);
        status.setPadding(0,15,0,15);
        root.addView(status);
        Button start=new Button(this);
        start.setAllCaps(false);
        start.setText("SOLICITAR PERMISO ANDROID · PRUEBA 20 S");
        start.setOnClickListener(v->requestConsent());
        root.addView(start);
        Button stop=new Button(this);
        stop.setAllCaps(false);
        stop.setText("DETENER PROYECCIÓN AHORA");
        stop.setOnClickListener(v->{
            Intent action=new Intent(this,BridgeProjectionLabService.class)
                .setAction(BridgeProjectionLabService.ACTION_STOP);
            startService(action);
            status.setText("Se solicitó detener la captura.");
        });
        root.addView(stop);
        TextView result=new TextView(this);
        result.setTextColor(Color.WHITE);
        result.setTextSize(14);
        result.setPadding(0,20,0,0);
        result.setText("ÚLTIMO RESULTADO: "+
            BridgeProjectionLabService.lastResult(this));
        root.addView(result);
        setContentView(root);
        status.setText("No hay captura activa. Requiere permiso por sesión.");
    }
    private void requestConsent(){
        if(consentPending)return;
        MediaProjectionManager manager=(MediaProjectionManager)
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if(manager==null){
            BridgeProjectionLabService.report(this,
                "PERMISO NO DISPONIBLE: MediaProjectionManager ausente.");
            status.setText("Android no ofrece MediaProjection.");
            return;
        }
        try{
            consentPending=true;
            startActivityForResult(manager.createScreenCaptureIntent(),REQUEST_CAPTURE);
            status.setText("Esperando autorización explícita de Android.");
        }catch(RuntimeException error){
            consentPending=false;
            BridgeProjectionLabService.report(this,
                "CONSENTIMIENTO NO MOSTRADO: "+error.getClass().getSimpleName());
            status.setText("No se pudo abrir la autorización de Android.");
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_CAPTURE)return;
        consentPending=false;
        if(resultCode!=RESULT_OK||data==null){
            BridgeProjectionLabService.report(this,
                "PROYECCIÓN DENEGADA O CANCELADA POR EL USUARIO.");
            status.setText("Permiso no concedido. Sin captura.");
            return;
        }
        try{
            Intent svc=new Intent(this,BridgeProjectionLabService.class)
                .setAction(BridgeProjectionLabService.ACTION_START);
            svc.putExtra(BridgeProjectionLabService.EXTRA_RESULT_CODE,resultCode);
            svc.putExtra(BridgeProjectionLabService.EXTRA_RESULT_DATA,data);
            startForegroundService(svc);
            status.setText("Permiso recibido. Prueba iniciada: "+
                "abrí Waze MANUALMENTE en la pantalla central. "+
                "La captura se corta a los 20 segundos.");
        }catch(RuntimeException error){
            BridgeProjectionLabService.report(this,
                "NO SE PUDO INICIAR SERVICIO: "+error.getClass().getSimpleName());
            status.setText("Android rechazó iniciar el servicio de proyección.");
        }
    }
}
