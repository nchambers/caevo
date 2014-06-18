package caevo;

import java.util.ArrayList;
import java.util.List;

import org.jdom.Namespace;
import org.w3c.dom.Element;

import caevo.util.TimebankUtil;

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
  public static final String NAME_ELEM = "event";
  public static final String ID_ELEM = "id";
  public static final String EIID_ELEM = "eiid";
  public static final String OFFSET_ELEM = "offset";
  public static final String STRING_ELEM = "string";
  public static final String TENSE_ELEM = "tense";
  public static final String ASPECT_ELEM = "aspect";
  public static final String CLASS_ELEM = "class";
  public static final String POL_ELEM = "polarity";
  public static final String MODAL_ELEM = "modality";
  public static final String HAPPENED_ELEM = "happen";
  public static final String UPPER_DURATION_ELEM = "upperBoundDuration";
  public static final String LOWER_DURATION_ELEM = "lowerBoundDuration";

  public static enum Tense    { PRESENT, PRESPART, PAST, PASTPART, INFINITIVE, FUTURE, PASSIVE, NONE };
  public static enum Aspect   { PROGRESSIVE, PERFECTIVE, IMPERFECTIVE, PERFECTIVE_PROGRESSIVE, IMPERFECTIVE_PROGRESSIVE, NONE };
  public static enum Class   	{ OCCURRENCE, ASPECTUAL, STATE, I_ACTION, I_STATE, REPORTING, PERCEPTION, NONE };
  public static enum Polarity { POS, NEG };
    
  private String ID; // event ID from the XML
  private List<String> eiids; // event instance IDs from makeinstances...
  private int sid;   // sentence number in the document
  private int index; // position in the sentence (the first word of the event)
  private String text = ""; // literal string of the event
  private Element element;
  private Tense tense = Tense.NONE;
  private Aspect aspect = Aspect.NONE;
  private Polarity polarity = Polarity.POS;
  private Class theclass = Class.NONE;
  private String modality = "";
  private String happened = "";
  private String lowerDuration = "";
  private String upperDuration = "";

  /**
   * Build the object from an XML specification
   */
  public TextEvent(int sid, org.jdom.Element el) {
    this.ID = el.getAttributeValue(TextEvent.ID_ELEM);
    this.sid = sid;
    this.index = Integer.valueOf(el.getAttributeValue(TextEvent.OFFSET_ELEM));
    this.text = el.getAttributeValue(TextEvent.STRING_ELEM).replaceAll("\\&\\#.+;", " ").replaceAll("\\n", " "); // replace is new, get rid of newlines
    this.tense = Tense.valueOf(el.getAttributeValue(TextEvent.TENSE_ELEM));
    this.aspect = Aspect.valueOf(el.getAttributeValue(TextEvent.ASPECT_ELEM));
    this.polarity = Polarity.valueOf(el.getAttributeValue(TextEvent.POL_ELEM));
    this.theclass = Class.valueOf(el.getAttributeValue(TextEvent.CLASS_ELEM));
    this.modality = el.getAttributeValue(TextEvent.MODAL_ELEM);
    this.happened = el.getAttributeValue(TextEvent.HAPPENED_ELEM);
    this.lowerDuration = el.getAttributeValue(TextEvent.LOWER_DURATION_ELEM);
    this.upperDuration = el.getAttributeValue(TextEvent.UPPER_DURATION_ELEM);
    
    addEiidsFromAttributeString(el.getAttributeValue(TextEvent.EIID_ELEM));
  }

  public TextEvent(String sidstr, org.jdom.Element el) {
  	this(Integer.valueOf(sidstr), el);
  }

  public TextEvent(String id, int sid, int index, Element el) {
    this.ID = id;
    this.element = el;
    this.sid = sid;
    this.index = index;
    
    saveFeats(el);
  }

  // Used for non-dom based TextEvents (e.g. gigaword corpus)
  public TextEvent(String text, String id, int sid, int index) {
    this.ID = id;
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
    if( el.hasAttribute("TENSE") ) this.tense = Tense.valueOf(el.getAttribute("TENSE"));
    if( el.hasAttribute("tense") ) this.tense = Tense.valueOf(el.getAttribute("tense"));
    if( el.hasAttribute("CLASS") ) this.theclass = Class.valueOf(el.getAttribute("CLASS"));
    if( el.hasAttribute("class") ) this.theclass = Class.valueOf(el.getAttribute("class"));
    if( el.hasAttribute("MODALITY") ) this.modality = el.getAttribute("MODALITY");
    if( el.hasAttribute("modality") ) this.modality = el.getAttribute("modality");
    if( el.hasAttribute("POLARITY") ) this.polarity = Polarity.valueOf(el.getAttribute("POLARITY"));
    if( el.hasAttribute("polarity") ) this.polarity = Polarity.valueOf(el.getAttribute("polarity"));
    if( el.hasAttribute("ASPECT") ) this.aspect = Aspect.valueOf(el.getAttribute("ASPECT"));
    if( el.hasAttribute("aspect") ) this.aspect = Aspect.valueOf(el.getAttribute("aspect"));
    if( el.hasAttribute("happen") ) this.happened = el.getAttribute("happen");
    if( el.hasAttribute("HAPPEN") ) this.happened = el.getAttribute("HAPPEN");
    if( el.hasAttribute("lowerBoundDuration") ) this.lowerDuration = el.getAttribute("lowerBoundDuration");
    if( el.hasAttribute("upperBoundDuration") ) this.upperDuration = el.getAttribute("upperBoundDuration");

    if( el.hasAttribute(TextEvent.EIID_ELEM) ) addEiidsFromAttributeString(el.getAttribute(EIID_ELEM));
  }
  
  /**
   * Add an event instance ID (eiid) to this event. An event can have more than one, but usually just one.
   * The eiid variable holds the first one, but if more are added, then we create the eiids list and
   * add the extra ones there. The eiid is not in the eiids list (this is just to preserve space).
   */
  public void addEiid(String eiid) {
  	if( this.eiids == null ) {
      this.eiids = new ArrayList<String>();
      this.eiids.add(eiid);
    }
    else if( !containsEiid(eiid) )
      this.eiids.add(eiid);
//    System.out.println("  finished with " + eiids);
  }
  
  public void setText(String text) { 
  	this.text = text;
  }
  
  public void setModality(String modality) { 
  	this.modality = modality; 
  }
  
  public void setHappened(String happened) { 
  	this.happened = happened; 
  }
  
  public void setTense(Tense tense) {
  	this.tense = tense;
  }
  
  public void setAspect(Aspect aspect) {
  	this.aspect = aspect;
  }
  
  public void setTheClass(Class theClass) { 
  	this.theclass = theClass;
  }
  
  public void setPolarity(Polarity polarity) { 
  	this.polarity = polarity;
  }
  
  private String buildEiidString() {
  	String str = "";
  	if( this.eiids != null && this.eiids.size() > 0 )
  		str = eiids.get(0);
  	for( int xx = 1; xx < this.eiids.size(); xx++ )
  		str += "," + this.eiids.get(xx);
  	return str;
  }
  
  /**
   * Determines if this event is mapped to the eiid given. True if yes, false otherwise. 
   * @param id An eiid such as "ei5"
   * @return True or false.
   */
  public boolean containsEiid(String eiid) {
  	if( eiid != null && this.eiids != null ) {
  		for( String id : eiids ) {
  			if( id.equalsIgnoreCase(eiid) )
  				return true;
  		}      
  	}
  	return false;
  }
  
  public String getString() {
    //	if( element != null ) return ((Text)element.getFirstChild()).getData();
    if( this.element != null ) return TimebankUtil.stringFromElement(this.element).trim();
    else return this.text;
  }
  
  public String getId() { 
  	return this.ID; 
  }
  
  public int getSid() { 
  	return this.sid; 
  }

  public List<String> getAllEiids() {
    return eiids;
  }

  public String getEiid() {
  	if( eiids != null && eiids.size() > 0 )
  		return eiids.get(0);
  	else return null;
  }
  
  public int getIndex() {
  	return this.index; 
  }
  
  public Element getElement() { 
  	return this.element; 
  }
  
  public Tense getTense() { 
  	return this.tense;
  }
  
  public Aspect getAspect() { 
  	return this.aspect;
  }
  
  public Class getTheClass() {
  	return this.theclass;
  }
  
  public Polarity getPolarity() { 
  	return this.polarity;
  }
  
  public String getModality() { 
  	return this.modality; 
  }
  
  public String getHappened() { 
  	return this.happened; 
  }
  
  public String getUpperBoundDuration() { 
  	return this.upperDuration; 
  }
  
  public String getLowerBoundDuration() { 
  	return lowerDuration; 
  }

  /**
   * Determines if this event occurs before the other in the document.
   * It assumes both events are from the same document.
   * @param other Another event to compare against.
   * @return True if this event occurs before the other in the document.
   */
  public boolean isBeforeInText(TextEvent other) {
  	if( sid < other.getSid() )
  		return true;
  	else if( sid == other.getSid() && index < other.getIndex() )
  		return true;
  	else 
  		return false;
  }
  
  public org.jdom.Element toElement(Namespace ns) {
    org.jdom.Element el = new org.jdom.Element(TextEvent.NAME_ELEM,ns);
    
    el.setAttribute(TextEvent.ID_ELEM, String.valueOf(this.ID));
    el.setAttribute(TextEvent.EIID_ELEM, buildEiidString());
    el.setAttribute(TextEvent.OFFSET_ELEM,String.valueOf(this.index));
    el.setAttribute(TextEvent.STRING_ELEM, getString());
    el.setAttribute(TextEvent.TENSE_ELEM, this.tense.toString());
    el.setAttribute(TextEvent.ASPECT_ELEM, this.aspect.toString());
    el.setAttribute(TextEvent.CLASS_ELEM, this.theclass.toString());
    el.setAttribute(TextEvent.POL_ELEM, this.polarity.toString());
    el.setAttribute(TextEvent.MODAL_ELEM, this.modality);
    el.setAttribute(TextEvent.HAPPENED_ELEM, this.happened);
    el.setAttribute(TextEvent.LOWER_DURATION_ELEM, this.lowerDuration);
    el.setAttribute(TextEvent.UPPER_DURATION_ELEM, this.upperDuration);
    
    return el;
  }

  /**
   * Compare if two TextEvent objects contain the same critical info.
   * @param other The other object to compare against.
   * @return True if they are the same, false otherwise.
   */
  public boolean compareTo(TextEvent other) {
    if( this.ID.equals(other.getId()) && this.sid == other.getSid() && getString().equals(other.getString()) )
      return true;
    else {
      return false;
    }
  }
  
  public String toString() {
    String str = this.ID + "(" + this.buildEiidString() + ") " + getString() + "-" + this.index;
    return str;
  }
}
