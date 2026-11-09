package ar.com.rango5gps.drivelabauto.sender;

import android.service.notification.NotificationListenerService;

/**
 * Android grants this service media-session control after an explicit user opt-in.
 * We do NOT read, retain or transmit any notification contents.
 */
public final class SpotifySessionListener extends NotificationListenerService {
    @Override public void onListenerConnected(){
        super.onListenerConnected();
        SpotifyAutoPlay.checkConnected(this,"listener habilitado",false,null);
    }
}
