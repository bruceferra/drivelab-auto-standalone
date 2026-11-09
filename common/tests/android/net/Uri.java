package android.net;
import java.net.*;
import java.nio.charset.StandardCharsets;
/** Test-only tiny Android Uri substitute: never bundled into APK. */
public final class Uri {
 private final String raw;private final URI value;
 private Uri(String text){raw=text;value=URI.create(text);}
 public static Uri parse(String s){return new Uri(s);}
 public String getScheme(){return value.getScheme();}
 public String getHost(){return value.getHost();}
 public String getSchemeSpecificPart(){return value.getSchemeSpecificPart();}
 public String getEncodedPath(){return value.getRawPath();}
 public String getQueryParameter(String k){
  String query=value.getRawQuery();if(query==null)return null;
  for(String token:query.split("&")){
   int eq=token.indexOf('=');String key=eq<0?token:token.substring(0,eq);
   if(decode(key).equals(k))return eq<0?"":decode(token.substring(eq+1));
  }
  return null;
 }
 public static String decode(String value){return URLDecoder.decode(value,StandardCharsets.UTF_8);}
 public static String encode(String text,String allow){
  StringBuilder builder=new StringBuilder();
  for(char c:text.toCharArray()){
   if(Character.isLetterOrDigit(c)||"-._~".indexOf(c)>=0||allow.indexOf(c)>=0){builder.append(c);}
   else if(c==' '){builder.append("%20");}
   else builder.append(String.format("%%%02X",(int)c));
  }
  return builder.toString();
 }
}
