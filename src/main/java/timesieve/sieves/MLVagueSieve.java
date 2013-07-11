package timesieve.sieves;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveDocumentsAnalyzer;
import timesieve.TextEvent;
import timesieve.TextEventPairPattern;
import timesieve.tlink.TLink;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLinkClassifier;
import timesieve.tlink.TLinkDatum;
import timesieve.util.Pair;
import timesieve.util.Util;

/**
 *  
 *
 * @author Bill McDowell
 */
public class MLVagueSieve implements Sieve {
	/* FIXME: MOVE ALL THESE ATTRIBUTES TO PROPERTIES */
	private static String MODEL_SAVE_PATH = "src/main/resources/models/tlinks/MLVagueSieve";
	private static int MINIMUM_FEATURE_OCCURRENCE = 5;

	/* Indicate whether to include various attributes of events in the model */
	private boolean includeClass = true;
	private boolean includeTense = true;
	private boolean includeAspect = true;
	private boolean includeSentence = true;
	
	/* TLink types to classify (everything else is NONE) */
	private HashSet<TLink.Type> includeTypes = new HashSet<TLink.Type>();
	private Classifier<String, String> model;
	
	public MLVagueSieve() {
		File f = new File(MLVagueSieve.MODEL_SAVE_PATH);
		if (f.exists())
			this.model = Util.readClassifierFromFile(MLVagueSieve.MODEL_SAVE_PATH);
		
		this.includeTypes.add(TLink.Type.VAGUE);
		this.includeTypes.add(TLink.Type.BEFORE);
		this.includeTypes.add(TLink.Type.AFTER);
		this.includeTypes.add(TLink.Type.INCLUDES);
		this.includeTypes.add(TLink.Type.IS_INCLUDED);
		this.includeTypes.add(TLink.Type.SIMULTANEOUS);
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
		int x = 0;
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
    
    if (linkType != TLink.Type.NONE && labelProb.second() > .6) {
    	TLink link = new EventEventLink(event1.getEiid(), event2.getEiid(), linkType);
    	link.setRelationConfidence(labelProb.second());
    	return link;
    } else {
    	return null;
    }
	}
	
	private TLinkDatum textEventsToUnlabelledDatum(TextEvent event1, TextEvent event2) {
    TLinkDatum datum = new TLinkDatum();
    TextEventPairPattern pattern = new TextEventPairPattern(event1, event2, this.includeClass, this.includeTense, this.includeAspect, this.includeSentence);
    
    datum.addFeature(pattern.toString());
    
    return datum;
	}
	

	public void train(SieveDocuments trainingInfo) {
		SieveDocumentsAnalyzer analyzer = new SieveDocumentsAnalyzer(trainingInfo);
		HashMap<TextEventPairPattern, HashMap<TLink.Type, Integer>> linkTypeCounts = 
				analyzer.getEventEventLinkCounts(this.includeClass, 
																				 this.includeTense,
																				 this.includeAspect,
																				 this.includeSentence);
		
		List<TLinkDatum> data = new ArrayList<TLinkDatum>();
		for (Entry<TextEventPairPattern, HashMap<TLink.Type, Integer>> e1 : linkTypeCounts.entrySet()) {
			for (Entry<TLink.Type, Integer> e2 : e1.getValue().entrySet()) {
				TLink.Type type = TLink.Type.NONE;
				if (this.includeTypes.contains(e2.getKey()))
					type = e2.getKey();

				for (int i = 0; i < e2.getValue(); i++) {
					TLinkDatum datum = new TLinkDatum(type);
					datum.addFeature(e1.getKey().toString());
					data.add(datum);
				}
			}
		}
		
    this.model = TLinkClassifier.train(data, MLVagueSieve.MINIMUM_FEATURE_OCCURRENCE);
    
    try {
    	IOUtils.writeObjectToFile(this.model, MLVagueSieve.MODEL_SAVE_PATH);
    } catch(Exception ex) {
    	System.out.println("ERROR: couldn't write classifiers to file in MLEventEventSameSent");
    	ex.printStackTrace();
    }
	}

}