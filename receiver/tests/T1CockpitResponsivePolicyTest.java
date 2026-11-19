package ar.com.rango5gps.drivelabauto.receiver;

public final class T1CockpitResponsivePolicyTest {
    static void yes(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        int full=T1CockpitResponsivePolicy.mode(1440);
        int seventy=T1CockpitResponsivePolicy.mode(1010);
        int sixty=T1CockpitResponsivePolicy.mode(865);
        int third=T1CockpitResponsivePolicy.mode(476);
        yes(full==T1CockpitResponsivePolicy.WIDE,"Full cockpit width");
        yes(seventy==T1CockpitResponsivePolicy.MEDIUM,
            "70% Freeform must reduce font and wrap metrics");
        yes(sixty==T1CockpitResponsivePolicy.MEDIUM,
            "60% Freeform must be responsive");
        yes(third==T1CockpitResponsivePolicy.NARROW,
            "33% Freeform must stack trip controls and overview");
        yes(T1CockpitResponsivePolicy.columns(sixty,6,3,2)==3,
            "Six metrics wrap into rows of three at 60%");
        yes(T1CockpitResponsivePolicy.columns(third,6,3,2)==2,
            "Six metrics wrap into rows of two at 33%");
        yes(T1CockpitResponsivePolicy.columns(third,3,3,1)==1,
            "Three trip cards stack individually at 33%");
        yes(T1CockpitResponsivePolicy.fontScale(sixty)>.85f&&
            T1CockpitResponsivePolicy.fontScale(sixty)<1f,
            "Reduce text without illegible scaling");
        yes(T1CockpitResponsivePolicy.fontScale(full)==1f,
            "Returning to fullscreen restores type size");
        System.out.println("PASS cockpit responsive at 100/70/60/33 percent");
    }
}
