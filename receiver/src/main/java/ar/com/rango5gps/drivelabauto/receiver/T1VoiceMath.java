package ar.com.rango5gps.drivelabauto.receiver;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One deliberately bounded, explicit spoken arithmetic expression.
 * Pure JVM; no network, vehicle signal writes, address or intent guessing.
 */
final class T1VoiceMath {
    private static final MathContext PRECISION=new MathContext(10,RoundingMode.HALF_UP);
    private static final Pattern OP=Pattern.compile(
        "(?<![a-z0-9])(?:dividido\\s+(?:por|entre)|multiplicado\\s+por|"+
        "mas|menos|por|entre|\\+|\\*|/)(?![a-z0-9])");
    private static final Map<String,Integer> NUMBERS=new HashMap<>();
    static {
        String[] first={"cero","uno","dos","tres","cuatro","cinco","seis","siete",
            "ocho","nueve","diez","once","doce","trece","catorce","quince",
            "dieciseis","diecisiete","dieciocho","diecinueve","veinte",
            "veintiuno","veintidos","veintitres","veinticuatro","veinticinco",
            "veintiseis","veintisiete","veintiocho","veintinueve"};
        for(int i=0;i<first.length;i++)NUMBERS.put(first[i],i);
        NUMBERS.put("un",1);NUMBERS.put("una",1);
        String[] tens={"treinta","cuarenta","cincuenta","sesenta","setenta",
            "ochenta","noventa"};
        for(int i=0;i<tens.length;i++)NUMBERS.put(tens[i],30+i*10);
        NUMBERS.put("cien",100);NUMBERS.put("ciento",100);
        NUMBERS.put("doscientos",200);NUMBERS.put("doscientas",200);
        NUMBERS.put("trescientos",300);NUMBERS.put("trescientas",300);
        NUMBERS.put("cuatrocientos",400);NUMBERS.put("cuatrocientas",400);
        NUMBERS.put("quinientos",500);NUMBERS.put("quinientas",500);
        NUMBERS.put("seiscientos",600);NUMBERS.put("seiscientas",600);
        NUMBERS.put("setecientos",700);NUMBERS.put("setecientas",700);
        NUMBERS.put("ochocientos",800);NUMBERS.put("ochocientas",800);
        NUMBERS.put("novecientos",900);NUMBERS.put("novecientas",900);
        NUMBERS.put("mil",1000);
    }
    private T1VoiceMath(){}
    static String normalize(String input){
        return Normalizer.normalize(input,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","")
            .toLowerCase(Locale.ROOT).trim().replaceAll("[¿?¡!]","")
            .replaceAll("\\s+"," ");
    }
    private static BigDecimal parse(String input){
        String s=input.trim();
        if(s.matches("[0-9]{1,9}(?:[.,][0-9]{1,6})?")){
            try{return new BigDecimal(s.replace(',','.'));}
            catch(NumberFormatException ignored){return null;}
        }
        int sum=0,current=0;boolean any=false,connect=false;
        String[] words=s.split(" ");
        for(int i=0;i<words.length;i++){
            String w=words[i];
            if(w.equals("y")){
                if(!any||connect||i+1==words.length)return null;
                connect=true;continue;
            }
            if(w.equals("mil")){
                if(connect||sum!=0)return null;
                sum+=(current==0?1:current)*1000;
                current=0;any=true;continue;
            }
            Integer n=NUMBERS.get(w);
            if(n==null)return null;
            if(connect&&(n>=10||current%100<30))return null;
            if(n>=100){
                if(current!=0)return null;
            }else if(n>=20){
                if(current%100!=0)return null;
            }else if(n>=10){
                if(current%100!=0)return null;
            }else{
                if(current%10!=0&&current%100<30)return null;
                if(current%100>=10&&current%100<30)return null;
            }
            current+=n;any=true;connect=false;
        }
        if(!any||sum+current>999999)return null;
        return BigDecimal.valueOf(sum+current);
    }
    static String calculate(String spoken){
        if(spoken==null||spoken.length()>150)return null;
        String s=normalize(spoken);
        s=s.replaceFirst("^(?:(?:cuanto\\s+(?:es|son|da))|calcula|"+
            "calcular|decime\\s+cuanto\\s+es|dime\\s+cuanto\\s+es)\\s+","");
        Matcher m=OP.matcher(s);
        if(!m.find())return null;
        String left=s.substring(0,m.start()).trim();
        String op=m.group().trim();
        String right=s.substring(m.end()).trim();
        if(OP.matcher(right).find())return null;
        BigDecimal a=parse(left),b=parse(right);
        if(a==null||b==null)return null;
        BigDecimal result;
        try{
            if(op.equals("+")||op.equals("mas"))result=a.add(b);
            else if(op.equals("menos"))result=a.subtract(b);
            else if(op.equals("*")||op.equals("por")||
                    op.startsWith("multiplicado"))result=a.multiply(b);
            else if(op.equals("/")||op.equals("entre")||
                    op.startsWith("dividido")){
                if(b.compareTo(BigDecimal.ZERO)==0)
                    return "No se puede dividir por cero.";
                result=a.divide(b,PRECISION);
            }else return null;
            if(result.abs().compareTo(new BigDecimal("999999999999"))>0)
                return "El número es demasiado grande para esta cuenta.";
            return "El resultado es "+result.stripTrailingZeros().toPlainString()+".";
        }catch(ArithmeticException err){
            return "No pude resolver esa cuenta.";
        }
    }
}