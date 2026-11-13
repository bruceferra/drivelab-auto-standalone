package ar.com.rango5gps.drivelabauto.receiver;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Current, attributed news headlines for explicit voice requests.
 * RSS headlines are NOT a verified sports scoreboard or deep AI answer.
 * The phone/car sends only a short topic, never GPS/VIN/driver identity.
 * Must be invoked from a background thread.
 */
final class T1VoiceNews {
    private static final long NEWS_MAX_AGE=48L*60*60*1000;
    private T1VoiceNews(){}
    static String topic(String phrase){
        if(phrase==null)return null;
        String s=T1VoiceMath.normalize(phrase);
        if(s.matches(".*\\b(?:trump|presidente de estados unidos)\\b.*"))
            return "Donald Trump";
        if(s.matches(".*\\b(?:san lorenzo|cicl[oó]n)\\b.*"))
            return "San Lorenzo futbol resultado";
        if(s.matches(".*\\b(?:noticias|actualidad|titulares|que paso hoy)\\b.*"))
            return "Argentina noticias";
        return null;
    }
    private static long published(String value){
        if(value==null)return -1;
        try{
            SimpleDateFormat df=new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss Z",Locale.US);
            df.setLenient(false);
            Date date=df.parse(value);
            return date==null?-1:date.getTime();
        }catch(Exception e){return -1;}
    }
    static String headlines(String phrase)throws Exception{
        String topic=topic(phrase);
        if(topic==null)return null;
        String encoded=URLEncoder.encode(topic+" when:2d","UTF-8");
        String url="https://news.google.com/rss/search?q="+encoded+
            "&hl=es-419&gl=AR&ceid=AR:es-419";
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","DriveLabAuto/1.0");
        c.setRequestProperty("Accept","application/rss+xml,application/xml");
        try{
            if(c.getResponseCode()!=200)
                throw new IllegalStateException("No disponible HTTP "+c.getResponseCode());
            ArrayList<String> newest=new ArrayList<>();
            XmlPullParser xml=Xml.newPullParser();
            try(BufferedInputStream in=new BufferedInputStream(c.getInputStream())){
                xml.setInput(in,"UTF-8");
                int event=xml.getEventType(),seen=0;
                boolean inItem=false;
                String title="",date="";
                long now=System.currentTimeMillis();
                while(event!=XmlPullParser.END_DOCUMENT&&seen<25&&
                    newest.size()<3){
                    if(event==XmlPullParser.START_TAG){
                        String tag=xml.getName();
                        if("item".equalsIgnoreCase(tag)){
                            inItem=true;title="";date="";seen++;
                        }else if(inItem&&("title".equalsIgnoreCase(tag)||
                                          "pubDate".equalsIgnoreCase(tag))){
                            String value=xml.nextText().trim();
                            if("title".equalsIgnoreCase(tag))title=value;
                            else date=value;
                        }
                    }else if(event==XmlPullParser.END_TAG&&
                        "item".equalsIgnoreCase(xml.getName())&&inItem){
                        long time=published(date);
                        if(!title.isEmpty()&&title.length()<=300&&time>0&&
                            now-time>=0&&now-time<=NEWS_MAX_AGE)
                            newest.add(title);
                        inItem=false;
                    }
                    event=xml.next();
                }
            }
            if(newest.isEmpty())return "No encontré noticias recientes verificables "+
                "para ese tema. No voy a inventar información.";
            StringBuilder answer=new StringBuilder();
            if(topic.startsWith("San Lorenzo"))
                answer.append("Encontré titulares recientes de San Lorenzo. "+
                    "No son un marcador oficial; para un resultado confirmado "+
                    "necesitamos una fuente deportiva de partidos. ");
            else if(topic.equals("Donald Trump"))
                answer.append("Estos son titulares recientes sobre Donald Trump. ");
            else answer.append("Estas son noticias recientes de Argentina. ");
            for(int j=0;j<newest.size();j++){
                answer.append(j+1).append(": ").append(newest.get(j)).append(". ");
            }
            answer.append("Fuente: titulares de Google News, últimas 48 horas.");
            return answer.toString();
        }finally{c.disconnect();}
    }
}