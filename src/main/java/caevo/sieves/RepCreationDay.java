package caevo.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.TextEvent;
import caevo.Timex;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.util.CaevoProperties;

/**
 *This sieve returns IS_INCLUDED for an event/timex pair of 
 *the form (class=REPORTING, val=DCT')
 *DCT' is DCT, truncated to the day granularity (e.g. 1998-01-08T03:00 --> 1998-01-08)
 *
 *
 *07/23/2013
 *There was a major bug here before; the many correct labels were repeated.
 *Recall is significantly lower now.
 *train:
 *includePresentRef = false
 *RepCreationDay			p=0.84	16 of 19	Non-VAGUE:	p=0.89	16 of 18 (consider tense and aspect)
 *RepCreationDay			p=0.81	17 of 21	Non-VAGUE:	p=0.89	17 of 19 (don't consider tense and aspect)
 *includePresentRef = true
 *RepCreationDay			p=0.79	19 of 24	Non-VAGUE:	p=0.90	19 of 21 (consider tense and aspect)
 *RepCreationDay			p=0.77	20 of 26	Non-VAGUE:	p=0.91	20 of 22 (don't consider tense and aspect)
 *dev:
 *(includePresentRef true or false)
 *RepCreationDay			p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1
 *
 * 
 * @author cassidy
 */
public class RepCreationDay implements Sieve {
	public boolean debug = false;
	private int leftSentWindow = 0;
	private int rightSentWindow = 0;
	private boolean considerTA = true;
	private boolean includePresentRef = false;
	private static String creationTimeRegex = "(\\d{4}-\\d{2}-\\d{2}).*";
	private static Pattern creationTimePattern = Pattern.compile(creationTimeRegex);
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
			try {
				leftSentWindow = CaevoProperties.getInt("RepCreationDay.leftSentWindow", 0);
				rightSentWindow = CaevoProperties.getInt("RepCreationDay.rightSentWindow", 0);
				considerTA = CaevoProperties.getBoolean("RepCreationDay.considerTA", true);
				includePresentRef = CaevoProperties.getBoolean("RepCreationDay.includePresentRef", false);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		//Sieve Code
			
		// Get list of events and timexes by sid; list of sentences
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
		
		// if we don't know the DCT the sieve won't work!
		// we could use the most common date...
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
		if (debug == true) {
			System.out.println("INFO:" + doc.getDocname() + ":" + dcd);
		}
		
		
		
		
		// check timexes/event pairs in each sentence against sieve criteria.
		int numSents = allTimexes.size();
		for(int sid = 0; sid < numSents; sid++) {
			for (Timex timex : allTimexes.get(sid)) {
				// only proceed if timex is valid (according to parameters)
				if (!validateTimex(timex, dcd)) continue;
				// Look at events in sentences whose sid are within the sent window (according to parameters)
				for (int sid2 = Math.max((sid - this.leftSentWindow), 0); 
						sid2 < numSents && sid2 <= (sid + this.rightSentWindow); sid2++) {
					for (TextEvent event : allEvents.get(sid2)) {
						// only proceed if event is valid (according to parameters)
						if (!validateEvent(event)) continue;
						// label the event as included in the dcd timex
						TLink.Type label = TLink.Type.IS_INCLUDED;
						proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), label));
			 }
			}
		 }
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
		// Always validate timex whose val is the document creation day
		if (value.equals(dcd)) return true;
		// If includePresentRef is set, then validate such timexes as well.
		else if (value.equals("PRESENT_REF") && this.includePresentRef == true) return true;
		else return false;
	}
	
	private Boolean validateEvent(TextEvent event){
		// Only consider reporting events
		if (event.getTheClass()== TextEvent.Class.REPORTING)
			// If considerTA is set, then don't include past perfective verbs.
			// Note that there are some instances of reporting verbs in the AP documents labeled
			// as past perfective that lack "had"
			if (!considerTA) return true;
			else if (!(event.getTense() == TextEvent.Tense.PAST 
									 	&& event.getAspect() == TextEvent.Aspect.PERFECTIVE)) return true;
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
