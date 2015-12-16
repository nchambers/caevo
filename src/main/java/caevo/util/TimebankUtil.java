package caevo.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node; // yes?
import org.w3c.dom.NodeList; // yes?
import org.w3c.dom.Text; // yes?
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import caevo.BethardAnnotation;
import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.Timex;
import caevo.tlink.EventEventLink;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;

public class TimebankUtil {
  
	/**
	 * Remove any tlinks from the list whose confidence is below the given minimum.
	 * @param links A list of TLinks.
	 * @param minProb The probability cutoff.
	 */
	public static void trimLowProbability(List<TLink> links, double minProb) {
		List<TLink> removal = new ArrayList<TLink>();
		for( TLink link : links )
			if( link.getRelationConfidence() < minProb )
				removal.add(link);
		
		for( TLink link : removal ) links.remove(link);
	}

  public static boolean isDayOfWeek(String str) {
    str = str.toLowerCase();
    if( str.equals("sunday") || str.equals("monday") || str.equals("tuesday") ||
        str.equals("wednesday") || str.equals("thursday") || str.equals("friday") || str.equals("saturday") )
      return true;
    else
      return false;
  }
  
  /**
   * @return True if event1 is before event2 in the text.
   */
  public static boolean isBeforeInText(TextEvent event1, TextEvent event2) {
    // Same sentence
    if( event1.getSid() == event2.getSid() ) {
      if( event1.getIndex() < event2.getIndex() )
        return true;
      else
        return false;
    }
    // Different sentence
    else {
      if( event1.getSid() < event2.getSid() )
        return true;
      else
        return false;
    }    
  }
  
  public static boolean isBeforeInText(TextEvent event1, Timex timex) {
    // Same sentence
    if( event1.getSid() == timex.getSid() ) {
      if( event1.getIndex() < timex.getTokenOffset() )
        return true;
      else
        return false;
    }
    // Different sentence
    else {
      if( event1.getSid() < timex.getSid() )
        return true;
      else
        return false;
    }    
  }
  
  public static boolean isIntraSentence(TextEvent e1, TextEvent e2) {
    if( e1 == null || e2 == null ) return false;
    
    return e1.getSid() == e2.getSid();
  }

  public static boolean isNeighborSentence(TextEvent e1, TextEvent e2) {
    if( e1 == null || e2 == null ) return false;
    
    return (Math.abs(e1.getSid() - e2.getSid()) == 1);
  }

  public static boolean isEventDCTLink(TLink link, List<Timex> dcts) {
    boolean isdctlink = false;
    
    if( link instanceof EventTimeLink ) {
      String tid = link.getId1();
      if( tid.startsWith("e") ) tid = link.getId2();
      
      for( Timex dct : dcts )
        if( dct.getTid().equalsIgnoreCase(tid) )
          isdctlink = true;
    }
    
    return isdctlink;
  }

  /**
   * Read an XML Document from a path
   * @return Document representation of the xml 
   */
  public static Document getXMLDoc(String filename) {
    try {
      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      Document doc = docBuilder.parse(new File(filename));

      // normalize text representation
      doc.getDocumentElement().normalize();
      //            System.out.println("Root element: " + doc.getDocumentElement().getNodeName());

      return doc;

    } catch (SAXParseException err) {
      System.out.println ("** Parsing error" + ", line " 
          + err.getLineNumber () + ", uri " + err.getSystemId ());
      System.out.println(" " + err.getMessage ());

    } catch (SAXException e) {
      Exception x = e.getException ();
      ((x == null) ? e : x).printStackTrace ();

    } catch (Throwable t) {
      t.printStackTrace ();
    }
    return null;
  }
  
  /**
   * Add Bethard's tlink annotations into the full TimeBank corpus.
   * The given infoFile receives the new tlinks destructively.
   * @param path The path to Bethard's single file containing all new tlinks.
   * @param infoDocs The current TimeBank info file.
   */
  public static void mergeBethard(String path, SieveDocuments infoDocs) {
    BethardAnnotation bethard = new BethardAnnotation(path);
    for( SieveDocument doc : infoDocs.getDocuments() ) {
      List<TLink> newlinks = new ArrayList<TLink>();
      List<TLink> bethardLinks = bethard.getTLinks(doc.getDocname());
      List<TLink> tbLinks = doc.getTlinks();
      // Create a reverse EIID to EID lookup table.
      Map<String,String> eiidToID = eiidToID(doc.getEvents());
      
      // Bethard only annotated the WSJ docs, some TimeBank docs will be null.
      if( bethardLinks != null ) {
        for( TLink bethardLink : bethardLinks ) {
          // Convert from the instance ID to the event ID.
          String event1 = eiidToID.get(bethardLink.getId1());
          String event2 = eiidToID.get(bethardLink.getId2());
          if( event1 == null )
            System.out.println("ERROR: Bethard event1 id unknown " + bethardLink.getId1());
          else if( event2 == null )
            System.out.println("ERROR: Bethard event2 id unknown " + bethardLink.getId2());

          else {
            // Create a new tlink with the event IDs
            TLink newlink = new EventEventLink(bethardLink.getId1(), bethardLink.getId2(), bethardLink.getRelation());
            newlink.setOrigin("bethard");

            // Now make sure it is a new tlink.
            boolean duplicate = false;
            for( TLink current : tbLinks ) {
              if( current.getRelation() != TLink.Type.NONE ) {
                // Conflicts should be skipped.
                if( newlink.conflictsWith(current) ) {
                  System.out.println("CONFLICTS Bethard: " + newlink + " with previous " + current);
                  duplicate = true;
                }
                // Exact duplicates should be skipped.
                if( newlink.compareToTLink(current) ) {
                  System.out.println("Duplicate Bethard: " + newlink + " with timebank's " + current);
                  duplicate = true;
                }
              }
            }

            // Now add the link.
            if( !duplicate ) {
              newlinks.add(newlink);
              System.out.println("Adding Bethard: " + newlink);
            }
          }
        }

        doc.addTlinks(newlinks);
      }
    }
  }

  /**
   * Create a reverse EIID to EID lookup table.
   */
  public static Map<String,String> eiidToID(Collection<TextEvent> events) {
    Map<String,String> eiidToID = new HashMap<String,String>();
    for( TextEvent event : events ) {
      for( String eiid : event.getAllEiids() )
        eiidToID.put(eiid, event.getId());
    }
    return eiidToID;
  }

  /**
   * @returns The text of all text node leafs appended together
   */
  public static String stringFromElement(Node node) {
    if( node instanceof Text ) {
      return ((Text)node).getData().trim();
    } else {
      String str = "";
      NodeList list = node.getChildNodes();
      for( int i = 0; i < list.getLength(); i++ ) {
        if( i == 0 ) str = stringFromElement(list.item(i));
        else str += " " + stringFromElement(list.item(i));
      }
      return str;
    }
  }
  /**
   * There are syntactic contexts that aren't considered "future tense"
   * but where intuitively, the event in question is located in the future.
   * For example, in "X would Y", where X is the agent of the event Y,
   * Y most likely has not yet happened (how to interpret the likelihood of Y ever hapening at all
   * is a different concern - the point is that if it does happen it will be in the future).
   * 
   * 
   * @returns the tense of event, where tense is less strictly defined.
   */
  
  // Basically if a word governs a modal word then it is considered as being in the future tense.
  public static TextEvent.Tense pseudoTense(SieveSentence sent, List<TypedDependency> tds, TextEvent event) {
  	int eventIndex = event.getIndex();
  	for (TypedDependency td : tds) {
  		TreeGraphNode dep = td.dep();
  		if (eventIndex == td.gov().index() && 
  				isModalWord(dep.toString("value").toLowerCase()) &&
  				td.reln().toString().equals("aux")) {
  			return TextEvent.Tense.FUTURE;
  		}
  			
  		}
  		return event.getTense();
  	}
  
  // This method was meant to generalize the above, but the intuition is not quite right.
  // a modal verb is governed by the verb it modifies, but a verb that governs that verb is not int he modal context.
  // Need to hash this out still.
  public static TextEvent.Tense pseudoTense2(SieveSentence sent, List<TypedDependency> tds, TextEvent event) {
  	int eventIndex = event.getIndex();
  	List<CoreLabel> tokens = sent.tokens();
  	for (int t = 0; t < tokens.size(); t++) {
  		String tokenText = tokens.get(t).originalText();
  		if (isModalWord(tokenText)) {
  			String dp = TreeOperator.directPath(eventIndex, t+1, tds); // add 1 because of stanford indexing starting at 1
  			if (dp != null){
  				System.out.println("dp: " + dp);
  				System.out.println(sent.sentence());
  				System.out.println(event.getString());
  				return TextEvent.Tense.FUTURE;
  			}
  		}
  	}
  	return event.getTense();
  }
  
  public static boolean isModalWord(String word) {
  	if (word.toLowerCase().equals("would") || word.toLowerCase().equals("could") ||
  			word.toLowerCase().equals("might") || word.toLowerCase().equals("may") ||
  			word.toLowerCase().equals("should") || word.toLowerCase().equals("'d") ||
  			word.toLowerCase().equals("will")){
  		return true;
  	}
  	else return false;
  }
  
  /**
   * return true if two timexes have the same value, or if both arguments are null
   * @param t1
   * @param t2
   * @return
   */
  public static boolean compareTimexesByValue(Timex t1, Timex t2) {
  	if (t1 == null && t2 == null || t1.getValue().equals(t2.getValue())){
			return true;
		}
		else return false;
  }
}
