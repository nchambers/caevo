package caevo;

import java.util.ArrayList;
import java.util.List;

import org.jdom.Element;
import org.jdom.Namespace;

import edu.stanford.nlp.ling.CoreLabel;

/**
 * Represents a sentence in a document, including the following data:
 * - tokens
 * - events as TextEvent objects
 * - times as Timex objects
 * 
 * @author chambers
 */
public class Sentence {
  private String sid;
  private String sentence;
  private String parse;
  private List<CoreLabel> tokens;
  private List<TextEvent> events; // of TextEvent objects
  private List<Timex> timexes; // of Timex objects

  public Sentence(Element el, Namespace ns) {
    if( el != null ) {
      sid = el.getAttributeValue(InfoFile.SID_ELEM);
      events = new ArrayList<TextEvent>();
      timexes = new ArrayList<Timex>();
      sentence = el.getChildTextTrim(InfoFile.SENT_ELEM,ns);
      parse = el.getChildTextTrim(InfoFile.PARSE_ELEM,ns);
      tokens = null;
      
      if( el.getChild(InfoFile.TOKENS_ELEM,ns) != null ) {
        tokens = new ArrayList<CoreLabel>();
        Element alltokens = el.getChild(InfoFile.TOKENS_ELEM,ns);
        List<Element> children = alltokens.getChildren(InfoFile.TOKEN_ELEM, ns);
        for( Element child : children )
          tokens.add(InfoFile.stringToCoreLabel(child.getText()));
      }
      
      if( sid == null ) System.out.println("SID null");
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
          newtimex.setSid(Integer.parseInt(sid));
          timexes.add(newtimex);
        }
      }
    }	
  }

  public String sid() { return sid; }
  public String sentence() { return sentence; }
  public String parse() { return parse; }
  public List<CoreLabel> tokens() { return tokens; }
  public List<TextEvent> events() { return events; }
  public List<Timex> timexes() { return timexes; }
}
