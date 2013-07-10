package timesieve.sieves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.TextEvent;
import timesieve.TextEventPattern;
import timesieve.Timex;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
/**
 * Order document creation time with events by considering tenses, aspects, and classes
 * 
 * FIXME: Rules in this sieve are currently only based on tense.  
 * 				They might be improved to some extent by considering aspect and class, or the dependency
 * 				parse.  For example, "INFINITIVE" often is in the future (e.g. "is going to X"), but this
 * 				is not always the case (e.g. "was going to X").  				
 *				Also, PRESENT PERFECTIVE verbs are often in the past, but not necessarily.  It sometimes
 *				depends on whether document creation time is a time or a date
 *
 *	Current Results: 0 (0 of 0) (we haven't annotated  document creation time to event links)
 *
 * @author Bill McDowell
 */
public class EventCreationTimeSieve implements Sieve {
	private HashMap<TextEventPattern, TLink.Type> tenseRules;
	
	public EventCreationTimeSieve() {
		/* 
		 * Rules map attributes of events to the TLink.Type of the link from document creation time to the event
		 * 
		 * Rules are represented as TextEventPatterns so they can easily be extended to include attributes other
		 * than tense in the future
		 * 
		 */
		this.tenseRules = new HashMap<TextEventPattern, TLink.Type>();
		this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.FUTURE, null), TLink.Type.BEFORE);
		this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.INFINITIVE, null), TLink.Type.BEFORE);
		this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.PAST, null), TLink.Type.AFTER);
		this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.PASTPART, null), TLink.Type.AFTER);
		this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.PRESENT, null), TLink.Type.IS_INCLUDED);
		this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.PRESPART, null), TLink.Type.IS_INCLUDED);
		this.tenseRules.put(new TextEventPattern(null, TextEvent.Tense.NONE, null), TLink.Type.IS_INCLUDED);
	}
	
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<TLink> proposed = new ArrayList<TLink>();
		
		if (doc.getDocstamp() == null || doc.getDocstamp().isEmpty())
			return proposed;
		
		Timex creationTime = doc.getDocstamp().get(0);
		List<TextEvent> allEvents = doc.getEvents();
		
		TextEventPattern eventPattern = new TextEventPattern();
		for (TextEvent event : allEvents) {
			eventPattern.setFromCanonicalEvent(event, false, true, false);
			if (this.tenseRules.containsKey(eventPattern))
				proposed.add(new EventTimeLink(event.getEiid(), creationTime.getTid(), this.tenseRules.get(eventPattern)));
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