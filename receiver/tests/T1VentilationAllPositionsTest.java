package ar.com.rango5gps.drivelabauto.receiver;
public final class T1VentilationAllPositionsTest{
    static void ok(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    public static void main(String[] args){
        for(int state=0;state<=4;state++){
            ok(WindowGroupPolicy.decision(WindowGroupPolicy.VENT_QUARTER,state)==
                (state==3?WindowGroupPolicy.SKIP:WindowGroupPolicy.SEND),
                "VENT 3 allowed from all OEM states including full open "+state);
            ok(WindowGroupPolicy.decision(WindowGroupPolicy.CLOSE,state)==
                (state==1?WindowGroupPolicy.SKIP:WindowGroupPolicy.SEND),
                "CLOSE 1 allowed from state "+state);
        }
        int[] p=WindowGroupPolicy.plan(WindowGroupPolicy.VENT_QUARTER,
            new int[]{4,2,0,1});
        ok(p.length==4&&p[0]==WindowGroupPolicy.SEND,
            "Never block mixed starting positions");
        ok(WindowGroupPolicy.validId(2004)&&
            !WindowGroupPolicy.validId(2008),"Only four OEM windows");
        // One lower window at neighborhood entry MUST restore quarter,
        // not close everything, regardless of the previous button press.
        ok(WindowGroupPolicy.smartOperation(new int[]{3,3,3,3})==
            WindowGroupPolicy.CLOSE,"All four quarter -> close all");
        ok(WindowGroupPolicy.smartOperation(new int[]{4,3,3,3})==
            WindowGroupPolicy.VENT_QUARTER,
            "One lowered window -> restore all quarter");
        ok(WindowGroupPolicy.smartOperation(new int[]{1,3,2,0})==
            WindowGroupPolicy.VENT_QUARTER,"Mixed state -> all quarter");
        ok(WindowGroupPolicy.smartOperation(new int[]{1,1,1,1})==
            WindowGroupPolicy.VENT_QUARTER,"All closed -> all quarter");
        try {
            WindowGroupPolicy.smartOperation(new int[]{3,3,3});
            throw new AssertionError("incomplete OEM read must fail");
        }catch(IllegalArgumentException expected){}
        try {
            WindowGroupPolicy.smartOperation(new int[]{3,3,3,8});
            throw new AssertionError("invalid OEM state must fail");
        }catch(IllegalArgumentException expected){}

        System.out.println("PASS quarter ventilation from 0/1/2/3/4");
    }
}
