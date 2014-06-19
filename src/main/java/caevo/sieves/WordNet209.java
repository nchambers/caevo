package caevo.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import caevo.Main;
import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.TextEvent;
import caevo.Timex;
import caevo.tlink.EventEventLink;
import caevo.tlink.TLink;
import caevo.tlink.TimeTimeLink;
import caevo.util.CaevoProperties;
import caevo.util.TreeOperator;
import net.didion.jwnl.data.POS;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;

/**
 * This Sieve labels event-event and time-time pairs based on whether
 * they (1) are possibly synonomous according to wordnet 
 * and/or (2) share the same lemma
 * 
 *  Parameters
 *  
 *  checkSiblings: label if the two words share the same synset
 *  checkLemmas: label if the two words share the same lemma
 *   (NOTE: we expect pairs licensed for checking by checkLemmas to be a subset
 *    of those licensed by checkSiblings!)
 *  sentWindow: label pairs of words within this many sentences
 *  ignoreReporting: ignore pair if either verb is a reporting verb
 *  ignoreIAction: ignore pair if either verb is an intentional action
 *  ignoreSameSentence: ignore pair if from the same sentence
 *  ttPairs: consider timex-timex pairs
 *  eePairs: consider event-event pairs
 *  
 *  
 *  Label timexes with the same wordnet lemma as simultaneous
 *  Label events with the same wordnet lemma, or whose wordnet synsets overlap,
 *  as vague. 
 *  
 *  
 *  Some results
 *  (NOTE: I think there is a problem with my enumeration of event pairs - more below)
 *  
 *  checkSiblings(y)checkLemmas(y)sentWindow(0)ignoreReporting(n)ignoreIAction(n)
 *  ignoreSameSentence(n)ttPairs(y)eePairs(y)
 *  WordNet209			p=0.52	13 of 25	Non-VAGUE:	p=0.54	13 of 24
 *  
 *  checkSiblings(y)checkLemmas(y)sentWindow(1)ignoreReporting(n)ignoreIAction(n)
 *  ignoreSameSentence(n)ttPairs(y)eePairs(y)
 *  WordNet209			p=0.75	75 of 100	Non-VAGUE:	p=0.76	75 of 99
 *  WordNet209			p=0.77	69 of 90	Non-VAGUE:	p=0.78	69 of 89 (ignoreIaction(y))
 *  WordNet209			p=0.65	34 of 52	Non-VAGUE:	p=0.67	34 of 51 (ignoreIaction/reporting(y))
 *  only check lemmas ( checkSiblings(n)checkLemmas(y) )
 *  WordNet209			p=0.61	23 of 38	Non-VAGUE:	p=0.62	23 of 37
 *  
 *  checkSiblings(y)checkLemmas(y)sentWindow(1)ignoreReporting(n)ignoreIAction(n)
 *  ignoreSameSentence(y)ttPairs(y)eePairs(y)
 *  WordNet209			p=0.77	65 of 84	Non-VAGUE:	p=0.77	65 of 84
 *  only check lemmas ( checkSiblings(n)checkLemmas(y) )
 *  WordNet209			p=0.61	20 of 33	Non-VAGUE:	p=0.62	23 of 37
 *  
 *  ==> I think there is a bug - Shouldn't the denominators line up (first + third = second)?
 *  (25 + 84 != 100). Since we are checking if two events are in the same sentence
 *  before classifying, the mistake is probably that we are missing 9 pairs during the 
 *  enumeration of the 100 for the second case above. Which 9? 
 *  
 *  
 * @author cassidy
 */

public class WordNet209 implements Sieve {
	public boolean debug = false;
	private static TreeFactory tf = new LabeledScoredTreeFactory();
	private int sentWindow = 1;
	private boolean checkSiblings = false;
	private boolean checkLemmas = false;
	private boolean ignoreReporting = false;
	private boolean ignoreIAction = false;
	private boolean ignoreSameSentence = false;
	private boolean eePairs = true;
	private boolean ttPairs = true;
	
	private static final Map<String, POS> postagSimpleToPOS;
  static
  {
  	postagSimpleToPOS = new HashMap<String, POS>();
  	postagSimpleToPOS.put("VB", POS.VERB);
  	postagSimpleToPOS.put("NN", POS.NOUN);
  	postagSimpleToPOS.put("JJ", POS.ADJECTIVE);
  	postagSimpleToPOS.put("RB", POS.ADVERB);
  }
	/**
	 * The main function. All sieves must have this.
	 */
	/* (non-Javadoc)
	 * @see timesieve.sieves.Sieve#annotate(timesieve.InfoFile, java.lang.String, java.util.List)
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
				// Get property values from the config file
				try {
					sentWindow = CaevoProperties.getInt("Wordnet209.sentWindow", 1);
					checkSiblings = CaevoProperties.getBoolean("Wordnet209.checkSiblings", true);
					checkLemmas = CaevoProperties.getBoolean("Wordnet209.checkLemmas", true);
					ignoreReporting = CaevoProperties.getBoolean("Wordnet209.ignoreReporting", true);
					ignoreIAction = CaevoProperties.getBoolean("Wordnet209.ignoreIAction", true);
					ignoreSameSentence = CaevoProperties.getBoolean("Wordnet209.ignoreSameSentence", true);
					eePairs = CaevoProperties.getBoolean("Wordnet209.eePairs", true);
					ttPairs = CaevoProperties.getBoolean("Wordnet209.ttPairs", true);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				// proposed will hold all TLinks proposed by the sieve
				List<TLink> proposed = new ArrayList<TLink>();
				
				// get all events, timexes, by sentence
				List<List<TextEvent>> allEvents = doc.getEventsBySentence();
				List<List<Timex>> allTimexes = doc.getTimexesBySentence();
				
				// we need all trees in order to get pos tags
				List<Tree> trees = doc.getAllParseTrees();
				
				// hold all pairs to be classified (in accordance with sentWindow)
				// in array lists. No need for ET pairs here.
				ArrayList<TextEvent[]> eventPairs = getEventPairs(allEvents);
				ArrayList<Timex[]> TimexPairs = getTimexPairs(allTimexes);
				
			// classify each type of pair based on parameter settings
			if (eePairs == true){
				for (TextEvent[] eventPair : eventPairs) {
					TLink tlink = getEELink(eventPair[0], eventPair[1], trees);
					if (tlink != null) {
						proposed.add(tlink);
					}
				}
			}
			if (ttPairs == true){
				for (Timex[] timexPair : TimexPairs) {
					TLink tlink = getTTLink(timexPair[0], timexPair[1], trees);
					if (tlink != null) {
						proposed.add(tlink);
					}
				}
			}
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
	}
	
	// Get all pairs of events that need to be classified
	// TODO Note that if ignoreSameSentence is true, such pairs are excluded in
	// getLink() - this is not entirely efficient.
	private ArrayList<TextEvent[]> getEventPairs(List<List<TextEvent>> allEvents){
		// for each event, compare is with all events in range, in accordance
		// with sentWindow.
		ArrayList<TextEvent[]> eventPairs = new ArrayList<TextEvent[]>(); 
		int numSents = allEvents.size();
		// iterate over each sentence
		for (int sid = 0; sid < numSents; sid ++) {
			// iterate over events in the sent that corresponds with sid
			int numEvents = allEvents.get(sid).size();
			for (int i = 0; i < numEvents; i++) {
				// get the event in the list at index i
				TextEvent e1 = allEvents.get(sid).get(i);
				// iterate over remaining events in the sentence
				for (int j = i + 1; j < numEvents; j++) {
					TextEvent e2 = allEvents.get(sid).get(j);
					TextEvent[] eventPair = {e1, e2};
					eventPairs.add(eventPair);
					// if the label is null, the mapping couldn't be applied.
					// otherwise, add (e1, e2, label) to proposed.
				}
				// iterate over other events in subsequent sentences in accordance
				// with sentWindow.
				// Note that if sentWindow == 0, the loop will never start since
				// sid2 <= sid + sentWindow will never be satisfied
				for (int sid2 = sid + 1; 
						sid2 <= sid + sentWindow && sid2 < numSents; sid2++) {
					// iterate over each event in a given window sentence
					int numEvents2 = allEvents.get(sid2).size();
					// compare e1 with all events in the sentence with id sid2
					for (int k = 0; k < numEvents2; k++) {
						// label (e1, e2, label) only if label is not null, as in above
						TextEvent e2 = allEvents.get(sid2).get(k);
						TextEvent[] eventPair = {e1, e2};
						eventPairs.add(eventPair);
					}
				}
			}
		}
		return eventPairs;
	}
	// Get all pairs of timexes that need to be classified
	// TODO Note that if ignoreSameSentence is true, such pairs are excluded in
	// getLink() - this is not entirely efficient.
	private ArrayList<Timex[]> getTimexPairs(List<List<Timex>> allTimexes){
		// for each event, compare is with all events in range, in accordance
		// with sentWindow.
		ArrayList<Timex[]> timexPairs = new ArrayList<Timex[]>(); 
		int numSents = allTimexes.size();
		// iterate over each sentence
		for (int sid = 0; sid < numSents; sid ++) {
			// iterate over events in the sent that corresponds with sid
			int numTimexes = allTimexes.get(sid).size();
			for (int i = 0; i < numTimexes; i++) {
				// get the event in the list at index i
				Timex e1 = allTimexes.get(sid).get(i);
				// iterate over remaining events in the sentence
				for (int j = i + 1; j < numTimexes; j++) {
					Timex e2 = allTimexes.get(sid).get(j);
					Timex[] timexPair = {e1, e2};
					timexPairs.add(timexPair);
				}
				// iterate over other events in subsequent sentences in accordance
				// with sentWindow.
				// Note that if sentWindow == 0, the loop will never start since
				// sid2 <= sid + sentWindow will never be satisfied
				for (int sid2 = sid + 1; 
						sid2 <= sid + sentWindow && sid2 < numSents; sid2++) {
					// iterate over each event in a given window sentence
					int numEvents2 = allTimexes.get(sid2).size();
					// compare e1 with all events in the sentence with id sid2
					for (int k = 0; k < numEvents2; k++) {
						// label (e1, e2, label) only if label is not null, as in above
						Timex e2 = allTimexes.get(sid2).get(k);
						Timex[] timexPair = {e1, e2};
						timexPairs.add(timexPair);
					}
				}
			}
		}
		return timexPairs;
	}
	/**
	 * all pairs of events that are siblings (i.e. their WordNet synsets overlap) 
	 * are labeled VAGUE. D&G use SIMULTANEOUS.
	 */
		private TLink getEELink(TextEvent e1, TextEvent e2, List<Tree> trees) {
			// Check if any of the ignore criteria hold
			// If so, do not classify the pair
			boolean e1IsReporting = e1.getTheClass() == TextEvent.Class.REPORTING;
			boolean e2IsReporting = e2.getTheClass() == TextEvent.Class.REPORTING;
			if ( ignoreReporting &&  ( e1IsReporting || e2IsReporting) 
					|| ignoreIAction && e1.getTheClass() == TextEvent.Class.I_ACTION
					|| ignoreSameSentence && e1.getSid() == e2.getSid()) 
				{return null;}
			// Get the word, lemma, and pos tag 
			// (the penn treebank string and the more basic wordnet POS via postagSimpleToPOS)
			Tree sentParseTree1 = trees.get(e1.getSid());
			Tree sentParseTree2 = trees.get(e2.getSid());
			String postagStr1 = posTagFromTree(sentParseTree1, e1.getIndex());
			String postagStr2 = posTagFromTree(sentParseTree2, e2.getIndex());
			
			if (postagStr1.length() < 2 || postagStr2.length() < 2) {
				return null;
			}
			
			String postagSimple1 = postagStr1.substring(0,2);
			String postagSimple2 = postagStr2.substring(0,2);
			POS pos1 = postagSimpleToPOS.get(postagSimple1);
			POS pos2 = postagSimpleToPOS.get(postagSimple2);
			String lemma1 = Main.wordnet.lemmatizeTaggedWord(e1.getString(), postagStr1);
			String lemma2 = Main.wordnet.lemmatizeTaggedWord(e2.getString(), postagStr2);
			//Synset[] syns1 = Main.wordnet.synsetsOf(e1.getString(), pos1);
			//Synset[] syns2 = Main.wordnet.synsetsOf(e2.getString(), pos2);
			// Only proceed if pos1 and pos2 are non-null and equal,
			// and if so check if they are siblings.
			// Note that areSiblings appears be resistant to the case when
			// two words have the same lemma and (basic) pos but appear in 
			// different morphological forms - presumably because the method ultimately
			// compares two synsets, which are fixed. D'Souza and Ng's rule says
			// to check if the headWordLemma of the event is within the synset of the other;
			// I don't think it is necessary to check that here.
			if (checkSiblings && pos1 != null && pos2 != null && pos1 == pos2
					&& Main.wordnet.areSiblings(e1.getString(), e2.getString(), pos1)) {
				return new EventEventLink(e1.getEiid() , e2.getEiid(), TLink.Type.VAGUE);
			}
			if (checkLemmas && lemma1.equals(lemma2)) {
				return new EventEventLink(e1.getEiid() , e2.getEiid(), TLink.Type.VAGUE);	
				}
			else {
				return null;
			}
		}

		private TLink getTTLink(Timex e1, Timex e2, List<Tree> trees) {
			//
			if (ignoreSameSentence && e1.getSid() == e2.getSid()) return null;
			// Get the word, lemma, and pos tag 
			// (the penn treebank string and the more basic wordnet POS via postagSimpleToPOS)
			Tree sentParseTree1 = trees.get(e1.getSid());
			Tree sentParseTree2 = trees.get(e2.getSid());
			String postagStr1 = posTagFromTree(sentParseTree1, e1.getTokenOffset());
			String postagStr2 = posTagFromTree(sentParseTree2, e2.getTokenOffset());
			
			if (postagStr1.length() < 2 || postagStr2.length() < 2) {
				return null;
			}
			
			String postagSimple1 = postagStr1.substring(0,2);
			String postagSimple2 = postagStr2.substring(0,2);
			
			POS pos1 = postagSimpleToPOS.get(postagSimple1);
			POS pos2 = postagSimpleToPOS.get(postagSimple2);
			String lemma1 = Main.wordnet.lemmatizeTaggedWord(e1.getText(), postagStr1);
			String lemma2 = Main.wordnet.lemmatizeTaggedWord(e2.getText(), postagStr2);
			//Synset[] syns1 = Main.wordnet.synsetsOf(e1.getString(), pos1);
			//Synset[] syns2 = Main.wordnet.synsetsOf(e2.getString(), pos2);
			// Only proceed if pos1 and pos2 are non-null and equal,
			// and if so check if they are siblings.
			// Note that areSiblings appears be resistant to the case when
			// two words have the same lemma and (basic) pos but appear in 
			// different morphological forms - presumably because the method ultimately
			// compares two synsets, which are fixed. D'Souza and Ng's rule says
			// to check if the headWordLemma of the event is within the synset of the other;
			// I don't think it is necessary to check that here.
			//if (checkSiblings && pos1 != null && pos2 != null && pos1 == pos2
				//	&& Main.wordnet.areSiblings(e1.getText(), e2.getText(), pos1)) {
				//return new TimeTimeLink(e1.getTid() , e2.getTid(), TLink.Type.SIMULTANEOUS);
			//}
			if (checkLemmas && lemma1.equals(lemma2)) {
				return new TimeTimeLink(e1.getTid() , e2.getTid(), TLink.Type.SIMULTANEOUS);	
				}
			else {
				return null;
			}
		}
		
		// Use posTagFromTree(sidToTree(*),i) to get pos tag for index i in a sentence
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
