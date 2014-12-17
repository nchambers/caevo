package caevo;

import java.util.ArrayList;
import java.util.List;

import org.jdom.Element;
import org.jdom.Namespace;

import caevo.util.TreeOperator;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * Represents a sentence in a document, including the following data:
 * - tokens
 * - events as TextEvent objects
 * - times as Timex objects
 * 
 * @author chambers
 */
public class SieveSentence {
	private SieveDocument parent;
  private int sid;
  private String sentence;
  private String parseStr;
  private Tree parseTree;
  private String depsStr;
  private List<TypedDependency> deps;
  private List<CoreLabel> tokens;
  private List<TextEvent> events; // of TextEvent objects
  private List<Timex> timexes; // of Timex objects

  public SieveSentence(SieveDocument doc, int sid, String sentence, String strParse, String strDeps, List<CoreLabel> tokens, List<TextEvent> events, List<Timex> timexes) {
  	this.parent = doc;
  	this.sid = sid;
  	this.sentence = sentence;
  	this.parseStr = strParse;
  	this.depsStr = strDeps;
  	this.tokens = tokens;
  	this.events = events;
  	this.timexes = timexes;
  	
  	if( this.events == null ) 	this.events = new ArrayList<TextEvent>();
  	if( this.timexes == null ) 	this.timexes = new ArrayList<Timex>();
  }
  
  public SieveSentence(Element el, Namespace ns) {
    if( el != null ) {
    	String sidStr = el.getAttributeValue(InfoFile.SID_ELEM);
    	if( sidStr != null ) sid = Integer.parseInt(sidStr);
      events = new ArrayList<TextEvent>();
      timexes = new ArrayList<Timex>();
      sentence = el.getChildTextTrim(InfoFile.SENT_ELEM,ns);
      parseStr = el.getChildTextTrim(InfoFile.PARSE_ELEM,ns);
      depsStr = el.getChildTextTrim(InfoFile.DEPS_ELEM,ns);
      tokens = null;
      
      if( el.getChild(InfoFile.TOKENS_ELEM,ns) != null ) {
        tokens = new ArrayList<CoreLabel>();
        Element alltokens = el.getChild(InfoFile.TOKENS_ELEM,ns);
        List<Element> children = alltokens.getChildren(InfoFile.TOKEN_ELEM, ns);
        for( Element child : children )
          tokens.add(stringToCoreLabel(child.getText()));
      }
      
      Element evel = el.getChild("events",ns);
      if( evel != null ) {
        List children = evel.getChildren(TextEvent.NAME_ELEM,ns);
        for( Object obj : children ) events.add(new TextEvent(sid,(Element)obj));
      }

      evel = el.getChild(InfoFile.TIMEXES_ELEM,ns);
      if( evel != null ) {
        List children = evel.getChildren(Timex.TIMEX_ELEM,ns);
        for( Object obj : children ) {
          Timex newtimex = new Timex((Element)obj);
          newtimex.setSid(sid);
          timexes.add(newtimex);
        }
      }
    }	
  }


  public void addEvents(List<TextEvent> newEvents) {
  	if( newEvents != null ) {
  		if( events == null )
  			events = new ArrayList<TextEvent>();
  		events.addAll(newEvents);
  	}
  }

  public void addTimexes(List<Timex> newTimexes) {
  	if( newTimexes != null ) {
  		if( timexes == null )
  			timexes = new ArrayList<Timex>();

  		for (Timex timex : newTimexes)
  			timex.setSid(this.sid);
  		timexes.addAll(newTimexes);
  	}
  }
  
  public static SieveSentence fromXML(Element el) {
  	Namespace ns = Namespace.getNamespace(SieveDocuments.INFO_NS);
  	return new SieveSentence(el, ns);
  }
  
  public Element toXML() {
  	Namespace ns = Namespace.getNamespace(SieveDocuments.INFO_NS);

    Element entry = new Element(SieveDocuments.ENTRY_ELEM,ns);
    entry.setAttribute(SieveDocuments.SID_ELEM,String.valueOf(sid));
    entry.setAttribute(SieveDocuments.FILE_ELEM, parent.getDocname());
    Element sentElem = new Element(SieveDocuments.SENT_ELEM,ns);
    sentElem.addContent(sentence);
    Element tokensElem = new Element(SieveDocuments.TOKENS_ELEM,ns);
    if( tokens != null ) {
      for( CoreLabel token : tokens ) {
        Element tokenElem = new Element(SieveDocuments.TOKEN_ELEM,ns);
        tokenElem.addContent(generateCoreLabelString(token));
        tokensElem.addContent(tokenElem);
      }
//      tokensElem.addContent(tokens);
    }
    Element parseElem = new Element(SieveDocuments.PARSE_ELEM,ns);
    parseElem.setText(parseStr);

    // Add the events vector
    Element eventsElem = new Element(SieveDocuments.EVENTS_ELEM,ns);
    if( events != null )
      for( TextEvent te : events )
        eventsElem.addContent(te.toElement(ns));

    // Add the timex vector
    Element timexesElem = new Element(SieveDocuments.TIMEXES_ELEM,ns);
    if( timexes != null )
      for( Timex te : timexes )
        timexesElem.addContent(te.toElement(ns));

    // Add the typed dependencies
    Element depsElem = new Element(SieveDocuments.DEPS_ELEM,ns);
    depsElem.setText(depsStr);

    entry.addContent(sentElem);
    entry.addContent(tokensElem);
    entry.addContent(parseElem);
    entry.addContent(depsElem);
    entry.addContent(eventsElem);
    entry.addContent(timexesElem);

    return entry;
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

  public Tree getParseTree() {
  	if( parseTree == null ) {
      TreeFactory tf = new LabeledScoredTreeFactory();
  		parseTree = TreeOperator.stringToTree(parseStr, tf);
  	}
  	return parseTree; 
  }
  
  public List<TypedDependency> getDeps() {
  	if( deps == null )
  		deps = TreeOperator.stringToDependencies(depsStr);
  	return deps;
  }
  
  public void setParent(SieveDocument doc) { parent = doc; }
  public int sid() { return sid; }
  public String sentence() { return sentence; }
  public List<CoreLabel> tokens() { return tokens; }
  public List<TextEvent> events() { return events; }
  public List<Timex> timexes() { return timexes; }
}
