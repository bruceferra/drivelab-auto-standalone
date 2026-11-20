package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.app.ActivityOptions;
import android.content.Intent;
import android.view.Display;

/** Launches ONLY Bridge's own, non-exported Android Activity.
 * A private virtual display is NOT a Waze/Maps embedding permission.
 */
final class BridgeVirtualTaskLauncher {
    private BridgeVirtualTaskLauncher(){}
    static void launchOwn(Activity host,Display target,long token,
                          boolean expectPublic){
        if(host==null||target==null||token<=0)
            throw new IllegalArgumentException("Sesión o display inválido");
        int id=target.getDisplayId();
        boolean privateDisplay=(target.getFlags()&Display.FLAG_PRIVATE)!=0;
        if(id<=1||privateDisplay==expectPublic||
            target.getState()==Display.STATE_OFF)
            throw new SecurityException("Display de Bridge no coincide con modo "+
                (expectPublic?"público":"privado"));
        Intent own=new Intent(host,BridgeVirtualSceneActivity.class);
        own.putExtra(BridgeVirtualSceneActivity.EXTRA_EXPECTED_DISPLAY,id);
        own.putExtra(BridgeVirtualSceneActivity.EXTRA_SESSION,token);
        own.putExtra(BridgeVirtualSceneActivity.EXTRA_EXPECT_PUBLIC,expectPublic);
        own.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ActivityManager manager=(ActivityManager)host.getSystemService(
            Context.ACTIVITY_SERVICE);
        if(manager==null)
            throw new SecurityException("PRECHECK_NO_ACTIVITY_MANAGER");
        // Android 11 public launch-policy preflight. Do not try to defeat
        // the denial with privileged ADB, task moves or OEM system flags.
        if(!manager.isActivityStartAllowedOnDisplay(host,id,own))
            throw new SecurityException(expectPublic?
                "PRECHECK_DENIED_PUBLIC_DISPLAY":
                "PRECHECK_DENIED_PRIVATE_DISPLAY");
        ActivityOptions options=ActivityOptions.makeBasic();
        options.setLaunchDisplayId(id);
        host.startActivity(own,options.toBundle());
    }
}
