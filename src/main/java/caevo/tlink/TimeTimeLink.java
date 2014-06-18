package caevo.tlink;


import org.jdom.Element;
import org.jdom.Namespace;

import caevo.Timex;

public class TimeTimeLink extends TLink {

  public TimeTimeLink(String tid1, String tid2, String rel) {
    super(tid1,tid2,rel);
  }
  public TimeTimeLink(String tid1, String tid2, TLink.Type rel) {
    super(tid1,tid2,rel);
  }
  public TimeTimeLink(String tid1, String tid2, TLink.Type rel, boolean closed) {
    super(tid1,tid2,rel,closed);
  }
  public TimeTimeLink(Element el) {
    super(el);
  }

  public Element toElement(Namespace ns) {
    Element el = super.toElement(ns);
    el.setAttribute(TLINK_TYPE_ATT, TLink.TIME_TIME_TYPE_VALUE);
    return el;
  }
  
  public Timex getTime1() {
  	return this.document.getTimexByTid(this.id1);
  }
  
  public Timex getTime2() {
  	return this.document.getTimexByTid(this.id2);
  }
  
}
