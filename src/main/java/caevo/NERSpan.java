package caevo;

/**
 * The sentence IDs are indexed from 0
 * The word spans are indexed from 1, and the end point is exclusive.
 * @author chambers
 */
public class NERSpan {
  public static enum TYPE { NONE, PERSON, ORGANIZATION, LOCATION };

  int sid;   // indexed from 0
  int start; // positions indexed from 1 (just like dependency parses)
  int end;   // exclusive
  TYPE type = TYPE.NONE;


  NERSpan(TYPE t, int sid, int s, int e) {
    this.sid = sid;
    start = s;
    end = e;
    type = t;
  }

  public int sid() { return sid; }
  public int start() { return start; }
  public int end() { return end; }
  public TYPE type() { return type; }

  public static TYPE stringToType(String type) {
    char ch = type.charAt(0);
    if( ch == 'p' || ch == 'P' ) return TYPE.PERSON;
    if( ch == 'o' || ch == 'O' ) return TYPE.ORGANIZATION;
    if( ch == 'l' || ch == 'L' ) return TYPE.LOCATION;
    else return TYPE.NONE;
  }
  
  private static TYPE stringIntToType(String strindex) {
    return TYPE.values()[Integer.valueOf(strindex)];
  }

//  public static String typeToString(int type) {
//    if( type == PERSON ) return "PERSON";
//    if( type == ORGANIZATION ) return "ORGANIZATION";
//    if( type == LOCATION ) return "LOCATION";
//    return "OTHER";
//  }

  public static NERSpan fromString(String str) {
//    System.out.println("fromString " + str);
    String parts[] = str.split("\t");
    // DEPRECATED : shouldn't read indices anymore
    if( Character.isDigit(parts[0].charAt(0)) )
      return new NERSpan(stringIntToType(parts[0]), Integer.valueOf(parts[1]), Integer.valueOf(parts[2]), Integer.valueOf(parts[3]));
    // NORMAL: reads the TYPE print string
    else 
      return new NERSpan(TYPE.valueOf(parts[0]), Integer.valueOf(parts[1]), Integer.valueOf(parts[2]), Integer.valueOf(parts[3]));
  }

  public String toString() {
    return type.toString() + "\t" + sid + "\t" + start + "\t" + end;
  }
}
