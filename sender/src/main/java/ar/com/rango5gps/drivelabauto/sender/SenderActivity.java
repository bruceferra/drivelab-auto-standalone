package ar.com.rango5gps.drivelabauto.sender;

import android.app.*;
import android.os.*;
import android.content.*;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import ar.com.rango5gps.drivelabauto.common.MapLinks;
import ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget;
import ar.com.rango5gps.drivelabauto.common.Updater;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.barcode.common.Barcode;

/** User-facing Sender controls only: no vehicle telemetry or OEM access. */
public final class SenderActivity extends Activity {
    private static final int NAVY=0xFF111821,TEAL=0xFF108A80;
    private static final int REQUEST_BT_CONNECT=2195;
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
        title(body,"DESTINO PREDETERMINADO DE UBER",18);
        String navMode=SenderRouteTarget.selected(this);
        title(body,"Al tocar NAVEGAR: "+SenderRouteTarget.label(navMode)+
            "\\nSe guarda la elección. No pregunta en cada viaje.",14);
        button(body,"ELEGIR DESTINO · WAZE / GOOGLE / AMBOS",
            this::chooseNavigationTarget);
        boolean paired=SenderClient.configured(this);
        title(body,paired?"T1 VINCULADA":"T1 SIN VINCULAR",18);
        if(paired)title(body,"IP receptor: "+SenderClient.prefs(this).getString("receiver_ip",""),14);
        title(body,"Envíos confirmados: "+SenderClient.prefs(this).getInt("sent",0)+
            "\nÚltimo resultado: "+SenderClient.prefs(this).getString("last_status","Sin pruebas"),14);
        String routeState=getSharedPreferences(
            RouteDispatchService.STATE_PREFS,MODE_PRIVATE).getString(
                RouteDispatchService.LAST_STATE,"");
        if(!routeState.isEmpty())title(body,"Última navegación desde Uber: "+
            routeState,13);
        button(body,"VINCULAR T1 POR QR · SIN CÓDIGOS",this::scanPairQr);
        button(body,"Vincular manualmente · 6 dígitos (respaldo)",this::pair);
        button(body,"Probar envío real a La Plata",this::test);
        button(body,"Pegar enlace de Google Maps / WhatsApp",this::paste);
        title(body,"AUDIO DE LA T1 · ACTIVAR SIN SPOTIFY",18);
        android.content.SharedPreferences audio=SenderAudioBootstrap.prefs(this);
        String selected=audio.getString(SenderAudioBootstrap.LABEL,"Sin vehículo elegido");
        boolean enabled=SenderAudioBootstrap.configured(this);
        title(body,"Bluetooth emparejado: "+selected+
            "\nPrueba automática: "+(enabled?"ACTIVA (solo la T1)":"NO CONFIGURADA")+
            "\nÚltima prueba: "+audio.getString(SenderAudioBootstrap.STATUS,"Sin prueba"),13);
        title(body,"El selector muestra ÚNICAMENTE dispositivos "+
            "que ya están emparejados con el Xiaomi. Si la T1 no aparece, "+
            "abrí Ajustes Bluetooth y emparejala allí; después volvé "+
            "a seleccionar la T1 en DriveLab. No es la vinculación Wi-Fi "+
            "del Receiver.",13);
        title(body,"La primera vez elegí el Bluetooth REAL de la T1. "+
            "Cuando el Xiaomi detecte su conexión A2DP, Sender intentará "+
            "una reproducción suave de 1,4 segundos, sin abrir Spotify. "+
            "La radio puede requerir que la fuente Bluetooth esté elegida. "+
            "El resultado físico aún debe probarse.",13);
        button(body,"1 · ELEGIR T1 EMPAREJADA · ACTIVAR AUTO",
            this::chooseCarAudio);
        button(body,"ABRIR AJUSTES BLUETOOTH DEL XIAOMI",
            this::openBluetoothSettings);
        button(body,"2 · PROBAR AHORA · SIN ABRIR SPOTIFY",
            this::testCarAudio);
        if(enabled)button(body,"DESACTIVAR INICIO DE AUDIO T1",
            ()->{
                audio.edit().putBoolean(SenderAudioBootstrap.ENABLED,false)
                    .putString(SenderAudioBootstrap.STATUS,
                        "Inicio automático desactivado por usuario").apply();
                render();
            });
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
    private void chooseNavigationTarget(){
        String current=SenderRouteTarget.selected(this);
        final String[] choices={"Waze","Google Maps","Ambos simultáneamente"};
        int selected=BridgeRouteTarget.MAPS.equals(current)?1:
            BridgeRouteTarget.BOTH.equals(current)?2:0;
        new AlertDialog.Builder(this)
            .setTitle("NAVEGACIÓN DE UBER EN LA T1")
            .setSingleChoiceItems(choices,selected,(dialog,which)->{
                SenderRouteTarget.choose(this,which==1?
                    BridgeRouteTarget.MAPS:which==2?
                    BridgeRouteTarget.BOTH:BridgeRouteTarget.WAZE);
                dialog.dismiss();render();
            })
            .setNegativeButton("CANCELAR",null).show();
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
            SenderClient.Delivery result=SenderClient.send(this,
                BridgeRouteTarget.sendKind("coord",SenderRouteTarget.selected(this)),
                "-34.9214,-57.9545");
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
    private boolean hasBtPermission(){
        return SenderAudioBootstrap.permission(this);
    }
    private void chooseCarAudio(){
        if(!hasBtPermission()){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                REQUEST_BT_CONNECT);
            return;
        }
        final BluetoothAdapter adapter;
        try{
            BluetoothManager manager=(BluetoothManager)
                getSystemService(BLUETOOTH_SERVICE);
            adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){
                info("Activá Bluetooth del Xiaomi para elegir la T1");
                return;
            }
        }catch(SecurityException ex){
            info("Android no autorizó consultar Bluetooth");return;
        }
        final ArrayList<BluetoothDevice> devices=new ArrayList<>();
        final ArrayList<String> labels=new ArrayList<>();
        try{
            // Paired devices only; no BLUETOOTH_SCAN, no location.
            for(BluetoothDevice device:adapter.getBondedDevices()){
                String name=device.getName();
                if(name==null||name.trim().isEmpty())name="Dispositivo sin nombre";
                String mac=device.getAddress();
                if(mac==null||mac.length()<5)continue;
                devices.add(device);
                labels.add(name+" ("+mac.substring(mac.length()-5)+")");
            }
        }catch(SecurityException error){
            info("Falta autorizar Dispositivos cercanos");return;
        }
        if(devices.isEmpty()){
            new AlertDialog.Builder(this)
                .setTitle("NINGÚN BLUETOOTH EMPAREJADO")
                .setMessage("El selector solo muestra equipos ya vinculados "+
                    "con Android. Primero emparejá la Jetour T1 desde "+
                    "Ajustes Bluetooth del Xiaomi y volvé a elegirla.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("ABRIR BLUETOOTH",(d,w)->
                    openBluetoothSettings())
                .show();
            return;
        }
        // DO NOT mix setMessage() and setItems(): Android's AlertDialog
        // silently hides the selectable device list (only CANCELAR remains).
        // This caused the exact Xiaomi 17T Pro bug reported in Sender 0.2.17.
        new AlertDialog.Builder(this)
            .setTitle("ELEGIR T1 · "+devices.size()+" DISPOSITIVOS")
            .setItems(labels.toArray(new String[0]),(dialog,which)->{
                BluetoothDevice chosen=devices.get(which);
                try{
                    SenderAudioBootstrap.prefs(this).edit()
                        .putString(SenderAudioBootstrap.ADDRESS,
                            chosen.getAddress())
                        .putString(SenderAudioBootstrap.LABEL,
                            labels.get(which))
                        .putBoolean(SenderAudioBootstrap.ENABLED,true)
                        .putLong(SenderAudioBootstrap.ATTEMPT_MS,0L)
                        .putString(SenderAudioBootstrap.STATUS,
                            "T1 elegida. Esperando próxima conexión A2DP. "+
                            "Se puede probar ahora, con el auto detenido.")
                        .apply();
                    render();
                }catch(SecurityException error){
                    info("Android impidió guardar selección Bluetooth");
                }
            })
            .setNeutralButton("AJUSTES BLUETOOTH",(dialog,which)->
                openBluetoothSettings())
            .setNegativeButton("CANCELAR",null).show();
    }
    /** System pairing UI: app only chooses an already-bonded car, it never
     * scans or initiates Bluetooth pairing to an unknown address. */
    private void openBluetoothSettings(){
        try{
            startActivity(new Intent(
                android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
        }catch(RuntimeException error){
            info("Android no pudo abrir Ajustes Bluetooth: "+
                error.getClass().getSimpleName());
        }
    }
    private void testCarAudio(){
        if(!hasBtPermission()){
            info("Primero autorizá Dispositivos cercanos y elegí la T1");
            return;
        }
        if(!SenderAudioBootstrap.configured(this)){
            info("Primero elegí el Bluetooth de la Jetour T1");
            return;
        }
        info("Buscando salida A2DP del Xiaomi, sin abrir Spotify…");
        new Thread(()->{
            SenderAudioBootstrap.prime(getApplicationContext(),false);
            String result=SenderAudioBootstrap.prefs(this)
                .getString(SenderAudioBootstrap.STATUS,"Resultado no disponible");
            runOnUiThread(()->info(result));
        },"Sender-T1-Audio-Test").start();
    }
    @Override public void onRequestPermissionsResult(int requestCode,
        String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode!=REQUEST_BT_CONNECT)return;
        if(grantResults.length>0&&grantResults[0]==
                PackageManager.PERMISSION_GRANTED)
            chooseCarAudio();
        else info("Sin permiso de dispositivos cercanos no se puede "+
            "identificar la T1; no se iniciará audio automático.");
    }

}
