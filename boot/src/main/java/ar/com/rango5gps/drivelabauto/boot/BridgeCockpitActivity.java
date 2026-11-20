package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Launcher destination: OPEN DIRECTLY IN THE LAST SAVED TWO-APP MOSAIC.
 * Settings live behind a top-right overlay gear, not on the launch screen.
 * Android displays real installed apps; no fake VirtualDisplay of Maps/Spotify.
 * A native split request can still be denied or resized by Jetour.
 */
public final class BridgeCockpitActivity extends Activity {
    static final String REOPEN="bridge.cockpit.REOPEN";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable launch=this::openSavedMosaic;
    private TextView status;
    private boolean scheduled;
    private static final int BG=0xFF102333,TEAL=0xFF35D7DD;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        BridgeImmersive.apply(this);
        draw();
        schedule();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(intent!=null&&REOPEN.equals(intent.getAction())){
            schedule();
        }else schedule(); // tapping app launcher returns to saved mosaic
    }
    private String appName(String pkg){
        if(pkg==null)return "Sin aplicación";
        try{return getPackageManager().getApplicationLabel(
            getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(Exception missing){return pkg;}
    }
    private void draw(){
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(30,0,30,0);
        content.setBackgroundColor(BG);
        TextView logo=new TextView(this);
        logo.setText("DRIVELAB BRIDGE");
        logo.setTextSize(26f);logo.setTextColor(TEAL);
        content.addView(logo);
        String left=appName(BridgeStandaloneWindows.first(this));
        String right=appName(BridgeStandaloneWindows.second(this));
        int ratio=BridgeStandaloneWindows.ratio(this);
        TextView desc=new TextView(this);
        desc.setText(left+"   "+ratio+"%   |   "+
            (100-ratio)+"%   "+right);
        desc.setTextSize(18f);desc.setTextColor(Color.WHITE);
        desc.setPadding(0,12,0,16);
        content.addView(desc);
        status=new TextView(this);
        status.setText("Abriendo el mosaico guardado…");
        status.setTextSize(15f);status.setTextColor(Color.WHITE);
        content.addView(status);
        Button gear=new Button(this);
        gear.setText("\u2699  CONFIGURACIÓN");
        gear.setAllCaps(false);
        gear.setOnClickListener(v->startActivity(
            new Intent(this,BootActivity.class)));
        content.addView(gear);
        Button retry=new Button(this);
        retry.setText("ABRIR MOSAICO");
        retry.setAllCaps(false);
        retry.setOnClickListener(v->schedule());
        content.addView(retry);
        setContentView(content);
    }
    private void schedule(){
        if(scheduled)return;
        scheduled=true;
        main.postDelayed(launch,280L);
    }
    private void openSavedMosaic(){
        scheduled=false;
        if(isFinishing()||isDestroyed())return;
        if(!BridgeWindowsPolicy.validCenter(
            BridgeStandaloneWindows.first(this),
            BridgeStandaloneWindows.second(this),
            BridgeStandaloneWindows.ratio(this))){
            show("Configuración incompleta. Entrá a la ruedita y "+
                "seleccioná dos aplicaciones distintas.");
            return;
        }
        // Without this authorized overlay, opening third-party apps
        // would hide the only Bridge settings access.
        if(!Settings.canDrawOverlays(this)){
            show("Mosaico preparado. La ruedita sobre apps necesita "+
                "el permiso Android Mostrar sobre otras aplicaciones. "+
                "Entrá a CONFIGURACIÓN, habilitá SEGUNDO PLANO y "+
                "volvé a abrir Bridge.");
            return;
        }
        try{
            startForegroundService(new Intent(this,
                BridgeCockpitOverlayService.class)
                .setAction(BridgeCockpitOverlayService.SHOW));
        }catch(RuntimeException denied){
            show("Android no permitió iniciar la ruedita: "+
                denied.getClass().getSimpleName()+
                ". Entrá a CONFIGURACIÓN.");
            return;
        }
        BridgeStandaloneWindows.Result result=
            BridgeCockpitConfig.FREEFORM.equals(
                BridgeCockpitConfig.mode(this))?
                BridgeStandaloneWindows.center(this):
                BridgeStandaloneWindows.centerSplitNoFreeform(this);
        BridgeCockpitConfig.status(this,result.message);
        if(!result.submitted){
            show("No se pudo abrir el mosaico. "+
                result.message+" Usá CONFIGURACIÓN para cambiar el modo.");
        }else{
            show("Mosaico solicitado. Reparto "+
                BridgeStandaloneWindows.ratio(this)+"/"+
                (100-BridgeStandaloneWindows.ratio(this))+
                ". Android puede ignorar la proporción o conservar barras.");
        }
    }
    private void show(String result){
        if(status!=null)status.setText(result);
    }
    @Override public void onWindowFocusChanged(boolean focus){
        super.onWindowFocusChanged(focus);
        if(focus)BridgeImmersive.apply(this);
    }
    @Override protected void onDestroy(){
        main.removeCallbacks(launch);
        super.onDestroy();
    }
}
