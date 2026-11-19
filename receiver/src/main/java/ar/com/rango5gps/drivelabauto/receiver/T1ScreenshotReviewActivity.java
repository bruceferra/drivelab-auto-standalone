package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import java.io.File;

/** Review before sharing a screenshot. There is NO automatic transfer to
 * an unverified destination. Existing QR cloud supports diagnostic CSV,
 * not PNG. Share uses Android's user-selected target with URI read grant.
 */
public final class T1ScreenshotReviewActivity extends Activity {
    private File current;
    private LinearLayout root;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        draw();
    }
    private void label(String content,int textSp){
        TextView v=new TextView(this);
        v.setText(content);v.setTextSize(textSp);
        v.setTextColor(Color.WHITE);
        v.setPadding(16,12,16,12);
        root.addView(v);
    }
    private void button(String text,Runnable action){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setOnClickListener(v->action.run());
        root.addView(b,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private void share(){
        if(current==null)return;
        try{
            Uri uri=FileProvider.getUriForFile(this,
                getPackageName()+".screenshotfiles",current);
            Intent send=new Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM,uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newUri(getContentResolver(),
                "Captura DriveLab",uri));
            startActivity(Intent.createChooser(send,
                "Compartir captura del T1"));
        }catch(Exception ex){
            new AlertDialog.Builder(this).setTitle("NO SE COMPARTIÓ")
                .setMessage(ex.getClass().getSimpleName()+": "+
                    ex.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xFF101B25);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,14,24,30);
        scroll.addView(root);
        label("CAPTURA DE PANTALLA · DRIVELAB",22);
        label("Deslizá tres dedos hacia abajo sobre Display 0, "+
            "con el servicio activado en Accesibilidad. Se guarda "+
            "localmente y solo se comparte cuando vos elegís COMPARTIR.",13);
        String name=getIntent()==null?null:
            getIntent().getStringExtra("filename");
        try{
            current=name==null?T1ScreenshotShareStore.latest(this):
                T1ScreenshotShareStore.selected(this,name);
        }catch(Exception ex){current=null;}
        if(current==null){
            label("Todavía no hay capturas guardadas.",15);
        }else{
            label(current.getName(),13);
            ImageView img=new ImageView(this);
            Bitmap bitmap=BitmapFactory.decodeFile(
                current.getAbsolutePath());
            if(bitmap!=null)img.setImageBitmap(bitmap);
            img.setAdjustViewBounds(true);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(img,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            button("COMPARTIR CAPTURA · ELEGIR DESTINO",this::share);
        }
        button("CAPTURAR AHORA",()->{
            if(T1ScreenshotGestureService.manualCapture()){
                label("Captura solicitada: abrí la notificación "+
                    "«Captura guardada» para revisarla.",13);
            }else{
                new AlertDialog.Builder(this)
                    .setTitle("ACTIVAR CAPTURA")
                    .setMessage("El servicio opcional de capturas "+
                        "todavía no está activo. Habilitalo "+
                        "manualmente en Accesibilidad. El gesto de "+
                        "tres dedos puede cambiar la manera en que "+
                        "Android interpreta algunos toques. "+
                        "Probalo solo con el auto detenido.")
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("ABRIR ACCESIBILIDAD",(d,w)->
                        startActivity(new Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .show();
            }
        });
        button("CONFIGURAR GESTO DE TRES DEDOS",()->
            new AlertDialog.Builder(this)
                .setTitle("GESTO OPCIONAL")
                .setMessage("Android debe permitir el servicio "+
                    "«DriveLab · Captura tres dedos». "+
                    "Necesita Accesibilidad para reconocer gestos "+
                    "en otras aplicaciones. No se activa solo. "+
                    "Puede alterar el táctil o interferir con "+
                    "otros servicios de accesibilidad; "+
                    "deshabilitalo en Ajustes si ocurre. "+
                    "No se envían capturas automáticamente.")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("ABRIR AJUSTES",(d,w)->
                    startActivity(new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .show());
        label("SERVIDOR: el canal de QR existente solo admite CSV. "+
            "No hay un endpoint autenticado de imágenes verificado "+
            "en promanager.com.ar. Por seguridad, no se "+
            "suben capturas a otro servidor sin configurarlo.",12);
        setContentView(scroll);
    }
}
