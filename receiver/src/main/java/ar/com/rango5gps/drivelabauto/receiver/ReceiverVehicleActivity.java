package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Optional manual vehicle panel. ReceiverService and BootReceiver never create
 * this activity or issue OEM operations. All writes need an explicit foreground
 * button press, and all data remain labeled as unverified OEM observations.
 */
public final class ReceiverVehicleActivity extends Activity {
    private final String[] tabNames={"VEHÍCULO","ENERGÍA","CODE"};
    private int activeTab=0;
    private ScrollView currentScroll;
    private VehicleWindowGroup windows;
    private MirrorPulsePilot mirror;
    private MirrorPositionProbe mirrorProbe;
    private PedestrianAlertControl pedestrian;
    private VehicleReadOnly reader;
    private VehicleChangeScanner changeScanner;
    private VehicleBatchReader batchReader;
    private TextView operationView,alertView,scannerView,scannerValuesView,scannerEventsView,mirrorView;
    private TextView mirrorGearView,mirrorRawView,mirrorHalView;
    private String windowStatus="Sin órdenes enviadas";
    private String mirrorStatus="Validado en la T1: derecho 2069=1 sube, 2069=2 baja. Izquierdo 2068 pendiente.";
    private String mirrorGearStatus="Marcha y velocidad: sin consultar";
    private String mirrorRawStatus="Estados RAW 2068/2069: sin consultar";
    private String mirrorPositionStatus="Posiciones HAL aún no consultadas";
    private String pedestrianStatus="SIN CONSULTAR · alarma de peatones";
    private String fuelStatus="Sin lectura actual";
    private String energyStatus="Sin lectura";
    private String scanStatus="Detector detenido";
    private String scanValues="";
    private String scanEvents="";
    private String batchStatus="Sin lecturas";
    private String batchValues="";
    private String exportStatus="";
    private int batchStart=2001;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        windows=new VehicleWindowGroup(this);
        mirror=new MirrorPulsePilot(this);
        mirrorProbe=new MirrorPositionProbe(this);
        pedestrian=new PedestrianAlertControl(this);
        reader=new VehicleReadOnly(this);
        changeScanner=new VehicleChangeScanner(this);
        batchReader=new VehicleBatchReader(this);
        if(saved!=null){
            activeTab=Math.min(2,Math.max(0,saved.getInt("tab",0)));
            batchStart=saved.getInt("batch",2001);
        }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putInt("tab",activeTab);out.putInt("batch",batchStart);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume(){
        super.onResume();
        windows.setForeground(true);
        mirror.setForeground(true);
        mirrorProbe.setForeground(true);
        pedestrian.setForeground(true);
    }
    @Override protected void onPause(){
        windows.setForeground(false);
        mirror.setForeground(false);
        mirrorProbe.setForeground(false);
        mirror.cancel();
        pedestrian.setForeground(false);
        changeScanner.stop();
        super.onPause();
    }
    @Override protected void onDestroy(){
        if(mirror!=null)mirror.cancel();
        super.onDestroy();
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private void label(LinearLayout parent,String text,int size){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(size);
        t.setPadding(10,9,10,9);parent.addView(t);
    }
    private TextView status(LinearLayout parent,String text){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(13);t.setTextIsSelectable(true);
        t.setPadding(12,9,12,12);
        parent.addView(t);
        return t;
    }
    private void button(LinearLayout parent,String text,Runnable run){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        b.setOnClickListener(v->run.run());parent.addView(b);
    }
    private void section(LinearLayout parent,String name){
        label(parent,name,19);
    }
    private void render(){
        final int restoreY=currentScroll==null?0:currentScroll.getScrollY();
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=column();root.setPadding(16,10,16,26);
        scroll.addView(root);
        label(root,"DRIVELAB AUTO · T1",23);
        label(root,"v0.2.16 · Resultado de espejos junto a cada lector",12);
        button(root,"← VOLVER A MAPAS Y PUENTE",this::finish);
        LinearLayout menu=new LinearLayout(this);
        menu.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<tabNames.length;i++){
            final int idx=i;
            Button b=new Button(this);
            b.setText(tabNames[i]);b.setAllCaps(false);
            b.setTextSize(11);
            b.setActivated(i==activeTab);
            b.setOnClickListener(v->{activeTab=idx;render();});
            menu.addView(b,new LinearLayout.LayoutParams(0,60,1));
        }
        root.addView(menu);
        if(activeTab==0)renderVehicle(root);
        else if(activeTab==1)renderEnergy(root);
        else renderCode(root);
        label(root,"Sin root ni ADB. No reemplaza los controles OEM de seguridad. "+
            "Esta versión no convierte DriveLab en launcher HOME.",11);
        setContentView(scroll);
        currentScroll=scroll;
        if(restoreY>0)scroll.post(()->{
            if(currentScroll==scroll)scroll.scrollTo(0,restoreY);
        });
    }
    private boolean vehicleBusy(){
        return windows.busy()||mirror.busy()||mirrorProbe.busy()||pedestrian.busy()||
            changeScanner.isRunning()||batchReader.busy();
    }
    private void renderVehicle(LinearLayout body){
        section(body,"ESPEJOS · CONTROL MANUAL EN P");
        mirrorView=status(body,mirrorStatus);
        label(body,"Derecho 2069: 1 SUBE, 2 BAJA. Izquierdo: 2068 en ensayo. "+
            "Un toque ejecuta un movimiento breve y libera a 0. "+
            "El piloto exige P y velocidad cero.",13);
        button(body,"LEER MARCHA Y VELOCIDAD · SIN MOVER",()->{
            if(vehicleBusy()){
                updateMirrorGear("Otra consulta OEM está en curso");return;
            }
            if(!mirror.readGearOnce(this::updateMirrorGear))
                updateMirrorGear("No se inició lectura de marcha");
            else updateMirrorGear("Consultando marcha 1033 y velocidad…");
        });
        mirrorGearView=status(body,mirrorGearStatus);

        section(body,"LECTOR DE ESTADO · DERECHO 2069 / IZQUIERDO 2068");
        button(body,"LEER ESTADO RAW 2068 / 2069 · MANDO SUELTO",()->{
            if(vehicleBusy()){
                updateMirrorRaw("Otra consulta OEM está en curso");return;
            }
            if(!mirror.readMirrorStates(this::updateMirrorRaw))
                updateMirrorRaw("No se inició lectura 2068/2069");
            else updateMirrorRaw("Leyendo estados RAW 2068/2069…");
        });
        // Always show the real readback HERE, immediately under its button.
        // A callback changes this TextView without rerendering or scrolling.
        mirrorRawView=status(body,mirrorRawStatus);
        label(body,"La lectura 0/1/2 debe contrastarse con extremos físicos. "+
            "No confundir el valor LEÍDO con el comando de SUBIR/BAJAR.",12);

        section(body,"OTRAS POSICIONES · HAL SOLO LECTURA");
        button(body,"LEER POSICIONES Y RANGOS HAL · SOLO LECTURA",()->{
            if(vehicleBusy()){
                updateMirrorHal("Otra operación OEM está en curso");return;
            }
            if(!mirrorProbe.readOnce(this::updateMirrorHal))
                updateMirrorHal("No se pudo iniciar lectura HAL");
            else updateMirrorHal("Consultando CarPropertyManager…");
        });
        mirrorHalView=status(body,mirrorPositionStatus);
        button(body,"GUARDAR LECTURA ACTUAL COMO REFERENCIA D",()->saveMirrorSnapshot("D"));
        button(body,"GUARDAR LECTURA ACTUAL COMO REFERENCIA R",()->saveMirrorSnapshot("R"));
        label(body,"La HAL puede no estar accesible. Estas instantáneas NO son "+
            "memorias de ángulo verificadas ni habilitan movimientos automáticos.",12);

        section(body,"DERECHO · CONTROL PROBADO");
        LinearLayout rightRow=new LinearLayout(this);
        rightRow.setOrientation(LinearLayout.HORIZONTAL);
        Button upRight=new Button(this);upRight.setText("SUBIR ▲ 500 ms");
        upRight.setAllCaps(false);
        upRight.setOnClickListener(v->confirmMirrorPulse(2069,1,500));
        rightRow.addView(upRight,new LinearLayout.LayoutParams(0,64,1));
        Button downRight=new Button(this);downRight.setText("BAJAR ▼ 500 ms");
        downRight.setAllCaps(false);
        downRight.setOnClickListener(v->confirmMirrorPulse(2069,2,500));
        rightRow.addView(downRight,new LinearLayout.LayoutParams(0,64,1));
        body.addView(rightRow);
        label(body,"Prueba física: 500, 1.000 y 2.000 ms producen el mismo "+
            "desplazamiento observable. Se conserva solo 500 ms para no "+
            "mandar una orden más larga sin beneficio.",12);
        section(body,"IZQUIERDO · 2068 SIN VALIDACIÓN FÍSICA");
        label(body,"Códigos candidatos. No se conoce todavía si 1/2 corresponde "+
            "a subir/bajar ni si ese ID acepta órdenes en esta unidad. "+
            "Un solo ensayo por vez.",12);
        LinearLayout leftRow=new LinearLayout(this);
        leftRow.setOrientation(LinearLayout.HORIZONTAL);
        Button leftOne=new Button(this);leftOne.setText("IZQ · 1 · 500 ms");
        leftOne.setAllCaps(false);
        leftOne.setOnClickListener(v->confirmMirrorPulse(2068,1,500));
        leftRow.addView(leftOne,new LinearLayout.LayoutParams(0,64,1));
        Button leftTwo=new Button(this);leftTwo.setText("IZQ · 2 · 500 ms");
        leftTwo.setAllCaps(false);
        leftTwo.setOnClickListener(v->confirmMirrorPulse(2068,2,500));
        leftRow.addView(leftTwo,new LinearLayout.LayoutParams(0,64,1));
        body.addView(leftRow);
        section(body,"PLEGADO DE ESPEJOS · OEM PERMISO PRIVILEGIADO");
        label(body,"Solo si Android habilita MIRROR_FOLD 287312709 de tipo Boolean "+
            "y Receiver posee el permiso OEM. Si falta, se informa sin mover nada. "+
            "No se utilizan los IDs vendor desconocidos del launcher.",12);
        button(body,"PLEGAR ESPEJOS · PROBAR OEM",()->confirmMirrorFold(true));
        button(body,"DESPLEGAR ESPEJOS · PROBAR OEM",()->confirmMirrorFold(false));
        button(body,"LIBERAR ÚLTIMO ESPEJO · VALOR 0",()->{
            mirror.cancel();
            if(!mirror.release(this::updateMirror))
                updateMirror("No se pudo intentar liberar: panel no visible");
            else updateMirror("Solicitando liberación OEM del último espejo…");
        });
        label(body,"NO hay cambio automático a R/D ni desplazamiento a topes. "+
            "La respuesta Binder no prueba parada física. Si algo se mueve de "+
            "forma anormal, usá el mando OEM y suspendé la prueba.",12);
        section(body,"VENTANILLAS · CONTROLES MANUALES");
        label(body,"Accionamientos OEM para 2004–2007. Una operación por vez, "+
            "con lectura anterior/posterior. Probar detenido, en P y sin obstáculos.",13);
        button(body,"VENTILACIÓN TOTAL · 1/4  (2004–2007)",
            ()->startWindows(-1,WindowGroupPolicy.VENT_QUARTER));
        button(body,"CIERRE TOTAL  (2004–2007)",
            ()->startWindows(-1,WindowGroupPolicy.CLOSE));
        for(int i=0;i<WindowGroupPolicy.IDS.length;i++){
            final int id=WindowGroupPolicy.IDS[i];
            section(body,WindowGroupPolicy.NAMES[i]+" · "+id);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            Button vent=new Button(this);vent.setText("VENTILAR 1/4");vent.setAllCaps(false);
            vent.setOnClickListener(v->startWindows(id,WindowGroupPolicy.VENT_QUARTER));
            row.addView(vent,new LinearLayout.LayoutParams(0,58,1));
            Button close=new Button(this);close.setText("CERRAR");close.setAllCaps(false);
            close.setOnClickListener(v->startWindows(id,WindowGroupPolicy.CLOSE));
            row.addView(close,new LinearLayout.LayoutParams(0,58,1));
            body.addView(row);
        }
        label(body,"Los estados OEM 1=cerrado y 3=banda del primer cuarto no indican "+
            "posición exacta. No se emiten comandos 0, ni se fuerza apertura total. "+
            "Si una ventana ya está demasiado abierta, ventilar exige cerrarla antes.",12);
        operationView=status(body,windowStatus);
        if(windows.busy())button(body,"CANCELAR ÓRDENES RESTANTES",
            ()->{windows.cancel();windowStatus="Cancelación solicitada; no detiene un motor ya activo";
                 if(operationView!=null)operationView.setText(windowStatus);});
        section(body,"ALARMA DE PEATONES · ID 2074");
        label(body,"Solo lectura/alternancia manual. No se desactiva al arrancar. "+
            "Es una alerta de seguridad: mantenerla activa para circular.",13);
        button(body,"ALARMA PEATÓN · CAMBIAR ON/OFF",this::confirmPedestrian);
        alertView=status(body,pedestrianStatus);
    }
    /** One tap = one attempt. OEM validation is enforced in MirrorPulsePilot. */
    private void confirmMirrorFold(boolean folded){
        if(vehicleBusy()){
            updateMirror("Otra operación OEM está en curso");return;
        }
        // No modal confirmation: if privileged permission is unavailable,
        // MirrorPulsePilot refuses the command and reports why.
        if(!mirror.requestFold(folded,this::updateMirror))
            updateMirror("Consulta de plegado no iniciada");
        else updateMirror("Comprobando disponibilidad OEM de plegado…");
    }
    /** User-triggered mirror action: no modal dialogs or surprise scrolling. */
    private void confirmMirrorPulse(int mirrorId,int direction,int durationMs){
        if(vehicleBusy()){
            updateMirror("Otra operación OEM está en curso");return;
        }
        // Mandatory stationarity/gear check, cooldown, bounded duration and
        // release(0) are performed by the pilot BEFORE/AFTER the OEM write.
        if(!mirror.pulse(mirrorId,direction,durationMs,this::updateMirror))
            updateMirror("No se inició: panel oculto, espera o control ocupado");
        else updateMirror("Moviendo espejo "+mirrorId+
            " · verificación OEM P/0 y liberación automática…");
    }
    private void saveMirrorSnapshot(String position){
        if(!mirrorPositionStatus.startsWith("POSICIÓN DE ESPEJOS")){
            updateMirror("Primero tocá LEER POSICIONES Y RANGOS HAL");
            return;
        }
        getSharedPreferences("mirror_calibration",MODE_PRIVATE).edit()
            .putString("mirror_reference_"+position,mirrorPositionStatus)
            .putLong("mirror_reference_"+position+"_at",System.currentTimeMillis())
            .apply();
        updateMirror("Referencia "+position+" guardada como instantánea HAL "+
            "(NO prueba ángulo ni permite auto-reversa).");
    }
    private void updateMirrorGear(String value){
        mirrorGearStatus=value;
        if(mirrorGearView!=null&&!isFinishing()&&!isDestroyed())
            mirrorGearView.setText(value);
    }
    private void updateMirrorRaw(String value){
        mirrorRawStatus=value;
        if(mirrorRawView!=null&&!isFinishing()&&!isDestroyed())
            mirrorRawView.setText(value);
    }
    private void updateMirrorHal(String value){
        mirrorPositionStatus=value;
        if(mirrorHalView!=null&&!isFinishing()&&!isDestroyed())
            mirrorHalView.setText(value);
    }
    private void updateMirror(String value){
        mirrorStatus=value;
        if(mirrorView!=null&&!isFinishing()&&!isDestroyed())
            mirrorView.setText(value);
    }
    private void startWindows(int id,int operation){
        if(vehicleBusy()){updateOperation("Hay otra operación OEM en curso");return;}
        boolean ok=id==-1?windows.start(operation,p->updateOperation(p.message+
            "\n"+String.join("\n",p.events))):
            windows.startOne(id,operation,p->updateOperation(p.message+
            "\n"+String.join("\n",p.events)));
        if(!ok)updateOperation("Orden NO iniciada: app no visible, ID inválido o control ocupado");
        else updateOperation("Leyendo estado OEM inicial…");
    }
    private void updateOperation(String s){
        windowStatus=s;
        if(operationView!=null&&!isFinishing()&&!isDestroyed())
            operationView.setText(s);
    }
    private void confirmPedestrian(){
        if(vehicleBusy()){updateAlert("Otra operación OEM está en curso");return;}
        new AlertDialog.Builder(this)
            .setTitle("Alarma peatonal AVAS · 2074")
            .setMessage("Se leerá su estado actual y se solicitará invertirlo. "+
                "El auto debe estar detenido en P. La alarma de peatones debe "+
                "quedar activada antes de circular. ¿Continuar?")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CAMBIAR AHORA",(d,w)->{
                if(!pedestrian.toggle(r->updateAlert(r.summary())))
                    updateAlert("Sin orden: la app debe estar visible y fuera de cooldown");
                else updateAlert("Consultando y cambiando 2074…");
            }).show();
    }
    private void updateAlert(String s){
        pedestrianStatus=s;
        if(alertView!=null&&!isFinishing()&&!isDestroyed())
            alertView.setText(s);
    }
    private void renderEnergy(LinearLayout body){
        section(body,"TABLERO T1 · HISTORIAL Y DATOS OEM");
        button(body,"ABRIR TABLERO T1 · CONSUMO REAL POR SURTIDOR",()->
            startActivity(new android.content.Intent(this,T1TankDashboardActivity.class)));
        label(body,"Compara los dos tanques de surtidor con lecturas CI186 en vivo. "+
            "No utiliza la fórmula antigua del launcher.",12);
        section(body,"LECTURA OEM · COMBUSTIBLE");
        label(body,"CI186: litros informados por el firmware. No equivale "+
            "necesariamente a litros utilizables ni a autonomía calculada.",13);
        button(body,"LEER LITROS OEM (UNA VEZ)",()->{
            fuelStatus="Consultando CI186…";render();
            reader.readFuelOnce(r->{
                fuelStatus=r.valid()?FuelReadingPolicy.display(r.liters)+
                    " · muestra "+new SimpleDateFormat("HH:mm:ss",Locale.getDefault())
                    .format(new Date(r.timestampMillis)):"SIN LECTURA · "+r.detail;
                if(activeTab==1&&!isFinishing())render();
            });
        });
        status(body,fuelStatus);
        section(body,"BATERÍA · LECTURAS PILOTO");
        button(body,"LEER SOC 1042 / 1062 Y CORRIENTE 1064",()->{
            energyStatus="Leyendo…";render();
            reader.readEnergyOnce(values->{
                StringBuilder b=new StringBuilder();
                for(Map.Entry<String,String> e:values.entrySet())
                    b.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                energyStatus=b.toString();
                if(activeTab==1&&!isFinishing())render();
            });
        });
        status(body,energyStatus);
        label(body,"IDs conocidos para futuros ensayos: 1041 SOC protegido, "+
            "1063 voltaje HV, 1044 flujo, 1083 potencia, 1048/1049 contadores. "+
            "NO se escriben ni se muestran como lecturas reales sin verificar.",12);
        section(body,"MÚSICA / SPOTIFY");
        label(body,"Diagnóstico pendiente: fuente Bluetooth/Spotify Connect, "+
            "metadatos NOT PROVIDED y acciones del volante. "+
            "No hay reproducción automática ni Listener de notificaciones en esta versión.",13);
    }
    private void renderCode(LinearLayout body){
        section(body,"CODE · DETECTOR 45 SEGUNDOS");
        label(body,"Lectura diferencial de señales conocidas sin escritura. "+
            "Prueba con T1 estacionada.",13);
        button(body,changeScanner.isRunning()?"DETENER DETECTOR":
            "INICIAR DETECTOR DE CAMBIOS",()->{
            if(changeScanner.isRunning()){changeScanner.stop();return;}
            if(vehicleBusy()){scanStatus="Otra operación OEM está en curso";render();return;}
            scanStatus="Detector iniciando…";scanValues="";scanEvents="";render();
            if(!changeScanner.start(result->{
                scanStatus=result.status+" · muestras "+result.sampleCount+
                    " · cambios "+result.changedCount;
                StringBuilder values=new StringBuilder();
                for(Map.Entry<String,String> e:result.latest.entrySet())
                    values.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                scanValues=values.toString();
                scanEvents=String.join("\n",result.events);
                if(activeTab==2&&!isFinishing()){
                    if(scannerView!=null)scannerView.setText(scanStatus);
                    if(scannerValuesView!=null)scannerValuesView.setText(scanValues);
                    if(scannerEventsView!=null)scannerEventsView.setText(scanEvents);
                }
            })){scanStatus="No se pudo iniciar el detector";render();}
        });
        scannerView=status(body,scanStatus);
        scannerValuesView=status(body,scanValues);
        scannerEventsView=status(body,scanEvents);
        section(body,"EXPLORADOR DE 25 CÓDIGOS · SOLO LECTURA");
        label(body,"Usa getters conocidos. No permite escribir IDs arbitrarios.",12);
        label(body,"Rango: "+batchStart+"–"+BatchReadPolicy.end(batchStart),14);
        LinearLayout range=new LinearLayout(this);range.setOrientation(LinearLayout.HORIZONTAL);
        Button prev=new Button(this);prev.setText("−25");prev.setOnClickListener(v->{
            batchStart=BatchReadPolicy.shift(batchStart,-1);render();});
        range.addView(prev,new LinearLayout.LayoutParams(0,60,1));
        Button next=new Button(this);next.setText("+25");next.setOnClickListener(v->{
            batchStart=BatchReadPolicy.shift(batchStart,1);render();});
        range.addView(next,new LinearLayout.LayoutParams(0,60,1));body.addView(range);
        button(body,"CAMBIAR CÓDIGO INICIAL",()->{
            EditText field=new EditText(this);field.setSingleLine(true);
            field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            field.setText(String.valueOf(batchStart));
            new AlertDialog.Builder(this).setTitle("ID inicial (1–9999)")
                .setView(field).setNegativeButton("CANCELAR",null)
                .setPositiveButton("ACEPTAR",(d,w)->{
                    try{batchStart=BatchReadPolicy.validate(
                        Integer.parseInt(field.getText().toString()));render();}
                    catch(Exception e){batchStatus="ID inválido";render();}
                }).show();
        });
        button(body,"LEER TANDA DE 25 CÓDIGOS",()->{
            if(vehicleBusy()){batchStatus="Otra operación OEM está en curso";render();return;}
            batchStatus="Leyendo "+batchStart+"–"+BatchReadPolicy.end(batchStart)+"…";render();
            if(!batchReader.read(batchStart,r->{
                batchStatus=r.status;
                StringBuilder b=new StringBuilder();
                for(Map.Entry<String,String> e:r.results.entrySet())
                    b.append(e.getKey()).append(" · ").append(e.getValue()).append("\n");
                batchValues=b.toString();
                if(activeTab==2&&!isFinishing())render();
            })){batchStatus="No se pudo iniciar lectura";render();}
        });
        status(body,batchStatus+"\n"+batchValues);
        button(body,"EXPORTAR DIAGNÓSTICO CSV",this::exportCsv);
        status(body,exportStatus);
        label(body,"Códigos de ventanas: 2004 delantera izquierda, "+
            "2005 delantera derecha, 2006 trasera izquierda, 2007 trasera derecha; "+
            "2054 señal general. Alarma 2074. Batería 1042/1062/1064. "+
            "Un código observado NO autoriza una escritura.",12);
    }
    private static String csv(String value){
        if(value==null)value="";
        return "\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";
    }
    private void exportCsv(){
        try{
            StringBuilder b=new StringBuilder("grupo;dato;valor\n");
            b.append("estado;ventanillas;").append(csv(windowStatus)).append("\n");
            b.append("estado;alarma_2074;").append(csv(pedestrianStatus)).append("\n");
            b.append("energia;combustible_CI186;").append(csv(fuelStatus)).append("\n");
            b.append("energia;lecturas;").append(csv(energyStatus)).append("\n");
            b.append("code;detector;").append(csv(scanStatus)).append("\n");
            b.append("code;valores;").append(csv(scanValues)).append("\n");
            b.append("code;eventos;").append(csv(scanEvents)).append("\n");
            b.append("code;tanda;").append(csv(batchStatus)).append("\n");
            b.append("code;resultados;").append(csv(batchValues)).append("\n");
            ContentValues meta=new ContentValues();
            String name="DriveLabAuto-CODE-"+System.currentTimeMillis()+".csv";
            meta.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
            meta.put(MediaStore.MediaColumns.MIME_TYPE,"text/csv");
            meta.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/DriveLabAuto");
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,meta);
            if(uri==null)throw new IllegalStateException("Sin destino de Descargas");
            try(OutputStream stream=getContentResolver().openOutputStream(uri)){
                if(stream==null)throw new IllegalStateException("No se pudo abrir el archivo");
                stream.write(b.toString().getBytes(StandardCharsets.UTF_8));
            }
            exportStatus="CSV guardado en Descargas/DriveLabAuto: "+name;
        }catch(Exception error){
            exportStatus="No se pudo exportar CSV: "+error.getClass().getSimpleName();
        }
        if(activeTab==2)render();
    }
}
