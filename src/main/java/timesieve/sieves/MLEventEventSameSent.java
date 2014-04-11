package timesieve.sieves;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Tree;

/**
 * Machine learned event-event pairs intra-sentence.
 * 
 * Right now this just makes one classifier for all event-event pairs, and doesn't use the
 * specific ones for syntactic dominance.
 *
 * MLEventEventSameSent            p=0.43  96 of 225       Non-VAGUE:      p=0.49  96 of 194
 * (Baseline 0.36)
 * 
 * @author chambers
 */
public class MLEventEventSameSent implements Sieve {
	Classifier<String,String> eeSameSentClassifier = null; // intra-sentence event-event links.
	Map<TLink.Type,Classifier<String,String>> binaryLabelClassifiers;
	
  Classifier<String,String> eeSameSentExistsClassifier = null; // binary, is there a link or not?
  TLinkFeaturizer featurizer;
  
  TLink.Type[] labels = { TLink.Type.BEFORE, TLink.Type.AFTER, TLink.Type.INCLUDES, TLink.Type.IS_INCLUDED, TLink.Type.SIMULTANEOUS, TLink.Type.VAGUE };
  String eeSameSentName = "tlink.ee.samesent.classifier";
  
  String modelDir = "/models/tlinks";
  String doBinaryLabel = null;
  boolean eesplit = false;
  boolean debug = true;
  int featMinOccurrence = 2;
  
  double minProb = 0.0;
  
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
	}
	
	private void init() {
		// Flags
		try {
  		eesplit = TimeSieveProperties.getBoolean("MLEventEventSameSent.eesplit",false);
  		debug = TimeSieveProperties.getBoolean("MLEventEventSameSent.debug",false);
  		doBinaryLabel = TimeSieveProperties.getString("MLEventEventSameSent.binaryLabel",null);
		} catch( IOException ex ) { }
		
		readClassifiers();
	}
	
	public void printLabelStats(List<TLink> links) {
		Counter<TLink.Type> counts = new ClassicCounter<TLink.Type>();
		for( TLink link : links ) counts.incrementCount(link.getRelation());
		System.out.println("# Predicted Labels");
		for( TLink.Type label : counts.keySet() )
			System.out.println(label + "\t" + counts.getCount(label));
	}
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// Classifier loading must have failed in init()
		if( eeSameSentClassifier == null )
			return null;
		
		List<TLink> labeled = extractSameSentenceEventEventLinks(doc);
		
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

    // We are doing a binary classification on one link type.
    if( doBinaryLabel != null ) 
    	targetClassifier = binaryLabelClassifiers.get(TLink.Type.valueOf(doBinaryLabel));
    
    // Use 2 classifiers for event-event links. One for syntactic dominance, the other for general.
    // This just sets 'targetClassifier' to null. Maintaining in case someone
    // wants to actually fix the implementation.
//    if( eesplit ) {
//    	if( featurizer.oneEventDominates(event1, event2, trees) )
//    		targetClassifier = eeSameSentDominatesClassifier;
//    	else
//    		targetClassifier = eeSameSentNoDominatesClassifier;
//    }

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
  	String path = modelDir + File.separator + eeSameSentName;
  	eeSameSentClassifier = Util.readClassifierFromFile(this.getClass().getResource(path));
  	if( eeSameSentClassifier == null )
  		System.out.println("ERROR: MLEventEventSameSent could not read its classifier at: " + path);

  	// Read the binary classifiers, one for each label type.
  	binaryLabelClassifiers = new HashMap<TLink.Type,Classifier<String,String>>();
  	for( TLink.Type label : labels ) {
    	String mpath = "/models/tlinks/tlink.ee.samesent." + label.toString() + ".classifier";
    	Classifier<String,String> classifier = Util.readClassifierFromFile(this.getClass().getResource(mpath));
    	binaryLabelClassifiers.put(label, classifier);
  	}
  }
  
  private void writeClassifier(Classifier<String,String> classifier, String filename) {
    try {
    	IOUtils.writeObjectToFile(classifier, filename);
    } catch(Exception ex) {
    	System.out.println("ERROR: couldn't write classifier " + filename + " in MLEventEventSameSent");
    	ex.printStackTrace();
    }
  }
  
  private List<TLinkDatum> createBinaryData(TLink.Type targetLabel, List<TLinkDatum> links) {
    List<TLinkDatum> binaryData = new ArrayList<TLinkDatum>();
    for( TLinkDatum datum : links ) {
    	TLinkDatum newd = new TLinkDatum();
    	newd.setLabel((datum.getLabel() == targetLabel) ? targetLabel : TLink.Type.NONE);
    	newd.addFeatures(datum.getFeatures());
    	binaryData.add(newd);
    }
    return binaryData;
  }
  
	/**
	 * Train on the documents.
	 */
	public void train(SieveDocuments docs) {

    //		featurizer.debug = true;
		List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, null);
    System.out.println("Final training data size: " + data.size());

    // Train the multi-class classifier.
    eeSameSentClassifier = TLinkClassifier.train(data, featMinOccurrence);    
    writeClassifier(eeSameSentClassifier, eeSameSentName);    

    // Train binary classifiers for each label.
    for( TLink.Type target : labels ) {
    	List<TLinkDatum> binarydata = createBinaryData(target, data);
    	String modelName = "tlink.ee.samesent." + target.toString() + ".classifier";
    	writeClassifier(TLinkClassifier.train(binarydata, featMinOccurrence), modelName);
    }
	}

}
