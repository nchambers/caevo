package timesieve.sieves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TLink.TYPE;
import timesieve.tlink.TimeTimeLink;

/**
 * Use Mapping from Derczynski & Gaizauskas (2013), as adapted
 * for our relation scheme. 
 * Only uses tense/aspect profile pairs that map to a single
 * interval relation.
 * This corresponds with D&G setting: "Same/Adjacent Sentence, same SR"
 * The temporal context heuristic used is as follows:
 * Two events are assumed to have the same reference time if they
 * occur within one sentence of each other, and they have the same tense.
 * 
 * @author cassidy
 */
public class ReichenbachDG13_4 implements Sieve {
	
	// Map tense/aspect pairs to corresponding relation
	private static final Map<String, TLink.TYPE> tenseAspectToLabel;
  static
  {
  	tenseAspectToLabel = new HashMap<String, TLink.TYPE>();
  	tenseAspectToLabel.put("PAST-NONE/PAST-PERFECTIVE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("PAST-NONE/FUTURE-NONE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PAST-NONE/FUTURE-PERFECTIVE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PAST-PERFECTIVE/PAST-NONE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PAST-PERFECTIVE/PRES-NONE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PAST-PERFECTIVE/PRESENT-PERFECTIVE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PAST-PERFECTIVE/FUTURE-NONE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PAST-PERFECTIVE/FUTURE-PERFECTIVE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PRESENT-NONE/PAST-PERFECTIVE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("PRESENT-NONE/FUTURE-NONE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PRESENT-PERFECTIVE/PAST-PERFECTIVE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("PRESENT-PERFECTIVE/FUTURE-NONE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("PRESENT-PERFECTIVE/FUTURE-PERFECTIVE", TLink.TYPE.BEFORE);
  	tenseAspectToLabel.put("FUTURE-NONE/PAST-NONE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("FUTURE-NONE/PAST-PERFECTIVE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("FUTURE-NONE/PRESENT-NONE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("FUTURE-NONE/PRESENT-PERFECTIVE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("FUTURE-PERFECTIVE/PAST-NONE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("FUTURE-PERFECTIVE/PAST-PERFECTIVE", TLink.TYPE.AFTER);
  	tenseAspectToLabel.put("FUTURE-PERFECTIVE/PRESENT-PERFECTIVE", TLink.TYPE.AFTER);
  }
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence(docname);

		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Make BEFORE links between all intra-sentence pairs.
		List<Sentence> sentList = info.getSentences(docname);
		int numSentences = sentList.size();
		int sid = 0;
		
		for( Sentence sent : sentList ) {
			if (sid == numSentences - 1) continue;
			System.out.println("DEBUG: adding tlinks from " + docname + " sentences:\n" + sent.sentence()
													+ "\n" + sent.sentence());
			List<TextEvent> allEventsSents = new ArrayList<TextEvent>();
			allEventsSents.addAll(allEvents.get(sid));
			allEventsSents.addAll(allEvents.get(sid + 1));
			proposed.addAll(allPairsEvents(allEventsSents));
			sid++;
		}

		System.out.println("TLINKS: " + proposed);
		return proposed;
	}

	/**
	 * Extract Tense/Aspect profile and use tenseAspectToLabel to yield before or after
	 */
	private List<TLink> allPairsEvents(List<TextEvent> events) {
		List<TLink> proposed = new ArrayList<TLink>();

		for( int xx = 0; xx < events.size(); xx++ ) {
			// Confirm event1's tense-aspect profile could be in the domain of tenseAspectToLabel 
			TextEvent e1 = events.get(xx);
			String e1Tense = simplifyTense(e1.getTense());
			String e1Aspect = simplifyAspect(e1.getAspect());
			if (e1Tense == null || e1Aspect == null) continue;
			for( int yy = xx+1; yy < events.size(); yy++ ) {
			// Confirm event2's tense-aspect profile could be in the domain of tenseAspectToLabel
				TextEvent e2 = events.get(yy);
				String e2Tense = simplifyTense(e2.getTense());
				if (!e2Tense.equals(e1Tense)) continue; //apply Same SR constraint
				String e2Aspect = simplifyAspect(e2.getAspect());
				if (e2Tense == null || e2Aspect == null) continue;
				// Extract tense-aspect profiles, and label the pair accordingly
				String taProfilePair = e1Tense+"-"+e1Aspect+"/"+e2Tense+"-"+e2Aspect;
				TLink.TYPE label = taToLabel(taProfilePair);
				if (label != null){
				proposed.add(new EventEventLink(e1.eiid(), e2.eiid(), label));
				}
			}
		}
		
		System.out.println("events: " + events);
		System.out.println("created tlinks: " + proposed);
		return proposed;
	}
	
	private TLink.TYPE taToLabel(String taProfilePair) {
		return tenseAspectToLabel.get(taProfilePair);
	}
	
	private String simplifyTense(String tense){
		if (tense.equals("PRESPART")) return "PRESENT";
		else if (tense.equals("PASTPART")) return "PAST";
		else if (tense.equals("PAST") || tense.equals("PRESENT") || tense.equals("FUTURE")) {
			return tense;
		}
		else return null; 
	}
		
	private String simplifyAspect(String aspect){
		if (aspect.equals("PERFECTIVE_PROGRESSIVE")) return "PERFECTIVE";
		else if (aspect.equals("IMPERFECTIVE") || aspect.equals("IMPERFECTIVE_PROGRESSIVE") || aspect.equals("NONE")) {
			return aspect;
		}
		else return null; 
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(InfoFile trainingInfo) {
		// no training
	}

}
