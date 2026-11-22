package ar.com.rango5gps.drivelabauto.boot;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * NAV52 isolated four-slot controls, not staged in the user-facing APK.
 * Two small ↗ overlays stay on CENTRAL A and B; a third compact control
 * in NAV's upper toolbar swaps TABLERO A/B from the touchscreen.
 *
 * This controller NEVER touches Android tasks, surfaces or OEM keys.
 * It sends an exact from/to/session/revision request to the owner.
 * The owner must check the Android driver's outcome before publishing it.
 */
final class NavFourSlotControls {
    interface SwapRequest {
        void request(NavFourSlotPolicy.Slot from,
                     NavFourSlotPolicy.Slot to,
                     long session,long revision);
    }
    private final Activity activity;
    private final SwapRequest send;
    private final Button left,right,dashboard;
    private NavFourSlotPolicy.State state;
    private boolean busy,requiresRecovery;

    NavFourSlotControls(Activity owner,
                        FrameLayout centralA,FrameLayout centralB,
                        LinearLayout navToolbar,SwapRequest callback){
        if(owner==null||centralA==null||centralB==null||
           navToolbar==null||callback==null)
            throw new SecurityException("Controles NAV sin panel central");
        if(centralA.getContext()!=owner||
           centralB.getContext()!=owner)
            throw new SecurityException("Paneles centrales no pertenecen a NAV");
        activity=owner;send=callback;
        left=overlay(centralA,NavFourSlotPolicy.Slot.CENTRAL_A);
        right=overlay(centralB,NavFourSlotPolicy.Slot.CENTRAL_B);
        dashboard=chip("TABLERO  A⇄B");
        dashboard.setTextSize(10);
        navToolbar.addView(dashboard,
            new LinearLayout.LayoutParams(0,dp(40),1.4f));
        dashboard.setOnClickListener(v->requestDashboardSwap());
        setBusy(false);
    }
    private int dp(int size){
        return Math.round(size*
            activity.getResources().getDisplayMetrics().density);
    }
    private Button chip(String label){
        Button button=new Button(activity);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(13);
        button.setPadding(0,0,0,0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setTextColor(0xFFE8F5F8);
        GradientDrawable rounded=new GradientDrawable();
        rounded.setColor(0xB31C4351);
        rounded.setCornerRadius(dp(9));
        rounded.setStroke(dp(1),0x707AC1D2);
        button.setBackground(rounded);
        return button;
    }
    private Button overlay(FrameLayout panel,NavFourSlotPolicy.Slot from){
        Button button=chip("↗");
        button.setContentDescription(
            "Mover app de "+name(from)+" a otro panel");
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(
            dp(34),dp(33),Gravity.TOP|Gravity.RIGHT);
        pos.setMargins(0,dp(6),dp(6),0);
        panel.addView(button,pos);
        button.setOnClickListener(v->chooseDestination(from));
        return button;
    }
    private static String name(NavFourSlotPolicy.Slot slot){
        switch(slot){
            case CENTRAL_A:return "Central A";
            case CENTRAL_B:return "Central B";
            case TABLERO_A:return "Tablero A";
            case TABLERO_B:return "Tablero B";
            default:throw new SecurityException("Panel desconocido");
        }
    }
    private static String appName(NavFourSlotPolicy.Stream stream){
        if(stream==null)return "LIBRE";
        if("com.waze".equals(stream.pkg))return "Waze";
        if("com.google.android.apps.maps".equals(stream.pkg))
            return "Google Maps";
        if("com.spotify.music".equals(stream.pkg))return "Spotify";
        return stream.pkg;
    }
    void publish(NavFourSlotPolicy.State verified){
        if(verified==null||busy||requiresRecovery)
            throw new SecurityException("Estado NAV sin verificación");
        NavFourSlotPolicy.validate(verified);
        state=verified;
        refresh();
    }
    void setBusy(boolean inProgress){
        busy=inProgress;
        refresh();
    }
    void blockUntilRecovery(String reason){
        requiresRecovery=true;
        busy=false;
        refresh();
        new AlertDialog.Builder(activity)
            .setTitle("Paneles NAV · recuperación necesaria")
            .setMessage(reason==null?"Se perdió una superficie NAV":
                reason)
            .setPositiveButton("ENTENDIDO",null)
            .show();
    }
    private void refresh(){
        boolean permitted=state!=null&&!busy&&!requiresRecovery;
        if(left!=null){
            left.setVisibility(permitted&&
                state.at(NavFourSlotPolicy.Slot.CENTRAL_A)!=null?
                    View.VISIBLE:View.GONE);
            left.setEnabled(permitted);
        }
        if(right!=null){
            right.setVisibility(permitted&&
                state.at(NavFourSlotPolicy.Slot.CENTRAL_B)!=null?
                    View.VISIBLE:View.GONE);
            right.setEnabled(permitted);
        }
        if(dashboard!=null){
            dashboard.setEnabled(permitted&&
                (state.at(NavFourSlotPolicy.Slot.TABLERO_A)!=null||
                 state.at(NavFourSlotPolicy.Slot.TABLERO_B)!=null));
        }
    }
    private void chooseDestination(NavFourSlotPolicy.Slot from){
        if(state==null||busy||requiresRecovery||state.at(from)==null)
            return;
        final NavFourSlotPolicy.State snapshot=state;
        NavFourSlotPolicy.Slot[] options=new NavFourSlotPolicy.Slot[3];
        String[] labels=new String[3];
        int i=0;
        for(NavFourSlotPolicy.Slot target:NavFourSlotPolicy.Slot.values()){
            if(target==from)continue;
            options[i]=target;
            NavFourSlotPolicy.Stream occupant=snapshot.at(target);
            labels[i]=name(target)+"  ·  "+
                appName(occupant)+(occupant!=null?
                    "  (INTERCAMBIAR)":"  (MOVER)");
            i++;
        }
        new AlertDialog.Builder(activity)
            .setTitle("Mover "+appName(snapshot.at(from))+
                      " desde "+name(from))
            .setItems(labels,(dialog,index)->
                requestVerified(from,options[index],snapshot))
            .setNegativeButton("CANCELAR",null).show();
    }
    private void requestDashboardSwap(){
        if(state==null||busy||requiresRecovery)return;
        final NavFourSlotPolicy.State snapshot=state;
        NavFourSlotPolicy.Slot from=
            snapshot.at(NavFourSlotPolicy.Slot.TABLERO_A)!=null?
            NavFourSlotPolicy.Slot.TABLERO_A:
            NavFourSlotPolicy.Slot.TABLERO_B;
        if(snapshot.at(from)==null)return;
        NavFourSlotPolicy.Slot to=
            from==NavFourSlotPolicy.Slot.TABLERO_A?
            NavFourSlotPolicy.Slot.TABLERO_B:
            NavFourSlotPolicy.Slot.TABLERO_A;
        new AlertDialog.Builder(activity)
            .setTitle("Intercambiar tablero A/B")
            .setMessage(name(from)+" · "+appName(snapshot.at(from))+
                "\n"+name(to)+" · "+appName(snapshot.at(to))+
                "\n\nSe conservan las tareas y las rutas.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("INTERCAMBIAR",
                (dialog,which)->requestVerified(from,to,snapshot))
            .show();
    }
    private void requestVerified(NavFourSlotPolicy.Slot from,
                                 NavFourSlotPolicy.Slot to,
                                 NavFourSlotPolicy.State original){
        if(busy||requiresRecovery||state!=original||
           original.at(from)==null)return;
        setBusy(true);
        send.request(from,to,original.session,original.revision);
        // No optimistic success. Caller must invoke publish() only
        // after Android task identity AND Surface frames are verified.
    }
}
