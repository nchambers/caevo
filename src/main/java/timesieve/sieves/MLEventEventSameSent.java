package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.trees.Tree;

import timesieve.Main;
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

/**
 * Machine learned event-event pairs intra-sentence.
 * 
 * Right now this just makes one classifier for all event-event pairs, and doesn't use the
 * specific ones for syntactic dominance.
 * 
 * @author chambers
 */
public class MLEventEventSameSent implements Sieve {
	Classifier<String,String> eeSameSentClassifier = null; // intra-sentence event-event links.
  Classifier<String,String> eeSameSentExistsClassifier = null; // binary, is there a link or not?
  Classifier<String,String> eeSameSentDominatesClassifier = null;   // intra-sentence, event-event syntactically dominates
  Classifier<String,String> eeSameSentNoDominatesClassifier = null; // intra-sentence, event-event no syntactically dominates
  TLinkFeaturizer featurizer;
  
  String eeSameSentName = "tlink.ee.samesent.classifier-all";
  
  boolean eesplit = false;
  boolean debug = true;
  int featMinOccurrence = 2;
  
  /**
   * Constructor uses the global properties for parameters.
   */
	public MLEventEventSameSent() {
		// Setup the featurizer for event-event intrasentence links.
		featurizer = new TLinkFeaturizer();
		featurizer._eventEventOnly = true;
    featurizer._eventTimeOnly = false;
    featurizer._eventDCTOnly = false;
    featurizer._sameSentenceOnly = true;
    featurizer._ignoreSameSentence = false;
    featurizer._eventEventNoDominates = false;
    featurizer._eventEventDominates = false;
		
		init();
		System.out.println("Wordnet test: running -> " + Main.wordnet.lemmatizeTaggedWord("running", "VBG"));
		System.out.println("Wordnet test: house physical -> " + Main.wordnet.isPhysicalObject("house"));
	}
	
	private void init() {
		// Flags
		try {
  		eesplit = TimeSieveProperties.getBoolean("MLEventEventSameSent.eesplit",false);
  		debug = TimeSieveProperties.getBoolean("MLEventEventSameSent.debug",false);
		} catch( IOException ex ) { }
		
		readClassifiers();
	}
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// Classifier loading must have failed in init()
		if( eeSameSentClassifier == null )
			return null;
		
		return extractSameSentenceEventEventLinks(doc);
	}

	
  /**
   * Put event-event links into the global .info file between same sentence event-event links.
   */
  public List<TLink> extractSameSentenceEventEventLinks(SieveDocument doc) {
    List<SieveSentence> sentences = doc.getSentences();
    if( debug ) System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Loop over each sentence and get TLinks.
    for( SieveSentence sent : sentences ) {
      List<TextEvent> events = sent.events();

      if( debug ) System.out.println("events: " + events);
      for( int ii = 0; ii < events.size()-1; ii++ ) {
      	TextEvent event1 = events.get(ii);

      	for( int jj = ii+1; jj < events.size(); jj++ ) {
      		TextEvent event2 = events.get(jj);
      		TLink link = createIntraSentenceEELink(doc, event1, event2);
      		tlinks.add(link);
      	}
      }
    }
    if( debug ) System.out.println("Returning same e-e tlinks: " + tlinks);
    return tlinks;
  }
  
  
  private EventEventLink createIntraSentenceEELink(SieveDocument doc, TextEvent event1, TextEvent event2) {
    // Normal, 1 classifier for all event-event links.
    Classifier<String,String> targetClassifier = eeSameSentClassifier;
    List<Tree> trees = doc.getAllParseTrees();

    // Use 2 classifiers for event-event links. One for syntactic dominance, the other for general.
    if( eesplit ) {
    	if( featurizer.oneEventDominates(event1, event2, trees) )
    		targetClassifier = eeSameSentDominatesClassifier;
    	else
    		targetClassifier = eeSameSentNoDominatesClassifier;
    }

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
  	String path = "/models/tlinks/" + eeSameSentName;
  	eeSameSentClassifier = Util.readClassifierFromFile(this.getClass().getResource(path));
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
    
    eeSameSentClassifier = TLinkClassifier.train(data, featMinOccurrence);    
    
    try {
    	IOUtils.writeObjectToFile(eeSameSentClassifier, eeSameSentName);
    } catch(Exception ex) {
    	System.out.println("ERROR: couldn't write classifiers to file in MLEventEventSameSent");
    	ex.printStackTrace();
    }
	}

}
