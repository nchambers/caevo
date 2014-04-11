package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;

/**
 * EventCorefSieve labels pairs of coreferent event mentions as simultaneous.  The coreference is determined
 * by outside tools, added through the SieveDocument event coref sets.
 * 
 * TODO: Get performance numbers for this.
 * 
 * @author Bill McDowell
 */
public class EventCorefSieve implements Sieve {
	public EventCorefSieve() {
		
	}
	
	
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<List<TextEvent>> corefEventSets = doc.getEventCorefSets();
		List<TLink> proposed = new ArrayList<TLink>();
		
		for (List<TextEvent> corefEvents : corefEventSets) {
			for (int i = 0; i < corefEvents.size(); i++) {
				for (int j = i + 1; j < corefEvents.size(); j++) {
					proposed.add(
							new EventEventLink(corefEvents.get(i).getEiid(), corefEvents.get(j).getEiid(), TLink.Type.SIMULTANEOUS)	
					);
				}
			}
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