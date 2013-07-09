package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.tlink.TLink;
import timesieve.tlink.EventEventLink;

/**
 * Labels pairs of reporting events as vague.
 * 
 * Precision varies depending on whether events with non-equal tense and aspect are considered.
 * 
 * Precision for all reporting event pairs: .79 (65 of 82)
 * Precision for same-tense pairs: .82 (50 of 61)
 * Precision for same-tense-and-aspect pairs: .84 (49 of 58)
 * 
 * Currently, it's set to consider all event pairs.
 * 
 * @author Bill McDowell
 */
public class RepEventRepEventSieve implements Sieve {

	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<TLink> proposed = new ArrayList<TLink>();
		
		List<TLink> sentencePairLinks = annotateBySentencePair(doc);
		
		if (sentencePairLinks != null)
			proposed.addAll(sentencePairLinks);

		return proposed;
	}
	
	public List<TLink> annotateBySentencePair(SieveDocument doc) {
		List<List<TextEvent>> sentenceEvents = doc.getEventsBySentence();
		List<TLink> proposed = new ArrayList<TLink>();
		
		for (int s = 0; s < sentenceEvents.size(); s++) {
			for (int e1 = 0; e1 < sentenceEvents.get(s).size(); e1++) {						
				for (int e2 = e1 + 1; e2 < sentenceEvents.get(s).size(); e2++) {
					TLink link = this.orderEvents(sentenceEvents.get(s).get(e1), sentenceEvents.get(s).get(e2));
					if (link != null) 
						proposed.add(link);
				}
				
				if (s + 1 < sentenceEvents.size()) {
					for (int t2 = 0; t2 < sentenceEvents.get(s+1).size(); t2++) {
						TLink link = this.orderEvents(sentenceEvents.get(s).get(e1), sentenceEvents.get(s+1).get(t2));
						if (link != null) 
							proposed.add(link);
					}
				}
			}
		}
		
		return proposed;
	}
	
	private TLink orderEvents(TextEvent event1, TextEvent event2) {
		if (event1.getTheClass() == TextEvent.Class.REPORTING 
				 && event2.getTheClass() == TextEvent.Class.REPORTING
				 /*&& event1.getTense() == event2.getTense() */
				 /*&& event1.getAspect() == event2.getAspect()*/) {				
			return new EventEventLink(event1.getEiid(), event2.getEiid(), TLink.Type.VAGUE);
		} else {
			return null;
		}
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}