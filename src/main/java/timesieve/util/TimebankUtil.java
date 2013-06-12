package timesieve.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timesieve.tlink.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node; // yes?
import org.w3c.dom.Text; // yes?
import org.w3c.dom.NodeList; // yes?
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import timesieve.*;

public class TimebankUtil {
  
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
    if( event1.sid() == event2.sid() ) {
      if( event1.index() < event2.index() )
        return true;
      else
        return false;
    }
    // Different sentence
    else {
      if( event1.sid() < event2.sid() )
        return true;
      else
        return false;
    }    
  }
  
  public static boolean isBeforeInText(TextEvent event1, Timex timex) {
    // Same sentence
    if( event1.sid() == timex.sid() ) {
      if( event1.index() < timex.offset() )
        return true;
      else
        return false;
    }
    // Different sentence
    else {
      if( event1.sid() < timex.sid() )
        return true;
      else
        return false;
    }    
  }
  
  public static boolean isIntraSentence(TextEvent e1, TextEvent e2) {
    if( e1 == null || e2 == null ) return false;
    
    return e1.sid() == e2.sid();
  }

  public static boolean isNeighborSentence(TextEvent e1, TextEvent e2) {
    if( e1 == null || e2 == null ) return false;
    
    return (Math.abs(e1.sid() - e2.sid()) == 1);
  }

  public static boolean isEventDCTLink(TLink link, List<Timex> dcts) {
    boolean isdctlink = false;
    
    if( link instanceof EventTimeLink ) {
      String tid = link.event1();
      if( tid.startsWith("e") ) tid = link.event2();
      
      for( Timex dct : dcts )
        if( dct.tid().equalsIgnoreCase(tid) )
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
   * @param infoFile The current TimeBank info file.
   */
  public static void mergeBethard(String path, InfoFile infoFile) {
    BethardAnnotation bethard = new BethardAnnotation(path);
    for( String doc : infoFile.getFiles() ) {
      List<TLink> newlinks = new ArrayList<TLink>();
      List<TLink> bethardLinks = bethard.getTLinks(doc);
      List<TLink> tbLinks = infoFile.getTlinks(doc);
      // Create a reverse EIID to EID lookup table.
      Map<String,String> eiidToID = eiidToID(infoFile.getEvents(doc));
      
      // Bethard only annotated the WSJ docs, some TimeBank docs will be null.
      if( bethardLinks != null ) {
        for( TLink bethardLink : bethardLinks ) {
          // Convert from the instance ID to the event ID.
          String event1 = eiidToID.get(bethardLink.event1());
          String event2 = eiidToID.get(bethardLink.event2());
          if( event1 == null )
            System.out.println("ERROR: Bethard event1 id unknown " + bethardLink.event1());
          else if( event2 == null )
            System.out.println("ERROR: Bethard event2 id unknown " + bethardLink.event2());

          else {
            // Create a new tlink with the event IDs
            TLink newlink = new EventEventLink(bethardLink.event1(), bethardLink.event2(), bethardLink.relation());
            newlink.setOrigin("bethard");

            // Now make sure it is a new tlink.
            boolean duplicate = false;
            for( TLink current : tbLinks ) {
              if( current.relation() != TLink.TYPE.NONE ) {
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

        infoFile.addTlinks(doc, newlinks);
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
        eiidToID.put(eiid, event.id());
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
  
}
