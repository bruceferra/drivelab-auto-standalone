package ar.com.rango5gps.drivelabauto.receiver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Verify no guessed raw code is trusted, and manual A/B reads stay read-only. */
public final class T1SocOemReadOnlyCalibrationContractTest {
    private static String src(String name)throws Exception{
        return Files.readString(Path.of(
            "receiver/src/main/java/ar/com/rango5gps/drivelabauto/receiver",name));
    }
    private static void yes(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String pilot=src("T1SocRegenPilot.java");
        String automatic=src("T1OptimizedAutoService.java");
        String screen=src("T1OptimizedModeActivity.java");
        String map=src("T1SocTargetCodec.java");
        int gate=pilot.indexOf("T1SocTargetCodec.observedRawForPercent(targets.forcedSoc)<0");
        int binder=pilot.indexOf("return withBinder(b->");
        yes(gate>0&&binder>gate&&
            pilot.contains("SOC_FORZADO_NO_AUTORIZADO_SIN_MAPEO")&&
            pilot.contains("NO se envió 1041/1040/1046"),
            "Unknown target 60/70 MUST be blocked before Binder write");
        int safety=automatic.indexOf("OPT_PERFIL_BLOQUEADO_SIN_MAPEO");
        int drivingMode=automatic.indexOf("T1OptimizedAutoGate.Decision decision=gate.examine(");
        yes(safety>0&&drivingMode>safety&&
            automatic.contains("No se enviaron 1043/1040/")&&
            automatic.contains("putBoolean(ENABLED,false)"),
            "Unmapped SOC must stop entire optimizer before ANY 1043 mode switch");
        yes(map.contains("case 56:return 50;")&&
            !map.contains("case 72:return 70;")&&
            !map.contains("case 64:return 60;"),
            "Never guess the 1041 raw readback mapping");
        yes(screen.contains("LEER A · ANTES")&&
            screen.contains("LEER B · DESPUÉS")&&
            screen.contains("captureSocOem(boolean after)")&&
            screen.contains("T1OptimizedAutoService.enabled(this)")&&
            screen.contains("gear!=1||!Double.isFinite(speed)")&&
            screen.contains("oem.readInt(1040)")&&
            screen.contains("oem.readInt(1041)")&&
            screen.contains("oem.readInt(1046)")&&
            screen.contains("SOC_OEM_CALIBRACION_B")&&
            !screen.contains("oem.write("),
            "Calibration must read 1040/1041/1046 in P/0, AUTO OFF, with persistent A/B and no writes");
        System.out.println("PASS OEM forced SOC A/B read-only calibration and unmapped-target fail-closed");
    }
}
