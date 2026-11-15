package ar.com.rango5gps.drivelabauto.sender;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SenderBluetoothPickerSourceContractTest {
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        String activity=Files.readString(Path.of(
            "sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderActivity.java"));
        String manifest=Files.readString(Path.of(
            "sender/src/main/AndroidManifest.xml"));
        int start=activity.indexOf("private void chooseCarAudio(){");
        int end=activity.indexOf("private void testCarAudio(){",start);
        check(start>=0&&end>start,"Bluetooth chooser must exist");
        String picker=activity.substring(start,end);
        int dialog=picker.indexOf("new AlertDialog.Builder(this)\n            .setTitle(\"ELEGIR T1");
        int list=picker.indexOf(".setItems(labels.toArray(new String[0])",dialog);
        int finish=picker.indexOf(".setNegativeButton(\"CANCELAR\",null).show();",list);
        check(dialog>=0&&list>dialog&&finish>list,
            "Xiaomi must render selectable list, not a blank dialog");
        check(!picker.substring(dialog,finish).contains(".setMessage("),
            "Never combine AlertDialog setMessage and setItems: hides device list");
        check(picker.contains("adapter.getBondedDevices()")&&
            picker.contains("BLUETOOTH_CONNECT")&&
            picker.contains("putString(SenderAudioBootstrap.ADDRESS,")&&
            picker.contains("putBoolean(SenderAudioBootstrap.ENABLED,true)")&&
            picker.contains("chosen.getAddress()"),
            "Only an explicitly selected already-bonded Bluetooth device is saved");
        check(picker.contains("if(devices.isEmpty())")&&
            picker.contains("openBluetoothSettings()")&&
            picker.contains("ACTION_BLUETOOTH_SETTINGS")&&
            activity.contains("ABRIR AJUSTES BLUETOOTH DEL XIAOMI"),
            "No-pair case must offer native OS Bluetooth pairing settings");
        check(!manifest.contains("android.permission.BLUETOOTH_SCAN")&&
            !picker.contains("startDiscovery(")&&
            !picker.contains("createBond(")&&
            !picker.contains("setBluetoothA2dpOn("),
            "No scanning, automatic Bluetooth pairing or privileged profile changes");
        System.out.println("PASS Xiaomi T1 paired Bluetooth list is visible and selectable");
    }
}