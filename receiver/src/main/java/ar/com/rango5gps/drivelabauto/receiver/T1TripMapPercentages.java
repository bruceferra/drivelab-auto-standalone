package ar.com.rango5gps.drivelabauto.receiver;

/** Pure math for distance-weighted offline map replay.
 * Classes: 0 CITY, 1 ROAD, 2 MOTORWAY, 3 UNKNOWN.
 * The unclassified distance remains IN the denominator.
 */
final class T1TripMapPercentages {
    static final int UNKNOWN=3;
    final double[][] categoryMeters=new double[3][4];
    final double[][] sharedMeters=new double[3][3];
    final double[][] agreementMeters=new double[3][3];
    double totalMeters,threeAgreeMeters;
    int buckets;
    void add(double length,int[] kind){
        if(!Double.isFinite(length)||length<=0)
            throw new IllegalArgumentException("Invalid GPS segment length");
        if(kind==null||kind.length!=3)
            throw new IllegalArgumentException("Expected three independent maps");
        for(int i=0;i<3;i++){
            if(kind[i]<0||kind[i]>UNKNOWN)
                throw new IllegalArgumentException("Unsupported map category");
        }
        totalMeters+=length;
        buckets++;
        for(int i=0;i<3;i++)categoryMeters[i][kind[i]]+=length;
        if(kind[0]!=UNKNOWN&&kind[0]==kind[1]&&kind[1]==kind[2])
            threeAgreeMeters+=length;
        for(int i=0;i<3;i++)for(int j=i+1;j<3;j++){
            if(kind[i]==UNKNOWN||kind[j]==UNKNOWN)continue;
            sharedMeters[i][j]+=length;
            if(kind[i]==kind[j])agreementMeters[i][j]+=length;
        }
    }
    double percent(double meters){return totalMeters>0?100*meters/totalMeters:Double.NaN;}
    double coverage(int source){
        return percent(totalMeters-categoryMeters[source][UNKNOWN]);
    }
    double pairAgreement(int first,int second){
        return sharedMeters[first][second]>0?
            100*agreementMeters[first][second]/sharedMeters[first][second]:
            Double.NaN;
    }
}
