package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import net.didion.jwnl.data.POS;

import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.util.*;

/**
 * Implements rule number 209, in the category of "WORDNET", from D'Souza & Ng 2013.
 * In English: 
 * If e1 and e2 are events, and they are members of the same synset, 
 * Then label them SIMULTANEOUS.
 * 
 * Only considers events within one sentence of each other
 * 
 * @author cassidy
 */

public class WordNet209 implements Sieve {

	/**
	 * The main function. All sieves must have this.
	 */
	/* (non-Javadoc)
	 * @see timesieve.sieves.Sieve#annotate(timesieve.InfoFile, java.lang.String, java.util.List)
	 */
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence(docname);

		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
	
		// Use wn to extract WordNet derived information about events
		 WordNet wn = new WordNet(WordNet.findWordnetPath()); 
		
		// Obtain all event pairs within one sentence of one another
		List<Sentence> sentList = info.getSentences(docname);
		int numSentences = sentList.size();
		int sid = 0;
		for ( Sentence sent : sentList ) {
			if (sid == numSentences - 1) continue;
			System.out.println("DEBUG: adding tlinks from " + docname + " sentences:\n" + sent.sentence()
			 										 + "\n" + sent.sentence());
		  // Get all events from the current and following sent and apply allPairsEvents to their
			// concatenation
			List<TextEvent> allEventsSent = new ArrayList<TextEvent>();
			allEventsSent.addAll(allEvents.get(sid));
			allEventsSent.addAll(allEvents.get(sid + 1));
			proposed.addAll(allPairsEvents(allEventsSent, wn));
			sid ++;
		}
		
		System.out.println("TLINKS: " + proposed);
		return proposed;
	}
	
	/**
	 * all pairs of events that are siblings (i.e. their WordNet synsets overlap) 
	 * are labeled SIMULTANEOUS.
	 */
		private List<TLink> allPairsEvents(List<TextEvent> events, WordNet wn) {
			List<TLink> proposed = new ArrayList<TLink>();
			
			for( int xx = 0; xx < events.size(); xx++ ) {
				for( int yy = xx+1; yy < events.size(); yy++ ) {
					TextEvent e1 = events.get(xx);
					TextEvent e2 = events.get(yy);
					// Check e1, e2 against rule criteria
					/** Note: checks event strings along with each possible POS tag
					 *  This is naive in one sense because the rule appears to assume the
					 *  words appear as the same POS. On the other hand the POS tagger
					 *  may be incorrect.
					 */
					for (Object postag : POS.getAllPOS()) {
						if (wn.areSiblings(e1.string(), e2.string(), (POS) postag)) {
							proposed.add(new EventEventLink(e1.eiid() , e2.eiid(), TLink.TYPE.SIMULTANEOUS));
							break;
						}
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
