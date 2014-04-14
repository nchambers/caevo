package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;

/**
 * Baseline Majority Class Sieve that labels everything as Vague.
 * Label every possible pair (both events, times, and mixes of both) as vague if they are in the
 * same sentence or in adjacent sentences.
 * 
 * @author chambers
 */
public class AllVagueSieve implements Sieve {

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
			proposed.addAll(allPairs(allEvents.get(sid), (sid+1 < allEvents.size() ? allEvents.get(sid+1) : null), 
					allTimexes.get(sid), (sid+1 < allTimexes.size() ? allTimexes.get(sid+1) : null)));
			
			proposed.addAll(allEventDCTPairs(allEvents.get(sid), doc.getDocstamp().get(0)));
			proposed.addAll(allTimeDCTPairs(allTimexes.get(sid), doc.getDocstamp().get(0)));

			//System.out.println("added links: " + proposed);
			sid++;
		}

		return proposed;
	}

	/**
	 * Labels as vague all event-DCT links.
	 * @return List of new TLink links.
	 */
	private List<TLink> allTimeDCTPairs(List<Timex> timexes, Timex dct) {
		List<TLink> proposed = new ArrayList<TLink>();
		for( Timex timex : timexes )
			proposed.add(new EventTimeLink(timex.getTid(), dct.getTid(), TLink.Type.VAGUE));
		return proposed;
	}
	
	/**
	 * Labels as vague all event-DCT links.
	 * @return List of new TLink links.
	 */
	private List<TLink> allEventDCTPairs(List<TextEvent> events, Timex dct) {
		List<TLink> proposed = new ArrayList<TLink>();
		for( TextEvent event : events )
			proposed.add(new EventTimeLink(event.getEiid(), dct.getTid(), TLink.Type.VAGUE));
		return proposed;
	}
	
	/**
	 * Labels as vague all pairs of intra-sentence event-event, event-time, and time-time links.
	 * Also labels all pairs of adjacent sentence event-event, event-time, and time-time links.
	 * @return List of new TLink links.
	 */
	private List<TLink> allPairs(List<TextEvent> events, List<TextEvent> nextSentEvents, List<Timex> timexes, List<Timex> nextSentTimexes) {
		List<TLink> proposed = new ArrayList<TLink>();

		for( int xx = 0; xx < events.size(); xx++ ) {
			TextEvent e1 = events.get(xx);
			// Remaining events in this sentence.
			for( int yy = xx+1; yy < events.size(); yy++ )
				proposed.add(new EventEventLink(e1.getEiid(), events.get(yy).getEiid(), TLink.Type.VAGUE));

			// All times in this sentence.
			for( int yy = 0; yy < timexes.size(); yy++ ) {
				if( e1.getIndex() < timexes.get(yy).getTokenOffset() )
					proposed.add(new EventTimeLink(events.get(xx).getEiid(), timexes.get(yy).getTid(), TLink.Type.VAGUE));
				else 
					proposed.add(new EventTimeLink(timexes.get(yy).getTid(), events.get(xx).getEiid(), TLink.Type.VAGUE));
			}

			// Events in the next sentence.
			if( nextSentEvents != null )
				for( TextEvent next : nextSentEvents )
					proposed.add(new EventEventLink(e1.getEiid(), next.getEiid(), TLink.Type.VAGUE));

			// Times in the next sentence.
			if( nextSentTimexes != null )
				for( Timex next : nextSentTimexes )
					proposed.add(new EventTimeLink(e1.getEiid(), next.getTid(), TLink.Type.VAGUE));
		}
		
		// Start from this sentence's times.
		for( int xx = 0; xx < timexes.size(); xx++ ) {
			Timex t1 = timexes.get(xx);
			// Remaining times in this sentence.
			for( int yy = xx+1; yy < timexes.size(); yy++ )
				proposed.add(new TimeTimeLink(t1.getTid(), timexes.get(yy).getTid(), TLink.Type.VAGUE));
			
			// Times in the next sentence.
			if( nextSentTimexes != null )
				for( Timex next : nextSentTimexes )
					proposed.add(new TimeTimeLink(t1.getTid(), next.getTid(), TLink.Type.VAGUE));
			
			// Events in the next sentence.
			if( nextSentEvents != null )
				for( TextEvent next : nextSentEvents )
					proposed.add(new EventTimeLink(t1.getTid(), next.getEiid(), TLink.Type.VAGUE));
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
