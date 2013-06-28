package timesieve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.jdom.Namespace;
import org.w3c.dom.Element;

import timesieve.util.TimebankUtil;

/**
 * index is 1 indexed.
 * sid is 0 indexed.
 *
 * This class holds all attributes about text events. It defines tense, aspect, class, and
 * polarity. It also contains the event ID, it's instance ID, and the XML attribute names.
 * 
 * @author chambers
 */
public class TextEvent {
  public static String NAME_ELEM = "event";
  public static String ID_ELEM = "id";
  public static String EIID_ELEM = "eiid";
  public static String OFFSET_ELEM = "offset";
  public static String STRING_ELEM = "string";
  public static String TENSE_ELEM = "tense";
  public static String ASPECT_ELEM = "aspect";
  public static String CLASS_ELEM = "class";
  public static String POL_ELEM = "polarity";
  public static String MODAL_ELEM = "modality";
  public static String HAPPENED_ELEM = "happen";
  public static String UPPER_DURATION_ELEM = "upperBoundDuration";
  public static String LOWER_DURATION_ELEM = "lowerBoundDuration";

  public static enum TENSE    { PRESENT, PRESPART, PAST, PASTPART, INFINITIVE, FUTURE, PASSIVE, NONE };
  public static enum ASPECT   { PROGRESSIVE, PERFECTIVE, IMPERFECTIVE, PERFECTIVE_PROGRESSIVE, IMPERFECTIVE_PROGRESSIVE, NONE };
  public static enum CLASS    { OCCURRENCE, ASPECTUAL, STATE, I_ACTION, I_STATE, REPORTING, PERCEPTION, NONE };
  public static enum POLARITY { POS, NEG };
    
  private String ID; // event ID from the XML
  private String eiid; // event instance ID from makeinstances...
  private List<String> eiids; // event instance ID from makeinstances...
  private int sid;   // sentence number in the document
  private int index; // position in the sentence (the first word of the event)
  private String text = ""; // literal string of the event
  private Element element;
  private HashMap<Integer,String> _entities;
  private String prepClause = null;
  private int[] pos; // array of POS tags around event
  private TENSE tense = TENSE.NONE;
  private ASPECT aspect = ASPECT.NONE;
  private POLARITY polarity = POLARITY.POS;
  private CLASS theclass = CLASS.NONE;
  private String modality = "",happened = "";
  private String lowerDuration = "", upperDuration = "";
  private Vector<String> _dominates = new Vector<String>();

  /**
   * Build the object from an XML specification
   */
  public TextEvent(int sid, org.jdom.Element el) {
    ID = el.getAttributeValue(ID_ELEM);
    addEiidsFromAttributeString(el.getAttributeValue(EIID_ELEM));
    this.sid = sid;
    index = Integer.valueOf(el.getAttributeValue(OFFSET_ELEM));
    text = el.getAttributeValue(STRING_ELEM).replaceAll("\\&\\#.+;", " ").replaceAll("\\n", " "); // replace is new, get rid of newlines
    setTense(el.getAttributeValue(TENSE_ELEM));
    setAspect(el.getAttributeValue(ASPECT_ELEM));
    setPolarity(el.getAttributeValue(POL_ELEM));
    setTheClass(el.getAttributeValue(CLASS_ELEM));
    modality = el.getAttributeValue(MODAL_ELEM);
    happened = el.getAttributeValue(HAPPENED_ELEM);
    lowerDuration = el.getAttributeValue(LOWER_DURATION_ELEM);
    upperDuration = el.getAttributeValue(UPPER_DURATION_ELEM);
  }
  public TextEvent(String sidstr, org.jdom.Element el) {
  	this(Integer.valueOf(sidstr), el);
  }

  public TextEvent(String id, int sid, int index, Element el) {
    ID = id;
    element = el;
    this.sid = sid;
    this.index = index;
    saveFeats(el);
  }

  // Used for non-dom based TextEvents (e.g. gigaword corpus)
  public TextEvent(String text, String id, int sid, int index) {
    ID = id;
    addEiid(id);
    this.sid = sid;
    this.index = index;
    this.text = text;
  }
  
  // eiid attributes can be singles "ei4" or multiple "ei4,ei8,ei12"
  private void addEiidsFromAttributeString(String str) {
    String[] parts = str.split(",");
    for( int xx = 0; xx < parts.length; xx++ )
      addEiid(parts[xx]);
    //    System.out.println("Event " + this + " with eiid=" + eiid + " and eiids=" + eiids);
  }
  
  public void saveFeats(Element el) {
    if( el.hasAttribute("TENSE") ) setTense(el.getAttribute("TENSE"));
    if( el.hasAttribute("tense") ) setTense(el.getAttribute("tense"));
    if( el.hasAttribute("CLASS") ) setTheClass(el.getAttribute("CLASS"));
    if( el.hasAttribute("class") ) setTheClass(el.getAttribute("class"));
    if( el.hasAttribute("MODALITY") ) modality = el.getAttribute("MODALITY");
    if( el.hasAttribute("modality") ) modality = el.getAttribute("modality");
    if( el.hasAttribute("POLARITY") ) setPolarity(el.getAttribute("POLARITY"));
    if( el.hasAttribute("polarity") ) setPolarity(el.getAttribute("polarity"));
    if( el.hasAttribute("ASPECT") ) setAspect(el.getAttribute("ASPECT"));
    if( el.hasAttribute("aspect") ) setAspect(el.getAttribute("aspect"));
    if( el.hasAttribute("happen") ) happened = el.getAttribute("happen");
    if( el.hasAttribute("HAPPEN") ) happened = el.getAttribute("HAPPEN");
    if( el.hasAttribute("lowerBoundDuration") ) lowerDuration = el.getAttribute("lowerBoundDuration");
    if( el.hasAttribute("upperBoundDuration") ) upperDuration = el.getAttribute("upperBoundDuration");

    if( el.hasAttribute(EIID_ELEM) ) addEiidsFromAttributeString(el.getAttribute(EIID_ELEM));
  }

  public String string() {
    //	if( element != null ) return ((Text)element.getFirstChild()).getData();
    if( element != null ) return TimebankUtil.stringFromElement(element).trim();
    else return text;
  }

  public void setPOSTags(int[] p) { pos = p; }
  public void setEiid(String id) { eiid = id; }
  
  /**
   * Add an event instance ID (eiid) to this event. An event can have more than one, but usually just one.
   * The eiid variable holds the first one, but if more are added, then we create the eiids list and
   * add the extra ones there. The eiid is not in the eiids list (this is just to preserve space).
   */
  public void addEiid(String id) {
    if( eiid == null )
      eiid = id;
    else if( eiid.equalsIgnoreCase(id) )
      return;
    else if( eiids == null ) {
      eiids = new ArrayList<String>();
      eiids.add(id);
    }
    else if( !containsEiid(id) )
      eiids.add(id);
//    System.out.println("  finished with " + eiids);
  }
  
  /**
   * Determines if this event is mapped to the eiid given. True if yes, false otherwise. 
   * @param id An eiid such as "ei5"
   * @return True or false.
   */
  public boolean containsEiid(String id) {
    if( eiid != null && eiid.equalsIgnoreCase(id) )
      return true;
    else if( eiids != null ) {
      for( int xx = 0; xx < eiids.size(); xx++ ) {
        if( eiids.get(xx).equalsIgnoreCase(id) )
          return true;
      }      
    }
    return false;
  }

  public void addPrepConstraint(String prep) {
    prepClause = prep;
  }
  public void addEntityRelation(int index, String relation) {
    if( _entities == null ) _entities = new HashMap<Integer, String>();
    _entities.put(index,relation);
  }
  public String getEntity(int index) {
    if( _entities == null ) return null;
    return (String)_entities.get(index);
  }
  public Integer[] getEntities() {
    if( _entities != null ) {
      Integer[] ints = new Integer[1];
      return _entities.keySet().toArray(ints);
    }
    return null;
  }
  // Return true if there is at least one entity
  public boolean hasEntities() {
    if( _entities != null ) return true;
    return false;
  }

  public void addDominance(String id) {
    _dominates.add(id);
  }

  /**
   * Return true if this TextEvent dominates the given id
   * @param id The id of another TextEvent
   */
  public boolean dominates(String id) {
    if( _dominates.contains(id) ) return true;
    else return false;
  }
  
  /**
   * Compare if two TextEvent objects contain the same critical info.
   * @param other The other object to compare against.
   * @return True if they are the same, false otherwise.
   */
  public boolean compareTo(TextEvent other) {
    if( ID.equals(other.id()) && sid == other.sid() && string().equals(other.string()) )
      return true;
    else {
      return false;
    }
  }

  public int[] pos() { return pos; }
  public String prep() { return prepClause; }
  public String id() { return ID; }
  public int sid() { return sid; }

  public String eiid() { return eiid; }
  public List<String> getAllEiids() {
    List<String> ids = new ArrayList<String>();
    if( eiid != null ) ids.add(eiid);
    if( eiids != null )
      for( String ii : eiids )
        ids.add(ii);
    return ids;
  }
  
  public int index() { return index; }
  public Element element() { return element; }
  public String getTense() { return (tense == null ? null : tense.toString()); }
  public String getAspect() { return (aspect == null ? null : aspect.toString()); }
  public String getTheClass() { return (theclass == null ? null : theclass.toString()); }
  public String getPolarity() { return (polarity == null ? null : polarity.toString()); }
  public String getModality() { return modality; }
  public String getHappened() { return happened; }
  public String getUpperBoundDuration() { return upperDuration; }
  public String getLowerBoundDuration() { return lowerDuration; }
  public void setText(String t) { text = t; }
  public void setModality(String val) { modality = val; }
  public void setHappened(String val) { happened = val; }
  public void setTense(String val) {
    if( val != null && val.length() > 0 )
      tense = TENSE.valueOf(val);
    else {
      System.out.println("ERROR: unknown tense=" + val);
      tense = null;
    }
  }
  public void setAspect(String val) { 
    if( val != null && val.length() > 0 )
      aspect = ASPECT.valueOf(val); 
    else {
      System.out.println("ERROR: unknown aspect=" + val);
      aspect = null;
    }
  }
  public void setTheClass(String val) { 
    if( val != null && val.length() > 0 )
      theclass = CLASS.valueOf(val); 
    else {
      System.out.println("ERROR: unknown class=" + val);
      theclass = null;
    }
  }
  public void setPolarity(String val) { 
    if( val != null && val.length() > 0 )
      polarity = POLARITY.valueOf(val); 
    else {
      System.out.println("ERROR: unknown polarity=" + val);
      polarity = null;
    }
  }

  private String buildEiidString() {
    if( eiid == null ) return "";
    else {
      String str = eiid;
      if( eiids != null ) 
        for( int xx = 0; xx < eiids.size(); xx++ )
          str += "," + eiids.get(xx);
      return str;
    }
  }

  public org.jdom.Element toElement(Namespace ns) {
    org.jdom.Element el = new org.jdom.Element(NAME_ELEM,ns);
    el.setAttribute(ID_ELEM,String.valueOf(ID));
    el.setAttribute(EIID_ELEM, buildEiidString());
    el.setAttribute(OFFSET_ELEM,String.valueOf(index));
    el.setAttribute(STRING_ELEM,string());
    el.setAttribute(TENSE_ELEM,tense.toString());
    el.setAttribute(ASPECT_ELEM,aspect.toString());
    el.setAttribute(CLASS_ELEM,theclass.toString());
    el.setAttribute(POL_ELEM,polarity.toString());
    el.setAttribute(MODAL_ELEM,modality);
    el.setAttribute(HAPPENED_ELEM,happened);
    el.setAttribute(LOWER_DURATION_ELEM,lowerDuration);
    el.setAttribute(UPPER_DURATION_ELEM,upperDuration);
    return el;
  }

  public String toString() {
    String str = ID + "(" + index + ") - " + string();
    if( _entities != null ) {
      for( Map.Entry<Integer, String> entry : _entities.entrySet() )
        str += " (" + entry.getKey() + " " + entry.getValue() + ")";
    }
    if( prepClause != null ) str += " (PREP " + prepClause + ")";
    //	str += "\nt=" + tense + " a=" + aspect + " m=" + modality + 
    //	    " p=" + polarity + " c=" + theclass;
    return str;
  }
}
