package caevo.tlink;


import org.jdom.Element;
import org.jdom.Namespace;

import caevo.TextEvent;
import caevo.Timex;

public class EventTimeLink extends TLink {

  public EventTimeLink(String eiid, String tid, String rel) {
    super(eiid,tid,rel);
  }
  public EventTimeLink(String eiid, String tid, TLink.Type rel) {
    super(eiid,tid,rel);
  }
  public EventTimeLink(String eiid, String tid, TLink.Type rel, boolean closed) {
    super(eiid,tid,rel,closed);
  }
  public EventTimeLink(Element el) {
    super(el);
  }

  public Element toElement(Namespace ns) {
    Element el = super.toElement(ns);
    el.setAttribute(TLink.TLINK_TYPE_ATT, TLink.EVENT_TIME_TYPE_VALUE);
    return el;
  }
  
  public TextEvent getEvent() {
  	TextEvent event = this.document.getEventByEiid(this.id1);
  	if (event != null)
  		return event;
  	else
  		return this.document.getEventByEiid(this.id2);
  }
  
  public Timex getTime() {
  	Timex time = this.document.getTimexByTid(this.id2);
  	if (time != null)
  		return time;
  	else
  		return this.document.getTimexByTid(this.id1);
  }
  
  public String getTimeId() {
  	Timex time = this.document.getTimexByTid(this.id2);
  	if (time != null)
  		return this.id2;
  	else
  		return this.id1;
  }
  
  public String getEventId() {
  	TextEvent event = this.document.getEventByEiid(this.id1);
  	if (event != null)
  		return this.id1;
  	else
  		return this.id2;
  }
}
