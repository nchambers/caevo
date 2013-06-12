package timesieve;

import org.jdom.Namespace;

/**
 * This class represents a sequence of tokens for a Timex. It holds all TimeML attributes.
 * 
 * sid is 0 indexed, just like the InfoFile specifies. The first sentence entry is 0.
 * offset is 1 indexed, just like Stanford parse trees. The first token is 1.
 *
 * @author chambers
 */
public class Timex {
  String text;
  int offset, length, sid;
  String tid, anchor_tid, type, mod, value, docFunction;
  boolean temporalFunction = false;
  String preposition; // feature not in Timebank

  public static String TIMEX_ELEM = "timex";
  public static String TID_ATT = "tid";
  public static String ANCHORTID_ATT = "anchortid";
  public static String TYPE_ATT = "type";
  public static String MOD_ATT = "mod";
  public static String VALUE_ATT = "value";
  public static String DOCFUNC_ATT = "docFunction";
  public static String TEMPFUNC_ATT = "temporalFunction";
  public static String TEXT_ATT = "text";
  public static String OFFSET_ATT = "offset";
  public static String LENGTH_ATT = "length";


  public Timex() {
  }

  public Timex(org.jdom.Element el) {
    tid = el.getAttributeValue(TID_ATT);
    text = el.getAttributeValue(TEXT_ATT);
    offset = Integer.valueOf(el.getAttributeValue(OFFSET_ATT));
    length = Integer.valueOf(el.getAttributeValue(LENGTH_ATT));
    anchor_tid = el.getAttributeValue(ANCHORTID_ATT);
    type = el.getAttributeValue(TYPE_ATT);
    mod = el.getAttributeValue(MOD_ATT);
    value = el.getAttributeValue(VALUE_ATT);
    docFunction = el.getAttributeValue(DOCFUNC_ATT);
    String temp = el.getAttributeValue(TEMPFUNC_ATT);
    if( temp.equalsIgnoreCase("true") ) temporalFunction = true;
    else temporalFunction = false;
   }

  public void setText(String text) { this.text = text; }
  public void setSID(int i) { sid = i; }
  public void setTID(String id) { tid = id; }

  /**
   * @param s The integer offset where the timex starts
   * @param e The integer offset where the timex ends, exclusive
   */
  public void setSpan(int s, int e) {
    offset = s;
    length = e - s;
  }

  public void setType(String v) { type = v; }
  public void setValue(String v) { value = v; }
  public void setPrep(String prep) { preposition = prep; }
  public void setDocFunction(String func) { docFunction = func; }
  public String prep() { return preposition; }

  //<TIMEX3 mod="APPROX" endPoint="t42" tid="t39" temporalFunction="false" type="DURATION" functionInDocument="NONE" value="P2Y" >
  public void saveAttributes(org.w3c.dom.Element el) {
    tid = el.getAttribute("tid");
    anchor_tid = el.getAttribute("anchorTimeID");
    type = el.getAttribute("type");
    mod = el.getAttribute("mod");
    value = el.getAttribute("value");
    docFunction = el.getAttribute("functionInDocument");
    String temp = el.getAttribute("temporalFunction");
    if( temp != null && temp.equalsIgnoreCase("true") ) temporalFunction = true;
    else temporalFunction = false;
  }


  public int offset() { return offset; }
  public int length() { return length; }

  public int sid() { return sid; }
  public String tid() { return tid; }
  public String type() { return type; }
  public String mod() { return mod; }
  public String value() { return value; }
  public String text() { return text; }
  public String docFunction() { return docFunction; }
  public boolean temporalFunction() { return temporalFunction; }


  /**
   * @desc Looks in the "value" attribute of the timexes, and compares the ordering
   *       if they are both DATEs or TIMEs.  The value is a date string "YYYY-MM-DD".
   * @param other The date/time with which to compare this object
   * @return True if this timex occurs before the given timex
   */
  public boolean before(Timex other) {
    // don't bother if we're not a date or time
    if( (type.equals("DATE") || type.equals("TIME"))  &&
        (other.type().equals("DATE") || other.type().equals("TIME")) ) {
      String ovalue = other.value();

      // make sure there are values to compare
      if( value != null  && value.length() > 0  && value.matches("\\d.*") &&
          ovalue != null && ovalue.length() > 0 && ovalue.matches("\\d.*") ) {

        if( value.equalsIgnoreCase(ovalue) ) return false;
        
        try {
          //	  System.out.println("Checking " + value + " and " + ovalue);
          // check years
          int year  = Integer.valueOf(value.substring(0,4));
          int oyear = Integer.valueOf(ovalue.substring(0,4));
          if( year < oyear ) return true;
          else if( oyear < year ) return false;

          // check months
          if( value.length() > 4 && ovalue.length() > 4 ) {
            int month  = Integer.valueOf(value.substring(5,7));
            int omonth = Integer.valueOf(ovalue.substring(5,7));
            if( month < omonth ) return true;
            else if( omonth < month ) return false;
          }

          // check days - some timexes don't have days e.g. "1998-10"
          if( value.length() > 7 && ovalue.length() > 7 ) {
            int day  = Integer.valueOf(value.substring(8,10));
            int oday = Integer.valueOf(ovalue.substring(8,10));
            if( day < oday ) return true;
            else if( oday < day ) return false;
          }
          // Called when the date is bad, non-numeric ... "1998-WXX"
        } catch( NumberFormatException ex ) { } 
      }
    }
    return false;
  }

  /**
   * @desc Looks in the "value" attribute of the timexes, and compares the ordering
   *       if they are both DATEs or TIMEs.  The value is a date string "YYYY-MM-DD".
   * @param other The date/time with which to compare this object
   * @return True if this timex occurs before the given timex
   */
  public boolean includes(Timex other) {
    // don't bother if we're not a date or time
    if( (type.equals("DATE") || type.equals("TIME") || type.equals("DURATION"))  &&
        (other.type().equals("DATE") || other.type().equals("TIME") || other.type().equals("DURATION")) ) {
      String ovalue = other.value();

      // make sure there are values to compare
      if( value != null  && value.length() > 0  && value.matches("\\d.*") &&
          ovalue != null && ovalue.length() > 0 && ovalue.matches("\\d.*") ) {

        // If the values are exactly the same, then they aren't INCLUDES.
        if( value.equalsIgnoreCase(ovalue) ) return false;
        
        // If a timex in the document is "now", then it INCLUDES the document's creation time.
        if( value.equalsIgnoreCase("PRESENT_REF") && other.docFunction().equalsIgnoreCase("CREATION_TIME") )
          return true;
        
        try {
          //    System.out.println("Checking " + value + " and " + ovalue);
          // check years
          int year  = Integer.valueOf(value.substring(0,4));
          int oyear = Integer.valueOf(ovalue.substring(0,4));
          if( year != oyear ) return false;
          else { // same year

            // Then this time is a year, and the other is a month in the year. True!
            if( value.length() == 4 && ovalue.length() > 4 )
              return true;

            // Both have a month, so see if it is the same month.
            else if( value.length() > 4 && ovalue.length() > 4 ) {
              int month  = Integer.valueOf(value.substring(5,7));
              int omonth = Integer.valueOf(ovalue.substring(5,7));
              if( month != omonth ) return false;
              else if( value.length() == 7 && ovalue.length() > 7 )
                return true;
            }
          }

          // Called when the date is bad, non-numeric ... "1998-WXX"
        } catch( NumberFormatException ex ) { } 
      }
    }
    return false;
  }
  
  /**
   * This prints only the opening TIMEX3 tag, and does not close it nor print its inner text contents.
   * @return A string representation of the <TIMEX3 ...> opening tag.
   */
  public String toXMLString() {
  	StringBuffer buf = new StringBuffer();
  	buf.append("<TIMEX3 tid=\""); buf.append(tid()); buf.append('\"');
  	buf.append(" type=\""); buf.append(type()); buf.append('\"');
  	buf.append(" value=\""); buf.append(value()); buf.append('\"');
  	buf.append(" temporalFunction=\""); buf.append(temporalFunction()); buf.append('\"');
  	buf.append(" functionInDocument=\""); 
  	if( docFunction() == null || docFunction().length() == 0 )
  	  buf.append("NONE");
  	else 
  	  buf.append(docFunction()); 
  	buf.append('\"');
  	buf.append('>');
//  	buf.append(text());
//  	buf.append("</TIMEX3>");
  	return buf.toString();
  }

  public org.jdom.Element toElement(Namespace ns) {
    org.jdom.Element el = new org.jdom.Element(TIMEX_ELEM,ns);
    el.setAttribute(TID_ATT, tid);
    el.setAttribute(TEXT_ATT, text);
    el.setAttribute(OFFSET_ATT, String.valueOf(offset));
    el.setAttribute(LENGTH_ATT, String.valueOf(length));
    if( anchor_tid != null ) el.setAttribute(ANCHORTID_ATT, anchor_tid);
    el.setAttribute(TYPE_ATT, type);
    if( mod != null ) el.setAttribute(MOD_ATT, mod);
    if( value != null ) el.setAttribute(VALUE_ATT, value);
    if( docFunction != null ) el.setAttribute(DOCFUNC_ATT, docFunction);
    el.setAttribute(TEMPFUNC_ATT, String.valueOf(temporalFunction));
    return el;
  }

  public String toString() {
    return tid + " " + sid + " " + type + " " + value + " '" + text + "' " + preposition;
  }
}
