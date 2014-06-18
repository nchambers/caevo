package caevo.sieves;

import java.util.ArrayList;
import java.util.List;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.tlink.EventEventLink;
import caevo.tlink.TLink;

/**
 * Implements rule number 5, in the category of "Word Features", from D'Souza & Ng 2013.
 * In English: If "before" appears in the five words preceding e1,
 * and if e2's tense is neither PRESENT nor PRESPART,
 * Then e1 AFTER e2
 * 
 * Only considers events within the same sentence
 * 
 * @author cassidy
 */

public class WordFeatures5 implements Sieve {
	public boolean debug = false;
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Obtain all event pairs within one sentence of one another
		List<SieveSentence> sentList = doc.getSentences();
		int sid = 0;
		
		for ( SieveSentence sent : sentList ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentences:\n" + sent.sentence() + "\n" + sent.sentence());
			}
			proposed.addAll(allPairsEvents(doc.getSentences().get(sid).events(), sent));
			sid ++;
			}
		
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
	}
	
		private List<TLink> allPairsEvents(List<TextEvent> events, SieveSentence sent) {
			
			List<TLink> proposed = new ArrayList<TLink>();
			for( int xx = 0; xx < events.size(); xx++ ) {
				for( int yy = xx+1; yy < events.size(); yy++ ) {
					// get both events
					TextEvent e1 = events.get(xx);
					TextEvent e2 = events.get(yy);
					// Check tense of e2 against criteria
					if (e2.getTense() == TextEvent.Tense.PRESENT && e2.getTense() == TextEvent.Tense.PRESPART) {
						// get e1's context tokens - the 5 words preceding event1 is "before"
						String sentString = sent.sentence();
						String delims = "[ ]+";
						String[] e1SentTokens = sentString.split(delims);
						int e1EndIndex = e1.getIndex();
						int e1StartIndex = Math.max(1, e1EndIndex - 5);
						String[] e1ContextTokens = java.util.Arrays.copyOfRange(e1SentTokens, e1StartIndex, e1EndIndex);
						// check if any of e1's context tokens is "before" - if so, add a tlink
						for (String token : e1ContextTokens) {
							if (token == "before") {
								proposed.add(new EventEventLink(e1.getEiid() , e2.getEiid(), TLink.Type.AFTER));
								break;
							}
						}
					}
				}
			}
			
			if (debug == true) {
				System.out.println("events: " + events);
				System.out.println("created tlinks: " + proposed);
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
