package ar.com.rango5gps.drivelabauto.boot;

public final class NavMosaicExitPolicyTest {
    static final int MAP=6, MEDIA=7;
    static final String MAPS="com.google.android.apps.maps";
    static final String SPOTIFY="com.spotify.music";
    private static void yes(boolean x){if(!x)throw new AssertionError();}
    private static String base(String map,String music){
        return "Stack id=99 displayId=1\n taskId=101: com.jetour.oem/.Main\n"+
          "Stack id=120 displayId=9\n taskId=102: com.waze/.Main\n"+
          (map.isEmpty()?"": "Stack id=52 displayId=6\n taskId=222: "+map+"\n")+
          (music.isEmpty()?"": "Stack id=53 displayId=7\n taskId=223: "+music+"\n");
    }
    public static void main(String[] args){
        String ok=base(MAPS+"/com.google.android.maps.MapsActivity",
                       SPOTIFY+"/.MainActivity");
        NavMosaicExitPolicy.Snapshot one=NavMosaicExitPolicy.parse(ok,MAP,MAPS,MEDIA);
        yes(one.valid&&one.map!=null&&one.spotify!=null&&one.targets().size()==2);
        yes(one.same(NavMosaicExitPolicy.parse(ok,MAP,MAPS,MEDIA)));
        yes("am stack remove 53".equals(NavMosaicExitPolicy.removeCommand(
             one.spotify,MAP,MAPS,MEDIA)));
        yes("am stack remove 52".equals(NavMosaicExitPolicy.removeCommand(
             one.map,MAP,MAPS,MEDIA)));
        yes(!NavMosaicExitPolicy.parse(ok,MAP,MAPS,MEDIA)
                    .same(NavMosaicExitPolicy.parse(ok.replace("taskId=222","taskId=224"),
                          MAP,MAPS,MEDIA)));
        yes(NavMosaicExitPolicy.parse(base("",""),MAP,MAPS,MEDIA).valid);
        yes(NavMosaicExitPolicy.parse(base("",SPOTIFY+"/.Main"),MAP,MAPS,MEDIA)
                     .targets().size()==1);
        yes(NavMosaicExitPolicy.parse(base("com.waze/.Main",SPOTIFY+"/.Main"),
                     MAP,"com.waze",MEDIA).targets().size()==2);
        yes(!NavMosaicExitPolicy.parse(ok+
            " taskId=225: com.jetour.oem/.Main\n",MAP,MAPS,MEDIA).valid);
        yes(!NavMosaicExitPolicy.parse(ok+
            "Stack id=60 displayId=6\n taskId=250: "+
            MAPS+"/.Other\n",MAP,MAPS,MEDIA).valid);
        yes(NavMosaicExitPolicy.parse(ok,MAP,MAPS,MEDIA).targets().size()==2);
        yes(!NavMosaicExitPolicy.parse(ok,0,MAPS,MEDIA).valid);
        yes(!NavMosaicExitPolicy.parse(ok,MAP,MAPS,MAP).valid);
        yes(!NavMosaicExitPolicy.parse(ok,MAP,"com.jetour.oem",MEDIA).valid);
        yes(NavMosaicExitPolicy.rejected("Error: permission denied"));
        yes(!NavMosaicExitPolicy.rejected(""));
        try {
            NavMosaicExitPolicy.removeCommand(one.map,1,MAPS,MEDIA);
            throw new AssertionError("unsafe root allowed");
        }catch(SecurityException expected){}
        System.out.println("PASS NavMosaicExitPolicyTest");
    }
}
