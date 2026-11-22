package ar.com.rango5gps.drivelabauto.boot;

import java.util.List;

/**
 * LAB44: deliver a Sender Waze destination only to NAV's verified existing
 * MainActivity task on NAV's own third VirtualDisplay.
 * Never start Waze by package alone, touch physical displays 0/1, T1Launcher,
 * another Waze instance, or any mixed/unknown task root.
 */
final class NavWazeRoutePolicy {
    static final class Target {
        final int display,map,media,stack,task;
        final long token;
        Target(int d,int m,int music,int s,int t,long nonce){
            display=d;map=m;media=music;stack=s;task=t;token=nonce;
        }
    }
    private NavWazeRoutePolicy(){}

    static NavWazeRelocationPolicy.Task verify(
            String report,Target wanted){
        if(wanted==null||wanted.token<=0||
           !NavOwnClusterWazePolicy.ownedIds(
               wanted.display,wanted.map,wanted.media)||
           wanted.task<=10||wanted.stack<=10)
            throw new SecurityException("Destino Waze NAV inválido");
        List<NavWazeRelocationPolicy.Task> tasks=
            NavWazeRelocationPolicy.ownedWaze(
                report,wanted.display,wanted.map,wanted.media);
        NavWazeRelocationPolicy.Task main=null;
        for(NavWazeRelocationPolicy.Task t:tasks){
            if("com.waze/com.waze.MainActivity".equals(t.component)||
               "com.waze/.MainActivity".equals(t.component)){
                if(main!=null)
                    throw new SecurityException("Más de una MainActivity Waze NAV");
                main=t;
            }
        }
        if(main==null||main.display!=wanted.display||
           main.stack!=wanted.stack||main.id!=wanted.task)
            throw new SecurityException(
                "Waze no está en la tarea NAV originalmente verificada");
        return main;
    }

    static boolean same(NavWazeRelocationPolicy.Task a,
                        NavWazeRelocationPolicy.Task b){
        return a!=null&&a.same(b);
    }

    static String command(Target wanted,String uri){
        if(wanted==null||wanted.token<=0||
           !NavOwnClusterWazePolicy.ownedIds(
               wanted.display,wanted.map,wanted.media)||
           wanted.stack<=10||wanted.task<=10||
           wanted.task>9999999||wanted.stack>9999999||
           uri==null||uri.length()>600||
           !uri.matches("waze://\\?(?:ll|q)=[A-Za-z0-9%.,_:+\\-]+&navigate=yes"))
            throw new SecurityException("Comando Waze NAV no autorizado");
        // Implicit ACTION_VIEW with Waze-only package avoids starting the
        // PRIVATE (nonexported) MainActivity directly. --task targets the
        // already running verified task, and --display restricts placement.
        return "am start --display "+wanted.display+
            " --task "+wanted.task+
            " --activity-single-top -a android.intent.action.VIEW"+
            " -d '"+uri+"' -p com.waze";
    }

    static boolean delivered(String reply){
        if(reply==null||reply.contains("SecurityException")||
           reply.contains("Exception")||reply.contains("Error:"))
            return false;
        return reply.contains(
            "intent has been delivered to currently running top-most instance.");
    }
}
