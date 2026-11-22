package ar.com.rango5gps.drivelabauto.boot;
public final class NavMosaicLifecyclePolicyTest{
    static void yes(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        yes(NavMosaicLifecyclePolicy.retainOnStop(true,false,false));
        yes(!NavMosaicLifecyclePolicy.retainOnStop(false,false,false));
        yes(!NavMosaicLifecyclePolicy.retainOnStop(true,true,false));
        yes(!NavMosaicLifecyclePolicy.retainOnStop(true,false,true));
        yes(NavMosaicLifecyclePolicy.retainSurfaceOnDestroy(
            true,false,false,true));
        yes(!NavMosaicLifecyclePolicy.retainSurfaceOnDestroy(
            false,false,false,true));
        yes(!NavMosaicLifecyclePolicy.retainSurfaceOnDestroy(
            true,false,false,false));
        yes(!NavMosaicLifecyclePolicy.retainSurfaceOnDestroy(
            true,false,true,true));
        yes(!NavMosaicLifecyclePolicy.retainSurfaceOnDestroy(
            true,true,false,true));
        yes(NavMosaicLifecyclePolicy.reattachSameDisplay(true,true,false));
        yes(!NavMosaicLifecyclePolicy.reattachSameDisplay(true,false,false));
        yes(!NavMosaicLifecyclePolicy.reattachSameDisplay(true,true,true));
        yes(NavMosaicLifecyclePolicy.closeAfterTimeout(true,false,false));
        yes(!NavMosaicLifecyclePolicy.closeAfterTimeout(true,false,true));
        yes(!NavMosaicLifecyclePolicy.closeAfterTimeout(false,false,false));
        yes(!NavMosaicLifecyclePolicy.closeAfterTimeout(true,true,false));
        System.out.println("PASS NavMosaicLifecyclePolicyTest");
    }
}
