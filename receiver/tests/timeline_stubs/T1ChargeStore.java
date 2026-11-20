package ar.com.rango5gps.drivelabauto.receiver;
final class T1ChargeStore {
    static final class Record {
        long id,start,end,last;
        double kwh,meterKwh,price;
        int open;
        String source;
    }
}
