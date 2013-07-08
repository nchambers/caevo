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
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;

import timesieve.Main;
import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TLinkClassifier;
import timesieve.tlink.TLinkDatum;
import timesieve.tlink.TLinkFeaturizer;
import timesieve.tlink.TimeTimeLink;
import timesieve.util.Pair;
import timesieve.util.TimeSieveProperties;
import timesieve.util.Util;

/**
 * JUST AN EXAMPLE
 * Stupid sieve that shows how to access basic data structures.
 * It generates BEFORE links between all intra-sentence pairs.
 * 
 * @author chambers
 */
public class MLEventEventSameSent implements Sieve {
	Classifier<String,String> eeSameSentClassifier; // intra-sentence event-event links.
  Classifier<String,String> eeSameSentExistsClassifier; // binary, is there a link or not?
  Classifier<String,String> eeSameSentDominatesClassifier;   // intra-sentence, event-event syntactically dominates
  Classifier<String,String> eeSameSentNoDominatesClassifier; // intra-sentence, event-event no syntactically dominates
  TLinkFeaturizer featurizer;
  
  String eeSameSentName = "tlink.ee.samesent.classifier-all";
  boolean eesplit = false;
  boolean debug = true;
  int featMinOccurrence = 2;
  
  /**
   * Constructor uses the global properties for parameters.
   */
	public MLEventEventSameSent() {
		System.out.println("CONSTRUCTOR MLEventEventSameSent");

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
  		eesplit = TimeSieveProperties.getBoolean("MLEventEventSameSent.eesplit");
  		debug = TimeSieveProperties.getBoolean("MLEventEventSameSent.debug");
		} catch( IOException ex ) { }
		
		// Classifiers
		readClassifiers();
	}
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		return extractSameSentenceEventEventLinks(doc);
	}

	
  /**
   * Put event-event links into the global .info file between same sentence event-event links.
   */
  public List<TLink> extractSameSentenceEventEventLinks(SieveDocument doc) {
    List<SieveSentence> sentences = doc.getSentences();
    if( debug ) System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Grab all the parse trees.
    List<Tree> trees = doc.getAllParseTrees();

    // Loop over each sentence and get TLinks.
    for( SieveSentence sent : sentences ) {
      List<TextEvent> events = sent.events();
      List<TypedDependency> deps = sent.getDeps();

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

//    try {
//      eeSameSentClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.samesent.classifier-all");
//      eeSameSentExistsClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.samesent.exists.classifier-all");
//      eeSameSentDominatesClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.samesent.dominate.classifier-all");
//      eeSameSentNoDominatesClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.samesent.nodominate.classifier-all");
//    } catch(Exception ex) { 
//      System.out.println("Had fatal trouble loading " + dirpath);
//      ex.printStackTrace(); System.exit(1); 
//    }
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
