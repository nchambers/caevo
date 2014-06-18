package caevo.tlink;

import org.jdom.Element;
import org.jdom.Namespace;

import caevo.TextEvent;

public class EventEventLink extends TLink {

  public EventEventLink(String eiid1, String eiid2, String rel) {
    super(eiid1,eiid2,rel);
  }
  public EventEventLink(String eiid1, String eiid2, TLink.Type rel) {
    super(eiid1,eiid2,rel);
  }
  public EventEventLink(String eiid1, String eiid2, TLink.Type rel, boolean closed) {
    super(eiid1,eiid2,rel,closed);
  }

  public EventEventLink(Element el) {
    super(el);
  }

  public Element toElement(Namespace ns) {
    Element el = super.toElement(ns);
    el.setAttribute(TLink.TLINK_TYPE_ATT, TLink.EVENT_EVENT_TYPE_VALUE);
    return el;
  }
  
  public TextEvent getEvent1() {
  	return this.document.getEventByEiid(this.id1);
  }
  
  public TextEvent getEvent2() {
  	return this.document.getEventByEiid(this.id2);
  }
}
