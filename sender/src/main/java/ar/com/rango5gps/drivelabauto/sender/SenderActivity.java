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
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import ar.com.rango5gps.drivelabauto.common.MapLinks;
import ar.com.rango5gps.drivelabauto.common.BridgeRouteTarget;
import ar.com.rango5gps.drivelabauto.common.Updater;
import ar.com.rango5gps.drivelabauto.common.SenderCloudOta;
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
    private long lastBridgeReconnectElapsed=0;
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
        if(SenderClient.configured(this)&&
            (lastBridgeReconnectElapsed==0||
             now-lastBridgeReconnectElapsed>120000)){
            lastBridgeReconnectElapsed=now;
            new Thread(()->{
                SenderClient.Delivery state=SenderClient.reconnect(this);
                runOnUiThread(()->{
                    if(!isFinishing()&&!isDestroyed()){
                        status=state.status;
                        render();
                    }
                });
            },"Sender-Bridge-QR-Persistent-Reconnect").start();
        }
        if(lastAutoCheckElapsed==0||now-lastAutoCheckElapsed>120000){
            lastAutoCheckElapsed=now;
            Updater.checkOnOpen(this,"sender",this::info);
        }
    }
    // Xiaomi Sender cosmetic layer. No change to SenderClient, route packet
    // signing, Bluetooth/media service, pairing, updater or Uber share flow.
    private static final int J_BG=0xFF081A25;
    private static final int J_CARD=0xFF142E3A;
    private static final int J_ACCENT=0xFF4FD6D3;
    private static final int J_TEXT=0xFFF3F8F9;
    private static final int J_MUTED=0xFFAAC3CC;
    private static final int J_LINE=0xFF285063;
    private static final int J_BUTTON=0xFF234556;
    private static final String ADVANCED_PREF="sender_xiaomi_jetour_advanced";
    private int dp(int amount){
        return Math.round(amount*getResources().getDisplayMetrics().density);
    }
    private GradientDrawable rounded(int fill,int radius,int line){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radius));
        if(line!=0)bg.setStroke(dp(1),line);
        return bg;
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private void infoText(LinearLayout parent,String value){
        TextView text=label(value,13,J_MUTED,false);
        text.setPadding(dp(2),dp(5),dp(2),dp(7));
        parent.addView(text);
    }
    private LinearLayout section(LinearLayout parent,String heading,
                                 String description){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(13),dp(14),dp(13));
        card.setBackground(rounded(J_CARD,16,J_LINE));
        LinearLayout.LayoutParams margins=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        margins.setMargins(0,0,0,dp(12));
        parent.addView(card,margins);
        TextView label=label(heading,16,J_TEXT,true);
        card.addView(label);
        if(description!=null&&!description.isEmpty())
            infoText(card,description);
        return card;
    }
    private Button action(String caption,Runnable click,boolean highlighted){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(caption);
        b.setTextSize(13f);
        b.setMinHeight(dp(46));
        b.setMinimumHeight(dp(46));
        b.setPadding(dp(8),dp(4),dp(8),dp(4));
        b.setTextColor(highlighted?J_BG:J_TEXT);
        b.setBackground(rounded(highlighted?J_ACCENT:J_BUTTON,11,
                               highlighted?0:J_LINE));
        b.setOnClickListener(v->click.run());
        return b;
    }
    private void addAction(LinearLayout parent,String caption,
                           Runnable click,boolean highlighted){
        Button b=action(caption,click,highlighted);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(5),0,dp(3));
        parent.addView(b,lp);
    }
    private void actionRow(LinearLayout parent,Button first,Button second){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        outer.setMargins(0,dp(5),0,dp(3));
        parent.addView(row,outer);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(
            0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);
        a.setMargins(0,0,dp(4),0);
        row.addView(first,a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(
            0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);
        b.setMargins(dp(4),0,0,0);
        row.addView(second,b);
    }
    private Button targetChip(String caption,String target,String selected){
        return action(caption,()->{
            SenderRouteTarget.choose(this,target);
            render();
        },target.equals(selected));
    }
    private void info(String text){status=text;render();}
    private void render(){
        int restoreY=currentScroll==null?0:currentScroll.getScrollY();
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(J_BG);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14),dp(14),dp(14),dp(28));
        scroll.addView(page);

        // Compact dashboard-like header: always shows pairing and nav target.
        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(15),dp(14),dp(15),dp(14));
        header.setBackground(rounded(0xFF0E2935,18,0xFF286E78));
        LinearLayout.LayoutParams headLp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        headLp.setMargins(0,0,0,dp(12));
        page.addView(header,headLp);
        header.addView(label("DRIVELAB   |   SENDER",23,J_TEXT,true));
        infoText(header,"JETOUR T1  •  XIAOMI  •  NAVEGACIÓN");
        boolean paired=SenderClient.configured(this);
        String navMode=SenderRouteTarget.selected(this);
        TextView summary=label(
            (paired?"● T1 vinculada":"○ T1 sin vincular")+
            "     •     "+SenderRouteTarget.label(navMode),13,
            paired?J_ACCENT:J_MUTED,true);
        summary.setPadding(0,dp(6),0,0);
        header.addView(summary);

        LinearLayout navigation=section(page,"Navegación",
            "Destino de Uber · tu elección se guarda para el próximo viaje");
        LinearLayout modeRow=new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        navigation.addView(modeRow);
        for(String[] spec:new String[][]{
                {"Waze",BridgeRouteTarget.WAZE},
                {"Maps",BridgeRouteTarget.MAPS},
                {"Ambos",BridgeRouteTarget.BOTH}
            }){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);
            lp.setMargins(dp(2),dp(2),dp(2),dp(2));
            modeRow.addView(targetChip(spec[0],spec[1],navMode),lp);
        }
        boolean only=SenderClient.prefs(this).getBoolean("only_cluster",true);
        infoText(navigation,only?"Salida: SOLO CLÚSTER":
            "Salida: CLÚSTER + XIAOMI");
        addAction(navigation,only?
            "Mostrar también la ruta en Xiaomi":"Enviar solo al clúster",
            ()->{SenderClient.prefs(this).edit()
                .putBoolean("only_cluster",!only).apply();render();},false);

        LinearLayout car=section(page,"Conexión con la T1",
            paired?"Receptor vinculado y clave de conexión conservada":
                   "Vinculá el receptor mediante QR para enviar rutas");
        if(paired)infoText(car,"IP del receptor: "+
            SenderClient.prefs(this).getString("receiver_ip",""));
        infoText(car,"Envíos confirmados: "+
            SenderClient.prefs(this).getInt("sent",0)+
            "   •   "+SenderClient.prefs(this).getString(
                "last_status","Sin pruebas"));
        actionRow(car,
            action("Escanear QR",this::scanPairQr,!paired),
            action("Reconectar",()->new Thread(()->{
                SenderClient.Delivery result=SenderClient.reconnect(this);
                runOnUiThread(()->info(result.status));
            },"Sender-Reconnect-No-QR").start(),paired));
        actionRow(car,
            action("Pegar ubicación",this::paste,false),
            action("Probar ruta",this::test,false));
        String routeState=getSharedPreferences(
            RouteDispatchService.STATE_PREFS,MODE_PRIVATE).getString(
                RouteDispatchService.LAST_STATE,"");
        if(!routeState.isEmpty())
            infoText(car,"Último envío desde Uber: "+routeState);

        LinearLayout music=section(page,"Audio y Spotify",
            "Bluetooth de Xiaomi → Jetour T1");
        android.content.SharedPreferences audio=SenderAudioBootstrap.prefs(this);
        boolean enabled=SenderAudioBootstrap.configured(this);
        String selected=audio.getString(SenderAudioBootstrap.LABEL,
            "Sin vehículo elegido");
        infoText(music,(enabled?"● AUTO AUDIO ACTIVO":"○ AUDIO SIN CONFIGURAR")+
            "   •   "+selected);
        actionRow(music,
            action("Elegir T1",this::chooseCarAudio,!enabled),
            action("Probar PLAY",this::testResumeCarAudio,false));
        actionRow(music,
            action("Ajustes Bluetooth",this::openBluetoothSettings,false),
            action("Diagnóstico A2DP",this::testCarAudio,false));
        if(enabled){
            boolean auto=audio.getBoolean(
                SenderAudioBootstrap.AUTO_RESUME,false);
            addAction(music,auto?
                "Auto PLAY al conectar · ON":"Auto PLAY al conectar · OFF",
                ()->{
                    boolean setting=!audio.getBoolean(
                        SenderAudioBootstrap.AUTO_RESUME,false);
                    audio.edit().putBoolean(
                        SenderAudioBootstrap.AUTO_RESUME,setting)
                        .putString(SenderAudioBootstrap.STATUS,setting?
                            "AUTO PLAY pedido al conectar la T1. "+
                            "Se envía PLAY al último reproductor Android.":
                            "AUTO PLAY desactivado; prueba A2DP solamente")
                        .apply();
                    render();
                },auto);
        }

        boolean advanced=getPreferences(MODE_PRIVATE).getBoolean(
            ADVANCED_PREF,false);
        addAction(page,advanced?"▴  Ocultar ajustes avanzados":
            "▾  Más opciones y diagnóstico",()->{
                getPreferences(MODE_PRIVATE).edit().putBoolean(
                    ADVANCED_PREF,!advanced).apply();
                render();
            },false);
        if(advanced){
            LinearLayout more=section(page,"Configuración avanzada",
                "Pruebas y recuperación · no cambian el clúster automáticamente");
            addAction(more,"Destino completo: Waze / Maps / Ambos / T1Launcher",
                this::chooseNavigationTarget,false);
            addAction(more,"Prueba T1Launcher (ruta real)",
                this::chooseT1LauncherRouteTrial,false);
            addAction(more,"Vinculación manual (6 dígitos)",
                this::pair,false);
            if(enabled)addAction(more,"Desactivar inicio de audio T1",()->{
                audio.edit().putBoolean(SenderAudioBootstrap.ENABLED,false)
                    .putString(SenderAudioBootstrap.STATUS,
                        "Inicio automático desactivado por usuario").apply();
                render();
            },false);
            infoText(more,"Último A2DP: "+
                audio.getString(SenderAudioBootstrap.STATUS,"Sin prueba"));
            addAction(more,"Desvincular receptor",()->
                new AlertDialog.Builder(this)
                    .setMessage("¿Desvincular? La próxima vez deberás "+
                        "comparar los 6 dígitos de emparejamiento.")
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("DESVINCULAR",(d,w)->{
                        SenderClient.unpair(this);render();
                    }).show(),false);
            infoText(more,"Micrófono y ruedita: pendientes en la T1. "+
                "El Sender no captura teclas del volante.");
        }
        addAction(page,"Buscar actualización del Sender",
            ()->Updater.check(this,"sender",this::info),false);
        infoText(page,SenderCloudOta.isEnrolled(this)?
            "OTA Cloud: dispositivo vinculado. Descarga con firma verificada.":
            "OTA Cloud: conexión pendiente. Sigue activo el canal OTA firmado de GitHub.");
        if(!status.isEmpty())
            infoText(page,"Actualización / estado: "+status);
        infoText(page,"En Uber seleccioná DriveLab Auto Sender. "+
            "La ruta se envía sin abrir la interfaz y sin "+
            "interrumpir la navegación del Xiaomi cuando está en solo clúster.");
        setContentView(scroll);
        currentScroll=scroll;
        if(restoreY>0)scroll.post(()->{
            if(currentScroll==scroll)scroll.scrollTo(0,restoreY);
        });
    }
    private void chooseNavigationTarget(){
        String current=SenderRouteTarget.selected(this);
        final String[] choices={"Waze","Google Maps","Ambos simultáneamente",
            "Google Maps en T1Launcher · tarea virtual existente (LAB)"};
        int selected=BridgeRouteTarget.MAPS.equals(current)?1:
            BridgeRouteTarget.BOTH.equals(current)?2:
            BridgeRouteTarget.T1_LAUNCHER.equals(current)?3:0;
        new AlertDialog.Builder(this)
            .setTitle("NAVEGACIÓN DE UBER EN LA T1")
            .setSingleChoiceItems(choices,selected,(dialog,which)->{
                SenderRouteTarget.choose(this,which==1?
                    BridgeRouteTarget.MAPS:which==2?
                    BridgeRouteTarget.BOTH:which==3?
                    BridgeRouteTarget.T1_LAUNCHER:BridgeRouteTarget.WAZE);
                dialog.dismiss();render();
            })
            .setNegativeButton("CANCELAR",null).show();
    }
    private void chooseT1LauncherRouteTrial(){
        new AlertDialog.Builder(this)
            .setTitle("T1LAUNCHER · NO DUPLICAR GOOGLE MAPS")
            .setMessage("Sólo con la T1 estacionada. Dejá Google Maps "+
                "abierto DENTRO del Cluster de T1Launcher, como en la foto. "+
                "Este botón selecciona la ruta experimental para los "+
                "PRÓXIMOS destinos compartidos desde Uber. "+
                "Bridge buscará una única tarea Google Maps en un "+
                "Display VIRTUAL (nunca el Display 1 físico), y "+
                "solicitará al Android entregar la dirección "+
                "explícitamente a ese ID de tarea. "+
                "Si la tarea no es única o cambió, FALLA sin abrir "+
                "otra ventana. No modifica el controlador del volante "+
                "de T1Launcher ni su mosaico. "+
                "Se puede volver a Waze/Maps/Ambos desde ELEGIR DESTINO.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ACTIVAR PARA PRÓXIMO UBER",(d,w)->{
                SenderRouteTarget.choose(this,BridgeRouteTarget.T1_LAUNCHER);
                info("MODO T1LAUNCHER LISTO · compartí un destino "+
                    "de Uber desde tu Xiaomi. No se manda una ruta ficticia.");
            }).show();
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
        if(BridgeRouteTarget.T1_LAUNCHER.equals(
                SenderRouteTarget.selected(this))){
            info("T1LAUNCHER: no se envía ruta de prueba a La Plata. "+
                "Usá un destino real compartido desde Uber; "+
                "no queremos reemplazar la navegación actual.");
            return;
        }
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
    private void testResumeCarAudio(){
        if(!hasBtPermission()||!SenderAudioBootstrap.configured(this)){
            info("Seleccioná la T1 en Bluetooth y autorizá Dispositivos cercanos");
            return;
        }
        info("Verificando perfil A2DP de la T1 antes de enviar PLAY…");
        new Thread(()->{
            SenderAudioBootstrap.resumeLastMedia(getApplicationContext(),false);
            String result=SenderAudioBootstrap.prefs(this)
                .getString(SenderAudioBootstrap.STATUS,"Sin resultado PLAY");
            runOnUiThread(()->info(result));
        },"Sender-T1-Media-Play").start();
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
