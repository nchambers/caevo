package timesieve.sieves;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveDocumentsAnalyzer;
import timesieve.TextEvent;
import timesieve.TextEventPairPattern;
import timesieve.tlink.TLink;
import timesieve.tlink.EventEventLink;

/**
 * Looks at training data to find out if certain pairs of tenses, classes,
 * and aspects typically are related by VAGUE TLinks, and then makes up rules
 * based on these patterns.  Rules are created for pairs of event types that tend
 * to have VAGUE links above a certain precision across a minimum number of 
 * examples.
 * 
 * Current results:
 * min_p: .7 min_ex: 20 => .75 (462 of 614)
 * min_p: .8 min_ex: 20 => .84 (179 of 214)
 * min_p: .85 min_ex: 20 => .87 (71 of 82)
 *
 * @author Bill McDowell
 */
public class FrequencyVagueSieve implements Sieve {
	/* FIXME: MOVE TO PROPERTIES */
	private static String RULE_SAVE_PATH = "src/main/resources/models/tlinks/FrequencyVagueSieve";
	private static double MINIMUM_RULE_PRECISION = .70;
	private static int MINIMUM_PRECISION_EXAMPLES = 60;
	
	/* The following static integers and matrix encode which types of rules are constructed by this sieve.
	 * Each static "INDEX" value is used to index into the arrays in the matrix.
   * Each array in the matrix encodes a particular type of rule.
   * Each type of rule matches a subset of the attributes for events. 
   * For example, one rule type considers only event classes, whereas another considers classes and tenses.
   * An array A encodes a rule type considering attribute X iff A[X_INDEX]=true.
   */
	private static int CLASS_INDEX = 0;
	private static int TENSE_INDEX = 1;
	private static int ASPECT_INDEX = 2;
	private static int SENTENCE_INDEX = 3;
	private static boolean[][] distinguishedFeatures = { {true,false,false,true},
																											 {false,true,false,true},
																											 {false,false,true,true},
																											 {true,true,false,true},
																											 {true,false,true,true},
																											 {false,true,true,true},
																											 {true,true,true,true}
																										 };
	
	private HashSet<TextEventPairPattern> vaguePatterns;
	
	public FrequencyVagueSieve() {
		this.vaguePatterns = new HashSet<TextEventPairPattern>();
		
		/* FIXME: Put this somewhere else */
  	try {
  		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FrequencyVagueSieve.RULE_SAVE_PATH));
  		Object o = ois.readObject();
  		ois.close();
  		this.vaguePatterns = (HashSet<TextEventPairPattern>)o;
  	} catch(Exception ex) { 
  		System.out.println("FrequencyVagueSieve: Had fatal trouble loading " + FrequencyVagueSieve.RULE_SAVE_PATH); 
  	}
	}
	
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<TLink> proposed = new ArrayList<TLink>();
		
		List<TLink> sentencePairLinks = annotateBySentencePair(doc);
		
		if (sentencePairLinks != null)
			proposed.addAll(sentencePairLinks);

		return proposed;
	}
	
	public List<TLink> annotateBySentencePair(SieveDocument doc) {
		List<List<TextEvent>> sentenceEvents = doc.getEventsBySentence();
		List<TLink> proposed = new ArrayList<TLink>();
		
		for (int s = 0; s < sentenceEvents.size(); s++) {
			for (int e1 = 0; e1 < sentenceEvents.get(s).size(); e1++) {						
				for (int e2 = e1 + 1; e2 < sentenceEvents.get(s).size(); e2++) {
					TLink link = this.orderEvents(sentenceEvents.get(s).get(e1), sentenceEvents.get(s).get(e2));
					if (link != null) 
						proposed.add(link);
				}
				
				if (s + 1 < sentenceEvents.size()) {
					for (int t2 = 0; t2 < sentenceEvents.get(s+1).size(); t2++) {
						TLink link = this.orderEvents(sentenceEvents.get(s).get(e1), sentenceEvents.get(s+1).get(t2));
						if (link != null) 
							proposed.add(link);
					}
				}
			}
		}
		
		return proposed;
	}
	
	private TLink orderEvents(TextEvent event1, TextEvent event2) {
		TextEventPairPattern pattern = new TextEventPairPattern();
		for (int t = 0; t < FrequencyVagueSieve.distinguishedFeatures.length; t++) {
			pattern.setFromCanonicalEvent(event1, event2, 
																						FrequencyVagueSieve.distinguishedFeatures[t][CLASS_INDEX], 
																						FrequencyVagueSieve.distinguishedFeatures[t][TENSE_INDEX],
																						FrequencyVagueSieve.distinguishedFeatures[t][ASPECT_INDEX],
																						FrequencyVagueSieve.distinguishedFeatures[t][SENTENCE_INDEX]);
		
			
			if (this.vaguePatterns.contains(pattern)) {
				return new EventEventLink(event1.getEiid(), event2.getEiid(), TLink.Type.VAGUE);
			}
		
		}
		
		return null;
	}
	

	/*
	 * Keep track of types of event pairs which are almost always vague, given at least some number
	 * of examples.
	 */
	public void train(SieveDocuments trainingInfo) {
		this.vaguePatterns = new HashSet<TextEventPairPattern>();
		SieveDocumentsAnalyzer analyzer = new SieveDocumentsAnalyzer(trainingInfo);
		for (int t = 0; t < FrequencyVagueSieve.distinguishedFeatures.length; t++) {
			HashMap<TextEventPairPattern, HashMap<TLink.Type, Integer>> linkTypeCounts = 
					analyzer.getEventEventLinkCounts(FrequencyVagueSieve.distinguishedFeatures[t][CLASS_INDEX], 
																					 FrequencyVagueSieve.distinguishedFeatures[t][TENSE_INDEX],
																					 FrequencyVagueSieve.distinguishedFeatures[t][ASPECT_INDEX],
																					 FrequencyVagueSieve.distinguishedFeatures[t][SENTENCE_INDEX]);
	
			for (Entry<TextEventPairPattern, HashMap<TLink.Type, Integer>> e1 : linkTypeCounts.entrySet()) {
				if (!e1.getValue().containsKey(TLink.Type.VAGUE))
					continue;
				
				int totalCount = 0;
				for (Entry<TLink.Type, Integer> e2 : e1.getValue().entrySet()) {
					totalCount += e2.getValue();
				}
				
				double precision = e1.getValue().get(TLink.Type.VAGUE)/(double)totalCount;
				if (precision >= FrequencyVagueSieve.MINIMUM_RULE_PRECISION
				 && totalCount >= FrequencyVagueSieve.MINIMUM_PRECISION_EXAMPLES) {
					this.vaguePatterns.add(e1.getKey());
					System.out.println("FrequencyVagueSieve found rule: " + e1.getKey() + " Precision: " + precision + " Count: " + totalCount);
				}
				
			}
		}
		
		/* FIXME: Put this somewhere else */
		try {
			FileOutputStream fos = new FileOutputStream(FrequencyVagueSieve.RULE_SAVE_PATH);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this.vaguePatterns);
			oos.flush();
			oos.close();
		} catch (Exception e) {
  		System.out.println("Had fatal trouble loading " + FrequencyVagueSieve.RULE_SAVE_PATH);
  		e.printStackTrace(); System.exit(1); 
		}
	}

}