package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;

/**
 * JUST AN EXAMPLE
 * Stupid sieve that shows how to access basic data structures.
 * It generates BEFORE links between all intra-sentence pairs.
 * 
 * @author chambers
 */
public class StupidSieve implements Sieve {

	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = doc.getEventsBySentence();
		List<List<Timex>> allTimexes = doc.getTimexesBySentence();

		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Make BEFORE links between all intra-sentence pairs.
		int sid = 0;
		for( SieveSentence sent : doc.getSentences() ) {
//			System.out.println("DEBUG: adding tlinks from " + docname + " sentence " + sent.sentence());
			proposed.addAll(allPairsEvents(allEvents.get(sid)));
			proposed.addAll(allPairsTimes(allTimexes.get(sid)));
			sid++;
		}

//		System.out.println("TLINKS: " + proposed);
		return proposed;
	}

	/**
	 * All pairs of events are BEFORE relations based on text order!
	 */
	private List<TLink> allPairsEvents(List<TextEvent> events) {
		List<TLink> proposed = new ArrayList<TLink>();

		for( int xx = 0; xx < events.size(); xx++ ) {
			for( int yy = xx+1; yy < events.size(); yy++ ) {
				proposed.add(new EventEventLink(events.get(xx).eiid(), events.get(yy).eiid(), TLink.TYPE.BEFORE));
			}
		}
		
//		System.out.println("events: " + events);
//		System.out.println("created tlinks: " + proposed);
		return proposed;
	}
	
	/**
	 * All pairs of times are BEFORE relations based on text order!
	 */
	private List<TLink> allPairsTimes(List<Timex> timexes) {
		List<TLink> proposed = new ArrayList<TLink>();

		for( int xx = 0; xx < timexes.size(); xx++ ) {
			for( int yy = xx+1; yy < timexes.size(); yy++ ) {
				proposed.add(new TimeTimeLink(timexes.get(xx).tid(), timexes.get(yy).tid(), TLink.TYPE.BEFORE));
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
