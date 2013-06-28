package timesieve.sieves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.trees.Tree;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TLink.TYPE;
import timesieve.tlink.TimeTimeLink;
import timesieve.util.TreeOperator;

import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;

/**
 * Use Mapping from Derczynski & Gaizauskas (2013), as adapted
 * for our relation scheme. 
 * Only uses tense/aspect profile pairs that map to a single
 * interval relation.
 * This corresponds with D&G setting: "Same Sentence, same SR"
 * The temporal context heuristic used is as follows:
 * Two events are assumed to have the same reference time if they
 * occur in the same sentence and have the same tense
 * 
 * @author cassidy
 */
public class ReichenbachDG13_3 implements Sieve {
	public boolean debug = false;
	// create TreeFactory to convert a sentence to a tree
	private static TreeFactory tf = new LabeledScoredTreeFactory();
	
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
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = doc.getEventsBySentence();
		// list of all parse strings for the document
		List<Tree> trees = doc.getAllParseTrees();

		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Label event pairs that match sieve criteria
		int sid = 0;
		for( SieveSentence sent : doc.getSentences() ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
			}
			proposed.addAll(allPairsEvents(allEvents.get(sid), trees));
			sid++;
		}

		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
	}

	/**
	 * Extract Tense/Aspect profile and use tenseAspectToLabel to yield before or after
	 */
	private List<TLink> allPairsEvents(List<TextEvent> events, List<Tree> trees) {
		List<TLink> proposed = new ArrayList<TLink>();

		for( int xx = 0; xx < events.size(); xx++ ) {
			// Confirm event1's tense-aspect profile could be in the domain of tenseAspectToLabel 
			TextEvent e1 = events.get(xx);
			// only proceed if e1 is a verb...
			int sid = e1.sid();
			Tree sentParseTree = trees.get(sid);
			if (!posTagFromTree(sentParseTree, e1.index()).startsWith("VB")) continue;
			// map e1 tense and aspect to simplified version based on D&G's mapping
			String e1Tense = simplifyTense(e1.getTense());
			String e1Aspect = simplifyAspect(e1.getAspect());
			// only proceed if e1 has tense and aspect...
			if (e1Tense == null || e1Aspect == null) continue;
			for( int yy = xx+1; yy < events.size(); yy++ ) {
				// Confirm event2's tense-aspect profile could be in the domain of tenseAspectToLabel
				TextEvent e2 = events.get(yy);
				// only proceed if e2 is a verb...
				// note: we know here that e1 and e2 are in the same sentence
				// compare with RBDG_2 and RBDG_4 which does not assume this
				if (!posTagFromTree(sentParseTree, e2.index()).startsWith("VB")) continue;
				// map e2 tense and aspect to simplified version based on D&G's mapping
				String e2Tense = simplifyTense(e2.getTense());
				// first confirm e2 has the same tense as e1 (Same S-R ordering constraint!)
				// (provided that e2 has a non-null simplified tense)
				if (e2Tense == null || !e2Tense.equals(e1Tense)) continue;
				String e2Aspect = simplifyAspect(e2.getAspect());
				// only proceed if e2 has aspect...
				if (e2Aspect == null) continue;
				// Extract tense-aspect profiles, and label the pair accordingly
				String taProfilePair = e1Tense+"-"+e1Aspect+"/"+e2Tense+"-"+e2Aspect;
				TLink.TYPE label = taToLabel(taProfilePair);
				if (label != null){
				proposed.add(new EventEventLink(e1.eiid(), e2.eiid(), label));
				}
			}
		}
		
		if (debug == true) {
			System.out.println("events: " + events);
			System.out.println("created tlinks: " + proposed);
		}
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
	
	private String posTagFromTree(Tree sentParseTree, int tokenIndex){
		String posTag = TreeOperator.indexToPOSTag(sentParseTree, tokenIndex);
		return posTag;
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
