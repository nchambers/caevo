package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
import timesieve.util.TreeOperator;

import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;

/**
 * Returns IS_INCLUDED for event, timex pairs for which
 * (1) event directly precedes timex
 * (2) timex if of type DATE
 * 
 * Only considers event/time pairs in the same sentence.
 * 
 * @author cassidy
 */
public class AdjacentVerbTimex implements Sieve {
	// Exclude timex that refer to "quarters" using this regex to be
	// applied to timex.value, since such a timex usually modifies an 
	// argument of the event verb, as opposed to serving as a stand-alone
	// temporal argument of the verb.
	public boolean debug = false;
	private String valQuarterRegex = "\\d{4}-Q\\d";
	private Pattern valQuarter = Pattern.compile(valQuarterRegex);
	private static TreeFactory tf = new LabeledScoredTreeFactory();
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence(docname);
		List<List<Timex>> allTimexes = info.getTimexesBySentence(docname);
		List<String> allParseStrings = info.getParses(docname);
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Make BEFORE links between all intra-sentence pairs.
		int sid = 0;
		List<Sentence> sentList = info.getSentences(docname);
		
		for( Sentence sent : sentList ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + docname + " sentence " + sent.sentence());
				}
			// get parse tree from sentence to calculate POS
			Tree sentParseTree = sidToTree(sid, allParseStrings);
			// check timex, event pairs against rule criteria
			for (Timex timex : allTimexes.get(sid)) {
				for (TextEvent event : allEvents.get(sid)) {
					// if event directly precedes timex,
					// timex is a date but isn't a quarter,
					// and event is a verb, add an event-to-time is_included tlink
					if (timex.offset() - 1 == event.index() && validateTimex(timex) &&
							posTagFromTree(sentParseTree, event.index()).startsWith("VB")) {
						if (debug == true) {
							System.out.println("DEBUGPOS: " + event.string() + "/" + posTagFromTree(sentParseTree, event.index()) + " " + timex.text() + "/" + timex.value());
							}
						proposed.add(new EventTimeLink(event.eiid() , timex.tid(), TLink.TYPE.IS_INCLUDED));
						}
					}
				}
			sid++;
			}
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
			}
		return proposed;
	}
	
	
	// validateTime ensures that timex value meets criteria
	private Boolean validateTimex(Timex timex){
		String val = timex.value();
		// Return false if timex value is not a date or is a quarter
		Matcher m = valQuarter.matcher(val);
		if (!m.matches() && timex.type().equals("DATE")) return false;
		else return false;
	}
	
	private String posTagFromTree(Tree sentParseTree, int tokenIndex){
		String posTag = TreeOperator.indexToPOSTag(sentParseTree, tokenIndex);
		return posTag;
	}
	
	private Tree sidToTree(int sid, List<String> allParseStrings) {
		String sentParseString = allParseStrings.get(sid);
		Tree sentParseTree = TreeOperator.stringToTree(sentParseString, tf);
		return sentParseTree;
	}
	/**
	 * No training. Just rule-based.
	 */
	public void train(InfoFile trainingInfo) {
		// no training
	}

}
