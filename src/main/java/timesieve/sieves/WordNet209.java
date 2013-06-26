package timesieve.sieves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;

import net.didion.jwnl.data.POS;

import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.util.*;

/**
 * Implements rule number 209, in the category of "WORDNET", from D'Souza & Ng 2013.
 * In English: 
 * If e1 and e2 are events, and they are members of the same synset, 
 * Then label them SIMULTANEOUS.
 * 
 * Only considers events within the same sentence
 * 
 * This rule performs terribly on our dataset. I suspect that many pairs
 * that match the rule's criteria are pairs of "said", which are probably
 * almost always vague.
 * 
 * @author cassidy
 */

public class WordNet209 implements Sieve {
	public boolean debug = false;
	private static TreeFactory tf = new LabeledScoredTreeFactory();
	
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
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence(docname);
		// list of all parse strings for the document
		List<String> allParseStrings = info.getParses(docname);
		// Use wn to extract WordNet derived information about events
		WordNet wn = new WordNet(WordNet.findWordnetPath()); 
			
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Obtain all event pairs within one sentence of one another
		List<Sentence> sentList = info.getSentences(docname);
		int sid = 0;
		
		for ( Sentence sent : sentList ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + docname + " sentences:\n" + sent.sentence() + "\n" + sent.sentence());
			}
			proposed.addAll(allPairsEvents(allEvents.get(sid), allParseStrings, sid, wn));
			sid ++;
		}
		
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
	}
	
	/**
	 * all pairs of events that are siblings (i.e. their WordNet synsets overlap) 
	 * are labeled SIMULTANEOUS.
	 */
		private List<TLink> allPairsEvents(List<TextEvent> events, List<String> allParseStrings, int sid, WordNet wn) {
			List<TLink> proposed = new ArrayList<TLink>();
			// scan through all pairs of events
			for( int xx = 0; xx < events.size(); xx++ ) {
				for( int yy = xx+1; yy < events.size(); yy++ ) {
					TextEvent e1 = events.get(xx);
					TextEvent e2 = events.get(yy);
					Tree sentParseTree = sidToTree(sid, allParseStrings);
					
					String postagStr1 = posTagFromTree(sentParseTree, e1.index());
					String postagStr2 = posTagFromTree(sentParseTree, e2.index());
					String postagSimple1 = postagStr1.substring(0,2);
					String postagSimple2 = postagStr2.substring(0,2);
					POS pos1 = postagSimpleToPOS.get(postagSimple1);
					POS pos2 = postagSimpleToPOS.get(postagSimple2);
					// only proceed if pos1 and pos2 are non-null and equal
					if (pos1 != null && pos2 != null && pos1.equals(pos2)) {
						// finally check if e1 and e2 are siblings
						if (wn.areSiblings(e1.string(), e2.string(), pos1)) {
							proposed.add(new EventEventLink(e1.eiid() , e2.eiid(), TLink.TYPE.SIMULTANEOUS));
						}
					}
				}
			}
			
			if (debug == true) {
				System.out.println("events: " + events);
				System.out.println("created tlinks: " + proposed);
			}
			return proposed;
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
	public void train(InfoFile trainingInfo) {
		// no training
	}

}
