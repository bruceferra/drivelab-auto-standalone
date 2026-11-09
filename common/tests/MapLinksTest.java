import ar.com.rango5gps.drivelabauto.common.MapLinks;
public final class MapLinksTest {
 private static void check(String input,String kind,String value){
  MapLinks.Destination d=MapLinks.parse(input);
  if(d==null||!d.kind.equals(kind)||!d.value.equals(value))
   throw new AssertionError("Expected "+kind+"="+value+" for "+input+", got "+(d==null?"null":d.kind+"="+d.value));
 }
 public static void main(String[] args){
  check("waze://?ll=-34.9214,-57.9545&navigate=yes","coord","-34.9214,-57.9545");
  check("geo:0,0?q=Obelisco+Baires","search","Obelisco Baires");
  check("google.navigation:q=-34.9214,-57.9545","coord","-34.9214,-57.9545");
  check("Compartí https://www.google.com/maps/dir/?api=1&destination=-34.9214,-57.9545","coord","-34.9214,-57.9545");
  check("https://www.google.com/maps/place/Casa/data=!3d-34.9214!4d-57.9545","coord","-34.9214,-57.9545");
  if(MapLinks.parse("https://evil.example/maps/?q=-34.9214,-57.9545")!=null)
   throw new AssertionError("Untrusted host accepted");
  if(MapLinks.parse("https://maps.app.goo.gl/ABCDEFG")!=null)
   throw new AssertionError("Ambiguous short URL treated as exact destination");
  System.out.println("OK: Waze, geo, Google navigation, Maps coordinates and untrusted URL rejection");
 }
}
