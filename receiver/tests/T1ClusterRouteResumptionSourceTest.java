package ar.com.rango5gps.drivelabauto.receiver;
import java.nio.file.Files;import java.nio.file.Path;

/** Source-level regression: no blind Waze fallback or receiver-wide
 * autoopen block when the owner resumes an approved split.
 * This is NOT proof Android actually resumed turn-by-turn guidance.
 */
public final class T1ClusterRouteResumptionSourceTest {
    private static String code(String name)throws Exception {
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver/"+name));
    }
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception {
        String cache=code("T1LastRoute.java");
        String toolbar=code("T1GlobalToolbar.java");
        String receiver=code("ReceiverService.java");
        String voice=code("T1VoiceRouteActivity.java");
        String activity=code("ReceiverActivity.java");
        check(cache.contains("MAX_AGE_MS=12L*60L*60L*1000L")&&
            cache.contains("BridgeRouteTarget.BOTH")&&
            cache.contains("T1NavigationTargets.send(")&&
            cache.contains("T1ClusterWazeRouter.routeDestination(")&&
            cache.contains("T1ClusterLayoutPolicy.pendingUnverified(c)")&&
            cache.contains("T1ClusterWazeRouter.selected(c)==null")&&
            cache.contains("!T1ClusterWazeRouter.enabled(c)"),
            "Only replay recent explicitly requested routes on verified cluster");
        check(receiver.contains("if(!launchWanted&&!cluster)")&&
            receiver.contains("if(attempted&&cluster)T1LastRoute.remember("),
            "Off-cluster auto-open switch must not block Sender to active cluster");
        check(voice.contains("if(result.attempted)T1LastRoute.remember(")&&
            voice.contains("if(T1ClusterWazeRouter.routeDestination(this,waze))")&&
            voice.contains("visibleNavigationOnCluster(this)")&&
            voice.contains("BridgeRouteTarget.MAPS")&&
            voice.contains("T1LastRoute.remember(this,"),
            "Only remember accepted direct microphone destinations");
        check(toolbar.contains("T1LastRoute.restore(owner)")&&
            toolbar.contains("if(launched&&!pendingLayout)")&&
            toolbar.contains("!T1ClusterWazeRouter.enabled(owner)")&&
            toolbar.contains("T1ClusterLayoutPolicy.pendingUnverified(owner)"),
            "A CLUSTER ON after boot may replay; OFF or pending geometry cannot");
        check(activity.contains("APERTURA FUERA DEL CLÚSTER")&&
            activity.contains("rutas del Sender entran"),
            "Clearly distinguish independent receiver permissions");
        check(!cache.contains("am stack")&&!cache.contains("su -c")&&
            !cache.contains("Runtime.getRuntime()"),
            "Restore must never resize tasks or touch OEM display");
        System.out.println("PASS scoped last-route restore after CLUSTER ON");
    }
}
