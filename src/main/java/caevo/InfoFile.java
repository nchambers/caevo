package caevo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import caevo.tlink.EventEventLink;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.tlink.TimeTimeLink;
import caevo.util.TimebankUtil;
import caevo.util.TreeOperator;
import caevo.util.Util;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * Timebank Corpus file that stores in an easier to read format, all the sentences and docs
 * from the TimeBank corpus, as well as event information on a per-sentence basis.
 * @author chambers
 */
public class InfoFile {
  private String INFO_NS = "http://chambers.com/corpusinfo";
  public static String SENT_ELEM = "sentence";
  public static String TOKENS_ELEM = "tokens";
  public static String TOKEN_ELEM = "t";
  public static String PARSE_ELEM = "parse";
  public static String DEPS_ELEM = "deps";
  public static String ENTRY_ELEM = "entry";
  public static String EVENTS_ELEM = "events";
  public static String TIMEXES_ELEM = "timexes";
  public static String SID_ELEM = "sid";
  public static String FILE_ELEM = "file";
  public static String FILENAME_ELEM = "name";
  private Document jdomDoc;

  public InfoFile() {
    Namespace ns = Namespace.getNamespace(INFO_NS);
    jdomDoc = new Document();
    Element root = new Element("root",ns);
    jdomDoc.setRootElement(root);
  }

  public InfoFile(String filepath) {
//    Namespace ns = Namespace.getNamespace(INFO_NS);
//    jdomDoc = new Document();
//    Element root = new Element("root",ns);
//    jdomDoc.setRootElement(root);
    readFromPath(filepath);
  }

  private String generateCoreLabelString(CoreLabel cl) {
    StringBuffer buf = new StringBuffer();
    buf.append('"');
    buf.append(cl.getString(CoreAnnotations.BeforeAnnotation.class));
    buf.append("\" \"");
    buf.append(cl.getString(CoreAnnotations.OriginalTextAnnotation.class));
    buf.append("\" \"");
    buf.append(cl.getString(CoreAnnotations.AfterAnnotation.class));
    buf.append("\"");
    return buf.toString();
  }
  
  /**
   * Adds a sentence to the file
   */
  public void addSentence(String file, int sid, String text, String parse, String deps, List<TextEvent> events, List<Timex> timexes) {
    addSentence(file, sid, text, null, parse, deps, events, timexes);
  }
  
  /**
   * Adds a sentence to the file
   * @param tokens These are the tokens in the sentence, listed one per line. Each line has three parts: (1) characters before, (2) original token, (3) characters after.
   */
  public void addSentence(String file, int sid, String text, List<CoreLabel> tokens, String parse, String deps, List<TextEvent> events, List<Timex> timexes) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element root = jdomDoc.getRootElement();

    Element entry = new Element(ENTRY_ELEM,ns);
    entry.setAttribute(SID_ELEM,String.valueOf(sid));
    entry.setAttribute(FILE_ELEM,file);
    Element sentElem = new Element(SENT_ELEM,ns);
    sentElem.addContent(text);
    Element tokensElem = new Element(TOKENS_ELEM,ns);
    if( tokens != null ) {
      for( CoreLabel token : tokens ) {
        Element tokenElem = new Element(TOKEN_ELEM,ns);
        tokenElem.addContent(generateCoreLabelString(token));
        tokensElem.addContent(tokenElem);
      }
//      tokensElem.addContent(tokens);
    }
    Element parseElem = new Element(PARSE_ELEM,ns);
    parseElem.setText(parse);

    // Add the events vector
    Element eventsElem = new Element(EVENTS_ELEM,ns);
    if( events != null )
      for( TextEvent te : events )
        eventsElem.addContent(te.toElement(ns));

    // Add the timex vector
    Element timexesElem = new Element(TIMEXES_ELEM,ns);
    if( timexes != null )
      for( Timex te : timexes )
        timexesElem.addContent(te.toElement(ns));

    // Add the typed dependencies
    Element depsElem = new Element(DEPS_ELEM,ns);
    depsElem.setText(deps);

    entry.addContent(sentElem);
    entry.addContent(tokensElem);
    entry.addContent(parseElem);
    entry.addContent(depsElem);
    entry.addContent(eventsElem);
    entry.addContent(timexesElem);

    Element mainfile = getFileElement(file);

    // Create a new file Element 
    if( mainfile == null ) {
      mainfile = new Element(FILE_ELEM,ns);
      mainfile.setAttribute(FILENAME_ELEM,file);
      root.addContent(mainfile);
    }

    mainfile.addContent(entry);
  }

  /**
   * Create a new <events> element in a single sentence. Fill it with the given events.
   */
  public void addEvents(String docname, int sid, List<TextEvent> events) {
    docname = stripFile(docname);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(docname);

    // Check each sentence entry
    List<Element> children = mainfile.getChildren(ENTRY_ELEM,ns);
    Element targetSentenceElement = children.get(sid);
      
    // Add the events vector
    if( events != null ) {
      Element eventsElem = targetSentenceElement.getChild(EVENTS_ELEM,ns);
      if( eventsElem == null ) {
        eventsElem = new Element(EVENTS_ELEM,ns);
        targetSentenceElement.addContent(eventsElem);
      }      
      for( TextEvent te : events )
        eventsElem.addContent(te.toElement(ns));
    }
  }
  
  /**
   * Add the given timexes to a single sentence. 
   * Create a new <timex> element if there is not already one present.
   */
  public void addTimexes(String docname, int sid, List<Timex> timexes) {
    docname = stripFile(docname);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(docname);

    // Check each sentence entry
    List<Element> children = mainfile.getChildren(ENTRY_ELEM,ns);
    Element targetSentenceElement = children.get(sid);
      
    // Add the timex vector
    if( timexes != null ) {
      Element timexesElem = targetSentenceElement.getChild(TIMEXES_ELEM,ns);
      if( timexesElem == null ) {
        timexesElem = new Element(TIMEXES_ELEM,ns);
        targetSentenceElement.addContent(timexesElem);
      }      
      for( Timex timex : timexes )
        timexesElem.addContent(timex.toElement(ns));
    }
  }
  
  /**
   * Adds a list of tlinks to the XML file
   * @param tlinks Vector of TLink objects
   */
  public void addTlinks(String file, List<TLink> tlinks) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element root = jdomDoc.getRootElement();

    // Find the file's Element
    Element mainfile = getFileElement(file);

    // Create a new file Element if we need to
    if( mainfile == null ) {
      System.out.println("InfoFile addTlinks new file = " + mainfile);
      mainfile = new Element(FILE_ELEM,ns);
      mainfile.setAttribute(FILENAME_ELEM,file);
      root.addContent(mainfile);
    }

    // Check for duplicates as we add
    for( TLink tlink : tlinks )
      mainfile.addContent(tlink.toElement(ns));
  }

  /**
   * Deletes all the TLinks from the XML file
   */
  public void deleteTlinks(String file) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element root = jdomDoc.getRootElement();

    // Find the file's Element
    Element mainfile = getFileElement(file);

    // If we found the element
    if( mainfile != null ) {
      List children = mainfile.getChildren(TLink.TLINK_ELEM,ns);
      int numdel = children.size();
      for( int i = 0; i < numdel; i++ ) {
        mainfile.removeChild(TLink.TLINK_ELEM,ns);
      }
    }
  }


  private TLink tlinkFromElement(Element el) {
    if( el.getAttributeValue(TLink.TLINK_TYPE_ATT).equals(TLink.EVENT_EVENT_TYPE_VALUE) )
      return new EventEventLink(el);
    else if( el.getAttributeValue(TLink.TLINK_TYPE_ATT).equals(TLink.EVENT_TIME_TYPE_VALUE) )
      return new EventTimeLink(el);
    else if( el.getAttributeValue(TLink.TLINK_TYPE_ATT).equals(TLink.TIME_TIME_TYPE_VALUE) )
      return new TimeTimeLink(el);
    System.err.println("ERROR: tlink element doesn't have a tlink type attribute");
    return null;
  }

  /**
   * @return A List of all TLink objects (event-event and event-time)
   */
  public List<TLink> getTlinks(String file, boolean noclosures) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(file);
    List<TLink> tlinks = new ArrayList<TLink>();

    if( mainfile != null ) {
      List children = mainfile.getChildren(TLink.TLINK_ELEM,ns);
      for( Object obj : children ) {
        if( noclosures ) { // don't add closed links
          TLink link = tlinkFromElement((Element)obj);
          if( !link.isFromClosure() ) tlinks.add(link);
        }
        // add all links
        else tlinks.add(tlinkFromElement((Element)obj));
      }
    }
    return tlinks;
  }
  public List<TLink> getTlinks(String file) {
    return getTlinks(file, false);
  }

  /**
   * @return A list of lists. 
   *         This is a list of sentences, each sentence is a list of CoreLabels with character information.
   */
  public List<List<CoreLabel>> getTokens(String file) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(file);

    if( mainfile == null ) {
      System.err.println("Can't find file " + file + " in document.");
      return null;
    }

    List<List<CoreLabel>> tokens = new ArrayList<List<CoreLabel>>();
    
    // Get each sentence entry.
    List<Element> children = mainfile.getChildren(ENTRY_ELEM,ns);
    for( Element obj : children ) {
      // Get the <tokens> element.
      Element ev = obj.getChild(TOKENS_ELEM,ns);
      String text = ev.getText();
      // Map the string to CoreLabel objects.
      tokens.add(stringToCoreLabels(text));
    }
    
    return tokens;
  }
  
  /**
   * Takes the strings from a .info file and puts them into CoreLabel objects that preserve the
   * original characters. Each line in the string is one label, three parts per line.
   */
  public static CoreLabel stringToCoreLabel(String text) {
//    System.out.println("stringToCoreLabel text: " + text);
    text = text.substring(1,text.length()-1);
    String[] triple = text.split("\" \"");
    CoreLabel label = new CoreLabel();
    label.set(CoreAnnotations.BeforeAnnotation.class, triple[0]);
    label.set(CoreAnnotations.OriginalTextAnnotation.class, triple[1]);
    label.set(CoreAnnotations.AfterAnnotation.class, (triple.length > 2 ? triple[2] : ""));
//    System.out.println("\t" + label);

    return label;
  }
  
  /**
   * Takes the strings from a .info file and puts them into CoreLabel objects that preserve the
   * original characters. Each line in the string is one label, three parts per line.
   */
  public static List<CoreLabel> stringToCoreLabels(String text) {
    List<CoreLabel> localtokens = new ArrayList<CoreLabel>();
    String[] parts = text.split("\n");
    for( String part : parts )
      localtokens.add(stringToCoreLabel(part));
    return localtokens;
  }
  
  /**
   * @return A List of TLink objects Event-Event, no Event-Time links
   */
  public List<TLink> getTlinksOfType(String file, String type) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(file);

    if( mainfile == null ) {
      System.err.println("Can't find file " + file + " in document.");
      return null;
    }
    List children = mainfile.getChildren(TLink.TLINK_ELEM,ns);
    List<TLink> tlinks = new ArrayList<TLink>();
    for( Object obj : children ) {
      Element el = (Element)obj;
      // return TLinks based on their type (e.g. event-event)
      //	    if( el.getAttributeValue(EventTimeLink.TIME_TLINK_ATT) == null )
      if( el.getAttributeValue(TLink.TLINK_TYPE_ATT).equals(type) )
        tlinks.add(new TLink(el));
    }
    return tlinks;
  }

  /**
   * @return A List of Lists of all Timex objects. This does not 
   *         return the document creation time!
   * 
   */
  public List<List<Timex>> getTimexesBySentence(String file) {
    List<List<Timex>> timexes = new ArrayList<List<Timex>>();
    
    // Timexes in each sentence.
    List<Sentence> sentences = getSentences(file);
    for( Sentence sent : sentences )
      timexes.add(sent.timexes());
    
    return timexes;
  }
  
  /**
   * @return A List of all Timex objects, including the document creation time.
   */
  public List<Timex> getTimexes(String file) {
    List<Timex> timexes = new ArrayList<Timex>();
    
    // Timexes in each sentence.
    List<Sentence> sentences = getSentences(file);
    for( Sentence sent : sentences )
      timexes.addAll(sent.timexes());
    
    // Document time stamp.
    List<Timex> dcts = getDocstamp(file);
    timexes.addAll(dcts);
    
    return timexes;
  }

  /**
   * @return A List of all Event objects in one document (file parameter)
   */
  public List<List<TextEvent>> getEventsBySentence(String file) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(file);

    // Check each sentence entry
    List<Element> children = mainfile.getChildren(ENTRY_ELEM,ns);
    List<List<TextEvent>> allEvents = new ArrayList<List<TextEvent>>();
    for( Element sentenceObj : children ) {
      String sid = sentenceObj.getAttributeValue(SID_ELEM);
      Element ev = sentenceObj.getChild(EVENTS_ELEM,ns);
      List<Element> localEventObjs = ev.getChildren(TextEvent.NAME_ELEM,ns);
      List<TextEvent> localEvents = new ArrayList<TextEvent>();
      for( Element child : localEventObjs )
        localEvents.add(new TextEvent(sid, child));
      allEvents.add(localEvents);
    }

    return allEvents;
  }
  
  /**
   * @return A List of all Event objects in one document (file parameter)
   */
  public List<TextEvent> getEvents(String file) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(file);

    // Check each sentence entry
    List<Element> children = mainfile.getChildren(ENTRY_ELEM,ns);
    List<TextEvent> events = new ArrayList<TextEvent>();
    for( Element obj : children ) {
      String sid = obj.getAttributeValue(SID_ELEM);
      Element ev = obj.getChild(EVENTS_ELEM,ns);
      List<Element> localevents = ev.getChildren(TextEvent.NAME_ELEM,ns);
      for( Element child : localevents )
        events.add(new TextEvent(sid, child));
    }

    return events;
  }


  /**
   * @param The filename of the document
   * @return A Timex object for the document creation time
   */
  public List<Timex> getDocstamp(String file) {
    List<Timex> stamps = new ArrayList<Timex>();
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(file);

    // Get the document time stamps (sometimes more than one, stupid Timebank)
    List<Element> elements = mainfile.getChildren(Timex.TIMEX_ELEM,ns);
    //    Element docstamp = mainfile.getChild(Timex.TIMEX_ELEM,ns);
    //    if( docstamp != null ) return new Timex(docstamp);
    for( Element stamp : elements )
      stamps.add(new Timex(stamp));

    if( stamps.size() == 0 ) System.out.println("WARNING: no docstamp in " + file);

    return stamps;
  }


  /**
   * Adds a list of TIMEX tags to the XML file
   * @param timexes Collection of Timex objects
   */
  public void addTimexes(String file, Collection<Timex> timexes) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element root = jdomDoc.getRootElement();

    // Find the file's Element
    Element mainfile = getFileElement(file);

    // Create a new file Element if we need to
    if( mainfile == null ) {
      mainfile = new Element(FILE_ELEM,ns);
      mainfile.setAttribute(FILENAME_ELEM,file);
      root.addContent(mainfile);
    }

    for( Timex timex : timexes )
      mainfile.addContent(timex.toElement(ns));
  }

  /**
   * @desc Adds the document stamp to the file, as a Timex
   */
  public void addCreationTime(String file, Timex timex) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element root = jdomDoc.getRootElement();

    // Find the file's Element
    Element mainfile = getFileElement(file);

    // Create a new file Element if we need to
    if( mainfile == null ) {
      mainfile = new Element(FILE_ELEM,ns);
      mainfile.setAttribute(FILENAME_ELEM,file);
      root.addContent(mainfile);
    }

    mainfile.addContent(timex.toElement(ns));
  }

  public void removeTLinks(String file) {
    file = stripFile(file);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element mainfile = getFileElement(file);
    if( mainfile != null )
      mainfile.removeChildren(TLink.TLINK_ELEM, ns);
  }

  /**
   * @desc Retrieve all TIMEXes from a document, and compare them for BEFORE relationships
   *       strictly based on their time values.  
   * @return The new BEFORE TLinks.
   */
  public Vector<TLink> computeTimeTimeLinks(String filename) {
    //    Timex docstamp = getDocstamp(filename);
    Timex docstamp = null;
    List<Timex> stamps = getDocstamp(filename);
    if( stamps.size() > 0 ) docstamp = stamps.get(0); // just use the first one...

    List<Timex> timexes = getTimexes(filename);

    if( timexes != null ) {
      Vector<TLink> links = new Vector<TLink>();
      for( int i = 0; i < timexes.size(); i++ ) {
        for( int j = i+1; j < timexes.size(); j++ ) {
          Timex time1 = timexes.get(i);
          Timex time2 = timexes.get(j);
          if( time1 != time2 ) {
            // Reset "present references" to the document stamp
            Timex orig1 = time1;
            Timex orig2 = time2;
            if( time1.getValue().equals("PRESENT_REF") && docstamp != null ) time1 = docstamp;
            if( time2.getValue().equals("PRESENT_REF") && docstamp != null ) time2 = docstamp;

            // Comparison
            if( time1.before(time2) ) links.add(new TimeTimeLink(orig1.getTid(), orig2.getTid(), TLink.Type.BEFORE));
            else if( time2.before(time1) ) links.add(new TimeTimeLink(orig2.getTid(), orig1.getTid(), TLink.Type.BEFORE));
          }
        }
      }
      return links;
    }
    return null;
  }


  /**
   * @return A Vector of String file names
   */
  public List<String> getFiles() {
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element root = jdomDoc.getRootElement();
    List children = root.getChildren(FILE_ELEM,ns);
    List<String> files = new ArrayList<String>();
    for( Object obj : children ) {
      files.add(((Element)obj).getAttributeValue(FILENAME_ELEM));
    }
    return files;
  }

  /**
   * @return A file Element
   */
  private Element getFileElement(String filename) {
    filename = stripFile(filename);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element root = jdomDoc.getRootElement();
    List children = root.getChildren(FILE_ELEM,ns);
    for( Object obj : children ) {
      String name = ((Element)obj).getAttributeValue(FILENAME_ELEM);
      if( name != null && name.equals(filename) )
        return (Element)obj;
    }
    return null;
  }

  /**
   * @return A Vector of Sentence objects
   */
  public List<Sentence> getSentences(String filename) {
    filename = stripFile(filename);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element fileElement = getFileElement(filename);
    List children = fileElement.getChildren(ENTRY_ELEM,ns);
    List<Sentence> sents = new ArrayList<Sentence>();
    for( Object obj : children ) {
      sents.add(new Sentence((Element)obj,ns));
    }
    return sents;	
  }

  /**
   * @return A List of Strings that are parse trees
   */
  public List<String> getParses(String filename) {
    filename = stripFile(filename);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element fileElement = getFileElement(filename);
    List children = fileElement.getChildren(ENTRY_ELEM,ns);
    List<String> parses = new ArrayList<String>();
    for( Object obj : children ) {
      Element parse = ((Element)obj).getChild(PARSE_ELEM,ns);
      parses.add(parse.getText().toString());
    }
    return parses;
  }

  /**
   * @return A List of Strings, one string per sentence, representing dependencies.
   */
  public List<String> getDependencies(String filename) {
    filename = stripFile(filename);
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element fileElement = getFileElement(filename);
    List children = fileElement.getChildren(ENTRY_ELEM,ns);
    List<String> deps = new ArrayList<String>();
    for( Object obj : children ) {
      Element depsObj = ((Element)obj).getChild(DEPS_ELEM,ns);
      deps.add(depsObj.getText().toString());
    }
    return deps;
  }
  
  /**
   * Takes a single sentence's string from the .info file. It is multiple lines, each line is
   * a typed dependency. Convert this to a list of dependencies.
   * @param strdeps A single string with newlines, one dependency per line.
   * @return A single sentence's dependencies.
   */
  public static List<TypedDependency> stringToDependencies(String strdeps) {
    List<TypedDependency> deps = new ArrayList<TypedDependency>();
    String[] lines = strdeps.split("\n");
    for( String line : lines ) {
      line = line.trim();
      if( line.length() > 0 ) {
        TypedDependency dep = TreeOperator.stringParensToDependency(line);
        deps.add(dep);
      }
    }
    return deps;
  }
  
  public static List<List<TypedDependency>> stringsToDependencies(List<String> strdeps) {
    List<List<TypedDependency>> alldeps = new ArrayList<List<TypedDependency>>();
    for( String deps : strdeps )
      alldeps.add(stringToDependencies(deps));
    return alldeps;
  }
  
  public boolean setEventAttribute(String filename, String eventID, String attr, String value) {
    Element el = getEventElement(filename, eventID);
//    System.out.println("setEventAttr el=" + el.toString());
    if( el != null ) {
//      System.out.println("Setting attr=" + attr + " and value=" + value);
      el.setAttribute(attr, value);
      return true;
    }
    else
      return false;
  }

  /**
   * @return A file Element for an event in a sentence.
   */
  private Element getEventElement(String filename, String eventID) {
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Element fileobj = getFileElement(filename);

    // Check each sentence entry.
    List<Element> sentenceElements = fileobj.getChildren(ENTRY_ELEM, ns);
    for( Element obj : sentenceElements ) {
      Element ev = obj.getChild(EVENTS_ELEM,ns);
      // Check each event in this sentence.
      List<Element> localevents = ev.getChildren(TextEvent.NAME_ELEM, ns);
      for( Element child : localevents )
        if( child.getAttributeValue(TextEvent.ID_ELEM).equalsIgnoreCase(eventID) )
          return child;
    }
    
    return null;
  }

  /**
   * Create a list of strings, each string is one TLink: <TLINK ... />
   * @param docname The document from which you want all tlinks.
   * @return A list of tlink strings.
   */
  public List<String> createTLinkStrings(String docname) {
  	List<String> strings = new ArrayList<String>();
  	int counter = 1;
  	
  	List<TLink> tlinks = getTlinks(docname);
  	for( TLink link : tlinks ) {
  		String str = "<TLINK lid=\"l" + counter + "\" relType=\"" + link.getRelation().toString() + "\" ";
  		
  		if( link instanceof EventEventLink ||
  				(link instanceof EventTimeLink && link.getId1().startsWith("e")) )
  			str += "eventInstanceID=\"";
  		else
  			str += "timeID=\"";
  		str += link.getId1() + "\" ";
  		
  		if( link instanceof EventEventLink ||
  				(link instanceof EventTimeLink && link.getId2().startsWith("e")) )
  			str += "relatedToEventInstance=\"";
  		else
  			str += "relatedToTime=\"";
  		str += link.getId2() + "\"";

  		str += " />";
  		
  		strings.add(str);
  		counter++;
  	}
  	
  	return strings;
  }
  
  /**
   * Create a list of strings, each string is one makeinstance: <MAKEINSTANCE eventID="3" ... />
   * @param docname The document from which you want to extract all makeinstances.
   * @return A list of makeinstance strings.
   */
  public List<String> createMakeInstanceStrings(String docname) {
    List<String> strings = new ArrayList<String>();

    List<Sentence> sentences = getSentences(docname);
    for( Sentence sent : sentences ) {
      for( TextEvent event : sent.events() ) {
        for( String eiid : event.getAllEiids() ) {
          strings.add("<MAKEINSTANCE eventID=\"" + event.getId() + 
              "\" eiid=\"" + eiid + 
              "\" tense=\"" + event.getTense() + 
              "\" aspect=\"" + event.getAspect() + 
              ( event.getPolarity() != null ? ("\" polarity=\"" + event.getPolarity()) : "") + 
          "\" />");
        }
      }
    }

    return strings;
  }
  
  public String markupOriginalText(String docname) {
  	return markupOriginalText(docname, TextEvent.NAME_ELEM, "eid", false, true, true, true);
  }
  
  /**
   * This function does not output a formal XML document, but instead just the raw text 
   * of a document annotated with XML markup around events and timexes.
   * @param docname The one file in the InfoFile that you want to stringify with XML markup.
   * @param eventElemName You can specify what you want the event's XML element to be (e.g., event or target)
   * @param idAttributeString You can specify what you want the event's XML attribute to be for its ID (e.g., id or eid)
   * @return A String which is the raw text with added XML markup around events and timexes.
   */
  public String markupOriginalText(String docname, String eventElemName, String idAttributeString, boolean numericIDOnly,
  		boolean showTense, boolean showAspect, boolean showClass) {
  	StringBuffer buf = new StringBuffer();
  	boolean firstToken = true;
  	
  	List<Sentence> sentences = getSentences(docname);
  	System.out.println("markup " + docname + " containing " + sentences.size() + " sentences.");
  	for( Sentence sent : sentences ) {
  		List<CoreLabel> tokens = sent.tokens();
  		// Grab the events.
  		Map<Integer,TextEvent> indexToEvents = new HashMap<Integer,TextEvent>();
  		for( TextEvent event : sent.events() )
  			indexToEvents.put(event.getIndex(), event);
  		// Grab the timexes.
  		Map<Integer,Timex> indexToTimexes = new HashMap<Integer,Timex>();
  		for( Timex timex : sent.timexes() )
  			indexToTimexes.put(timex.getTokenOffset(), timex);  		
  		Set<Integer> endTimexes = new HashSet<Integer>();
  		
      int ii = 1;
  		for( CoreLabel token : tokens ) {
  			if( firstToken ) {
  				buf.append(token.getString(CoreAnnotations.BeforeAnnotation.class));
  				firstToken = false;
  			}
  			boolean endevent = false;
  			
  			// If this token starts a TIMEX. (and we're not in an event tag already)
  			if( indexToTimexes.containsKey(ii) ) {
  				Timex timex = indexToTimexes.get(ii);
//  				System.out.println("timex: " + timex);
  				buf.append(timex.toXMLString());
  				endTimexes.add(ii+timex.getTokenLength()-1);
  			}
  			
  			// If this token is marked as an event.
  			if( indexToEvents.containsKey(ii) ) {
  				TextEvent event = indexToEvents.get(ii);
  				String eventid = event.getId();
  				if( numericIDOnly && eventid.startsWith("e") ) eventid = eventid.substring(1);
  				buf.append("<" + eventElemName);
  				buf.append(" " + idAttributeString + "=\"" + eventid + "\"");
  				if( showTense )
  					buf.append(" tense=\"" + event.getTense() + "\"");
  				if( showAspect )
  					buf.append(" aspect=\"" + event.getAspect() + "\"");
  				if( showClass )
  					buf.append(" class=\"" + event.getTheClass() + "\"");
  				buf.append(">");
  				endevent = true;
  			}

  			// Print the token.
  			String str = token.getString(CoreAnnotations.OriginalTextAnnotation.class);
//  			System.out.println("token=" + str + " len=" + str.length());
  			str = str.replaceAll("&", "&amp;");
//  			System.out.println("\tnow=" + str);
  			buf.append(str);
  				
  			if( endevent ) buf.append("</" + eventElemName + ">");
  			if( endTimexes.contains(ii) ) buf.append("</TIMEX3>");
  			
  			buf.append(token.getString(CoreAnnotations.AfterAnnotation.class));
  			ii++;
  		}
  	}
  	return buf.toString();
  }
  
  /**
   * Strip the huge path off and return just the filename
   */
  private String stripFile(String file) {
    int sep = file.lastIndexOf(File.separatorChar);
    if( sep == -1 ) sep = 0;
    else sep++;
    return file.substring(sep);
  }

  public void readFromPath(String path) {
    readFromFile(new File(path));
  }
  
  public void readFromFile(File file) {
    SAXBuilder builder = new SAXBuilder();
    try {
      jdomDoc = builder.build(file);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void writeToFile(File file) {
    try {
      FileOutputStream out = new FileOutputStream(file);
      XMLOutputter op = new XMLOutputter(Format.getPrettyFormat());
      op.output(jdomDoc, out);
      out.flush();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void outputMarkedUp(String dirpath) {
    // Create the directory.
    try {
      System.out.println("Creating directory: " + dirpath);
      File dir = new File(dirpath);
      dir.mkdir();
    } catch( Exception ex ) { ex.printStackTrace(); }
    
    for( String filename : getFiles() ) {
    	System.out.println(filename);
//    	System.out.println(markupOriginalText(filename));
    	
      try {
    	  String outfile = filename;
        // Strip off the ending ".TE3input" if it exists (for TempEval3)
//    	  if( outfile.endsWith(".TE3input") ) outfile = outfile.substring(0, outfile.lastIndexOf(".TE3input")); 
    	  
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(dirpath + File.separator + outfile)));
        
        writer.write("<?xml version=\"1.0\" ?>\n");
        writer.write("<TimeML xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://timeml.org/timeMLdocs/TimeML_1.2.1.xsd\">\n\n");

        writer.write("<DCT>");
        List<Timex> dcts = getDocstamp(filename);
        if( dcts == null || dcts.size() == 0 )
        	System.err.println("ERROR: " + filename + " does not have a DCT to write.");
        else {
        	writer.write(dcts.get(0).toXMLString());
        	writer.write(dcts.get(0).getText());
        	writer.write("</TIMEX3>");
        }
        writer.write("</DCT>\n\n");
        
        // The text.
        writer.write("<TEXT>");
        writer.write(markupOriginalText(filename));
        writer.write("</TEXT>\n\n");

        // Event makeinstance list.
        List<String> makes = createMakeInstanceStrings(filename);
        for( String make : makes ) writer.write(make + "\n");

        // TLinks.
        List<String> tlinks = createTLinkStrings(filename);
        for( String tlink : tlinks ) writer.write(tlink + "\n");
        
        writer.write("\n</TimeML>");
        writer.close();
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
  }

  public void countEventDCTLinks() {
    Counter<String> count = new ClassicCounter<String>();
    for( String docname : getFiles() ) {
      List<Timex> dcts = this.getDocstamp(docname);
      List<TextEvent> allevents = new ArrayList<TextEvent>();
      
      List<Sentence> sentences = getSentences(docname);
      for( Sentence sent : sentences )
        allevents.addAll(sent.events());
      
      for( TLink link : getTlinks(docname) ) {
        if( link instanceof EventTimeLink && TimebankUtil.isEventDCTLink(link, dcts) ) {
          String eventid = link.getId1();
          if( !eventid.startsWith("e") )
            eventid = link.getId2();
          
          for( TextEvent event : allevents )
            if( event.getId().equals(eventid) )
              count.incrementCount(event.getString());
        }
      }
    }
    for( String word : Util.sortCounterKeys(count) ) {
      System.out.println(word + "\t" + count.getCount(word));
    }
  }
  
  public static int factorial(int n) {
    int fact = 1; // this  will be the result
    for (int i = 1; i <= n; i++) {
        fact *= i;
    }
    return fact;
}

  private int numPossiblePairs(int num1) {
  	int num = 0;
  	for( int x = num1-1; x > 0; x-- ) {
  		num += x;
  	}
  	return num;
  }
  
  public void countEventPairLinks() {
  	int total2 = 0;
  	int total3 = 0;
  	int docs = 0;
    for( String docname : getFiles() ) {
      List<Sentence> sentences = getSentences(docname);
      for( int sid = 0; sid < sentences.size()-1; sid++ ) {
      	Sentence sent = sentences.get(sid);
      	int num = numPossiblePairs(sent.events().size());
      	int num2 = sent.events().size() * sentences.get(sid+1).events().size();
      	int num3 = 0;
      	if( sid < sentences.size()-2 )
      		num3 = sent.events().size() * sentences.get(sid+2).events().size();
      	total2 += num + num2;
      	total3 += num + num2 + num3;
      }
      docs++;
    }
    System.out.println("checked " + docs + " docs.");
    System.out.println("total event pairs between 2 sentences: " + total2);
    System.out.println("total event pairs between 3 sentences: " + total3);
  }
  
  /**
   * This main function is only here to print out automatic time-time links!
   */
  public static void main(String[] args) {
    if( args.length < 1 ) {
      System.err.println("InfoFile <infopath> <markup-out-dir> markup");
    }

    else if( args[args.length-1].equals("markup") ) {
    	InfoFile info = new InfoFile();
      info.readFromFile(new File(args[0]));
      info.outputMarkedUp(args[1]);
    }
    
    // InfoFile <info> count
    else if( args[args.length-1].equals("count") ) {
      InfoFile info = new InfoFile();
      info.readFromFile(new File(args[0]));
//      info.countEventDCTLinks();
      info.countEventPairLinks();
    }
    
    else {
      InfoFile info = new InfoFile();
      info.readFromFile(new File(args[0]));

      // Do each file
      for( String filename : info.getFiles() ) {
        //	System.out.println(filename);
        Vector<TLink> newlinks = info.computeTimeTimeLinks(filename);
        // Print each new link
        for( TLink link : newlinks ) {
          System.out.println(filename + " " + link.getId1() + " " + 
              link.getId2() + " " + link.getRelation());
        }
      }
    }
  }

}	
