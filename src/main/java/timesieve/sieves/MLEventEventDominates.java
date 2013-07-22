package timesieve.sieves;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.trees.Tree;

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
import timesieve.util.TimebankUtil;
import timesieve.util.Util;

/**
 * Machine learned event-event pairs that are in a syntactic domination relationship.
 * 
 * Right now this just makes one classifier for all event-event pairs, and doesn't use the
 * specific ones for syntactic dominance.
 * 
 * MLEventEventDominates           p=0.47  60 of 128       Non-VAGUE:      p=0.54  60 of 112
 * (Baseline 0.43)
 * 
 * @author chambers
 */
public class MLEventEventDominates extends MLEventEventSameSent {
  Classifier<String,String> eeSameSentDominatesClassifier = null;   // intra-sentence, event-event syntactically dominates
  String modelName = "tlink.ee.dominates.classifier";
  
  int featMinOccurrence = 2;
  double minProb = 0.0;
  
  /**
   * Constructor uses the global properties for parameters.
   */
	public MLEventEventDominates() {
		// Setup the featurizer for event-event intrasentence links.
		featurizer = new TLinkFeaturizer();
		featurizer._eventEventOnly = true;
    featurizer._eventTimeOnly = false;
    featurizer._eventDCTOnly = false;
    featurizer._sameSentenceOnly = true;
    featurizer._ignoreSameSentence = false;
    featurizer._eventEventNoDominates = false;
    featurizer._eventEventDominates = true;
		
		init();
	}
	
	private void init() {
		// Flags
		try {
  		debug = TimeSieveProperties.getBoolean("MLEventEventDominates.debug",false);
		} catch( IOException ex ) { }
		
		readClassifiers();
	}

	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// Classifier loading must have failed in init()
		if( eeSameSentDominatesClassifier == null )
			return null;
		
		List<TLink> labeled = extractEventEventDominatesLinks(doc);
		
		if( debug ) printLabelStats(labeled);
		
		TimebankUtil.trimLowProbability(labeled, minProb);
		
		// Trim out any NONE links (from binary classifier decisions)
		Set<TLink> removal = new HashSet<TLink>();
		for( TLink link : labeled )
			if( link.getRelation() == TLink.Type.NONE )
				removal.add(link);
		
		if( debug ) System.out.println("Labeled " + labeled.size() + " and will remove " + removal.size());
		
		// Remove the NONEs
		for( TLink link : removal ) labeled.remove(link);
		
		if( debug ) printLabelStats(labeled);
		return labeled;
	}

	
  /**
   * Put event-event links into the global .info file between same sentence event-event links.
   */
  public List<TLink> extractEventEventDominatesLinks(SieveDocument doc) {
    List<SieveSentence> sentences = doc.getSentences();
    if( debug ) System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Loop over each sentence and get TLinks.
    for( SieveSentence sent : sentences ) {
      List<TextEvent> events = sent.events();
      Tree tree = sent.getParseTree();

      if( debug ) System.out.println("events: " + events);
      for( int ii = 0; ii < events.size()-1; ii++ ) {
      	TextEvent event1 = events.get(ii);
      	for( int jj = ii+1; jj < events.size(); jj++ ) {
      		TextEvent event2 = events.get(jj);
      		if( featurizer.oneEventDominates(event1, event2, tree) ) {
      			TLink link = createIntraSentenceEELink(doc, event1, event2);
//      			if( link.getRelation() != TLink.Type.VAGUE )
      				tlinks.add(link);
      		}
      	}
      }
    }
    if( debug ) System.out.println("Returning same e-e tlinks: " + tlinks);
    return tlinks;
  }
  
  
  private EventEventLink createIntraSentenceEELink(SieveDocument doc, TextEvent event1, TextEvent event2) {
    // Normal, 1 classifier for all event-event links.
    Classifier<String,String> targetClassifier = eeSameSentDominatesClassifier;

    // Get the best label and its probability.
    TLinkDatum datum = featurizer.createEventEventDatum(doc, event1, event2, null);
    RVFDatum<String,String> rvf = datum.createRVFDatum();
    Pair<String,Double> labelProb = TLinkClassifier.getLabelProb(targetClassifier, rvf);
    String label = labelProb.first();

    // Create the actual link with the classified label.
    EventEventLink link = new EventEventLink(event1.getEiid(), event2.getEiid(), TLink.Type.valueOf(label));
    link.setRelationConfidence(labelProb.second());
    if( debug ) System.out.println(link + "\t prob=" + labelProb.second());
    return link;
  }
  
  
  /**
   * Load the previously trained classifiers from our model directory.
   */
  private void readClassifiers() {
  	String path = modelDir + File.separator + modelName;
  	eeSameSentDominatesClassifier = Util.readClassifierFromFile(this.getClass().getResource(path));
  	if( eeSameSentDominatesClassifier == null )
  		System.out.println("ERROR: MLEventEventDominates could not read its classifier at: " + path);
  }
  
  private void writeClassifier(Classifier<String,String> classifier, String filename) {
    try {
    	IOUtils.writeObjectToFile(classifier, filename);
    } catch(Exception ex) {
    	System.out.println("ERROR: couldn't write classifier " + filename + " in MLEventEventDominates");
    	ex.printStackTrace();
    }
  }
    
	/**
	 * Train on the documents.
	 */
	public void train(SieveDocuments docs) {
    //		featurizer.debug = true;
		List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, null);
    System.out.println("Final training data size: " + data.size());

    // Train the multi-class classifier.
    eeSameSentDominatesClassifier = TLinkClassifier.train(data, featMinOccurrence);    
    writeClassifier(eeSameSentDominatesClassifier, modelName);    
	}

}
