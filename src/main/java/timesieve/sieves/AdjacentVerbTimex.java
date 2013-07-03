package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
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
 * 
 * AdjacentVerbTimex intends to classify event/time pairs from the same sentence
 * in which the two are fairly close within the sentence and the event is a verb.
 * Syntactic features are naive in that they are specified at the surface level.
 * Semantic features have not yet been specified.
 * 
 * The order of the event and the timex (which comes first in the sentence),
 * as well as the maximum distance between the two entities, is parameterized:
 * 1. adjType --> VerbTimex, TimexVerb, unordered
 * 		specifies the allowed ordering for event and timex
 * 2. numInterWords --> 0, 1, ...
 * 		specifies the maximum number of words allowed between event and timex.
 * 		if a given event/timex pair exceeds this number it is not labeled.
 * 
 * Initially we had the following results:
 ***adjType = VerbTimex*** (N is numInterWords)
 * N    prec.   c     t
 * 0 		p=0.95	20 of 21
 * 1 		p=0.84	31 of 37	
 * 2 		p=0.72	39 of 54
 * 3 		p=0.63	41 of 65
 * 4 		p=0.73	49 of 76
 * 
 ***adjType = TimexVerb*** (N is numInterWords)
 * 0		p=0.45	5 of 11
 * 
 ***adjType = Uunordered*** (N is numInterWords)
 * 0		p=0.78	25 of 32
 * 
 * After adding simple rules based on the preposition immediately
 * preceding the timex:
 * 
 *  N    prec.   c     t
 * 0 		p=0.95	20 of 21
 * 1 		p=0.89	33 of 37
 * 2 		p=0.83	45 of 54
 * 3 		p=0.75	49 of 65
 * 4 		p=0.75	57 of 76
 * 5		p=0.65	62 of 95  (just for fun, no comparison to previous) 
 * 6		p=0.63	65 of 104 (just for fun, no comparison to previous)
 * 
 * More to come for other values of adjType...
 * 
 * @author cassidy
 */
public class AdjacentVerbTimex implements Sieve {
	
	public boolean debug = true;
	private String adjType = "VerbTimex";
	private int numInterWords = 0;
	
	// Exclude timex that refer to "quarters" using this regex to be
	// applied to timex.value, since such a timex usually modifies an 
	// argument of the event verb, as opposed to serving as a stand-alone
	// temporal argument of the verb.
	private String valQuarterRegex = "\\d{4}-Q\\d";
	private Pattern valQuarter = Pattern.compile(valQuarterRegex);
	
	
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// PROPERTIES CODE
	
		// load properties file
		try {
			TimeSieveProperties.load();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		// fill in properties values
		try {
			adjType = TimeSieveProperties.getString("AdjacentVerbTimex.adjType", "VerbTimex");
			numInterWords = TimeSieveProperties.getInt("AdjacentVerbTimex.numInterWords", 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// SIEVE CODE
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Get list of sentences in document. Iterate over this list and look for verb/timex pairs to classify.
		List<SieveSentence> sentList = doc.getSentences();
		for( SieveSentence sent : sentList ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
				}
			// We'll need the parse tree from sentence to calculate a word's POS
			Tree tree = null;  // initialize to null so we don't end up loading it (e.g. if no timexes are in the sentence)
			// check timex, event pairs against rule criteria by iterating through timexes
			// in the sentence and checking if there are events within the specified number
			// of words of the timex (by property numInterWords)
			for (Timex timex : sent.timexes()) {
				// Check if the timex is a quarter timex and if so skip it
				// There's probably a better way to handle this...
				if (!validateTimex(timex)) continue;
				// for the fixed timex, iterate over events and if eligible,
				// according to constraints, classidy the event/timex pair
				for (TextEvent event : sent.events()) {
					// set adjacency setting based on adjacency type, numInterWords,
					// and eventToTimexDist
					int eventIndex = event.getIndex();
					int timexIndex = timex.getTokenOffset();
					int eventToTimexDist = timexIndex - eventIndex;
					boolean verbBeforeTimex = 
							(eventToTimexDist <= (numInterWords + 1) && eventToTimexDist >= 1);
					boolean timexBeforeVerb = 
							(eventToTimexDist*(-1) <= (numInterWords + 1) && eventToTimexDist <= -1);
					boolean adjSetting;
					
					// define adjSetting boolean according to adjType property
					if (adjType.equals("unordered"))     
						adjSetting = (verbBeforeTimex || timexBeforeVerb);
					else if (adjType.equals("TimexVerb")) 
						adjSetting = (timexBeforeVerb); 	
					else 
						adjSetting = (verbBeforeTimex);
				

					// If the adjacency condition is satisfied, then try to classify!
					if (adjSetting) { 
						// Only proceed if the event is a verb...
						String eventPos = posTagFromTree(tree, sent, eventIndex);
						if (!eventPos.startsWith("VB")) continue;
						// Now, classify pairs for various parameter settings
						// if adjacency is strict, we always default to is_included
						// of course, further considerations/conditions can be added within
						// the if-block
						if (numInterWords == 0) {
							proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED));
						}
						// if adjacency is not strict...
						else {
							// if verb is before timex, use the following rules...
							if (verbBeforeTimex) {
								// First, determine if there is a preposition immediately 
								// preceding the timex
								int precTimexIndex = timexIndex - 1;
								String precTimexPos = posTagFromTree(tree, sent, precTimexIndex);
								// IN is the tag for prepositions and suborinating conjunctions
								if (precTimexPos.equals("IN")) {
									// Different rules for different prepositions
									// As of now the rules are solely based on preposition string
									// TODO: add more details to rules based on properties of event,
									// timex, and surrounding context.
									String prepText = getTextAtIndex(precTimexIndex, sent); 
									
									if (prepText.equals("in")) {
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED));
									}
									else if (prepText.equals("on")) {
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED));
									}
									else if (prepText.equals("for")) {
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.SIMULTANEOUS));
									}
									else if (prepText.equals("at")) {
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.SIMULTANEOUS));
									}
									else if (prepText.equals("by")) {
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE));
									}
									else if (prepText.equals("over")){
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED));
									}
									else if (prepText.equals("during")){
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED));
									}
									else if (prepText.equals("throughout")){
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.SIMULTANEOUS));
									}
									else if (prepText.equals("within")){
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED));
									}
									else if (prepText.equals("until")){
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.BEFORE));
									}
									else if (prepText.equals("from")){
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.AFTER));
									}
									// If we encounter a different IN (prep/sub conj)
									else {
										proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED));
									}
								}
								// If the word right before the timex is not IN
								else {
									proposed.add(new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED));
								}
							}
						}
						
						
					}
				}
			}
		}
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
			}
		return proposed;
	}
	
	
	// validateTime ensures that timex value meets criteria
	private Boolean validateTimex(Timex timex){
		String val = timex.getValue();
		// Return false if timex value is not a date or is a quarter
		Matcher m = valQuarter.matcher(val);
		if (!m.matches()) return true;
		else return false;
	}
	
	// Given a sentence parse tree and an (sentence) index, return
	// the pos of the corresponding word.
	private String posTagFromTree(Tree tree, SieveSentence sent, int index) {
		// tree might be null; we keep it null until we need a pos for the first time for a given sentence
		if (tree == null) tree = sent.getParseTree(); 
		String pos = TreeOperator.indexToPOSTag(tree, index);
		return pos;
	}

	// Return the text of the token at a given index
	// Here, the index given to the function assumes that the first
	// index is 1. sent.tokens() does not count this way, so we
	// need to subtract 1 from index when retrieving our core label.
	private String getTextAtIndex(int index, SieveSentence sent) {
		CoreLabel cl = sent.tokens().get(index - 1);
		String text = cl.originalText();
		return text;
	}
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
