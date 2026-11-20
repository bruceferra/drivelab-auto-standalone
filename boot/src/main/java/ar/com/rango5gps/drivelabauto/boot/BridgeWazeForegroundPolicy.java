package ar.com.rango5gps.drivelabauto.boot;

import java.util.List;

/** One-time, parked-only test on the already-present Waze task in Display 1.
 * Only Waze's one-task, independent root stack may be used. The source and
 * destination stack IDs MUST be the same; never move apps across displays,
 * bring an OEM task forward, dismiss overlays, or change the wheel.
 */
final class BridgeWazeForegroundPolicy {
    private BridgeWazeForegroundPolicy(){}
    static BridgeClusterRecoveryPolicy.Task uniqueWaze(String stackReport){
        List<BridgeClusterRecoveryPolicy.Task> tasks=
            BridgeClusterRecoveryPolicy.onlyVerifiedMapTasks(stackReport);
        BridgeClusterRecoveryPolicy.Task match=null;
        for(BridgeClusterRecoveryPolicy.Task task:tasks){
            if(!"com.waze".equals(task.pkg))continue;
            if(match!=null)return null;
            match=task;
        }
        return match;
    }
    static boolean same(BridgeClusterRecoveryPolicy.Task expected,
                        BridgeClusterRecoveryPolicy.Task candidate){
        return expected!=null&&candidate!=null&&
            "com.waze".equals(expected.pkg)&&
            "com.waze".equals(candidate.pkg)&&
            expected.taskId==candidate.taskId&&
            expected.stackId==candidate.stackId;
    }
    static String sameStackToTop(BridgeClusterRecoveryPolicy.Task task){
        if(task==null||
           !"com.waze".equals(task.pkg)||
           task.taskId<=10||task.taskId>=10_000_000||
           task.stackId<=10||task.stackId>=10_000_000)
            throw new SecurityException(
                "Solo se permite una tarea Waze verificada de Display 1");
        return "am stack move-task "+task.taskId+" "+
            task.stackId+" true";
    }
}
