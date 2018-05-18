package caevo;

import java.io.Serializable;

/**
 * Represents a set of TextEvents with certain fixed values for some attributes
 * (e.g. tense, aspect, and class). For example, one possible pattern is
 * (Tense=PAST, Aspect=PROGRESSIVE) representing all past progressive
 * TextEvents.
 * 
 * This is useful in constructing rules about text events based on their
 * attribrutes.
 * 
 * @author Bill
 *
 */
public class TextEventPattern implements Serializable {
  private static final long serialVersionUID = 6158057224376789755L;

  private TextEvent.Tense tense = null;
  private TextEvent.Aspect aspect = null;
  private TextEvent.Class theClass = null;

  public TextEventPattern() {

  }

  public TextEventPattern(TextEvent.Class theClass, TextEvent.Tense tense,
      TextEvent.Aspect aspect) {
    this.theClass = theClass;
    this.tense = tense;
    this.aspect = aspect;
  }

  public TextEventPattern(TextEvent canonicalEvent, boolean keepClass,
      boolean keepTense, boolean keepAspect) {
    setFromCanonicalEvent(canonicalEvent, keepClass, keepTense, keepAspect);
  }

  public void setFromCanonicalEvent(TextEvent canonicalEvent, boolean keepClass,
      boolean keepTense, boolean keepAspect) {
    if (keepClass)
      this.theClass = canonicalEvent.getTheClass();
    else
      this.theClass = null;

    if (keepTense)
      this.tense = canonicalEvent.getTense();
    else
      this.tense = null;

    if (keepAspect)
      this.aspect = canonicalEvent.getAspect();
    else
      this.aspect = null;
  }

  public void setTheClass(TextEvent.Class theClass) {
    this.theClass = theClass;
  }

  public void setTense(TextEvent.Tense tense) {
    this.tense = tense;
  }

  public void setAspect(TextEvent.Aspect aspect) {
    this.aspect = aspect;
  }

  public boolean matches(TextEvent event) {
    if (this.theClass != null && this.theClass != event.getTheClass())
      return false;
    if (this.tense != null && this.tense != event.getTense())
      return false;
    if (this.aspect != null && this.aspect != event.getAspect())
      return false;
    return true;
  }

  public int hashCode() {
    StringBuilder str = new StringBuilder();

    if (this.theClass != null)
      str.append(this.theClass.toString());
    if (this.tense != null)
      str.append(this.tense.toString());
    if (this.aspect != null)
      str.append(this.aspect.toString());

    return str.toString().hashCode();
  }

  public boolean equals(Object o) {
    TextEventPattern p = (TextEventPattern) o;
    return p.theClass == this.theClass && p.tense == this.tense
        && p.aspect == this.aspect;
  }

  public String toString() {
    if (this.theClass == null && this.tense == null && this.aspect == null)
      return "";

    StringBuilder str = new StringBuilder();

    if (this.theClass != null)
      str.append(this.theClass).append("\t");
    if (this.tense != null)
      str.append(this.tense).append("\t");
    if (this.aspect != null)
      str.append(this.aspect).append("\t");

    return str.substring(0, str.length() - 1).toString();
  }
}
