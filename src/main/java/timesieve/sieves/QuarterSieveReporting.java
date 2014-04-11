package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
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
 * The idea is that when a quarter expression like first, second, third, fourth
 * quarter, appears directly after a reporting verb, it tends to be the case that
 * the expression is not a temporal argument but rather modifies one of the verbs 
 * arguments. For example "company reported third quarter losses of $3 million...",
 * "third quarter" modifies" losses, and "third quarter losses" is itself an argument
 * of "reported".
 * 
 * @author cassidy
 */
public class QuarterSieveReporting implements Sieve {
	public boolean debug = false;
	// Regex checks if timex value indicates a quarter
	private String valQuarterRegex = "\\d{4}-Q\\d";
	private Pattern valQuarter = Pattern.compile(valQuarterRegex);
	// Regex to ensure that the text looks like "Xth quarter"
	private String textQuarterRegex = 
			"(first|second|third|fourth|1st|2nd|3rd|4th)(\\s|-)quarter";
	private Pattern textQuarter = Pattern.compile(textQuarterRegex);
	
	// create TreeFactory to convert a sentence to a tree
	private static TreeFactory tf = new LabeledScoredTreeFactory();
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = doc.getEventsBySentence();
		List<List<Timex>> allTimexes = doc.getTimexesBySentence();
		// list of all parse strings for the document
		List<Tree> trees = doc.getAllParseTrees();
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// check timexes/event pairs in each sentence against sieve criteria.
		int sid = 0;
		for( SieveSentence sent : doc.getSentences() ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
			}
			// get parse tree of sentence
			Tree sentParseTree = trees.get(sid);
			for (Timex timex : allTimexes.get(sid)) {
				// only proceed if timex is of form YYYY-QX
				if (!validateTimex(timex)) continue;
				System.out.println("DEBUG: encountered QUARTER timex");
				for (TextEvent event : allEvents.get(sid)) {
					// only proceed if event is of type REPORTING
					if (!validateEvent(event)) continue;
					// check if timex/event pair satisfy positional criteria
					// (ie that they occur within one word of one another)
					int timexIndex = timex.getTokenOffset();
					int eventIndex = event.getIndex();
					if (timexIndex - 1 == eventIndex) {
						TLink.Type label = TLink.Type.IS_INCLUDED;
						proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), label));
					}
					else if (timexIndex - 2 == event.getIndex()) {
						int interIndex = timexIndex - 1;
						String interWord = getTokenText(interIndex, sent);
						TLink.Type label = classifyInter(interWord);
						proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), label));
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
	
	private String getTokenText(int index, SieveSentence sent) {
		List<CoreLabel> tokens = sent.tokens();
		CoreLabel token = tokens.get(index);
		return token.originalText();
	}

	// returns the label appropriate for the word between the verb and quarter timex
	// could be improved by checking to see if the quarter is to occur in the future,
	// in which case BEFORE might be more appropriate
	private TLink.Type classifyInter(String interText){
		if (interText == "in") return TLink.Type.IS_INCLUDED;
		else {
			return TLink.Type.AFTER;
		}
	}
	
	private Boolean validateEvent(TextEvent event) {
		if (event.getTheClass() == TextEvent.Class.REPORTING) return true;
		else return false;
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
		String val = timex.getValue();
		String text = timex.getText();
		// check if value represent a quarter
		Matcher matchVal = valQuarter.matcher(val);
		Matcher matchText = valQuarter.matcher(text);
		if (matchText.matches() && matchVal.matches()) return true;
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
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
