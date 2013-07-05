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
 * Precision for all reporting event pairs: .76 (71 of 94)
 * Precision for same-tense pairs: .80 (55 of 69)
 * Precision for same-tense-and-aspect pairs: .83 (54 of 65)
 * 
 * Currently, it's set to only consider events with same tense and aspect.
 * 
 * @author Bill McDowell
 */
public class RepEventRepEventSieve implements Sieve {

	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<SieveSentence> sentences = doc.getSentences();
		/*for (SieveSentence sentence: sentences) {
			if (sentence.sentence().contains("because"))
				System.err.println(sentence.sentence());
		}*/
		
		List<TLink> proposed = new ArrayList<TLink>();
	
		List<TLink> sentencePairLinks = annotateBySentencePair(doc);
		
		if (sentencePairLinks != null)
			proposed.addAll(sentencePairLinks);

		return proposed;
	}
	
	public List<TLink> annotateBySentencePair(SieveDocument doc) {
		List<List<TextEvent>> allEvents = this.allEventsBySentencePair(doc.getEventsBySentence());
		List<TLink> proposed = new ArrayList<TLink>();
		
		for (List<TextEvent> closeEvents : allEvents) {
			for (int e1 = 0; e1 < closeEvents.size(); e1++) {				
				for (int e2 = e1 + 1; e2 < closeEvents.size(); e2++) {
					TextEvent event1 = closeEvents.get(e1);
					TextEvent event2 = closeEvents.get(e2);
					if (event1.getTheClass() == TextEvent.Class.REPORTING 
					 && event2.getTheClass() == TextEvent.Class.REPORTING
					 && event1.getTense() == event2.getTense() 
					 && event1.getAspect() == event2.getAspect()) {				
						TLink link = new EventEventLink(event1.getEiid(), event2.getEiid(), TLink.Type.VAGUE);
						proposed.add(link);
					}
				}
			}
		}
		
		return proposed;
	}
	
	private List<List<TextEvent>> allEventsBySentencePair(List<List<TextEvent>> allEventsBySentence) {
		List<List<TextEvent>> allEvents = new ArrayList<List<TextEvent>>();
		
		if (allEventsBySentence.size() == 1)
			allEvents.add(allEventsBySentence.get(0));
		
		for (int i = 0; i < allEventsBySentence.size() - 1; i++) {
			List<TextEvent> curEvents = new ArrayList<TextEvent>();
			curEvents.addAll(allEventsBySentence.get(i));
			curEvents.addAll(allEventsBySentence.get(i+1));
			allEvents.add(curEvents);
		}
		
		return allEvents;
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}