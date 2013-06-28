package timesieve.sieves;

import java.io.IOException;
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
import timesieve.util.TimeSieveProperties;
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
public class RepCreationDay implements Sieve {
	public boolean debug = false;
	private int leftSentWindow = 0;
	private int rightSentWindow = 0;
	private boolean considerTA = true;
	private static String creationTimeRegex = "(\\d{4}-\\d{2}-\\d{2})T\\d{2}:\\d{2}";
	private static Pattern creationTimePattern = Pattern.compile(creationTimeRegex);
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
	// PROPERTIES CODE
			try {
				TimeSieveProperties.load();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			try {
				leftSentWindow = TimeSieveProperties.getInt("RepCreationDay.leftSentWindow", 0);
				rightSentWindow = TimeSieveProperties.getInt("RepCreationDay.rightSentWindow", 0);
				considerTA = TimeSieveProperties.getBoolean("RepCreationDay.considerTA", true);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//Sieve Code
			
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = doc.getEventsBySentence();
		List<Timex> allTimexes = doc.getTimexes();
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
			
		// get list of DCT timexes - normally 1, sometimes < > 1
		List<Timex> dctTimexes = doc.getDocstamp();
		Timex dctTimex;
		
		// if there's only one, just use that one
		if (dctTimexes.size() == 1) {
			dctTimex = dctTimexes.get(0);
		}
		
		// if we don't know the DCT the sieve won't work!
		// we can use the most common date.
		else if (dctTimexes.size() == 0) {
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
		int numSentsInDoc = allEvents.size();
		for (Timex timex : allTimexes) {
			// only proceed if timex equals the dcd
			if (!validateTimex(timex, dcd)) continue;
			int timexSid = timex.sid();
			int sidLeft = Math.max(timexSid - leftSentWindow, 0);
			int sidRight = Math.min(timexSid + rightSentWindow, numSentsInDoc - 1);
			for (int sid = sidLeft; sid <= sidRight; sid++) {
				// only proceed if event is of type REPORTING
				for (TextEvent event : allEvents.get(sid)){
				if (!validateEvent(event)) continue;
				// label the reporting event as included in the dcd timex
				TLink.TYPE label = TLink.TYPE.IS_INCLUDED;
				proposed.add(new EventTimeLink(event.eiid() , timex.tid(), label));
				}
			}
		}
		
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
		}

	
	private String getDocCreationDate(Timex dctTimex) {
		String value = dctTimex.value();
		Matcher matcher = creationTimePattern.matcher(value);
		if (matcher.matches()) {
			String dcd = matcher.group(1);
			return dcd;
		}
		return null;
	}
	
	private Boolean validateTimex(Timex timex, String dcd){
		String value = timex.value();
		if (value.equals(dcd)) return true;
		else return false;
	}
	
	private Boolean validateEvent(TextEvent event){
		if (event.getTheClass()=="REPORTING")
			if (!considerTA) return true;
			else if (!event.getTense().equals("FUTURE") 
							 && !(event.getTense().equals("PAST") 
									 	&& event.getAspect().equals("PERFECTIVE"))) return true;
			else return false;
		else return false;
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
