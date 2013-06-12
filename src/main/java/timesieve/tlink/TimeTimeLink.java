package timesieve.tlink;


import org.jdom.*;
import org.jdom.Namespace;

public class TimeTimeLink extends TLink {

  public TimeTimeLink(String e1, String e2, String rel) {
    super(e1,e2,rel);
  }
  public TimeTimeLink(String e1, String e2, TYPE rel) {
    super(e1,e2,rel);
  }
  public TimeTimeLink(String e1, String e2, TYPE rel, boolean closed) {
    super(e1,e2,rel,closed);
  }
  public TimeTimeLink(Element el) {
    super(el);
  }

  public Element toElement(Namespace ns) {
    Element el = super.toElement(ns);
    el.setAttribute(TLINK_TYPE_ATT, TIME_TIME);
    return el;
  }
}
