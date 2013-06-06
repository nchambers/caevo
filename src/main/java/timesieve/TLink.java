package timesieve;

import org.jdom.*;
import org.jdom.Namespace;


public class TLink implements Comparable<TLink> {
	private String event1, event2;
	public String eiid1 = null, eiid2 = null;  // backups for Semeval, they insist on the eiid used instead of the eid.
  protected TYPE relation;
  protected String originalRelation;
  protected boolean closed = false;
  protected String origin = null; // originating source of this TLink (optional)
  protected double relationConfidence = 0.0; // probability of the relation
  
  public static final String TLINK_TYPE_ATT = "type";
  public static final String TIME_TIME = "tt";
  public static final String EVENT_TIME = "et";
  public static final String EVENT_EVENT = "ee";

  public static final String TLINK_ELEM = "tlink";
  public static final String EV1_ELEM = "event1";
  public static final String EV2_ELEM = "event2";
  public static final String REL_ELEM = "relation";
  public static final String CLOSED_ELEM = "closed";
  public static final String ORIGIN_ELEM = "origin";

  public static enum TYPE { BEFORE, AFTER, IBEFORE, IAFTER, INCLUDES, IS_INCLUDED, BEGINS, BEGUN_BY, ENDS, ENDED_BY, SIMULTANEOUS, 
    NONE, VAGUE, UNKNOWN, OVERLAP, BEFORE_OR_OVERLAP, OVERLAP_OR_AFTER };

  // This has been changed. Previously, the mode determined how the tlink relations would be saved
  // in the .info file once read from the TimeBank source. This is no longer true. The .info file now
  // always has the TimeBank relations. We need to change any mode swaps to do this live, so that changeMode
  // flips all current TLink objects and is done. This change would occur in the InfoFile class.
  public static enum MODE { FULL, REDUCED, BASIC, SYMMETRY, FULLSYMMETRY, BEFORE, TEMPEVAL };
  public static MODE currentMode = MODE.FULL;

  public static void changeMode(MODE newmode) {
    currentMode = newmode;
  }

  TLink() { }

  TLink(Element el) {
    event1 = el.getAttributeValue(EV1_ELEM);
    event2 = el.getAttributeValue(EV2_ELEM);
    relation = normalizeRelation(el.getAttributeValue(REL_ELEM));
    if( el.getAttributeValue(CLOSED_ELEM) != null )
      closed = Boolean.valueOf(el.getAttributeValue(CLOSED_ELEM));
    else closed = false;
    origin = el.getAttributeValue(ORIGIN_ELEM);
  }

  TLink(String e1, String e2, TLink.TYPE rel) {
    this(e1, e2, rel, false);
  }
  
  TLink(String e1, String e2, TLink.TYPE rel, boolean closure) {
    event1 = e1;
    event2 = e2;
    relation = rel;
    closed = closure;
  }
  
  TLink(String e1, String e2, String rel, boolean closure) {
    this(e1,e2,rel);
    closed = closure;
  }

  public TLink(String e1, String e2, String rel) {
    relation = normalizeRelation(rel);
    originalRelation = rel;
//    System.out.println("Creating new tlink (mode " + currentMode + "): " + e1 + " " + e2 + " rel=" + rel);
    event1 = e1;
    event2 = e2;
  }

  public static TYPE normalizeRelation(String str) {
    if( str.equalsIgnoreCase("during") )
      return TYPE.IS_INCLUDED;
    if( str.equalsIgnoreCase("during_inv") ) // appears once in TimeBank
      return TYPE.INCLUDES;
    else if( str.equalsIgnoreCase("identity") )
      return TYPE.SIMULTANEOUS;
    else {
      str = str.replaceAll("-", "_"); // Some relations have hyphens...ours use underscores.
      try {
        TYPE newrel = TYPE.valueOf(str.toUpperCase()); 
        return newrel;
      } catch( Exception ex ) {
        System.out.println("ERROR converting relation: " + str);
        ex.printStackTrace();
        System.exit(-1);
      }
      return null;
    }
  }
  
  /**
   * Compare two TLinks for equality in event IDs and their relation.
   * @param other The other TLink.
   * @return True if the same, false otherwise.
   */
  public boolean compareToTLink(TLink other) {
    // Exactly the same.
    if( relation == other.relation() && event1.equals(other.event1()) && event2.equals(other.event2()) )
      return true;
    // The event IDs might be swapped, so check for the same inverted relation.
    else if( event1.equals(other.event2()) && event2.equals(other.event1()) ) {
      if( invertRelation(relation) == other.relation() )
        return true;
    }
    return false;
  }
  
  /**
   * True if the two tlinks contain the same events, but different relations.
   */
  public boolean conflictsWith(TLink other) {
    if( relation != other.relation() && event1.equals(other.event1()) && event2.equals(other.event2()) )
      return true;
    // The event IDs might be swapped, so check for different inverted relations.
    else if( event1.equals(other.event2()) && event2.equals(other.event1()) ) {
      if( invertRelation(relation) != other.relation() )
        return true;
    }
    return false;
  }
  
  /**
   * Turn this TLink's relation into the reduced set: BEFORE,AFTER,OVERLAP
   */
  public void changeFullMode(MODE mode) {
    if( mode == MODE.SYMMETRY ) fullToSymmetry();
    else if( mode == MODE.BEFORE ) fullToBefore();
    else if( mode == MODE.REDUCED ) fullToReduced();
    else if( mode == MODE.BASIC ) fullToBasic();
    else if( mode == MODE.TEMPEVAL ) fullToTempeval();
  }

  public void fullToSymmetry() {
    if( relation == TYPE.BEFORE )   relation = TYPE.BEFORE;
    if( relation == TYPE.IBEFORE )  relation = TYPE.BEFORE;
    if( relation == TYPE.INCLUDES ) relation = TYPE.OVERLAP; // 3 is OVERLAP
    if( relation == TYPE.BEGINS )   relation = TYPE.OVERLAP;
    if( relation == TYPE.ENDS )     relation = TYPE.OVERLAP;
    if( relation == TYPE.SIMULTANEOUS ) relation = TYPE.OVERLAP;
    if( relation == TYPE.NONE ) relation = TYPE.NONE;
  }

  public void fullToBefore() {
    if( relation == TYPE.BEFORE )   relation = TYPE.BEFORE;
    else if( relation == TYPE.IBEFORE )  relation = TYPE.BEFORE;
    else relation = TYPE.NONE; 
  }

  public void fullToTempeval() {
    if( relation == TYPE.BEFORE || relation == TYPE.AFTER || relation == TYPE.NONE || relation == TYPE.BEFORE_OR_OVERLAP || relation == TYPE.OVERLAP_OR_AFTER ) { } // keep same 
    else if( relation == TYPE.IBEFORE ) relation = TYPE.BEFORE;
    else relation = TYPE.OVERLAP; 
  }

  public void fullToReduced() {
    if( relation == TYPE.BEFORE )   relation = TYPE.BEFORE;
    if( relation == TYPE.IBEFORE )  relation = TYPE.BEFORE;
    if( relation == TYPE.INCLUDES ) relation = TYPE.INCLUDES; // 2 is INCLUDES
    if( relation == TYPE.BEGINS || relation == TYPE.ENDS ) {
      relation = TYPE.INCLUDES;
      String temp = event1;
      event1 = event2;
      event2 = temp;
    }
  }

  public void fullToBasic() {
    if( relation == TYPE.BEFORE )   relation = TYPE.BEFORE;
    if( relation == TYPE.IBEFORE )  relation = TYPE.BEFORE;
    if( relation == TYPE.INCLUDES ) relation = TYPE.OVERLAP;
    if( relation == TYPE.BEGINS )   relation = TYPE.OVERLAP;
    if( relation == TYPE.ENDS )     relation = TYPE.OVERLAP;
    if( relation == TYPE.SIMULTANEOUS ) relation = TYPE.OVERLAP;
  }

  public void setRelationToOneDirection() {
    // Flip event order in the link.
    if( relation == TYPE.AFTER || relation == TYPE.IAFTER || relation == TYPE.IS_INCLUDED ||
        relation == TYPE.BEGUN_BY || relation == TYPE.ENDED_BY ) {
      String temp = event1;
      event1 = event2;
      event2 = temp;
      
    }
      
    // Flip the relation.
    if( relation == TYPE.AFTER )   relation = TYPE.BEFORE;
    else if( relation == TYPE.IAFTER )  relation = TYPE.IBEFORE;
    else if( relation == TYPE.IS_INCLUDED ) relation = TYPE.INCLUDES;
    else if( relation == TYPE.BEGUN_BY )   relation = TYPE.BEGINS;
    else if( relation == TYPE.ENDED_BY )     relation = TYPE.ENDS;
  }
  
  public static TYPE invertRelation(TYPE relation) {
    
    // TODO: need to write this for all modes!!
    
    // NOTE: I think these inversions are true for every possible mode. But better to address
    //       each mode as I need it, rather than assume it.     
    if( currentMode == MODE.FULL || currentMode == MODE.TEMPEVAL ) {
      if( relation == TYPE.BEFORE ) return TYPE.AFTER;
      if( relation == TYPE.IBEFORE ) return TYPE.IAFTER;
      if( relation == TYPE.AFTER ) return TYPE.BEFORE;
      if( relation == TYPE.IAFTER ) return TYPE.IBEFORE;
      if( relation == TYPE.INCLUDES ) return TYPE.IS_INCLUDED;
      if( relation == TYPE.IS_INCLUDED ) return TYPE.INCLUDES;
      if( relation == TYPE.BEGINS ) return TYPE.BEGUN_BY;
      if( relation == TYPE.BEGUN_BY ) return TYPE.BEGINS;
      if( relation == TYPE.ENDS ) return TYPE.ENDED_BY;
      if( relation == TYPE.ENDED_BY ) return TYPE.ENDS;
      if( relation == TYPE.BEFORE_OR_OVERLAP ) return TYPE.OVERLAP_OR_AFTER;
      if( relation == TYPE.OVERLAP_OR_AFTER ) return TYPE.BEFORE_OR_OVERLAP;
      if( relation == TYPE.OVERLAP || relation == TYPE.SIMULTANEOUS || relation == TYPE.NONE || relation == TYPE.VAGUE || relation == TYPE.UNKNOWN ) return relation;
      System.err.println("ERROR in TLink.invertRelation, unmatched relation " + relation);
    }      
      
    System.err.println("ERROR in TLink.invertRelation, no code for mode " + currentMode);
    System.exit(1);
    return null;
  }
  
  public String event1() { return event1; }
  public String event2() { return event2; }
  public TYPE relation() { return relation; }
  public double relationConfidence() { return relationConfidence; }
  public String originalRelation() { return originalRelation; }
  public String origin() { return origin; }
  public void setRelation(TYPE r) { relation = r; }
  public void setOrigin(String str) { origin = str; }
  public boolean isFromClosure() { return closed; }
  public void setRelationConfidence(double d) { relationConfidence = d; }

  public Element toElement(Namespace ns) {
  	return toElement(ns, false);
  }

  /**
   * @param ns
   * @param useEiids If true, use the eiid labels and not the main event labels.
   * @return A new XML Element.
   */
  public Element toElement(Namespace ns, boolean useEiids) {
  	//  System.out.println("toElement " + event1 + " " + event2 + " " + relation + " " + closed + " " + origin);
  	Element el = new Element(TLINK_ELEM, ns);
  	if( useEiids ) {
  		el.setAttribute(EV1_ELEM, (eiid1 != null ? eiid1 : event1));
  		el.setAttribute(EV2_ELEM, (eiid2 != null ? eiid2 : event2));
  	} else {
  		el.setAttribute(EV1_ELEM, event1);
  		el.setAttribute(EV2_ELEM, event2);
  	}
  	el.setAttribute(REL_ELEM, relation.toString());
  	el.setAttribute(CLOSED_ELEM, String.valueOf(closed));
  	if( origin != null ) el.setAttribute(ORIGIN_ELEM, origin);
  	return el;
  }

  public String toString() {
    String str = event1 + "->" + event2 + "=" + relation.toString();
    if( origin != null ) str += " (" + origin + ")";
    return str;
  }
  
  public int compareTo(TLink obj) {
    if( obj instanceof TLink ) {
      TLink other = (TLink)obj;
      if( this.relationConfidence < other.relationConfidence() )
        return 1;
      if( this.relationConfidence > other.relationConfidence() )
        return -1;
      return 0;
    }
    else return -1;
  }
}
