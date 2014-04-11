package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventTimeLink;
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
 * Machine learned event-time pairs inter-sentence.
 *
 * MLEventTimeDiffSent             p=0.37  36 of 97        Non-VAGUE:      p=0.41  36 of 87
 * (Baseline 0.35)
 *
 * @author chambers
 */
public class MLEventTimeDiffSent implements Sieve {
	Classifier<String,String> etDiffSentClassifier = null; // intra-sentence event-event links.
  Classifier<String,String> etDiffSentExistsClassifier = null; // binary, is there a link or not?
  TLinkFeaturizer featurizer;
  
  String etDiffSentName = "tlink.et.diffsent.classifier";
  
  boolean debug = true;
  int featMinOccurrence = 2;
  
  /**
   * Constructor uses the global properties for parameters.
   */
	public MLEventTimeDiffSent() {
		// Setup the featurizer for event-event intrasentence links.
		featurizer = new TLinkFeaturizer();
		featurizer._eventEventOnly = false;
    featurizer._eventTimeOnly = true;
    featurizer._noEventDCT = true;
    featurizer._sameSentenceOnly = false;
    featurizer._neighborSentenceOnly = true;
    featurizer._ignoreSameSentence = true;
    featurizer._noEventTimeDiff = false;
		
		featurizer.debug = false;

		init();
	}
	
	private void init() {
		// Flags
		try {
  		debug = TimeSieveProperties.getBoolean("MLEventTimeDiffSent.debug",false);
		} catch( IOException ex ) { }
		
		readClassifiers();
	}
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// Classifier loading must have failed in init()
		if( etDiffSentClassifier == null )
			return null;
		
		return extractDiffSentenceEventTimeLinks(doc);
	}

    
  /**
   * Put event-time links into the global .info file between different sentence event-time links.
   */
  public List<TLink> extractDiffSentenceEventTimeLinks(SieveDocument doc) {
    List<SieveSentence> sentences = doc.getSentences();
    if( debug ) System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Loop over sentences and get TLinks that cross sentence boundaries between events and times.
    for( int sid = 0; sid < sentences.size()-1; sid++ ) {
    	SieveSentence sent = sentences.get(sid);
    	List<TextEvent> events = sent.events();
    	List<Timex> timexes = sent.timexes();
    	SieveSentence sentNext = sentences.get(sid+1);
    	List<TextEvent> eventsNext = sentNext.events();
    	List<Timex> timexesNext = sentNext.timexes();
    	
    	if( events != null && timexesNext != null ) {
    		for( TextEvent event : events ) {
    			for( Timex timex : timexesNext ) {
      			TLinkDatum datum = featurizer.createEventTimeDatum(doc, event, timex, null);
      			RVFDatum<String,String> rvf = datum.createRVFDatum();
      			Pair<String,Double> labelProb = TLinkClassifier.getLabelProb(etDiffSentClassifier, rvf);
      			TLink link = new EventTimeLink(event.getEiid(), timex.getTid(), TLink.Type.valueOf(labelProb.first()));
      			link.setRelationConfidence(labelProb.second());
      			tlinks.add(link);
      		}
      	}
    	}
    	if( timexes != null && eventsNext != null ) {
    		for( Timex timex : timexes ) {
      		for( TextEvent event : eventsNext ) {
      			TLinkDatum datum = featurizer.createEventTimeDatum(doc, event, timex, null);
      			RVFDatum<String,String> rvf = datum.createRVFDatum();
      			Pair<String,Double> labelProb = TLinkClassifier.getLabelProb(etDiffSentClassifier, rvf);
      			TLink link = new EventTimeLink(event.getEiid(), timex.getTid(), TLink.Type.valueOf(labelProb.first()));
      			link.setRelationConfidence(labelProb.second());
      			tlinks.add(link);
      		}
      	}
    	}
    }
    if( debug ) System.out.println("Returning diff sentence e-t tlinks: " + tlinks);
    return tlinks;
  }
  
    
  private void readClassifiers() {
  	String path = "/models/tlinks/" + etDiffSentName;
  	System.out.println("Loading et diffsent from " + path);
  	etDiffSentClassifier = Util.readClassifierFromFile(this.getClass().getResource(path));
  }
  
  
	/**
	 * Train on the documents.
	 */
	public void train(SieveDocuments docs) {
		featurizer.debug = true;
		List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, null);
    System.out.println("Final training data size: " + data.size());
    
    if( debug ){
    	for( TLinkDatum dd : data ) {
    		System.out.println("** " + dd._originalTLink);    		
    		System.out.println(dd);
    	}
    }
    
    etDiffSentClassifier = TLinkClassifier.train(data, featMinOccurrence);    
    
    try {
    	IOUtils.writeObjectToFile(etDiffSentClassifier, etDiffSentName);
    } catch(Exception ex) {
    	System.out.println("ERROR: couldn't write classifiers to file in MLEventEventDiffSent");
    	ex.printStackTrace();
    }
	}

}
