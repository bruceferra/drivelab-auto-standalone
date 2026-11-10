package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * READ ONLY fuel dashboard: two physical refueling measurements and on-demand
 * OEM readbacks from allowlisted VehicleReadOnly.
 * Separate Activity: no vehicle writes, no GPS tracking, no service changes.
 * All computed numbers remain labelled as refueling estimates.
 */
public final class T1TankDashboardActivity extends Activity {
    private static final int NAVY=0xFF142638, TEAL=0xFF087F7A,
        GRAY=0xFF647585, PALE=0xFFEFF4F7, RED=0xFF9A4930, WHITE=Color.WHITE;
    private static final int CREATE_CSV=2107;
    private static final String OBS_FILE="t1_tank_ci186_readings.csv";
    private final Locale es=Locale.forLanguageTag("es-AR");
    private VehicleReadOnly reader;
    private TextView fuelValue,energyValue,readStatus;
    private boolean reading=false,initialRead=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        reader=new VehicleReadOnly(this);
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!initialRead){
            initialRead=true;
            readLive();
        }
    }

    private GradientDrawable background(int color,int radius){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        return bg;
    }
    private TextView txt(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setPadding(2,4,2,6);
        return t;
    }
    private LinearLayout vertical(){
        LinearLayout v=new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private void headline(LinearLayout root,String text){
        TextView title=txt(text,19,NAVY,true);
        title.setPadding(2,18,2,9);root.addView(title);
    }
    private void text(LinearLayout root,String value,int size,int color){
        root.addView(txt(value,size,color,false));
    }
    private Button action(LinearLayout root,String label,Runnable run){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(label);b.setTextSize(14);
        b.setOnClickListener(v->run.run());root.addView(b);
        return b;
    }
    private LinearLayout panel(LinearLayout root,int color){
        LinearLayout p=vertical();
        p.setPadding(19,16,19,16);
        p.setBackground(background(color,22));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin=14;
        root.addView(p,params);
        return p;
    }
    private void cycle(LinearLayout root,T1TankEvidence.Cycle c,int color){
        LinearLayout p=panel(root,PALE);
        p.addView(txt(c.label+" · corte "+c.cutoffDate,14,GRAY,true));
        p.addView(txt(T1TankEvidence.number(c.lPer100(),2)+" L/100 km",25,color,true));
        text(p,T1TankEvidence.number(c.kmPerL(),2)+" km/L de repostaje",14,NAVY);
        text(p,T1TankEvidence.number(c.litersPump,3)+" L surtidor · "+
            (c.distanceApproximate?"~":"")+T1TankEvidence.number(c.kmJetour,0)+
            " km Jetour",14,NAVY);
        text(p,c.distanceApproximate?"Distancia aproximada informada":
            "Distancia comunicada del contador Jetour",12,GRAY);
        ProgressBar bar=new ProgressBar(this,null,
            android.R.attr.progressBarStyleHorizontal);
        bar.setMax(900);bar.setProgress((int)Math.round(c.lPer100()*100));
        if(android.os.Build.VERSION.SDK_INT>=21)bar.setProgressTintList(
            android.content.res.ColorStateList.valueOf(color));
        p.addView(bar,new LinearLayout.LayoutParams(-1,18));
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root=vertical();
        root.setPadding(22,18,22,36);
        root.setBackgroundColor(WHITE);
        scroll.addView(root);

        LinearLayout header=panel(root,NAVY);
        header.addView(txt("DRIVELAB AUTO · TABLERO T1",20,WHITE,true));
        header.addView(txt("Consumo por surtidor · telemetría OEM · PHEV",13,0xFFBDE4DE,false));
        header.addView(txt("ETAPA 1 · SOLO LECTURA",12,0xFFFFD996,true));
        action(root,"← Volver a VEHÍCULO / ENERGÍA",this::finish);

        headline(root,"Dos ciclos reales de tanque");
        cycle(root,T1TankEvidence.PREVIOUS,GRAY);
        cycle(root,T1TankEvidence.SECOND,TEAL);
        text(root,"Referencias por primer corte de surtidor. Incluyen los kilómetros "+
            "eléctricos y dependen del SOC y de las condiciones de llenado. "+
            "NO representan el caudal instantáneo del térmico.",13,GRAY);

        headline(root,"Comparación de los tres contadores");
        LinearLayout comparison=panel(root,PALE);
        comparison.addView(txt("Jetour T1    887 km",18,NAVY,true));
        comparison.addView(txt("T1Launcher    867 km  (−20 km)",15,GRAY,false));
        comparison.addView(txt("DriveLab GPS    895,089 km  (+8,089 km)",15,GRAY,false));
        text(comparison,"Períodos y métodos pueden no coincidir; no promediar "+
            "contadores ni corregir el odómetro sin pruebas sincronizadas.",12,GRAY);

        headline(root,"Lecturas del vehículo · ahora");
        LinearLayout live=panel(root,PALE);
        fuelValue=txt("CI186 · sin consultar",19,TEAL,true);live.addView(fuelValue);
        energyValue=txt("SOC/corriente HV · sin consultar",13,NAVY,false);
        live.addView(energyValue);
        readStatus=txt("Todavía no se intentó una lectura OEM",12,GRAY,false);
        live.addView(readStatus);
        action(root,"ACTUALIZAR TELEMETRÍA OEM",this::readLive);
        text(root,"CI186 es un nivel filtrado del vehículo, NO litros físicos "+
            "medidos. SOC y corriente son señales crudas; unidad y signo de "+
            "corriente pendientes de validación.",12,GRAY);

        headline(root,"Corte y llenado · evidencia repetida");
        LinearLayout proof=panel(root,PALE);
        proof.addView(txt("5,3 L OEM · dos cortes del térmico observados",16,NAVY,true));
        proof.addView(txt("67,0 L OEM · lectura posterior a ambos repostajes",14,NAVY,false));
        proof.addView(txt("61,7 L · diferencia indicada por OEM",14,NAVY,false));
        proof.addView(txt("66,230 L · ingreso físico del segundo surtidor",14,TEAL,true));
        proof.addView(txt("+4,530 L · diferencia surtidor menos variación OEM",14,RED,true));
        text(proof,"La bandera CSV 'sobrellenado=1' no prueba sobrellenado: "+
            "el propietario confirmó primer corte, sin completar. "+
            "No aplicar un offset fijo a toda la curva.",12,GRAY);

        headline(root,"Último comprobante de surtidor");
        LinearLayout receipt=panel(root,PALE);
        receipt.addView(txt("10/11/2026 · Nafta SUPER",15,NAVY,true));
        receipt.addView(txt("$141.004",26,TEAL,true));
        receipt.addView(txt("66,230 L × $2.129/L",15,NAVY,false));
        text(receipt,"Misma estación, diferente surtidor. El costo de esta "+
            "carga NO es necesariamente el costo de los 887 km previos.",12,GRAY);

        headline(root,"Evidencia y próximos TRIP");
        text(root,"Cada lectura CI186 iniciada desde esta pantalla se conserva "+
            "en un CSV privado dentro de Receiver, con fecha y origen. "+
            "Podés exportarlo sin borrar los datos originales.",13,NAVY);
        action(root,"EXPORTAR LECTURAS CI186 (CSV)",this::exportReadings);
        text(root,"TRIP A (tanque actual desde 10/11) y TRIP B (parcial) "+
            "se implementarán cuando haya distancia continua validada. "+
            "Todavía NO se calculan litros instantáneos ni kWh sin sensor confirmado.",13,GRAY);
        text(root,"Regla permanente: cálculo VIEJO T1Launcher descartado. "+
            "Surtidor, CSV, sensor y lectura OEM permanecen separados.",12,RED);
        setContentView(scroll);
    }

    private void readLive(){
        if(reading){
            if(readStatus!=null)readStatus.setText("Ya hay una lectura en curso");
            return;
        }
        reading=true;
        if(readStatus!=null)readStatus.setText("Consultando CI186…");
        reader.readFuelOnce(r->{
            if(isFinishing()||isDestroyed()){reading=false;return;}
            if(r.valid()){
                fuelValue.setText("Tanque OEM CI186: "+
                    FuelReadingPolicy.display(r.liters)+" (indicador)");
                storeReading(r);
            }else{
                fuelValue.setText("Tanque CI186: SIN LECTURA · "+r.detail);
            }
            readStatus.setText("Consultando SOC y corriente OEM…");
            reader.readEnergyOnce(values->{
                reading=false;
                if(isFinishing()||isDestroyed())return;
                StringBuilder text=new StringBuilder();
                for(Map.Entry<String,String> row:values.entrySet()){
                    if(text.length()>0)text.append("\n");
                    text.append(row.getKey()).append(": ").append(row.getValue());
                }
                energyValue.setText(text.toString());
                readStatus.setText("Lectura puntual finalizada · "+
                    new SimpleDateFormat("dd/MM HH:mm:ss",es).format(new Date()));
            });
        });
    }
    private void storeReading(VehicleReadOnly.FuelReading reading){
        if(!reading.valid())return;
        File file=new File(getFilesDir(),OBS_FILE);
        try(FileOutputStream out=new FileOutputStream(file,true)){
            if(file.length()==0)
                out.write("timestamp_epoch_ms,source,litros_oem_indicador\n"
                    .getBytes(StandardCharsets.UTF_8));
            String line=reading.timestampMillis+",CI186,"+
                String.format(Locale.US,"%.3f",reading.liters)+"\n";
            out.write(line.getBytes(StandardCharsets.UTF_8));
        }catch(Exception ex){
            if(readStatus!=null)readStatus.setText(
                "Lectura recibida, pero no se pudo guardar el CSV local: "+
                ex.getClass().getSimpleName());
        }
    }
    private void exportReadings(){
        File file=new File(getFilesDir(),OBS_FILE);
        if(!file.isFile()){
            if(readStatus!=null)readStatus.setText("Todavía no hay registros para exportar");
            return;
        }
        try{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("text/csv");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,"DriveLab_T1_CI186_lecturas.csv");
            startActivityForResult(i,CREATE_CSV);
        }catch(Exception ex){
            if(readStatus!=null)readStatus.setText("Android no abrió el exportador: "+
                ex.getClass().getSimpleName());
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=CREATE_CSV||resultCode!=RESULT_OK||data==null||
            data.getData()==null)return;
        File file=new File(getFilesDir(),OBS_FILE);
        Uri destination=data.getData();
        try(InputStream in=new FileInputStream(file);
            OutputStream out=getContentResolver().openOutputStream(destination)){
            if(out==null)throw new IllegalStateException("Destino no grabable");
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))>=0)out.write(buffer,0,n);
            out.flush();
            if(readStatus!=null)readStatus.setText("CSV exportado; "+
                "registro original local conservado");
        }catch(Exception ex){
            if(readStatus!=null)readStatus.setText(
                "Falló exportación: "+ex.getClass().getSimpleName());
        }
    }
}
