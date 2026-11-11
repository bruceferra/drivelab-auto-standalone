package ar.com.rango5gps.drivelabauto.sender;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import ar.com.rango5gps.drivelabauto.common.MapLinks;
import ar.com.rango5gps.drivelabauto.common.Updater;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.barcode.common.Barcode;

/** User-facing Sender controls only: no vehicle telemetry or OEM access. */
public final class SenderActivity extends Activity {
    private static final int NAVY=0xFF111821,TEAL=0xFF108A80;
    private String status="";
    private ScrollView currentScroll;
    private long lastAutoCheckElapsed=0;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);render();acceptQrIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);acceptQrIntent(intent);
    }
    @Override public void onResume(){
        super.onResume();
        render();
        Updater.onResume(this,this::info);
        long now=android.os.SystemClock.elapsedRealtime();
        if(lastAutoCheckElapsed==0||now-lastAutoCheckElapsed>120000){
            lastAutoCheckElapsed=now;
            Updater.checkOnOpen(this,"sender",this::info);
        }
    }
    private void title(LinearLayout body,String s,int size){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);
        t.setTextColor(NAVY);t.setPadding(8,12,8,12);body.addView(t);
    }
    private void button(LinearLayout body,String text,Runnable work){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);
        b.setOnClickListener(v->work.run());body.addView(b);
    }
    private void info(String text){status=text;render();}
    private void render(){
        final int restoreY=currentScroll==null?0:currentScroll.getScrollY();
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(25,25,25,40);scroll.addView(body);
        title(body,"DRIVELAB AUTO · SENDER",24);
        title(body,"Xiaomi · Navegación Uber / Google Maps / Waze",14);
        boolean only=SenderClient.prefs(this).getBoolean("only_cluster",true);
        title(body,only?"MODO: SOLO CLÚSTER":"MODO: CLÚSTER + XIAOMI",18);
        button(body,only?"Cambiar a clúster + Xiaomi":"Cambiar a solo clúster",()->{
            SenderClient.prefs(this).edit().putBoolean("only_cluster",!only).apply();render();
        });
        boolean paired=SenderClient.configured(this);
        title(body,paired?"T1 VINCULADA":"T1 SIN VINCULAR",18);
        if(paired)title(body,"IP receptor: "+SenderClient.prefs(this).getString("receiver_ip",""),14);
        title(body,"Envíos confirmados: "+SenderClient.prefs(this).getInt("sent",0)+
            "\nÚltimo resultado: "+SenderClient.prefs(this).getString("last_status","Sin pruebas"),14);
        button(body,"VINCULAR T1 POR QR · SIN CÓDIGOS",this::scanPairQr);
        button(body,"Vincular manualmente · 6 dígitos (respaldo)",this::pair);
        button(body,"Probar envío real a La Plata",this::test);
        button(body,"Pegar enlace de Google Maps / WhatsApp",this::paste);
        button(body,"ACTUALIZAR SENDER · INSTALACIÓN DIRECTA",()->Updater.check(this,"sender",this::info));
        if(!status.isEmpty())title(body,"Actualizador / estado: "+status,14);
        button(body,"Desvincular Receiver",()->new AlertDialog.Builder(this)
            .setMessage("¿Desvincular? La próxima vez tendrás que comparar los 6 dígitos.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("DESVINCULAR",(d,w)->{SenderClient.unpair(this);render();}).show());
        title(body,"En Uber: elegí DriveLab Auto Sender para enviar destinos. "+
            "Ahora la pantalla de Uber queda visible: el envío al clúster se realiza "+
            "sin mostrar DriveLab. Los fallos aparecen en una notificación. "+
            "Modo Solo clúster no abre Waze en Xiaomi.",13);
        setContentView(scroll);
        currentScroll=scroll;
        if(restoreY>0)scroll.post(()->{
            if(currentScroll==scroll)scroll.scrollTo(0,restoreY);
        });
    }
    private void acceptQrIntent(Intent intent){
        if(intent==null||intent.getData()==null)return;
        String qr=intent.getDataString();
        if(qr!=null&&qr.startsWith("drivelabauto://pair?"))
            beginPairQr(qr);
    }
    private void scanPairQr(){
        try{
            GmsBarcodeScannerOptions options=new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build();
            GmsBarcodeScanning.getClient(this,options).startScan()
                .addOnSuccessListener(barcode->{
                    String raw=barcode.getRawValue();
                    if(raw==null||!raw.startsWith("drivelabauto://pair?")){
                        info("Ese QR no corresponde a DriveLab Auto. "+
                            "Escaneá el de VINCULAR XIAOMI de la T1.");return;
                    }
                    beginPairQr(raw);
                })
                .addOnCanceledListener(()->info("Lectura QR cancelada"))
                .addOnFailureListener(e->info("Escáner QR no disponible: "+
                    e.getClass().getSimpleName()+
                    ". Probá método manual o revisá Servicios de Google Play."));
        }catch(Exception e){
            info("No se pudo abrir el lector QR: "+e.getClass().getSimpleName()+
                ". Usá vinculación manual como alternativa.");
        }
    }
    private void beginPairQr(String qr){
        info("QR leído · conectando con T1 por Wi-Fi…");
        new Thread(()->{
            try{
                SenderClient.Session s=SenderClient.beginPairingQr(qr);
                SenderClient.finishPairing(this,s);
                runOnUiThread(()->info(
                    "T1 VINCULADA POR QR · cifrado activo · sin PIN ni códigos"));
            }catch(Exception ex){
                runOnUiThread(()->info("No se pudo vincular por QR: "+
                    ex.getClass().getSimpleName()+
                    (ex.getMessage()==null?"":" · "+ex.getMessage())));
            }
        },"Sender-QR-Pair").start();
    }
    private void pair(){
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setHint("IP del Receiver, por ej. 192.168.43.120");
        field.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        field.setText(SenderClient.prefs(this).getString("receiver_ip",""));
        new AlertDialog.Builder(this).setTitle("Vincular T1")
            .setMessage("En la T1 iniciá el Receiver y tocá VINCULAR XIAOMI · 6 DÍGITOS.")
            .setView(field).setNegativeButton("CANCELAR",null)
            .setPositiveButton("MOSTRAR CÓDIGO",(d,w)->{
                String ip=field.getText().toString().trim();
                new Thread(()->{
                    try{
                        SenderClient.Session session=SenderClient.beginPairing(ip);
                        runOnUiThread(()->showCode(session));
                    }catch(Exception e){runOnUiThread(()->info("Sin vinculación: "+e.getMessage()));}
                },"Sender-Pair-Begin").start();
            }).show();
    }
    private void showCode(SenderClient.Session s){
        new AlertDialog.Builder(this).setTitle("CÓDIGO: "+s.code)
            .setMessage("Comprobá que la T1 muestre los mismos seis números. "+
                "Primero tocá AUTORIZAR en la T1; después CONFIRMAR aquí.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("COINCIDEN · CONFIRMAR",(d,w)->new Thread(()->{
                try{SenderClient.finishPairing(this,s);
                    runOnUiThread(()->info("T1 vinculada · llave fuerte cifrada guardada"));}
                catch(Exception error){runOnUiThread(()->info("Sin vinculación: "+error.getMessage()));}
            },"Sender-Pair-Finish").start()).show();
    }
    private void test(){
        if(!SenderClient.configured(this)){info("Primero vinculá el Receiver");return;}
        new Thread(()->{
            SenderClient.Delivery result=SenderClient.send(this,"coord","-34.9214,-57.9545");
            runOnUiThread(()->info(result.status));
        },"Sender-Real-Test").start();
    }
    private void paste(){
        android.content.ClipboardManager cb=getSystemService(android.content.ClipboardManager.class);
        if(cb==null||!cb.hasPrimaryClip()){info("Primero copiá un enlace de mapas");return;}
        android.content.ClipData clip=cb.getPrimaryClip();
        if(clip==null||clip.getItemCount()==0){info("Portapapeles vacío");return;}
        CharSequence raw=clip.getItemAt(0).coerceToText(this);
        if(raw==null){info("No hay enlace compatible");return;}
        Intent send=new Intent(this,RouteActivity.class);
        send.setAction(Intent.ACTION_SEND);send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,raw.toString());startActivity(send);
    }
}
