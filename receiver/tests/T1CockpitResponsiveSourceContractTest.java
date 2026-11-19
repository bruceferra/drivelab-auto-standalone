package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Cockpit adapts to its actual Freeform window, not the fixed 1920px
 * device display; toolbar controls remain reachable in all modes.
 */
public final class T1CockpitResponsiveSourceContractTest {
    static void ck(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }
    static String read(String path)throws Exception{
        return Files.readString(Path.of(path));
    }
    public static void main(String[] args)throws Exception{
        String root="receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/";
        String cockpit=read(root+"T1PremiumCockpitActivity.java"),
            responsive=read(root+"T1CockpitResponsive.java"),
            toolbar=read(root+"T1GlobalToolbar.java"),
            base=read(root+"T1ImmersiveActivity.java");
        ck(cockpit.contains("T1CockpitResponsive responsive=new T1CockpitResponsive(scroll)")&&
            cockpit.contains("responsive.register(top,9,3,2)")&&
            cockpit.contains("responsive.register(controls,3,3,1)")&&
            cockpit.contains("responsive.register(overview,3,3,1)")&&
            cockpit.contains("responsive.register(metrics,6,3,2)")&&
            cockpit.contains("responsive.register(commentaryColumns,2,1,1)")&&
            cockpit.contains("responsive.attach()"),
            "Compact layout must reflow actual existing Cockpit cards");
        ck(responsive.contains("addOnLayoutChangeListener")&&
            responsive.contains("adapt(r-l)")&&
            responsive.contains("getDisplayMetrics().density")&&
            responsive.contains("T1CockpitResponsivePolicy.mode(widthDp)"),
            "Use actual window width and refresh during live Freeform resize");
        ck(responsive.contains("group.reflow(mode,gap)")&&
            responsive.contains("params.get(i)")&&
            responsive.contains("naturalTextPx")&&
            responsive.contains("TypedValue.COMPLEX_UNIT_PX")&&
            !responsive.contains("startActivity(")&&
            !responsive.contains("force-stop"),
            "Preserve existing buttons, original sizing and trips, without relaunch");
        ck(toolbar.contains("HorizontalScrollView firstStrip")&&
            toolbar.contains("header.addView(firstStrip,new LinearLayout.LayoutParams(0,-1,1f))")&&
            toolbar.contains("firstButtons=leading")&&
            toolbar.contains("enableRowReorder(leading,\"first\")"),
            "Vehicle OEM controls must be horizontally scrollable in tiny windows");
        ck(toolbar.contains("header.addView(clockBox,")&&
            toolbar.contains("Button recoverMosaic=button(header,\"▦\"")&&
            toolbar.contains("Button update=button(header,\"⟳\"")&&
            toolbar.contains("boolean compact=(r-l)<dp(760)")&&
            toolbar.contains("reload.setText(compact?\"MAP\":\"MAPAS\")"),
            "Clock/mosaic/maps/update stay pinned and shrink if window narrows");
        ck(base.contains("shell.addView(globalToolbar.view()")&&
            base.contains("shell.addView(content,"),
            "Fixed top bar remains outside the Cockpit ScrollView");
        System.out.println("PASS responsive Cockpit cards, live text and fixed/scrollable global toolbar");
    }
}
