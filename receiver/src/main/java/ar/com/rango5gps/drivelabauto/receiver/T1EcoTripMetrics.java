package ar.com.rango5gps.drivelabauto.receiver;

import java.util.Locale;

/**
 * V1 local, reproducible route and smoothness metrics. No GPS coordinates,
 * personal identifiers or experimental fuel energy accounting. Route mix is
 * OSM classification, NEVER derived from instantaneous speed.
 *
 * The v1 Eco Score is an indicative smooth-driving score with road-context
 * adjustment, NOT measured battery/fuel efficiency. Keep its version visible
 * so owners never compare it with future calibrated energy scores by mistake.
 */
final class T1EcoTripMetrics {
    static final int UNKNOWN=0,CITY=1,ROAD=2,MOTORWAY=3;
    static final int BIN_COUNT=41,MAX_BIN=40;
    private final double[] roadKm=new double[4],roadSec=new double[4],
        speedKm=new double[BIN_COUNT],speedSec=new double[BIN_COUNT];
    private double validKm,validSeconds,accelSeconds,strongAccelSeconds,
        strongBrakeSeconds,moderateSeconds;
    private int validIntervals,abruptAccelerationEvents,abruptBrakeEvents;
    private int priorEpisode=0;

    static int speedBin(double speed){
        if(!Double.isFinite(speed)||speed<0||speed>220)return -1;
        return Math.min(MAX_BIN,(int)(speed/5.0));
    }
    static String label(int bin){
        return bin>=MAX_BIN?"200+ km/h":(bin*5)+"–"+(bin*5+5)+" km/h";
    }
    static String roadName(int road){
        switch(road){
            case CITY:return "CIUDAD";
            case ROAD:return "CARRETERA";
            case MOTORWAY:return "AUTOPISTA";
            default:return "SIN CLASIFICAR";
        }
    }
    static String fmt(double number,int decimals){
        return String.format(Locale.ROOT,"%."+decimals+"f",number);
    }
    static String duration(double seconds){
        long n=Math.round(seconds);
        return (n/3600>0?n/3600+" h ":"")+
            ((n%3600)/60)+" min "+(n%60)+" s";
    }
    void add(long ms,double distanceKm,double seconds,int roadId,
             double speedKmh,double acceleration){
        if(ms<=0||!Double.isFinite(distanceKm)||distanceKm<0||
           distanceKm>0.5||!Double.isFinite(seconds)||
           seconds<.3||seconds>10||speedBin(speedKmh)<0){
            priorEpisode=0;
            return;
        }
        int road=roadId>=CITY&&roadId<=MOTORWAY?roadId:UNKNOWN;
        int bin=speedBin(speedKmh);
        roadKm[road]+=distanceKm;
        roadSec[road]+=seconds;
        speedKm[bin]+=distanceKm;
        speedSec[bin]+=seconds;
        validKm+=distanceKm;
        validSeconds+=seconds;
        validIntervals++;
        if(Double.isFinite(acceleration)&&
           acceleration>=-9&&acceleration<=9){
            accelSeconds+=seconds;
            // Acceleration via consecutive OEM speeds is estimated; the
            // threshold is deliberately generous to avoid speed jitter.
            final int event=acceleration>=2.2?1:
                acceleration<=-2.5?-1:0;
            if(event!=0&&priorEpisode!=event){
                if(event>0)abruptAccelerationEvents++;
                else abruptBrakeEvents++;
            }
            priorEpisode=event;
            if(event==1)strongAccelSeconds+=seconds;
            if(event==-1)strongBrakeSeconds+=seconds;
            if(Math.abs(acceleration)>=1.25)moderateSeconds+=seconds;
        }else priorEpisode=0;
    }
    double km(){return validKm;}
    double roadKm(int road){return roadKm[road];}
    double speedKm(int bin){return speedKm[bin];}
    double speedSeconds(int bin){return speedSec[bin];}
    int intervals(){return validIntervals;}
    double roadCoverage(){
        return validKm>0?
            (roadKm[CITY]+roadKm[ROAD]+roadKm[MOTORWAY])/validKm:0;
    }
    double percent(int road){
        return validKm>0?roadKm[road]*100/validKm:0;
    }
    int abruptAccelerationEvents(){return abruptAccelerationEvents;}
    int abruptBrakeEvents(){return abruptBrakeEvents;}
    double accelCoverage(){return validSeconds>0?accelSeconds/validSeconds:0;}

    /**
     * Same motorist can compare smoothness on a city or motorway trip.
     * City has higher expected speed transitions; adjust event weight
     * rather than penalizing the urban road-share itself.
     *
     * Deliberately "N/D" for trips too short, GPS/OSM ambiguous, or
     * with too few acceleration samples. No fabricated energy consumption.
     */
    double ecoScore(){
        if(validKm<2||validSeconds<120||validIntervals<12||
            roadCoverage()<.50||accelCoverage()<.55)
            return Double.NaN;
        double cityShare=roadKm[CITY]/Math.max(0.001,validKm);
        double penaltyForLongEvents=
            30.0*(strongAccelSeconds+strongBrakeSeconds)/
                Math.max(1.0,accelSeconds);
        double moderatePenalty=
            7.0*moderateSeconds/Math.max(1.0,accelSeconds);
        double eventsPer10Km=(abruptAccelerationEvents+
            abruptBrakeEvents)*10.0/Math.max(2.0,validKm);
        // City driving naturally includes more acceleration/braking.
        double expectedCityWeight=1.0+1.8*cityShare;
        double eventPenalty=Math.max(0,(eventsPer10Km/expectedCityWeight)-1)*.23;
        double raw=10-penaltyForLongEvents-moderatePenalty-eventPenalty;
        return Math.round(Math.max(0,Math.min(10,raw))*10)/10.0;
    }
    String mix(){
        if(validKm<=0)return "SIN DISTANCIA VÁLIDA";
        return fmt(percent(CITY),0)+"% ciudad · "+
            fmt(percent(ROAD),0)+"% carretera · "+
            fmt(percent(MOTORWAY),0)+"% autopista"+
            (percent(UNKNOWN)>=.5?" · "+fmt(percent(UNKNOWN),0)+
                "% sin clasificar":"")+
            " · cobertura OSM "+fmt(roadCoverage()*100,0)+"%";
    }
    String headline(){
        double score=ecoScore();
        return (Double.isFinite(score)?"ECO "+fmt(score,1)+"/10":
            "ECO N/D · datos insuficientes")+
            " · "+mix();
    }
    String breakdown(){
        StringBuilder b=new StringBuilder();
        b.append("Eco Score v1 = conducción suave ajustada al tipo de vía; ")
            .append("NO mide por sí solo la eficiencia real de combustible ")
            .append("y batería.\n");
        b.append("Recorrido: ").append(fmt(validKm,2))
            .append(" km analizados · ")
            .append(validIntervals).append(" intervalos.\n");
        for(int cls=1;cls<=3;cls++)
            b.append(roadName(cls)).append(": ")
                .append(fmt(percent(cls),1)).append("% · ")
                .append(fmt(roadKm[cls],2)).append(" km · ")
                .append(duration(roadSec[cls])).append("\n");
        if(roadKm[UNKNOWN]>0)
            b.append("SIN CLASIFICAR: ").append(fmt(percent(UNKNOWN),1))
                .append("% · ").append(fmt(roadKm[UNKNOWN],2))
                .append(" km · no inferido por velocidad.\n");
        b.append("Aceleraciones bruscas estimadas: ")
            .append(abruptAccelerationEvents)
            .append(" · frenadas bruscas estimadas: ")
            .append(abruptBrakeEvents)
            .append(" · cobertura aceleración ")
            .append(fmt(accelCoverage()*100,0)).append("%\n");
        b.append("RANGOS DE VELOCIDAD · 5 km/h · km y tiempo\n");
        for(int i=0;i<BIN_COUNT;i++)
            if(speedSec[i]>.01)
                b.append(label(i)).append(": ")
                    .append(fmt(speedKm[i],3)).append(" km · ")
                    .append(duration(speedSec[i])).append("\n");
        return b.toString();
    }
}
