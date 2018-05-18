package caevo.sieves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.TextEvent;
import caevo.TextEventPattern;
import caevo.Timex;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;

/**
 * Order document creation time with events by considering tenses, aspects, and
 * classes
 * 
 * FIXME: More rule experimentation is needed. They might be improved to some
 * extent by considering attributes beyond just tense/class/aspect. Also:
 * "INFINITIVE" often is in the future (e.g. "is going to X"), but this is not
 * always the case (e.g. "was going to X").
 * 
 * The TextEventPatterns are a little confusing when rules start to overlap in
 * what they match. Be careful not to clobber a more precise rule that you
 * already added.
 *
 * Current Results (training): p=0.68 152 of 225 Non-VAGUE: p=0.80 152 of 189
 *
 * @author Bill McDowell
 * @author chambers
 */
public class EventCreationTimeSieve implements Sieve {
  private HashMap<TextEventPattern, TLink.Type> tenseRules;

  public EventCreationTimeSieve() {
    /*
     * Rules map attributes of events to the TLink.Type of the link from
     * document creation time to the event
     * 
     * Rules are represented as TextEventPatterns so they can easily be extended
     * to include attributes other than tense in the future
     * 
     */
    this.tenseRules = new HashMap<TextEventPattern, TLink.Type>();

    // p=0.62 31 of 50 Non-VAGUE: p=0.91 31 of 34
    for (TextEvent.Class type : TextEvent.Class.values())
      this.tenseRules.put(new TextEventPattern(type, TextEvent.Tense.PRESENT,
          TextEvent.Aspect.PERFECTIVE), TLink.Type.BEFORE);

    // p=1.00 8 of 8 Non-VAGUE: p=1.00 8 of 8
    for (TextEvent.Class type : TextEvent.Class.values())
      this.tenseRules.put(new TextEventPattern(type, TextEvent.Tense.PAST,
          TextEvent.Aspect.PERFECTIVE), TLink.Type.BEFORE);

    // p=0.77 27 of 35 Non-VAGUE: p=0.93 27 of 29
    for (TextEvent.Aspect type : TextEvent.Aspect.values())
      this.tenseRules.put(new TextEventPattern(TextEvent.Class.REPORTING,
          TextEvent.Tense.PAST, type), TLink.Type.IS_INCLUDED);

    for (TextEvent.Class ctype : TextEvent.Class.values())
      for (TextEvent.Aspect atype : TextEvent.Aspect.values()) {
        if (ctype != TextEvent.Class.REPORTING) {
          this.tenseRules.put(
              new TextEventPattern(ctype, TextEvent.Tense.PAST, atype),
              TLink.Type.BEFORE);
          // this.tenseRules.put(new TextEventPattern(ctype,
          // TextEvent.Tense.INFINITIVE, atype), TLink.Type.AFTER); // good, but
          // not as good
          // this.tenseRules.put(new TextEventPattern(ctype,
          // TextEvent.Tense.PASTPART, atype), TLink.Type.BEFORE); // good, but
          // not as good
        }
      }

    // These general INCLUDED-type rules don't work. Many should be vague.
    // this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.FUTURE,
    // null), TLink.Type.AFTER);
    // this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.PRESENT,
    // null), TLink.Type.IS_INCLUDED);
    // this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.PRESPART,
    // null), TLink.Type.IS_INCLUDED);
    // this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.NONE,
    // null), TLink.Type.IS_INCLUDED);
  }

  public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
    List<TLink> proposed = new ArrayList<TLink>();

    if (doc.getDocstamp() == null || doc.getDocstamp().isEmpty())
      return proposed;

    Timex creationTime = doc.getDocstamp().get(0);
    List<TextEvent> allEvents = doc.getEvents();

    TextEventPattern eventPattern = new TextEventPattern();
    for (TextEvent event : allEvents) {
      eventPattern.setFromCanonicalEvent(event, true, true, true);
      if (this.tenseRules.containsKey(eventPattern)) {
        proposed.add(new EventTimeLink(event.getEiid(), creationTime.getTid(),
            this.tenseRules.get(eventPattern)));
      }

      // BASELINES for DCT links?
      // BEFORE: p=0.36 229 of 629
      // VAGUE: p=0.29 184 of 629
      // IS_INCLUDED: p=0.15 95 of 629
      // proposed.add(new EventTimeLink(event.getEiid(), creationTime.getTid(),
      // TLink.Type.IS_INCLUDED));
    }

    return proposed;
  }

  /**
   * No training. Just rule-based.
   */
  public void train(SieveDocuments trainingInfo) {
    // no training
  }

}