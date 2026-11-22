package ar.com.rango5gps.drivelabauto.boot;

/**
 * Pure policy: prevent temporary virtual-display focus transfers from
 * destroying the two NAV-owned central tasks and their rendering surfaces.
 * This makes no claim about OEM visibility; native lifecycle remains
 * authoritative for a finished/destroyed Activity.
 */
final class NavMosaicLifecyclePolicy {
    private NavMosaicLifecyclePolicy(){}
    static boolean retainOnStop(boolean activeOwnWaze,
            boolean closed,boolean finishing){
        return activeOwnWaze&&!closed&&!finishing;
    }
    static boolean retainSurfaceOnDestroy(boolean activeOwnWaze,
            boolean closed,boolean finishing,boolean displayExists){
        return activeOwnWaze&&!closed&&!finishing&&displayExists;
    }
    static boolean reattachSameDisplay(boolean retained,
            boolean displayExists,boolean closed){
        return retained&&displayExists&&!closed;
    }
    static boolean closeAfterTimeout(boolean stillStopped,
            boolean closed,boolean hasOwnWaze){
        return stillStopped&&!closed&&!hasOwnWaze;
    }
}
