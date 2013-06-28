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

import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;

/**
 * Returns IS_INCLUDED for event, timex pairs for which
 * (1) event directly precedes timex
 * (2) timex is of type DATE
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
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// PROPERTIES CODE
		// 1. get adjacency type. this restricts acceptable verb-timex order
		// VerbTimex, TimexVerb, or unordered are the possible values.
		// VerbTimex is default; any value reverts to VerbTimex.
		
		// load properties file
		try {
			TimeSieveProperties.load();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// fill in properties values
		String adjType = null;
		
		try {
			adjType = TimeSieveProperties.getString("AdjacentVerbTimex", "VerbTimex");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// SIEVE CODE
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// 
		List<SieveSentence> sentList = doc.getSentences();
		for( SieveSentence sent : sentList ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
				}
			// get parse tree from sentence to calculate POS
			Tree tree = null;
			// check timex, event pairs against rule criteria
			for (Timex timex : sent.timexes()) {
				for (TextEvent event : sent.events()) {
					// set adjacency setting based on adjacency type 
					boolean adjSetting;
					if (adjType.equals("unordered")) adjSetting = (Math.abs(timex.offset() - event.index()) == 1);
					else if (adjType.equals("TimexVerb")) adjSetting = (timex.offset() + 1 == event.index());
					else adjSetting = (timex.offset() - 1 == event.index()); 
					
					// if the event/timex pair satisfies the adjacency setting,
					// and the event is a verb, then label the pair is_included
					if (adjSetting) { 
						String pos = posTagFromTree(tree, sent, event.index());
						if (pos.startsWith("VB")) {
							if (debug == true) {
								System.out.println("DEBUGPOS: " + event.string() + "/" + pos + " " + timex.text() + "/" + timex.value());
							}
							proposed.add(new EventTimeLink(event.eiid() , timex.tid(), TLink.TYPE.IS_INCLUDED));
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
		String val = timex.value();
		// Return false if timex value is not a date or is a quarter
		Matcher m = valQuarter.matcher(val);
		if (!m.matches() && timex.type().equals("DATE")) return false;
		else return false;
	}
	
	// given a sentence parse tree and an (sentence) index, return
	// the pos of the corresponding word.
	
	private String posTagFromTree(Tree tree, SieveSentence sent, int index) {
		// tree might be null; we keep it null until we need a pos for the first time for a given sentence
		if (tree == null) tree = sent.getParseTree(); 
		String pos = TreeOperator.indexToPOSTag(tree, index);
		return pos;
	}

	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
