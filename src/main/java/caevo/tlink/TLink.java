package caevo.tlink;

import org.jdom.Element;
import org.jdom.Namespace;

import caevo.SieveDocument;


public class TLink implements Comparable<TLink> {
  public static final String TLINK_TYPE_ATT = "type";
  public static final String TIME_TIME_TYPE_VALUE = "tt";
  public static final String EVENT_TIME_TYPE_VALUE = "et";
  public static final String EVENT_EVENT_TYPE_VALUE = "ee";

  public static final String TLINK_ELEM = "tlink";
  public static final String EV1_ELEM = "event1";
  public static final String EV2_ELEM = "event2";
  public static final String REL_ELEM = "relation";
  public static final String CLOSED_ELEM = "closed";
  public static final String ORIGIN_ELEM = "origin";
  
  public static enum Type { BEFORE, AFTER, IBEFORE, IAFTER, INCLUDES, IS_INCLUDED, BEGINS, BEGUN_BY, ENDS, ENDED_BY, SIMULTANEOUS, 
    NONE, VAGUE, UNKNOWN, OVERLAP, BEFORE_OR_OVERLAP, OVERLAP_OR_AFTER };

  // This has been changed. Previously, the mode determined how the tlink relations would be saved
  // in the .info file once read from the TimeBank source. This is no longer true. The .info file now
  // always has the TimeBank relations. We need to change any mode swaps to do this live, so that changeMode
  // flips all current TLink objects and is done. This change would occur in the InfoFile class.
  public static enum Mode { FULL, REDUCED, BASIC, SYMMETRY, FULLSYMMETRY, BEFORE, TEMPEVAL };
  
  public static Mode currentMode = TLink.Mode.FULL;
  
  public static void changeMode(TLink.Mode newmode) {
    currentMode = newmode;
  }
  
  public static TLink.Type normalizeRelation(String str) {
    if( str.equalsIgnoreCase("during") )
      return TLink.Type.IS_INCLUDED;
    else if( str.equalsIgnoreCase("during_inv") ) // appears once in TimeBank
      return TLink.Type.INCLUDES;
    else if( str.equalsIgnoreCase("identity") )
      return TLink.Type.SIMULTANEOUS;
    else {
      str = str.replaceAll("-", "_"); // Some relations have hyphens...ours use underscores.
      try {
        TLink.Type newrel = TLink.Type.valueOf(str.toUpperCase()); 
        return newrel;
      } catch( Exception ex ) {
        System.out.println("ERROR converting relation: " + str);
        ex.printStackTrace();
        System.exit(-1);
      }
      return null;
    }
  }
  
  public static TLink.Type invertRelation(TLink.Type relation) {
  	if( relation == TLink.Type.BEFORE ) 					return TLink.Type.AFTER;
  	if( relation == TLink.Type.AFTER ) 						return TLink.Type.BEFORE;
  	if( relation == TLink.Type.IBEFORE )					return TLink.Type.IAFTER;
  	if( relation == TLink.Type.IAFTER ) 					return TLink.Type.IBEFORE;
  	if( relation == TLink.Type.INCLUDES ) 				return TLink.Type.IS_INCLUDED;
  	if( relation == TLink.Type.IS_INCLUDED )			return TLink.Type.INCLUDES;
  	if( relation == TLink.Type.BEGINS ) 					return TLink.Type.BEGUN_BY;
  	if( relation == TLink.Type.BEGUN_BY ) 				return TLink.Type.BEGINS;
  	if( relation == TLink.Type.ENDS ) 						return TLink.Type.ENDED_BY;
  	if( relation == TLink.Type.ENDED_BY ) 					return TLink.Type.ENDS;
  	if( relation == TLink.Type.BEFORE_OR_OVERLAP ) 	return TLink.Type.OVERLAP_OR_AFTER;
  	if( relation == TLink.Type.OVERLAP_OR_AFTER ) 	return TLink.Type.BEFORE_OR_OVERLAP;
  	if( relation == TLink.Type.OVERLAP || relation == TLink.Type.SIMULTANEOUS || 
  			relation == TLink.Type.NONE || relation == TLink.Type.VAGUE || relation == TLink.Type.UNKNOWN ) 
  		return relation;

  	System.err.println("ERROR in TLink.invertRelation, unmatched relation " + relation);
    return null;
  }
  
	protected String id1, id2; // These should be timex tids or event eiids
	protected TLink.Type relation;
  protected String originalRelation;
  protected boolean closed = false;
  protected String origin = null; // originating source of this TLink (optional)
  protected double relationConfidence = 0.0; // probability of the relation
	protected SieveDocument document;

  public TLink() { }

  public TLink(Element el) {
    this.id1 = el.getAttributeValue(TLink.EV1_ELEM);
    this.id2 = el.getAttributeValue(TLink.EV2_ELEM);
    this.relation = TLink.normalizeRelation(el.getAttributeValue(TLink.REL_ELEM));
    if( el.getAttributeValue(TLink.CLOSED_ELEM) != null )
      this.closed = Boolean.valueOf(el.getAttributeValue(TLink.CLOSED_ELEM));
    else 
    	this.closed = false;
    this.origin = el.getAttributeValue(TLink.ORIGIN_ELEM);
  }

  public TLink(String id1, String id2, TLink.Type rel) {
    this(id1, id2, rel, false);
  }
  
  public TLink(String id1, String id2, TLink.Type rel, boolean closure) {
    this.id1 = id1;
    this.id2 = id2;
    this.relation = rel;
    this.closed = closure;
  }
  
  public TLink(String id1, String id2, String rel, boolean closure) {
    this(id1,id2,rel);
    this.closed = closure;
  }

  public TLink(String id1, String id2, String rel) {
    this.relation = TLink.normalizeRelation(rel);
    this.originalRelation = rel;
//    System.out.println("Creating new tlink (mode " + currentMode + "): " + e1 + " " + e2 + " rel=" + rel);
    this.id1 = id1;
    this.id2 = id2;
  }
  
  /**
   * Turn this TLink's relation into the reduced set: BEFORE,AFTER,OVERLAP
   */
  public void changeFullMode(TLink.Mode mode) {
    if( mode == TLink.Mode.SYMMETRY ) fullToSymmetry();
    else if( mode == TLink.Mode.BEFORE ) fullToBefore();
    else if( mode == TLink.Mode.REDUCED ) fullToReduced();
    else if( mode == TLink.Mode.BASIC ) fullToBasic();
    else if( mode == TLink.Mode.TEMPEVAL ) fullToTempeval();
  }

  public void fullToSymmetry() {
    if( this.relation == TLink.Type.BEFORE )   this.relation = TLink.Type.BEFORE;
    if( this.relation == TLink.Type.IBEFORE )  this.relation = TLink.Type.BEFORE;
    if( this.relation == TLink.Type.INCLUDES ) this.relation = TLink.Type.OVERLAP; // 3 is OVERLAP
    if( this.relation == TLink.Type.BEGINS )   this.relation = TLink.Type.OVERLAP;
    if( this.relation == TLink.Type.ENDS )     this.relation = TLink.Type.OVERLAP;
    if( this.relation == TLink.Type.SIMULTANEOUS ) this.relation = TLink.Type.OVERLAP;
    if( this.relation == TLink.Type.NONE ) this.relation = TLink.Type.NONE;
  }

  public void fullToBefore() {
    if( this.relation == TLink.Type.BEFORE )   this.relation = TLink.Type.BEFORE;
    else if( this.relation == TLink.Type.IBEFORE )  this.relation = TLink.Type.BEFORE;
    else this.relation = TLink.Type.NONE; 
  }

  public void fullToTempeval() {
    if( this.relation == TLink.Type.BEFORE || this.relation == TLink.Type.AFTER || this.relation == TLink.Type.NONE || this.relation == TLink.Type.BEFORE_OR_OVERLAP || this.relation == TLink.Type.OVERLAP_OR_AFTER ) { } // keep same 
    else if( this.relation == TLink.Type.IBEFORE ) this.relation = TLink.Type.BEFORE;
    else this.relation = TLink.Type.OVERLAP; 
  }

  public void fullToReduced() {
    if( this.relation == TLink.Type.BEFORE )   this.relation = TLink.Type.BEFORE;
    if( this.relation == TLink.Type.IBEFORE )  this.relation = TLink.Type.BEFORE;
    if( this.relation == TLink.Type.INCLUDES ) this.relation = TLink.Type.INCLUDES; // 2 is INCLUDES
    if( this.relation == TLink.Type.BEGINS || this.relation == TLink.Type.ENDS ) {
    	this.relation = TLink.Type.INCLUDES;
      String temp = this.id1;
      this.id1 = this.id2;
      this.id2 = temp;
    }
  }

  public void fullToBasic() {
    if( this.relation == TLink.Type.BEFORE )   this.relation = TLink.Type.BEFORE;
    if( this.relation == TLink.Type.IBEFORE )  this.relation = TLink.Type.BEFORE;
    if( this.relation == TLink.Type.INCLUDES ) this.relation = TLink.Type.OVERLAP;
    if( this.relation == TLink.Type.BEGINS )   this.relation = TLink.Type.OVERLAP;
    if( this.relation == TLink.Type.ENDS )     this.relation = TLink.Type.OVERLAP;
    if( this.relation == TLink.Type.SIMULTANEOUS ) this.relation = TLink.Type.OVERLAP;
  }

  public void setRelationToOneDirection() {
    // Flip event order in the link.
    if( this.relation == TLink.Type.AFTER || this.relation == TLink.Type.IAFTER || this.relation == TLink.Type.IS_INCLUDED ||
    		this.relation == TLink.Type.BEGUN_BY || this.relation == TLink.Type.ENDED_BY ) {
      String temp = this.id1;
      this.id1 = this.id2;
      this.id2 = temp;
      
    }
      
    // Flip the relation.
    if( this.relation == TLink.Type.AFTER )   this.relation = TLink.Type.BEFORE;
    else if( this.relation == TLink.Type.IAFTER )  this.relation = TLink.Type.IBEFORE;
    else if( this.relation == TLink.Type.IS_INCLUDED ) this.relation = TLink.Type.INCLUDES;
    else if( this.relation == TLink.Type.BEGUN_BY )   this.relation = TLink.Type.BEGINS;
    else if( this.relation == TLink.Type.ENDED_BY )     this.relation = TLink.Type.ENDS;
  }
  
  public void setRelation(TLink.Type r) { this.relation = r; }
  public void setOrigin(String str) { this.origin = str; }
  public void setRelationConfidence(double d) { this.relationConfidence = d; }
  public void setDocument(SieveDocument document) { this.document = document; }
  public void setClosure(boolean closed) { this.closed = closed; }
  public void setId1(String id) { id1 = id; }
  public void setId2(String id) { id2 = id; }
  
  public String getId1() { return this.id1; }
  public String getId2() { return this.id2; }
  public TLink.Type getRelation() { return this.relation; }
  public double getRelationConfidence() { return this.relationConfidence; }
  public String getOriginalRelation() { return this.originalRelation; }
  public String getOrigin() { return this.origin; }
  public boolean isFromClosure() { return this.closed; }
  public SieveDocument getDocument() { return this.document; }
  
  public Element toElement(Namespace ns) {
  	//  System.out.println("toElement " + event1 + " " + event2 + " " + relation + " " + closed + " " + origin);
  	Element el = new Element(TLink.TLINK_ELEM, ns);
  	el.setAttribute(TLink.EV1_ELEM, this.id1);
  	el.setAttribute(TLink.EV2_ELEM, this.id2);
  	el.setAttribute(TLink.REL_ELEM, this.relation.toString());
  	el.setAttribute(TLink.CLOSED_ELEM, String.valueOf(this.closed));
  	if( this.origin != null ) el.setAttribute(TLink.ORIGIN_ELEM, this.origin);
  	return el;
  }

  public String toString() {
    String str = this.id1 + "->" + this.id2 + "=" + this.relation.toString();
    if( this.origin != null ) str += " (" + this.origin + ")";
    return str;
  }
  
  public int compareTo(TLink obj) {
    if( obj instanceof TLink ) {
      TLink other = (TLink)obj;
      if( this.relationConfidence < other.relationConfidence )
        return 1;
      if( this.relationConfidence > other.relationConfidence )
        return -1;
      return 0;
    }
    else return -1;
  }
  
  /**
   * Compare two TLinks for equality in event IDs and their relation.
   * @param other The other TLink.
   * @return True if the same, false otherwise.
   */
  public boolean compareToTLink(TLink other) {
    // Exactly the same.
    if( this.relation == other.relation && this.id1.equals(other.id1) && this.id2.equals(other.id2) )
      return true;
    // The event IDs might be swapped, so check for the same inverted relation.
    else if( this.id1.equals(other.id2) && this.id2.equals(other.id1) ) {
      if( TLink.invertRelation(this.relation) == other.relation )
        return true;
    }
    return false;
  }
  
  /**
   * True if the two tlinks contain the same events, but different relations.
   */
  public boolean conflictsWith(TLink other) {
    if( this.relation != other.relation && this.id1.equals(other.id1) && this.id2.equals(other.id2) )
      return true;
    // The event IDs might be swapped, so check for different inverted relations.
    else if( this.id1.equals(other.id2) && this.id2.equals(other.id1) ) {
      if( TLink.invertRelation(relation) != other.relation )
        return true;
    }
    return false;
  }
  
  /**
   * True if the two TLinks link the same two events, ignoring relation type.
   * @param other The other TLink.
   * @return True if the same events, false otherwise.
   */
  public boolean coversSamePair(TLink other) {
  	if( this.id1.equals(other.id1) && this.id2.equals(other.id2) )
  		return true;
  	if( this.id2.equals(other.id1) && this.id1.equals(other.id2) )
  		return true;
  	return false;
  }
  
  /**
   * Return this link's relation based on the alphanumeric order of the two event IDs.
   * This allows us to compare two tlinks over the same events but with possibly different guessed labels.
   * @return
   */
  public Type getOrderedRelation() {
  	if( id1.compareTo(id2) < 0 )
  		return relation;
  	else
  		return invertRelation(relation);
  }

	public static String orderedIdPair(TLink tlink) {
		if( tlink.getId1().compareTo(tlink.getId2()) < 0 )
			return tlink.getId1() + tlink.getId2();
		else
			return tlink.getId2() + tlink.getId1();
	}

	public static TLink clone(TLink link) {
		TLink linkclone = null;
		if( link instanceof EventEventLink )
			linkclone = new EventEventLink(link.getId1(), link.getId2(), link.getRelation());
		else if( link instanceof EventTimeLink )
			linkclone = new EventTimeLink(link.getId1(), link.getId2(), link.getRelation());
		else if( link instanceof TimeTimeLink )
			linkclone = new TimeTimeLink(link.getId1(), link.getId2(), link.getRelation());
		else
			linkclone = new TLink(link.getId1(), link.getId2(), link.getRelation());
		
		linkclone.setOrigin(link.getOrigin());
		linkclone.setRelationConfidence(link.getRelationConfidence());
		linkclone.setDocument(link.getDocument());
		linkclone.setClosure(link.isFromClosure());

		return linkclone;
	}
}
