package ar.com.rango5gps.drivelabauto.receiver;

/** Pure, testable button state machine shared by total and individual trips. */
final class T1TripActionPolicy {
    static final class State {
        final boolean start,pause,end,resume;
        State(boolean start,boolean pause,boolean end,boolean resume){
            this.start=start;this.pause=pause;this.end=end;this.resume=resume;
        }
    }
    static State resolve(boolean paused,boolean active,boolean live,
                         boolean canStart,boolean canResume){
        // Pending start is active, but must not allow a second START.
        if(paused)return new State(false,false,active,canResume);
        if(active)return new State(false,live,true,false);
        return new State(canStart,false,false,false);
    }
    private T1TripActionPolicy(){}
}
