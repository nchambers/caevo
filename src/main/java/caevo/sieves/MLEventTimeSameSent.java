package caevo.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.Timex;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.tlink.TLinkClassifier;
import caevo.tlink.TLinkDatum;
import caevo.tlink.TLinkFeaturizer;
import caevo.util.Pair;
import caevo.util.CaevoProperties;
import caevo.util.Util;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;

/**
 * Machine learned event-time pairs intra-sentence.
 * 
 * Right now this just makes one classifier for all event-time pairs, and doesn't use the
 * specific ones for syntactic dominance.
 * 
 * MLEventTimeSameSent             p=0.48  26 of 54        Non-VAGUE:      p=0.57  26 of 46
 * (baseline 0.37)
 * 
 * @author chambers
 */
public class MLEventTimeSameSent implements Sieve {
	Classifier<String,String> etSameSentClassifier = null; // intra-sentence event-event links.
  Classifier<String,String> etSameSentExistsClassifier = null; // binary, is there a link or not?
  TLinkFeaturizer featurizer;
  
  String etSameSentName = "tlink.et.samesent.classifier";
  
  boolean debug = true;
  int featMinOccurrence = 2;
  
  /**
   * Constructor uses the global properties for parameters.
   */
	public MLEventTimeSameSent() {
		// Setup the featurizer for event-event intrasentence links.
		featurizer = new TLinkFeaturizer();
		featurizer._eventEventOnly = false;
    featurizer._eventTimeOnly = true;
    featurizer._noEventDCT = true;
    featurizer._sameSentenceOnly = true;
    featurizer._ignoreSameSentence = false;
    featurizer._diffSentenceOnly = false;
		
		featurizer.debug = false;

		init();
	}
	
	private void init() {
		// Flags
		try {
  		debug = CaevoProperties.getBoolean("MLEventTimeSameSent.debug",false);
		} catch( IOException ex ) { }
		
		readClassifiers();
	}
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// Classifier loading must have failed in init()
		if( etSameSentClassifier == null )
			return null;
		
		return extractSameSentenceEventTimeLinks(doc);
	}

    
  /**
   * Put event-time links into the global .info file between same sentence event-time links.
   */
  public List<TLink> extractSameSentenceEventTimeLinks(SieveDocument doc) {
    List<SieveSentence> sentences = doc.getSentences();
    if( debug ) System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Loop over each sentence and get TLinks.
    for( SieveSentence sent : sentences ) {
      List<TextEvent> events = sent.events();
      List<Timex> timexes = sent.timexes();

      if( timexes != null && events != null && events.size() > 0 && timexes.size() > 0 ) {
      	for( Timex timex : timexes ) {
      		for( TextEvent event : events ) {
      			TLinkDatum datum = featurizer.createEventTimeDatum(doc, event, timex, null);
      			RVFDatum<String,String> rvf = datum.createRVFDatum();
      			Pair<String,Double> labelProb = TLinkClassifier.getLabelProb(etSameSentClassifier, rvf);
      			TLink link = new EventTimeLink(event.getEiid(), timex.getTid(), TLink.Type.valueOf(labelProb.first()));
      			link.setRelationConfidence(labelProb.second());
      			tlinks.add(link);
      		}
      	}
      }
    }
    if( debug ) System.out.println("Returning same e-e tlinks: " + tlinks);
    return tlinks;
  }
  
    
  private void readClassifiers() {
  	String path = "/models/tlinks/" + etSameSentName;
  	System.out.println("Loading et samesent from " + path);
  	etSameSentClassifier = Util.readClassifierFromFile(this.getClass().getResource(path));
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
    
    etSameSentClassifier = TLinkClassifier.train(data, featMinOccurrence);    
    
    try {
    	IOUtils.writeObjectToFile(etSameSentClassifier, etSameSentName);
    } catch(Exception ex) {
    	System.out.println("ERROR: couldn't write classifiers to file in MLEventEventSameSent");
    	ex.printStackTrace();
    }
	}

}
