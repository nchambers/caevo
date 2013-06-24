package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;

/**
 * Implements rule number 64, in the category of "Word Features", from D'Souza & Ng 2013.
 * In English: 
 * If e1 and e2 are events, and share the same tense and aspect, e1 BEFORE e2
 * (where e1 occurs before e2 in the text)
 * 
 * Only considers events within one sentence of each other
 * 
 * @author cassidy
 */

public class WordFeatures64 implements Sieve {

	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence(docname);
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Obtain all event pairs within one sentence of one another
		List<Sentence> sentList = info.getSentences(docname);
		int numSentences = sentList.size();
		int sid = 0;
		
		for ( Sentence sent : sentList ) { 
			if (sid == numSentences - 1) continue;
			System.out.println("DEBUG: adding tlinks from " + docname + " sentences:\n" + sent.sentence()
					+ "\n" + sent.sentence());
			List<TextEvent> allEventsSents = new ArrayList<TextEvent>();
			allEventsSents.addAll(allEvents.get(sid));
			allEventsSents.addAll(allEvents.get(sid + 1));
		// check all event pairs in allEventsSent against rule criteria
			proposed.addAll(allPairsEvents(allEventsSents));
			sid ++;
		}
		
		System.out.println("TLINKS: " + proposed);
		return proposed;
	}
	
		private List<TLink> allPairsEvents(List<TextEvent> events) {
			List<TLink> proposed = new ArrayList<TLink>();
			
			for( int xx = 0; xx < events.size(); xx++ ) {
				for( int yy = xx+1; yy < events.size(); yy++ ) {
					TextEvent e1 = events.get(xx);
					TextEvent e2 = events.get(yy);
					// Check event pair against rule criteria; add matches to proposed
					if (e1.getTense() == e2.getTense() && e2.getAspect() == e2.getAspect()) {
						proposed.add(new EventEventLink(e1.eiid() , e2.eiid(), TLink.TYPE.BEFORE));
					}
				}
			}
			
			System.out.println("events: " + events);
			System.out.println("created tlinks: " + proposed);
			return proposed;
		}

	/**
	 * No training. Just rule-based.
	 */
	public void train(InfoFile trainingInfo) {
		// no training
	}

}
