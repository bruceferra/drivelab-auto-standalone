package ar.com.rango5gps.drivelabauto.receiver;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Latest COMPLETED San Lorenzo match from ESPN's public but undocumented
 * Liga Profesional schedule. May omit Copa Argentina, international cups, etc.
 * Never invent score or pretend an in-progress match has finished.
 */
final class T1VoiceFootball {
    private T1VoiceFootball(){}
    private static long time(String raw){
        if(raw==null||raw.length()<10)return -1;
        try{
            SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
            date.setTimeZone(TimeZone.getTimeZone("UTC"));
            return date.parse(raw.substring(0,10)).getTime();
        }catch(Exception e){return -1;}
    }
    private static String score(JSONObject object){
        Object raw=object.opt("score");
        if(raw instanceof String||raw instanceof Number)return String.valueOf(raw);
        if(raw instanceof JSONObject){
            Object number=((JSONObject)raw).opt("value");
            if(number instanceof Number)return String.valueOf(((Number)number).intValue());
        }
        return null;
    }
    static String latestSanLorenzo()throws Exception{
        final String url="https://site.api.espn.com/apis/site/v2/sports/soccer/arg.1/"+
            "teams/18/schedule?season="+
            new SimpleDateFormat("yyyy",Locale.ROOT).format(new Date());
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(11000);
        c.setRequestProperty("Accept","application/json");
        try{
            if(c.getResponseCode()!=200)throw new IllegalStateException(
                "ESPN no devolvió partidos: HTTP "+c.getResponseCode());
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] bytes=new byte[8192];int n;
                while((n=in.read(bytes))!=-1){
                    if(b.size()+n>500000)throw new IllegalStateException(
                        "Calendario deportivo demasiado grande");
                    b.write(bytes,0,n);
                }
            }
            JSONArray events=new JSONObject(new String(
                b.toByteArray(),StandardCharsets.UTF_8)).optJSONArray("events");
            if(events==null)return "No encontré un resultado comprobable de "+
                "San Lorenzo en la Liga Profesional. Puede haber jugado otra copa.";
            long now=System.currentTimeMillis(),best=-1;
            String answer=null;
            for(int i=0;i<events.length();i++){
                JSONObject event=events.optJSONObject(i);
                if(event==null)continue;
                JSONObject status=event.optJSONObject("status");
                JSONObject type=status==null?null:status.optJSONObject("type");
                if(type==null||!type.optBoolean("completed",false))continue;
                long start=time(event.optString("date"));
                if(start<0||start>now||start<best)continue;
                JSONArray comps=event.optJSONArray("competitions");
                if(comps==null||comps.length()==0)continue;
                JSONArray teams=comps.optJSONObject(0).optJSONArray("competitors");
                if(teams==null||teams.length()<2)continue;
                JSONObject a=teams.optJSONObject(0),other=teams.optJSONObject(1);
                if(a==null||other==null)continue;
                String scoreA=score(a),scoreB=score(other);
                if(scoreA==null||scoreB==null||
                   !scoreA.matches("[0-9]{1,2}")||!scoreB.matches("[0-9]{1,2}"))
                    continue;
                JSONObject teamA=a.optJSONObject("team"),teamB=other.optJSONObject("team");
                if(teamA==null||teamB==null)continue;
                String homeName=teamA.optString("displayName","Equipo");
                String awayName=teamB.optString("displayName","Equipo");
                boolean sanLorenzo=teamA.optString("id").equals("18")||
                    teamB.optString("id").equals("18");
                if(!sanLorenzo)continue;
                best=start;
                answer="Según ESPN, en su último partido registrado y FINALIZADO "+
                    "de Liga Profesional, el "+event.optString("date").substring(0,10)+
                    ", "+homeName+" "+scoreA+", "+awayName+" "+scoreB+
                    ". Puede haber partidos de otras copas no incluidos.";
            }
            return answer!=null?answer:
                "No pude confirmar un marcador final reciente de San Lorenzo "+
                "en la fuente consultada. No voy a inventarte un resultado.";
        }finally{c.disconnect();}
    }
}
