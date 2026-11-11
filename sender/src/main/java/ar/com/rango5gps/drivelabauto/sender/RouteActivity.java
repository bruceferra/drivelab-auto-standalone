package ar.com.rango5gps.drivelabauto.sender;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Transparent, instantaneous Android share/deep-link trampoline.
 * Only an Activity can be selected by Uber or Android ACTION_SEND; all link
 * parsing and encrypted delivery happens in RouteDispatchService after finish.
 * Uber stays visible (no DriveLab screen or modal progress dialogs).
 */
public final class RouteActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        dispatch(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        dispatch(intent);
    }
    private void dispatch(Intent source){
        try{
            String raw=null;
            if(source!=null && Intent.ACTION_VIEW.equals(source.getAction()) &&
                source.getData()!=null){
                raw=source.getDataString();
            }else if(source!=null && Intent.ACTION_SEND.equals(source.getAction())){
                CharSequence text=source.getCharSequenceExtra(Intent.EXTRA_TEXT);
                CharSequence subject=source.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
                if(text!=null){
                    raw=text.toString();
                    if(subject!=null&&!subject.toString().trim().isEmpty() &&
                       !subject.toString().contains("://"))
                        raw=subject.toString().trim()+"\n"+raw;
                }
            }
            Intent worker=new Intent(this,RouteDispatchService.class);
            worker.setAction(RouteDispatchService.ACTION_SEND_ROUTE);
            if(raw!=null&&raw.length()<=8192)worker.putExtra(RouteDispatchService.EXTRA_RAW,raw);
            startForegroundService(worker);
        }catch(Exception e){
            Toast.makeText(this,"DriveLab: no se pudo iniciar envío oculto",
                Toast.LENGTH_SHORT).show();
        }finally{
            finish();
            overridePendingTransition(0,0);
        }
    }
}
