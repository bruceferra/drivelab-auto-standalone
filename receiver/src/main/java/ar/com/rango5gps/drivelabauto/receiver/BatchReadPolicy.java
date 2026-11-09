package ar.com.rango5gps.drivelabauto.receiver;
public final class BatchReadPolicy {
 public static final int SIZE=25,DELAY_MS=45,MAX_ID=9999;
 public static int validate(int value){if(value<1||value>MAX_ID)throw new IllegalArgumentException("Fuera de rango");return value;}
 public static int end(int start){return Math.min(MAX_ID,validate(start)+SIZE-1);}
 /** Move the selected starting ID by whole 25-ID pages, bounded to 1..9999. */
 public static int shift(int start,int pages){
  long candidate=(long)validate(start)+(long)pages*SIZE;
  return (int)Math.max(1L,Math.min((long)MAX_ID,candidate));
 }
 public static boolean valid(int value){return value!=-1&&value>=-100000&&value<=1000000;}
 public static boolean valid(float value){return Float.isFinite(value)&&value!=-1f&&value>=-100000f&&value<=1000000f;}
 private BatchReadPolicy(){}
}