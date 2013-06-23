package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import timesieve.InfoFile;
import timesieve.Sentence;
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
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence(docname);
		List<List<Timex>> allTimexes = info.getTimexesBySentence(docname);

		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Make BEFORE links between all intra-sentence pairs.
		int sid = 0;
		for( Sentence sent : info.getSentences(docname) ) {
//			System.out.println("DEBUG: adding tlinks from " + docname + " sentence " + sent.sentence());
			proposed.addAll(allPairs(allEvents.get(sid), (sid+1 < allEvents.size() ? allEvents.get(sid+1) : null), 
					allTimexes.get(sid), (sid+1 < allTimexes.size() ? allTimexes.get(sid+1) : null)));
			sid++;
		}

		System.out.println("TLINKS: " + proposed);
		return proposed;
	}

	/**
	 * Labels as vague all pairs of intra-sentence event-event, event-time, and time-time links.
	 * Also labels all pairs of adjacent sentence event-event, event-time, and time-time links.
	 */
	private List<TLink> allPairs(List<TextEvent> events, List<TextEvent> nextSentEvents, List<Timex> timexes, List<Timex> nextSentTimexes) {
		List<TLink> proposed = new ArrayList<TLink>();

		for( int xx = 0; xx < events.size(); xx++ ) {
			TextEvent e1 = events.get(xx);
			// Remaining events in this sentence.
			for( int yy = xx+1; yy < events.size(); yy++ )
				proposed.add(new EventEventLink(e1.eiid(), events.get(yy).eiid(), TLink.TYPE.VAGUE));

			// All times in this sentence.
			for( int yy = 0; yy < timexes.size(); yy++ ) {
				if( e1.index() < timexes.get(yy).offset() )
					proposed.add(new EventTimeLink(events.get(xx).eiid(), timexes.get(yy).tid(), TLink.TYPE.VAGUE));
				else 
					proposed.add(new EventTimeLink(timexes.get(yy).tid(), events.get(xx).eiid(), TLink.TYPE.VAGUE));
			}

			// Events in the next sentence.
			if( nextSentEvents != null )
				for( TextEvent next : nextSentEvents )
					proposed.add(new EventEventLink(e1.eiid(), next.eiid(), TLink.TYPE.VAGUE));

			// Times in the next sentence.
			if( nextSentTimexes != null )
				for( Timex next : nextSentTimexes )
					proposed.add(new EventTimeLink(e1.eiid(), next.tid(), TLink.TYPE.VAGUE));
		}
		
		// Start from this sentence's times.
		for( int xx = 0; xx < timexes.size(); xx++ ) {
			Timex t1 = timexes.get(xx);
			// Remaining times in this sentence.
			for( int yy = xx+1; yy < timexes.size(); yy++ )
				proposed.add(new TimeTimeLink(t1.tid(), timexes.get(yy).tid(), TLink.TYPE.VAGUE));
			
			// Times in the next sentence.
			if( nextSentTimexes != null )
				for( Timex next : nextSentTimexes )
					proposed.add(new TimeTimeLink(t1.tid(), next.tid(), TLink.TYPE.VAGUE));
			
			// Events in the next sentence.
			if( nextSentEvents != null )
				for( TextEvent next : nextSentEvents )
					proposed.add(new EventTimeLink(t1.tid(), next.eiid(), TLink.TYPE.VAGUE));
		}
		
		return proposed;
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(InfoFile trainingInfo) {
		// no training
	}

}
