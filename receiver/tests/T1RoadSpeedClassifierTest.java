package ar.com.rango5gps.drivelabauto.receiver;

/** Offline map needs the legal OSM maximum, never instantaneous car speed. */
public final class T1RoadSpeedClassifierTest {
    static void check(boolean condition,String why){
        if(!condition)throw new AssertionError(why);
    }
    static T1OptimizedModePolicy.Road road(String h,String ref,String speed){
        return T1RoadSpeedClassifier.category(h,ref,speed,"no","no");
    }
    public static void main(String[] args){
        check(road("primary","","50")==T1OptimizedModePolicy.Road.CITY,
            "Urban primary 50 is city");
        check(road("secondary","","60")==T1OptimizedModePolicy.Road.CITY,
            "Urban avenue 60 is city");
        check(road("primary","RP 8","60")==T1OptimizedModePolicy.Road.CITY,
            "Posted 60 remains city even on RP tag");
        check(road("primary","RP 8","80")==T1OptimizedModePolicy.Road.ROAD,
            "80 is carretera");
        check(road("secondary","RP 27","70")==T1OptimizedModePolicy.Road.ROAD,
            "70 with provincial ref is carretera");
        check(road("secondary","","70")==T1OptimizedModePolicy.Road.CITY,
            "70 with no rural route ref remains city");
        check(road("motorway","RN 9","60")==T1OptimizedModePolicy.Road.MOTORWAY,
            "Motorway class overrides temporary posted speed");
        check(road("primary","","")==T1OptimizedModePolicy.Road.CITY,
            "Unknown maximum not blindly classified road");
        check(road("primary","RN 8","")==T1OptimizedModePolicy.Road.ROAD,
            "Signed national route without posted limit is road");
        check(road("footway","","30")==T1OptimizedModePolicy.Road.UNKNOWN,
            "Pedestrian road not drivable classification");
        check(T1RoadSpeedClassifier.maxKmh("50 mph")==80,
            "mph normalized");
        check(T1RoadSpeedClassifier.maxKmh("60") ==60,
            "bare speed in kmh");
        check(T1RoadSpeedClassifier.maxKmh("70;90")==-1,
            "do not infer combined speed limits");
        System.out.println("OK: OSM avenue vs provincial road classification");
    }
}
