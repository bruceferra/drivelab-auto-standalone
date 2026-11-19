package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class T1CenterTaskInventoryTest {
    private static void ck(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static T1CenterTaskInventory.Entry find(
            List<T1CenterTaskInventory.Entry> list,String pkg){
        for(T1CenterTaskInventory.Entry e:list)
            if(pkg.equals(e.pkg))return e;
        return null;
    }
    public static void main(String[] args){
        StringBuilder dump=new StringBuilder();
        dump.append("Stack id=2825 bounds=[960,101][1920,591] displayId=0 userId=0\n")
            .append(" taskId=2825: ar.com.rango5gps.uberbeta/"+
                ".MainActivity bounds=[960,101][1920,591]\n");
        for(int i=0;i<3000;i++)
            dump.append(" com.android.systemui/.HiddenSystemService "+
                "data which must not be retained ").append(i).append('\n');
        dump.append("Stack id=2826 bounds=[1152,591][1920,1080] displayId=0 userId=0\n")
            .append(" taskId=2826: com.openai.chatgpt/"+
                ".MainActivity bounds=[1152,591][1920,1080]\n")
            .append("Stack id=2850 bounds=[0,101][1920,1080] displayId=0 userId=0\n")
            .append(" taskId=2850: com.spotify.music/"+
                ".MainActivity bounds=[0,101][1920,1080]\n")
            .append("Stack id=2800 bounds=[0,0][1920,1080] displayId=1 userId=0\n")
            .append(" taskId=2800: com.spotify.music/"+
                ".OtherDisplayActivity bounds=[0,0][1920,1080]\n")
            .append(" taskId=2801: com.autolink.bluetooth/"+
                ".OemService bounds=[0,0][1920,1080]\n")
            .append(" taskId=2802: com.waze/.MainActivity bounds=[0,0][1920,1080]\n");
        ck(dump.length()>96000,"Simulate oversized ADB task listing");
        T1CenterTaskListExcerpt excerpt=new T1CenterTaskListExcerpt();
        byte[] bytes=dump.toString().getBytes(StandardCharsets.UTF_8);
        for(int start=0;start<bytes.length;start+=73){
            int size=Math.min(73,bytes.length-start);
            byte[] slice=new byte[size];
            System.arraycopy(bytes,start,slice,0,size);
            excerpt.accept(slice);
        }
        excerpt.finish();
        String filtered=excerpt.result();
        ck(filtered.length()<4096,"Output must be bounded and summary only");
        ck(!filtered.contains("HiddenSystemService"),
            "Never retain other verbose Android data");
        List<T1CenterTaskInventory.Entry> entries=
            T1CenterTaskInventory.parse(filtered);
        ck(find(entries,"com.autolink.bluetooth")==null,"OEM excluded");
        ck(find(entries,"ar.com.rango5gps.uberbeta")!=null,"Uber present");
        ck(find(entries,"com.openai.chatgpt")!=null,"ChatGPT present");
        T1CenterTaskInventory.Entry spotify=find(entries,"com.spotify.music");
        ck(spotify!=null&&spotify.centerTasks==1&&
            spotify.otherDisplayTasks==1,"Multiple display tasks counted");
        ck(!T1CenterTaskInventory.canStop(spotify),
            "Never stop a package also present on cluster/secondary display");
        ck(T1CenterTaskInventory.canStop(
            find(entries,"ar.com.rango5gps.uberbeta")),
            "Center-only user task is a candidate (other runtime checks apply)");
        ck(!T1AppStopPolicy.allowPackage("com.waze"),"Keep navigation alive");
        ck(!T1AppStopPolicy.allowPackage("com.google.android.apps.maps"),
            "Keep Maps alive");
        ck(!T1AppStopPolicy.allowPackage("com.jetour.adas"),
            "Keep OEM vehicle services");
        ck(!T1AppStopPolicy.allowPackage("com.mainthor.t1launcher"),
            "Keep aftermarket/active car launcher");
        ck(!T1AppStopPolicy.allowPackage(
            "ar.com.rango5gps.drivelabauto.receiver"),"Keep own Receiver");
        ck(T1AppStopPolicy.allowPackage("com.spotify.music"),
            "User-installed Spotify may be stopped after confirmation");
        ck(!T1AppStopPolicy.allowPackage("com.spotify.music;echo"),
            "Prevent shell interpolation");
        System.out.println("PASS bounded task inventory and safe user-app stop policy");
    }
}
