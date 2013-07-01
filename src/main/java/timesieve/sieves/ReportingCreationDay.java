package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
import timesieve.util.TreeOperator;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;

/**
 *This sieve returns IS_INCLUDED for an event/timex pair of 
 *the form (class=REPORTING, val=PRESENT_REF)
 * 
 * @author cassidy
 */
public class ReportingCreationDay implements Sieve {
	public boolean debug = false;
	private static String creationTime = "(\\d{4}-\\d{2}-\\d{2})T\\d{2}:\\d{2}";
	private static Pattern creationTimePattern = Pattern.compile(creationTime);
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
			
		// get list of DCT timexes - normally 1, sometimes < > 1
		List<Timex> dctTimexes = doc.getDocstamp();
		Timex dctTimex;
		if (dctTimexes != null && dctTimexes.size() == 1) {
			dctTimex = dctTimexes.get(0);
		}
		// if we don't know the DCT the sieve won't work
		else if (dctTimexes == null || dctTimexes.size() == 0) {
			return proposed;
		}
		// if there is more than one DCT, how do we choose?
		// a better solution may be to go with whichever is mentioned the most, 
		// followed by whichever appears first in the document, or vice versa
		else {
			// for now just use the first one in the list
			dctTimex = dctTimexes.get(0);
		}
		// extract the date of the document creation time
		String dcd = getDocCreationDate(dctTimex);
		
		// check timexes/event pairs in each sentence against sieve criteria.
		int sid = 0;
		for( SieveSentence sent : doc.getSentences() ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
			}
			for (Timex timex : allTimexes.get(sid)) {
				// only proceed if timex equals the dcd
				if (!validateTimex(timex, dcd)) continue;
				for (TextEvent event : allEvents.get(sid)) {
					// only proceed if event is of type REPORTING
					if (!validateEvent(event)) continue;
					// label the reporting event as included in the dcd timex
					TLink.Type label = TLink.Type.IS_INCLUDED;
					proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), label));
					}
				}
				sid++;
			}
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
		}

	
	private String getDocCreationDate(Timex dctTimex) {
		String value = dctTimex.getValue();
		Matcher matcher = creationTimePattern.matcher(value);
		if (matcher.matches()) {
			String dcd = matcher.group(1);
			return dcd;
		}
		return null;
	}
	
	private Boolean validateTimex(Timex timex, String dcd){
		String value = timex.getValue();
		if (value.equals(dcd)) return true;
		else return false;
	}
	
	private Boolean validateEvent(TextEvent event){
		if (event.getTheClass()== TextEvent.Class.REPORTING) return true;
		else return false;
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
