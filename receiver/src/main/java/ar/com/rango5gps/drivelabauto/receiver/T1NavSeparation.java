package ar.com.rango5gps.drivelabauto.receiver;
/** DriveLab Auto owns recordings, energy and OEM controls; Nav owns route/cluster/voice.
 * Fail-closed boundary. No stored data or navigation preferences are deleted.
 */
final class T1NavSeparation {
    static final boolean NAV_DELEGATED = true;
    private T1NavSeparation() {}
}
