package timesieve.sieves;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.TextEvent;
import timesieve.TextEventPairPattern;
import timesieve.tlink.TLink;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLinkClassifier;
import timesieve.tlink.TLinkDatum;
import timesieve.util.Pair;
import timesieve.util.TimeSieveProperties;
import timesieve.util.Util;

/**
 *  MLVagueSieve learns a maximum entropy model which uses features representing the classes, tenses, 
 *  aspects, and sentence-ids of two events to determine whether or not the link between the events is 
 *  vague. There is a occurrence_min (minFeatureOccurrence) parameter which determines the minimum number 
 *  of times a feature must occur in the training data for it to show up in the model, and a
 *  confidence_min (minConfidence) parameter which determines the minimum confidence of the model
 *  required for it to output a vague label for a given pair.  
 *  
 *  The following table contains the current results for different values of the parameters.  The table
 *  also contains results for when the model contains only simple features (only_simple), and for when
 *  it contains not only simple features (~only_simple).  A simple feature is one where only a single property
 *  of events is considered, and a non-simple feature is one where combinations of several properties
 *  of events are considered.  For example, there is a simple feature which indicates whether the tense of the first event
 *  is PAST and the second event is FUTURE, and there is a non-simple feature which indicates whether the tense and
 *  aspect for the first event are PAST and PROGRESSIVE and for the second event are FUTURE and NONE.  Using
 *  non-simple features slightly boosts the recall, but has almost no effect on precision.
 *  
 *                occurrence_min  confidence_min  train-set               dev-set
 *  ~only_simple  30              0.5             0.65 (1165 of 1804)     0.51 (145 of 284)
 *  ~only_simple  30              0.6             0.70 (857 of 1224)      0.54 (100 of 186)
 *  ~only_simple  30              0.7             0.78 (354 of 456)       0.59 (45 of 76)
 *  ~only_simple  80              0.5             0.62 (1180 of 1918)     0.48 (148 of 308)
 *  ~only_simple  80              0.6             0.67 (838 of 1255)      0.53 (99 of 188)
 *  ~only_simple  80              0.7             0.70 (131 of 187)       0.71 (25 of 35)
 *  only_simple   30              0.5             0.62 (1270 of 2050)     0.50 (161 of 320)
 *  only_simple   30              0.6             0.69 (679 of 978)       0.54 (81 of 150)
 *  only_simple   30              0.7             0.78 (262 of 336)       0.59 (38 of 64)
 *  only_simple   80              0.5             0.61 (1216 of 1998)     0.50 (154 of 310)
 *  only_simple   80              0.6             0.66 (837 of 1260)      0.53 (97 of 183)
 *  only_simple   80              0.7             0.72 (112 of 156)       0.75 (21 of 28)
 *
 *  The current default setting is to use all features (not only simple) with occurrence_min=80 and 
 *  confidence_min=.6.
 *
 * @author Bill McDowell
 */
public class MLVagueSieve implements Sieve {
	private String modelSavePath;
	private int minFeatureOccurrence;
	private double minConfidence;

	/* The following static integers and matrix encode which types of features are constructed by this sieve.
	 * Each static "INDEX" value is used to index into the arrays in the matrix.
   * Each array in the matrix encodes a particular type of feature.
   * Each type of rule matches a combination of some event attributes.
   * For example, one feature type considers only event class combinations, whereas another considers combinations of classes and tenses.
   * An array A encodes a feature type containing attribute X iff A[X_INDEX]=true.
   */
	private static int CLASS_INDEX = 0;
	private static int TENSE_INDEX = 1;
	private static int ASPECT_INDEX = 2;
	private static int SENTENCE_INDEX = 3;
	private static boolean[][] distinguishedFeatures = 
		{	{true,false,false,false}, // Simple feature
			{false,true,false,false}, // Simple feature
			{false,false,true,false}, // Simple feature
			{false,false,false,true}, // Simple feature
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
	
	/* TLink types to classify (everything else is NONE) */
	private HashSet<TLink.Type> includeTypes = new HashSet<TLink.Type>();
	private Classifier<String, String> model;
	
	public MLVagueSieve() {
		try {
			this.modelSavePath = TimeSieveProperties.getString("MLVagueSieve.modelSavePath", "src/main/resources/models/tlinks/MLVagueSieve");
			this.minFeatureOccurrence = TimeSieveProperties.getInt("MLVagueSieve.minFeatureOccurrence", 80);
			this.minConfidence = TimeSieveProperties.getDouble("MLVagueSieve.minConfidence", 0.7);
			
			File f = new File(this.modelSavePath);
			if (f.exists())
				this.model = Util.readClassifierFromFile(this.modelSavePath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		this.includeTypes.add(TLink.Type.VAGUE);
		//this.includeTypes.add(TLink.Type.BEFORE);
		//this.includeTypes.add(TLink.Type.AFTER);
	  //this.includeTypes.add(TLink.Type.INCLUDES);
		//this.includeTypes.add(TLink.Type.IS_INCLUDED);
		//this.includeTypes.add(TLink.Type.SIMULTANEOUS);
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
		
		if (this.model == null)
			return proposed;
		
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
    TLinkDatum datum = textEventsToUnlabelledDatum(event1, event2);
    RVFDatum<String,String> rvf = datum.createRVFDatum();
    
    Pair<String,Double> labelProb = TLinkClassifier.getLabelProb(this.model, rvf);
    TLink.Type linkType = TLink.Type.valueOf(labelProb.first());
    
    if (linkType != TLink.Type.NONE && labelProb.second() >= this.minConfidence) {
    	TLink link = new EventEventLink(event1.getEiid(), event2.getEiid(), linkType);
    	link.setRelationConfidence(labelProb.second());
    	return link;
    } else {
    	return null;
    }
	}
	
	private TLinkDatum textEventsToUnlabelledDatum(TextEvent event1, TextEvent event2) {
    TLinkDatum datum = new TLinkDatum();
    TextEventPairPattern pattern = new TextEventPairPattern();
    for (int i = 0; i < distinguishedFeatures.length; i++) {
    		pattern.setFromCanonicalEvent(event1, event2, distinguishedFeatures[i][CLASS_INDEX], 
    																									distinguishedFeatures[i][TENSE_INDEX],
    																									distinguishedFeatures[i][ASPECT_INDEX],
    																									distinguishedFeatures[i][SENTENCE_INDEX]);
    		datum.addFeature(pattern.toString());
    }
    
    return datum;
	}
	

	public void train(SieveDocuments trainingInfo) {
		List<TLinkDatum> data = new ArrayList<TLinkDatum>();
		List<SieveDocument> docs = trainingInfo.getDocuments();
		for (SieveDocument doc : docs) {
			List<TLink> links = doc.getTlinksOfType(EventEventLink.class);
			for (TLink link : links) {
				TextEvent e1 = doc.getEventByEiid(link.getId1());
				TextEvent e2 = doc.getEventByEiid(link.getId2());
				
				if (e1 == null || e2 == null) {
					System.err.println("WARNING: TLink points at " + doc.getDocname() + " missing events " + link.getId1() + " or " + link.getId2() + ".");
					continue;
				}
				
				TLink.Type type = TLink.Type.NONE;
				if (this.includeTypes.contains(link.getRelation()))
					type = link.getRelation();
				
				TLinkDatum datum = new TLinkDatum(type);
				
				for (int i = 0; i < distinguishedFeatures.length; i++) {
					TextEventPairPattern pattern = new TextEventPairPattern(e1, e2, 
							 distinguishedFeatures[i][CLASS_INDEX], 
							 distinguishedFeatures[i][TENSE_INDEX], 
							 distinguishedFeatures[i][ASPECT_INDEX], 
							 distinguishedFeatures[i][SENTENCE_INDEX]);
					
					
					datum.addFeature(pattern.toString());
				}
				
				data.add(datum);
			}
		}
		
    this.model = TLinkClassifier.train(data, this.minFeatureOccurrence);
    
    try {
    	IOUtils.writeObjectToFile(this.model, this.modelSavePath);
    } catch(Exception ex) {
    	System.out.println("ERROR: couldn't write classifiers to file in MLEventEventSameSent");
    	ex.printStackTrace();
    }
	}

}