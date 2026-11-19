package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.charset.StandardCharsets;

/** The external-user-app task scan must be bounded and must not return
 * another application's tasks or accidentally match an OEM component.
 */
public final class T1AdbPackageTaskExcerptTest {
    private static void yes(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static String read(String pkg,String data){
        T1AdbPackageTaskExcerpt filter=new T1AdbPackageTaskExcerpt(pkg);
        byte[] bytes=data.getBytes(StandardCharsets.UTF_8);
        for(int start=0;start<bytes.length;start+=71){
            int count=Math.min(71,bytes.length-start);
            byte[] segment=new byte[count];
            System.arraycopy(bytes,start,segment,0,count);
            filter.accept(segment);
        }
        filter.finish();
        return filter.result();
    }
    public static void main(String[] args){
        String pro="ar.com.promanager.nativeapp",
            uber="ar.com.rango5gps.uberbeta";
        StringBuilder dump=new StringBuilder();
        dump.append("Stack id=2799 bounds=[0,0][1920,1080] displayId=0 userId=0\n");
        dump.append(" taskId=2799: "+pro+"/.MainActivity bounds=[0,0][1920,1080]\n");
        for(int i=0;i<3000;i++)
            dump.append(" taskId=").append(10000+i)
                .append(": com.autolink.bluetooth/.Internal bounds=[0,0][1920,1080]\n");
        dump.append("Stack id=2818 bounds=[0,101][960,1181] displayId=0 userId=0\n");
        dump.append(" taskId=2818: "+pro+"/.MainActivity bounds=[0,101][960,1181]\n");
        dump.append("Stack id=2819 bounds=[960,101][1920,1181] displayId=0 userId=0\n");
        dump.append(" taskId=2819: "+uber+"/.MainActivity bounds=[960,101][1920,1181]\n");
        yes(dump.length()>96*1024,
            "Must simulate oversized Android task listing");
        String proList=read(pro,dump.toString());
        yes(proList.contains("taskId=2799:")&&
            proList.contains("taskId=2818:")&&
            !proList.contains("taskId=2819:")&&
            !proList.contains("com.autolink."),
            "Only approved ProManager tasks may be returned");
        yes(T1AdbTaskParser.tasksOnCenter(proList,pro).size()==2,
            "Both pre-existing and new ProManager tasks are distinguishable");
        String uberList=read(uber,dump.toString());
        yes(T1AdbTaskParser.tasksOnCenter(uberList,uber).size()==1,
            "Only one new Uber Beta task should be reported");
        String music="com.spotify.music";
        String spotifyStack="Stack id=2822 bounds=[0,101][960,1181] "+
            "displayId=0 userId=0\n"+
            " taskId=2822: "+music+"/.MainActivity bounds=[0,101][960,1181]\n";
        yes(T1AdbTaskParser.tasksOnCenter(read(music,spotifyStack),music).size()==1,
            "Saved chooser must support arbitrary external user-installed Spotify");
        boolean rejected=false;
        try{new T1AdbPackageTaskExcerpt("com.autolink.bluetooth");}
        catch(SecurityException e){rejected=true;}
        yes(rejected,"OEM app task query must be forbidden");
        System.out.println("PASS exact user-app task filtering and new-ID comparison");
    }
}
