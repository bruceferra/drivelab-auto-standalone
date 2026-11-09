package ar.com.rango5gps.drivelabauto.sender;

import android.app.*;
import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import ar.com.rango5gps.drivelabauto.common.MapLinks;
import ar.com.rango5gps.drivelabauto.common.Updater;

/** User-facing Sender controls only: no vehicle telemetry or OEM access. */
public final class SenderActivity extends Activity {
    private static final int NAVY=0xFF111821,TEAL=0xFF108A80;
    private String status="";
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    @Override public void onResume(){super.onResume();render();Updater.onResume(this,this::info);}
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
        button(body,"Vincular Receiver · código de 6 dígitos",this::pair);
        button(body,"Probar envío real a La Plata",this::test);
        button(body,"Pegar enlace de Google Maps / WhatsApp",this::paste);
        title(body,"SPOTIFY AUTOMÁTICO · XIAOMI",20);
        title(body,"Al conectar el audio Bluetooth de la T1, intenta reanudar Spotify "+
            "en este Xiaomi sin abrir su pantalla. No cambia MODE ni Spotify de la T1.",13);
        title(body,"Bluetooth elegido: "+SpotifyAutoPlay.label(this)+
            "\nDispositivos cercanos: "+(SpotifyAutoPlay.hasBluetoothPermission(this)?"CONCEDIDO":"SIN AUTORIZAR")+
            "\nControl de Spotify: "+(SpotifyAutoPlay.hasListenerAccess(this)?"AUTORIZADO":"SIN AUTORIZAR")+
            "\nEstado: "+SpotifyAutoPlay.status(this),14);
        if(!SpotifyAutoPlay.hasBluetoothPermission(this))
            button(body,"AUTORIZAR DISPOSITIVOS CERCANOS",this::requestBluetooth);
        button(body,"ELEGIR BLUETOOTH DE LA T1",this::pickSpotifyBluetooth);
        if(!SpotifyAutoPlay.hasListenerAccess(this))
            button(body,"AUTORIZAR CONTROL DE SPOTIFY",this::grantSpotifyControl);
        boolean spotifyAuto=SpotifyAutoPlay.enabled(this);
        button(body,spotifyAuto?"DESACTIVAR SPOTIFY AUTOMÁTICO":
            "ACTIVAR SPOTIFY AUTOMÁTICO",()->{
            if(!spotifyAuto&&(!SpotifyAutoPlay.hasBluetoothPermission(this)||
                !SpotifyAutoPlay.hasListenerAccess(this)||
                "Sin seleccionar".equals(SpotifyAutoPlay.label(this)))){
                info("Primero elegí T1 y concedé los dos permisos para el control de Spotify.");
                return;
            }
            SpotifyAutoPlay.setEnabled(this,!spotifyAuto);render();
        });
        button(body,"PROBAR PLAY DE SPOTIFY SIN ABRIRLO",()->{
            SpotifyAutoPlay.checkConnected(this,"prueba manual",true,
                ()->runOnUiThread(this::render));
            info("Verificando que el Bluetooth Music de la T1 esté conectado…");
        });
        button(body,"ACTUALIZAR ESTADO SPOTIFY",this::render);
        button(body,"ACTUALIZAR SENDER · INSTALACIÓN DIRECTA",()->Updater.check(this,"sender",this::info));
        button(body,"Desvincular Receiver",()->new AlertDialog.Builder(this)
            .setMessage("¿Desvincular? La próxima vez tendrás que comparar los 6 dígitos.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("DESVINCULAR",(d,w)->{SenderClient.unpair(this);render();}).show());
        title(body,"En Uber: si Android muestra un selector, elegí DriveLab Auto Sender. "+
            "El modo Solo clúster no abre Waze en el teléfono si la T1 confirma recepción.",13);
        if(!status.isEmpty())title(body,status,14);
        setContentView(scroll);
    }
    private void requestBluetooth(){
        if(android.os.Build.VERSION.SDK_INT>=31)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},71);
        else render();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==71){
            if(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED)
                info("Sin permiso de Dispositivos cercanos; no se activará Spotify.");
            else render();
        }
    }
    private void pickSpotifyBluetooth(){
        if(!SpotifyAutoPlay.hasBluetoothPermission(this)){requestBluetooth();return;}
        java.util.List<BluetoothDevice> devices=SpotifyAutoPlay.bonded(this);
        if(devices.isEmpty()){info("No se encontraron dispositivos Bluetooth emparejados.");return;}
        String[] names=new String[devices.size()];
        for(int i=0;i<devices.size();i++){
            try{
                String name=devices.get(i).getName();
                names[i]=name==null?"Dispositivo sin nombre":name;
            }catch(SecurityException error){names[i]="Dispositivo Bluetooth";}
        }
        new AlertDialog.Builder(this).setTitle("Elegí exclusivamente el Bluetooth de la T1")
            .setItems(names,(d,n)->{
                SpotifyAutoPlay.select(this,devices.get(n));render();
            }).setNegativeButton("CANCELAR",null).show();
    }
    private void grantSpotifyControl(){
        new AlertDialog.Builder(this).setTitle("Permiso para controlar Spotify")
            .setMessage("Android exige habilitar el acceso a notificaciones de DriveLab Auto "+
                "Sender para controlar la sesión multimedia de Spotify. Es un permiso "+
                "amplio del sistema; esta función solo consulta sesiones multimedia "+
                "y controla Spotify del Xiaomi. No lee ni almacena mensajes. "+
                "Podés revocarlo cuando quieras.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ABRIR AJUSTES",(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
                catch(Exception error){info("Abrí Ajustes → Apps → Acceso especial → "+
                    "Acceso a notificaciones → DriveLab Auto Sender");}
            }).show();
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
