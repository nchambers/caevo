package timesieve.tlink;


import org.jdom.*;
import org.jdom.Namespace;

public class EventTimeLink extends TLink {

  public EventTimeLink(String e1, String e2, String rel) {
    super(e1,e2,rel);
  }
  public EventTimeLink(String e1, String e2, TLink.TYPE rel) {
    super(e1,e2,rel);
  }
  public EventTimeLink(String e1, String e2, TLink.TYPE rel, boolean closed) {
    super(e1,e2,rel,closed);
  }
  public EventTimeLink(Element el) {
    super(el);
  }

  public Element toElement(Namespace ns) {
    Element el = super.toElement(ns);
    el.setAttribute(TLINK_TYPE_ATT, EVENT_TIME);
    return el;
  }
}
