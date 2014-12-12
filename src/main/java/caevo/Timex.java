package caevo;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Namespace;

import caevo.util.Pair;
import caevo.util.TimeValueParser;

/**
 * This class represents a sequence of tokens for a Timex. It holds all TimeML attributes.
 * 
 * sid is 0 indexed, just like the InfoFile specifies. The first sentence entry is 0.
 * offset is 1 indexed, just like Stanford parse trees. The first token is 1.
 *
 * @author chambers
 */
public class Timex {
  public static final String TIMEX_ELEM = "timex";
  public static final String TID_ATT = "tid";
  public static final String ANCHORTID_ATT = "anchortid";
  public static final String TYPE_ATT = "type";
  public static final String MOD_ATT = "mod";
  public static final String VALUE_ATT = "value";
  public static final String DOCFUNC_ATT = "docFunction";
  public static final String DOCFUNC_ATT_ALT = "functionInDocument";
  public static final String TEMPFUNC_ATT = "temporalFunction";
  public static final String TEXT_ATT = "text";
  public static final String OFFSET_ATT = "offset";
  public static final String LENGTH_ATT = "length";
  	
  public enum Type { DATE, TIME, DURATION, SET };
  public enum DocumentFunction { CREATION_TIME, EXPIRATION_TIME, MODIFICATION_TIME, PUBLICATION_TIME, RELEASE_TIME, RECEPTION_TIME, NONE };
  public enum Mod { BEFORE, AFTER, ON_OR_BEFORE, ON_OR_AFTER, LESS_THAN, MORE_THAN, EQUAL_OR_LESS, EQUAL_OR_MORE, START, MID, END, APPROX };
  
  private String text;
  private int tokenOffset;
  private int tokenLength;
  private int sid;
  private String tid;
  private String anchorTid;
  private Type type;
  private Mod mod;
  private String value;
  private DocumentFunction documentFunction;
  private boolean temporalFunction = false;
  private String preposition; // feature not in Timebank
  private Pair<Calendar, Calendar> timeRange;
  
  public Timex() {
  
  }
  
  /**
   * Copy Constructor
   * @param timex
   */
  public Timex(Timex timex) {
  	this.tid = timex.getTid();
    this.text = timex.getText();
    this.tokenOffset = timex.getTokenOffset();
    this.tokenLength = timex.getTokenLength();
    this.anchorTid = timex.getAnchorTid();
    this.type = timex.getType();
    this.value = timex.getValue();
    this.mod = timex.getMod(); 
    this.documentFunction = timex.getDocumentFunction();
    this.temporalFunction = timex.getTemporalFunction();
  }
  
  /**
   * Copy Constructor with new value specified
   * @param timex
   */
  public Timex(Timex timex, String newVal) {
  	this.tid = timex.getTid();
    this.text = timex.getText();
    this.tokenOffset = timex.getTokenOffset();
    this.tokenLength = timex.getTokenLength();
    this.anchorTid = timex.getAnchorTid();
    this.type = timex.getType();
    this.value = timex.getValue();
    this.mod = timex.getMod(); 
    this.documentFunction = timex.getDocumentFunction();
    this.temporalFunction = timex.getTemporalFunction();
  }
  /**
   * Copy Constructor with new value specified
   * @param timex
   */
  public Timex(String val) { 	
    this.value = val;
  }

  public Timex(org.jdom.Element el) {
    this.tid = el.getAttributeValue(Timex.TID_ATT);
    this.text = el.getAttributeValue(Timex.TEXT_ATT);
    this.tokenOffset = Integer.valueOf(el.getAttributeValue(Timex.OFFSET_ATT));
    this.tokenLength = Integer.valueOf(el.getAttributeValue(Timex.LENGTH_ATT));
    this.anchorTid = el.getAttributeValue(Timex.ANCHORTID_ATT);
    this.type = Type.valueOf(el.getAttributeValue(Timex.TYPE_ATT));
    this.value = el.getAttributeValue(Timex.VALUE_ATT);
    
    String modStr = el.getAttributeValue(Timex.MOD_ATT);
    if (modStr != null && !modStr.isEmpty())
    	this.mod = Mod.valueOf(el.getAttributeValue(Timex.MOD_ATT));
    
    String docFunStr = el.getAttributeValue(Timex.DOCFUNC_ATT);
    if (docFunStr != null && !docFunStr.isEmpty())
    	this.documentFunction = DocumentFunction.valueOf(docFunStr);
    
    this.temporalFunction = el.getAttributeValue(Timex.TEMPFUNC_ATT).equalsIgnoreCase("true");
  }

  
  //<TIMEX3 mod="APPROX" endPoint="t42" tid="t39" temporalFunction="false" type="DURATION" functionInDocument="NONE" value="P2Y" >
  public void saveAttributes(org.w3c.dom.Element el) {
    this.tid = el.getAttribute(Timex.TID_ATT);
    this.anchorTid = el.getAttribute(Timex.ANCHORTID_ATT);
    this.type = Type.valueOf(el.getAttribute(Timex.TYPE_ATT));
    this.value = el.getAttribute(Timex.VALUE_ATT);
    
    String modStr = el.getAttribute(Timex.MOD_ATT);
    if (modStr != null && !modStr.isEmpty())
    	this.mod = Mod.valueOf(modStr);
    
    String docFunStr = el.getAttribute(Timex.DOCFUNC_ATT);
    // If not found, try with the alternate name.
    if( docFunStr == null || docFunStr.isEmpty() )
    	docFunStr = el.getAttribute(Timex.DOCFUNC_ATT_ALT);
    // If found, grab the value.
    if (docFunStr != null && !docFunStr.isEmpty())
    	this.documentFunction = DocumentFunction.valueOf(docFunStr);
    
    this.temporalFunction = el.getAttribute(Timex.TEMPFUNC_ATT).equalsIgnoreCase("true");

    String text = el.getAttribute(Timex.TEXT_ATT); 
    if( text != null && text.length() > 0 ) 
    	this.text = text;
    else {
    	text = el.getTextContent();
    	if( text != null && text.length() > 0 ) this.text = text;
    }
  }

  public void setText(String text) { 
  	this.text = text;
  }
  
  public void setSid(int sid) { 
  	this.sid = sid; 
  }
  
  public void setTid(String tid) { 
  	this.tid = tid; 
  }
  
  public void setAnchorTid(String anchorTid) { 
  	this.anchorTid = anchorTid; 
  }

  /**
   * @param s The token integer offset where the timex starts within the sentence
   * @param e The token integer offset where the timex ends, exclusive within the sentence
   */
  public void setSpan(int s, int e) {
  	this.tokenOffset = s;
  	this.tokenLength = e - s;
  }

  public void setType(Type type) { 
  	this.type = type;
  }
  
  public void setValue(String value) { 
  	this.value = value;
  	this.timeRange = null;
  }
  
  public void setPrep(String prep) { 
  	this.preposition = prep; 
  }
  
  public void setDocumentFunction(DocumentFunction func) { 
  	this.documentFunction = func; 
  }

  public int getTokenOffset() { return this.tokenOffset; }
  public int getTokenLength() { return this.tokenLength; }
  public int getSid() { return this.sid; }
  public String getTid() { return this.tid; }
  public String getAnchorTid() { return this.anchorTid; }
  public Type getType() { return this.type; }
  public Mod getMod() { return this.mod; }
  public String getValue() { return this.value; }
  public String getText() { return this.text; }
  public DocumentFunction getDocumentFunction() { return this.documentFunction; }
  public boolean getTemporalFunction() { return this.temporalFunction; }
  public String getPrep() { return this.preposition; }
  
  public Pair<Calendar, Calendar> getRange() {
  	return getRange(null);
  }
  
  public Pair<Calendar, Calendar> getRange(Timex documentCreation) {
  	if (this.type == Timex.Type.DURATION 
  	  	|| this.type == Timex.Type.SET
  	    || isPastReference()
  	    || isFutureReference())
  	  		return null;
  	
  	if (this.timeRange == null) {
	  	TimeValueParser timeParse = new TimeValueParser(this.value);
	  	this.timeRange = timeParse.getRange();
  	}
  
  	return this.timeRange;
  }
  
  public boolean isReference() {
  	return isPastReference() || isPresentReference() || isFutureReference();
  }
  
  public boolean isPastReference() {
  	return this.value.equalsIgnoreCase("PAST_REF");
  }
  
  public boolean isPresentReference() {
  	return this.value.equalsIgnoreCase("PRESENT_REF");
  }
  
  public boolean isFutureReference() {
  	return this.value.equalsIgnoreCase("FUTURE_REF");
  }
  
  public boolean isDCT() {
  	return documentFunction == DocumentFunction.CREATION_TIME;
  }
  
  /**
   * Determines if this timex occurs before the other in the document.
   * It assumes both timexes are from the same document.
   * @param other Another timex to compare against.
   * @return True if this timex occurs before the other in the document.
   */
  public boolean isBeforeInText(Timex other) {
  	if( sid < other.getSid() )
  		return true;
  	else if( sid == other.getSid() && tokenOffset < other.getTokenOffset() )
  		return true;
  	else 
  		return false;
  }

	
  /**
   * @desc Looks in the "value" attribute of the timexes, and compares the ordering
   *       if they are both DATEs or TIMEs.  The value is a date string "YYYY-MM-DD".
   * @param other The date/time with which to compare this object
   * @return True if this timex occurs before the given timex
   */
  public boolean before(Timex other) {
    // don't bother if we're not a date or time
    if( (this.type == Type.DATE || this.type == Type.TIME)
    && (other.getType() == Type.DATE || other.getType() == Type.TIME) ) {
    	String ovalue = other.getValue();

      // make sure there are values to compare
    	if( this.value != null  && this.value.length() > 0  && this.value.matches("\\d.*") &&
          ovalue != null && ovalue.length() > 0 && ovalue.matches("\\d.*") ) {

        if( this.value.equalsIgnoreCase(ovalue) ) return false;
        
        try {
          //	  System.out.println("Checking " + value + " and " + ovalue);
          // check years
          int year  = Integer.valueOf(this.value.substring(0,4));
          int oyear = Integer.valueOf(ovalue.substring(0,4));
          if( year < oyear ) return true;
          else if( oyear < year ) return false;

          // check months
          if( this.value.length() > 4 && ovalue.length() > 4 ) {
            int month  = Integer.valueOf(this.value.substring(5,7));
            int omonth = Integer.valueOf(ovalue.substring(5,7));
            if( month < omonth ) return true;
            else if( omonth < month ) return false;
          }

          // check days - some timexes don't have days e.g. "1998-10"
          if( this.value.length() > 7 && ovalue.length() > 7 ) {
            int day  = Integer.valueOf(this.value.substring(8,10));
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
  	if( (this.type == Type.DATE || this.type == Type.TIME || this.type == Type.DURATION)  &&
        (other.getType() == Type.DATE || other.getType() == Type.TIME || other.getType() == Type.DURATION) ) {
      String ovalue = other.getValue();

      // make sure there are values to compare
      if( this.value != null  && this.value.length() > 0  && this.value.matches("\\d.*") &&
          ovalue != null && ovalue.length() > 0 && ovalue.matches("\\d.*") ) {

        // If the values are exactly the same, then they aren't INCLUDES.
        if( this.value.equalsIgnoreCase(ovalue) ) return false;
        
        // If a timex in the document is "now", then it INCLUDES the document's creation time.
        if( this.value.equalsIgnoreCase("PRESENT_REF") && other.getDocumentFunction() == DocumentFunction.CREATION_TIME )
          return true;
        
        try {
          //    System.out.println("Checking " + value + " and " + ovalue);
          // check years
          int year  = Integer.valueOf(this.value.substring(0,4));
          int oyear = Integer.valueOf(ovalue.substring(0,4));
          if( year != oyear ) return false;
          else { // same year

            // Then this time is a year, and the other is a month in the year. True!
            if( this.value.length() == 4 && ovalue.length() > 4 )
              return true;

            // Both have a month, so see if it is the same month.
            else if( this.value.length() > 4 && ovalue.length() > 4 ) {
              int month  = Integer.valueOf(this.value.substring(5,7));
              int omonth = Integer.valueOf(ovalue.substring(5,7));
              if( month != omonth ) return false;
              else if( this.value.length() == 7 && ovalue.length() > 7 )
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
  	buf.append("<TIMEX3 tid=\""); buf.append(this.getTid()); buf.append('\"');
  	buf.append(" type=\""); buf.append(this.getType()); buf.append('\"');
  	buf.append(" value=\""); buf.append(this.getValue()); buf.append('\"');
  	buf.append(" temporalFunction=\""); buf.append(this.getTemporalFunction()); buf.append('\"');
  	buf.append(" functionInDocument=\""); 
  	if( this.getDocumentFunction() == null )
  	  buf.append("NONE");
  	else 
  	  buf.append(this.getDocumentFunction()); 
  	buf.append('\"');
  	buf.append('>');
//  	buf.append(text());
//  	buf.append("</TIMEX3>");
  	return buf.toString();
  }

  public org.jdom.Element toElement(Namespace ns) {
    org.jdom.Element el = new org.jdom.Element(Timex.TIMEX_ELEM,ns);
    el.setAttribute(Timex.TID_ATT, tid);
    if( text != null ) el.setAttribute(Timex.TEXT_ATT, text);
    el.setAttribute(Timex.OFFSET_ATT, String.valueOf(this.tokenOffset));
    el.setAttribute(Timex.LENGTH_ATT, String.valueOf(this.tokenLength));
    if( this.anchorTid != null ) el.setAttribute(Timex.ANCHORTID_ATT, this.anchorTid);
    el.setAttribute(Timex.TYPE_ATT, String.valueOf(type));
    if( this.mod != null ) el.setAttribute(Timex.MOD_ATT, String.valueOf(this.mod.toString()));
    if( this.value != null ) el.setAttribute(Timex.VALUE_ATT, this.value);
    if( this.documentFunction != null ) el.setAttribute(Timex.DOCFUNC_ATT, String.valueOf(this.documentFunction));
    el.setAttribute(Timex.TEMPFUNC_ATT, String.valueOf(this.temporalFunction));
    return el;
  }

  public String toString() {
    return this.tid + " " 
    		 + this.sid + " " 
    		 + this.type + " " 
    		 + this.value + " '" 
    		 + this.text + "' " 
    		 + this.preposition;
  }
  
  
  public static String dateFromValue(String value) {
  	String creationTimeRegex = "(\\d{4}-\\d{2}-\\d{2}).*";
  	Pattern creationTimePattern = Pattern.compile(creationTimeRegex);
  	Matcher matcher = creationTimePattern.matcher(value);
  	if (matcher.matches()) {
  		String dayValue = matcher.group(1);
  		return dayValue;
  	}
  	else {
  		return null;
  	}
  }
  /**
   * @returns Timex with truncated DCT (to the day granularity)
   * @param DCTTimex
   */
  public static Timex dctDayTimex(Timex dctTimex) {
  	// First confirm that dctTimex is DCT
  	if (!dctTimex.isDCT())
  		return null;
  	
  	// Truncate the dctTimex's value to the day granularity if possible;
  	// otherwise, do nothing
  	Timex dcdTimex = new Timex(dctTimex);
  	String creationTimeRegex = "(\\d{4}-\\d{2}-\\d{2}).*";
  	Pattern creationTimePattern = Pattern.compile(creationTimeRegex);
  	Matcher matcher = creationTimePattern.matcher(dctTimex.getValue());
  	if (matcher.matches()) {
  		String dcdValue = matcher.group(1);
  		dcdTimex.setValue(dcdValue);
  	}
  	
  	// Confirm that dcdTimex's value is of the appropriate form (no need if match acheived above),
  	// and then return it;
  	// if it doesn't then return null
  	String creationDayRegex = "\\d{4}-\\d{2}-\\d{2}";
  	Pattern creationDayPattern = Pattern.compile(creationTimeRegex);
  	Matcher matcherDay = creationTimePattern.matcher(dctTimex.getValue());
  	if (matcherDay.matches()) {
  		return dcdTimex;
  	}
  	else {
  		return null;
  	}
  }
}
