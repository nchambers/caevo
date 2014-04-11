package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TLinkClassifier;
import timesieve.tlink.TLinkDatum;
import timesieve.tlink.TLinkFeaturizer;
import timesieve.util.Pair;
import timesieve.util.TimeSieveProperties;
import timesieve.util.Util;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;

/**
 * Machine learned event-event pairs inter-sentence (one sentence away).
 * 
 * MLEventEventDiffSent            p=0.40  160 of 404      Non-VAGUE:      p=0.45  160 of 359
 * (Baseline 0.43)
 *
 * @author chambers
 */
public class MLEventEventDiffSent implements Sieve {
	Classifier<String,String> eeDiffSentClassifier = null; // inter-sentence event-event links.
  Classifier<String,String> eeDiffSentExistsClassifier = null; // binary, is there a link or not?
  TLinkFeaturizer featurizer;
  
  String eeDiffSentName = "tlink.ee.diffsent.classifier";
  
  boolean eesplit = false;
  boolean debug = true;
  int featMinOccurrence = 2;
  
  /**
   * Constructor uses the global properties for parameters.
   */
	public MLEventEventDiffSent() {
		// Setup the featurizer for event-event intrasentence links.
		featurizer = new TLinkFeaturizer();
		featurizer._eventEventOnly = true;
    featurizer._eventTimeOnly = false;
    featurizer._eventDCTOnly = false;
    featurizer._sameSentenceOnly = false;
    featurizer._neighborSentenceOnly = true;
    featurizer._ignoreSameSentence = true; // redundancy is good :)
    featurizer._eventEventNoDominates = false;
    featurizer._eventEventDominates = false;
		
		init();
	}
	
	private void init() {
		// Flags
		try {
  		debug = TimeSieveProperties.getBoolean("MLEventEventDiffSent.debug",false);
		} catch( IOException ex ) { }
		
		readClassifiers();
	}
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// Classifier loading must have failed in init()
		if( eeDiffSentClassifier == null )
			return null;
		
		return extractDiffSentenceEventEventLinks(doc);
	}

	
  /**
   * Put event-event links into the global .info file between same sentence event-event links.
   */
  public List<TLink> extractDiffSentenceEventEventLinks(SieveDocument doc) {
    List<SieveSentence> sentences = doc.getSentences();
    if( debug ) System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Loop over each sentence and get TLinks.
    for( int sid = 0; sid < sentences.size()-1; sid++ ) {
    	SieveSentence sent = sentences.get(sid);
    	SieveSentence sentNext = sentences.get(sid+1);
    	List<TextEvent> events = sent.events();
    	List<TextEvent> eventsNext = sentNext.events();

      for( int ii = 0; ii < events.size(); ii++ ) {
      	TextEvent event1 = events.get(ii);

      	for( int jj = 0; jj < eventsNext.size(); jj++ ) {
      		TextEvent event2 = eventsNext.get(jj);
      		TLink link = createInterSentenceEELink(doc, event1, event2);
      		tlinks.add(link);
      	}
      }
    }
    if( debug ) System.out.println("Returning diff sentence e-e tlinks: " + tlinks);
    return tlinks;
  }
  
  
  private EventEventLink createInterSentenceEELink(SieveDocument doc, TextEvent event1, TextEvent event2) {
    // Normal, 1 classifier for all event-event links.
    Classifier<String,String> targetClassifier = eeDiffSentClassifier;

    // Get the best label and its probability.
    TLinkDatum datum = featurizer.createEventEventDatum(doc, event1, event2, null);
    RVFDatum<String,String> rvf = datum.createRVFDatum();
    Pair<String,Double> labelProb = TLinkClassifier.getLabelProb(targetClassifier, rvf);
    String label = labelProb.first();

    // Create the actual link with the classified label.
    EventEventLink link = new EventEventLink(event1.getEiid(), event2.getEiid(), TLink.Type.valueOf(label));
    link.setRelationConfidence(labelProb.second());
    return link;
  }
  
  
  private void readClassifiers() {
  	String path = "/models/tlinks/" + eeDiffSentName;
  	eeDiffSentClassifier = Util.readClassifierFromFile(this.getClass().getResource(path));
  }
  
  
	/**
	 * Train on the documents.
	 */
	public void train(SieveDocuments docs) {
//		featurizer.debug = true;
		List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, null);
    System.out.println("Final training data size: " + data.size());
    
    if( debug ){
    	for( TLinkDatum dd : data ) {
    		System.out.println("** " + dd._originalTLink);    		
    		System.out.println(dd);
    	}
    }
    
    eeDiffSentClassifier = TLinkClassifier.train(data, featMinOccurrence);    
    
    try {
    	IOUtils.writeObjectToFile(eeDiffSentClassifier, eeDiffSentName);
    } catch(Exception ex) {
    	System.out.println("ERROR: couldn't write classifiers to file in MLEventEventDiffSent");
    	ex.printStackTrace();
    }
	}

}
