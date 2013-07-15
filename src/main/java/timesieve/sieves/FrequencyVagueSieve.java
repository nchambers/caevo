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
import timesieve.util.TimeSieveProperties;

/**
 * FrequencyVagueSieve looks at training data to find out if certain pairs of tenses, classes,
 * and aspects typically are related by VAGUE TLinks, and then makes up rules
 * based on these patterns.  Rules are created for pairs of event types that tend
 * to have VAGUE links above a certain precision across a minimum number of 
 * examples.
 * 
 * NOTE: MLVagueSieve should be used instead of this.
 * 
 * The following table contains the current results for the train and dev sets when 
 * training on train.
 * 
 * p_min    ex_min      train-set                 dev-set
 * .6       20          0.66 (932 of 1410)        0.50 (100 of 200)
 * .7       20          0.76 (358 of 472)         0.48 (31 of 65)
 * .8       20          0.83 (186 of 225)         0.41 (14 of 34)
 * .6       80          0.65 (326 of 499)         0.51 (31 of 61)
 * .7       80          0.72 (58 of 81)           1.00 (6 of 6)
 * .7       50          0.75 (233 of 311)         0.52 (26 of 50)
 * .8       50          0.83 (99 of 120)          0.45 (10 of 22)
 * 
 * The current default setting is p_min=.7 and ex_min=50
 *
 * @author Bill McDowell
 */
public class FrequencyVagueSieve implements Sieve {
	private String ruleSavePath;
	private double minRulePrecision;
	private int minRuleExamples;
	
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
	private static boolean[][] distinguishedFeatures = 
		{	{true,false,false,false},
			{false,true,false,false},
			{false,false,true,false},
			{false,false,false,true},
			{true,false,false,true},
		  {false,true,false,true},
		  {false,false,true,true},
			{true,false,true,false},
			{false,true,true,false},
		  {true,true,false,false},
		  {true,true,false,true},
		  {true,false,true,true},
		  {false,true,true,true},
		  {true,true,true,true}
	 };
	
	private HashSet<TextEventPairPattern> vaguePatterns;
	
	public FrequencyVagueSieve() {
		this.vaguePatterns = new HashSet<TextEventPairPattern>();
			
		try {
			this.ruleSavePath = TimeSieveProperties.getString("FrequencyVagueSieve.ruleSavePath", "src/main/resources/models/tlinks/FrequencyVagueSieve");
			this.minRulePrecision = TimeSieveProperties.getDouble("FrequencyVagueSieve.minRulePrecision", .7);
			this.minRuleExamples = TimeSieveProperties.getInt("FrequencyVagueSieve.minRuleExamples", 50);

  		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(this.ruleSavePath));
  		Object o = ois.readObject();
  		ois.close();
  		this.vaguePatterns = (HashSet<TextEventPairPattern>)o;
  	} catch(Exception ex) { 
  		System.out.println("FrequencyVagueSieve: Had fatal trouble loading " + this.ruleSavePath); 
  		ex.printStackTrace();
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
				if (precision >= this.minRulePrecision
				 && totalCount >= this.minRuleExamples) {
					this.vaguePatterns.add(e1.getKey());
					System.out.println("FrequencyVagueSieve found rule: " + e1.getKey() + " Precision: " + precision + " Count: " + totalCount);
				}
				
			}
		}
		
		try {
			FileOutputStream fos = new FileOutputStream(this.ruleSavePath);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this.vaguePatterns);
			oos.flush();
			oos.close();
		} catch (Exception e) {
  		System.out.println("Had fatal trouble loading " + this.ruleSavePath);
  		e.printStackTrace(); System.exit(1); 
		}
	}

}