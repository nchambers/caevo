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
 * (2) event is in the past tense
 * (3) timex if of type DATE
 * (4) *I would like to add that event must be a verb - how do I do this?
 * 
 * Only considers event/time pairs in the same sentence.
 * 
 * @author cassidy
 */
public class AdjacentVerbTimex implements Sieve {
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
		for( Sentence sent : info.getSentences(docname) ) {
			System.out.println("DEBUG: adding tlinks from " + docname + " sentence " + sent.sentence());
			Tree sentParseTree = sidToTree(sid, allParseStrings);
			for (Timex timex : allTimexes.get(sid)) {
				for (TextEvent event : allEvents.get(sid)) {
					if (timex.offset() - 1 == event.index() &&  
							timex.type().equals("DATE") && validateTimex(timex) &&
							posTagFromTree(sentParseTree, event.index()).startsWith("VB")) {
						// System.out.println("DEBUGPOS: " + event.string() + "/" + posTagFromTree(sentParseTree, event.index()) + " " + timex.text() + "/" + timex.value());
						proposed.add(new EventTimeLink(event.eiid() , timex.tid(), TLink.TYPE.IS_INCLUDED));
						}
					}
				}
			sid++;
			}
			
		System.out.println("TLINKS: " + proposed);
		return proposed;
	}
	
	private Boolean validateEvent(TextEvent event){
		if (event.string().equals("ended")) return false;
		else return true;
	}
	/**
	 * validateTime ensures that timex value is of a certain form
	 * one option is: YYYY-MM-DD or YYYY-MM or YYYY
	 * The idea is that some time expressions are modifiers of arguments
	 * of the verb, and not arguments themselves. 
	 * For example, "X said Tuesday that ..." vs. "X said Tuesday's earnings were ..."
	 * In the latter case, 'said' may not be included_in Tuesday. A very common example
	 * is when the time expression is a quarter, since the verb is almost always after
	 * the quarter. 
	 */
	private Boolean validateTimex(Timex timex){
		String val = timex.value();
		// check if value represent a quarter
		Matcher m = valQuarter.matcher(val);
		if (m.matches()) return false;
		else return true;
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
