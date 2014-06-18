package caevo.tlink;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Set;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.Tester;
import caevo.TextEvent;
import caevo.Timex;
import caevo.util.ClassifiedDatum;
import caevo.util.HandleParameters;
import caevo.util.Pair;
import caevo.util.TimebankUtil;
import caevo.util.TreeOperator;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.PrecisionRecallStats;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.StringUtils;

/**
 * Command-line flags that are used:
 * 
 * -tlinks
 * Extract all tlinks.
 * 
 * -eesame, -eediff, -etsame, -edct
 * Four flags for 4 types of tlinks to extract. Use a subset of these if you don't use -tlinks.
 * 
 * -eesplit
 * Given with the -eesame flag, it will use 2 classifiers for event-event links instead of just 1 for all of them.
 * 
 * -etdet, -eedet, -edctdet
 * For testing: use the deterministic extractor, not a trained extractor for potential pairs.
 * 
 * -min <int>
 * The minimum feature occurrence count before removing a feature from training/test.
 * 
 * -bethard
 * Use any Bethard tlinks in the given info file to train the event-event classifier.
 * 
 * 
 * TRAINING
 * Provide a .info file on the command line, and this trains all possible TLINK classifiers, then saves them to disk.
 * java TLinkClassifier -info <path> traininfo
 * 
 * Another option is to provide already featurized data from TLinkFeaturizer. This code trains, and saves to disk.
 * java TLinkClassifier <feats-dir> trainfeats
 *
 *
 * INFERENCE
 * This code also provides "extractTLinks()" to label a .info file with TLinks. You should create an instance of
 * this class, provide it an info file and a directory of trained models. Then call the function and it will add
 * TLinks to the given info file.
 * java TLinkClassifier <info-path> <model-dir> testinfo
 * 
 */
public class TLinkClassifier {
  Properties props;
  public SieveDocuments docs;
  public TLinkFeaturizer featurizer;
  Classifier<String,String> eeSameSentClassifier; // intra-sentence event-event links.
  Classifier<String,String> eeSameSentExistsClassifier; // binary, is there a link or not?
  Classifier<String,String> eeSameSentDominatesClassifier;   // intra-sentence, event-event syntactically dominates
  Classifier<String,String> eeSameSentNoDominatesClassifier; // intra-sentence, event-event no syntactically dominates
  Classifier<String,String> eeDiffSentClassifier; // inter-sentence event-event links.
  Classifier<String,String> etSameSentClassifier; // intra-sentence event-time links.
  Classifier<String,String> etDiffSentClassifier; // inter-sentence event-time links.
  Classifier<String,String> etSameSentExistsClassifier; // binary, is there a link or not?
  Classifier<String,String> etDCTClassifier;      // event-docstamp links.  
  Classifier<String,String> etDCTExistsClassifier;      // event-docstamp links.  
  String modelDir = "tlinkmodels";
  int _featMinOccurrence = 2;
  double _tlinkProbabilityCutoff = 0.3;
  boolean _onlyDCTSaid = false; 
  boolean _etDeterministic = false; // Use the rule-based event-time neighbors for extraction.
  boolean _eeDeterministic = false; // Use the rule-based event-event neighbors for extraction.

  
  public TLinkClassifier() {
  }
  public TLinkClassifier(SieveDocuments docs, String dir, Properties props) {
    this.docs = docs;
    this.modelDir = dir;
    this.props = props;
    init();
    if( dir != null ) readClassifiersFromDirectory(this.modelDir);
  }
  
  /**
   * Given the command line args, read a .info file and build a classifier.
   * @param featuresPath Single file with feature data in it.
   */
  public void trainInfo(String args[]) {
    handleParams(args);
    init();
    trainInfo(docs, null);
  }
  
  private void handleParams(String[] args) {
    HandleParameters params = new HandleParameters(args);
    System.out.println("Load .info file from " + params.get("-info"));
    docs = new SieveDocuments(params.get("-info"));
    props = StringUtils.argsToProperties(args);
  }

  private void init() {
    if( props != null ) {
      if( props.containsKey("min") )     _featMinOccurrence = Integer.parseInt(props.getProperty("min"));
      if( props.containsKey("edctdet") ) _onlyDCTSaid = true;
      if( props.containsKey("etdet") )   _etDeterministic = true;
      if( props.containsKey("eedet") )   _eeDeterministic = true;
      if( props.containsKey("prob") )    _tlinkProbabilityCutoff = Double.parseDouble(props.getProperty("prob"));
    }
    featurizer = new TLinkFeaturizer();
  }
  
  public void trainInfo(SieveDocuments docs, Set<String> docnames) {
    featurizer._noEventFeats = true;
    
    // Event-Event links in the same sentence.
    if( props.containsKey("tlinks") || props.containsKey("eesame") ) {
      System.out.println("**** EVENT EVENT SAME SENTENCE LINKS ****");
      featurizer._eventEventOnly = true;
      featurizer._eventTimeOnly = false;
      featurizer._eventDCTOnly = false;
      featurizer._sameSentenceOnly = true;
      featurizer._ignoreSameSentence = false;
      featurizer._eventEventNoDominates = false;
      featurizer._eventEventDominates = false;
      if( props.containsKey("bethard") ) featurizer._doBethard = true;
//      System.out.println("BETHARD FEATURIZER = " + featurizer._doBethard);
      List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, docnames);
      System.out.println("Final training data size: " + data.size());
      eeSameSentClassifier = train(data, _featMinOccurrence);
      // Event-event all pairs: classify if a link exists or not.
      data = createDatasetEventEventSameSentExists(docs, docnames);
      System.out.println("Final event-event exists training data size: " + data.size());
      eeSameSentExistsClassifier = train(data, _featMinOccurrence);
      
      // Specialized event-event classifiers.
      if( props.containsKey("eesplit") ) {
      	featurizer._eventEventDominates = true;
        if( props.containsKey("bethard") ) featurizer._doBethard = true;
      	data = featurizer.infoToTLinkFeatures(docs, docnames);
      	System.out.println("Final event-event dominates training data size: " + data.size());
      	eeSameSentDominatesClassifier = train(data, _featMinOccurrence);
        featurizer._eventEventDominates = false;
        featurizer._eventEventNoDominates = true;
        if( props.containsKey("bethard") ) featurizer._doBethard = true; // will skip most of these, but some errors might still have Bethard gold labels
      	data = featurizer.infoToTLinkFeatures(docs, docnames);
      	System.out.println("Final event-event doesn't dominate training data size: " + data.size());
      	eeSameSentNoDominatesClassifier = train(data, _featMinOccurrence);
      }
    }
    
    // Event-Event links in different sentences.
    if( props.containsKey("tlinks") || props.containsKey("eediff") ) {
      System.out.println("**** EVENT EVENT DIFF SENTENCE LINKS ****");
      featurizer._eventEventOnly = true;
      featurizer._eventTimeOnly = false;
      featurizer._sameSentenceOnly = false;
      featurizer._diffSentenceOnly = true;
      featurizer._doBethard = false;
      List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, docnames);
      System.out.println("Final training data size: " + data.size());
      eeDiffSentClassifier = train(data, _featMinOccurrence);
    }
    
    // Event-Time links in the same sentence.
    if( props.containsKey("tlinks") || props.containsKey("etsame") ) {
      System.out.println("**** EVENT TIME SAME SENTENCE LINKS ****");
      featurizer._eventEventOnly = false;
      featurizer._eventTimeOnly = true;
      featurizer._noEventDCT = true;
      featurizer._sameSentenceOnly = true;
      featurizer._ignoreSameSentence = false;
      featurizer._diffSentenceOnly = false;
      List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, docnames);
      System.out.println("Final training data size: " + data.size());
      etSameSentClassifier = train(data, _featMinOccurrence);
      // Event-time all pairs: classify if a link exists or not.
      data = createDatasetEventTimeSameSentExists(docs, docnames);
      etSameSentExistsClassifier = train(data, _featMinOccurrence);
    }
    
    // Event-Time links in different sentences.
    if( props.containsKey("tlinks") || props.containsKey("etdiff") ) {
      System.out.println("**** EVENT TIME DIFF SENTENCE LINKS ****");
      featurizer._eventEventOnly = false;
      featurizer._eventTimeOnly = true;
      featurizer._noEventDCT = true;
      featurizer._eventDCTOnly = false;
      featurizer._sameSentenceOnly = false;
      featurizer._ignoreSameSentence = false;
      featurizer._diffSentenceOnly = true;
//      featurizer._neighborSentenceOnly = true;
      featurizer._noEventTimeDiff = false;
      List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, docnames);
      System.out.println("Final training data size: " + data.size());
      etDiffSentClassifier = train(data, _featMinOccurrence);
    }
    
    // Event-DCT links.
    if( props.containsKey("tlinks") || props.containsKey("edct") ) {
      System.out.println("**** EVENT DCT LINKS ****");
      featurizer._eventDCTOnly = true;
      featurizer._noEventDCT = false;
      featurizer._eventEventOnly = false;
      featurizer._eventTimeOnly = true;
      featurizer._sameSentenceOnly = false;
      featurizer._ignoreSameSentence = false;
      featurizer._diffSentenceOnly = false;
      featurizer._neighborSentenceOnly = false;
      List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, docnames);
      System.out.println("Final training data size: " + data.size());
      etDCTClassifier = train(data, _featMinOccurrence);
      // Event-DCT all events: classify if a link exists or not.
      data = createDatasetEventDCTExists(docs, docnames);
      etDCTExistsClassifier = train(data, _featMinOccurrence);
    }
  }
  
  public Classifier<String,String> trainFeats(String featuresPath) {
    Classifier<String,String> classifier = train(featuresPath, _featMinOccurrence);
    return classifier;
  }
  
  /**
   * Given a file of features, one datum per line, train a classifier.
   * @param featuresPath Single file with feature data in it.
   */
  public static Classifier<String,String> train(String featuresPath, int minFeatCutoff) {
    List<TLinkDatum> data = TLinkFeaturizer.readFromFile(featuresPath);
    
    return train(data, minFeatCutoff);
  }
  
  /**
   * Given a list of datums, train a classifier.
   * @param data The list of TLinkDatum objects to use as training data.
   */
  public static Classifier<String,String> train(List<TLinkDatum> data, int minFeatCutoff) {
    // Convert to the JavaNLP data structure.
    System.out.println("Converting to RVFDataset...");
    GeneralDataset<String,String> rvfdataset = listToDataset(data);
    
    // Threshold it
    if( minFeatCutoff > 1 ) {
      System.out.println("Removing sparse features < " + minFeatCutoff);
      rvfdataset.applyFeatureCountThreshold(minFeatCutoff);
    }
    
    // Create the classifier.
    System.out.println("Training the classifier...");
    LinearClassifierFactory<String,String> linearFactory = new LinearClassifierFactory<String,String>();
    Classifier<String,String> classifier = linearFactory.trainClassifier(rvfdataset);

    return classifier;
  }

  
  /**
   * Test the given classifier on the given list of TLink data.
   * @param classifier The pre-trained classifier.
   * @param testData A list of TLinkDatum objects to test on.
   */
  public static ClassifiedDatum[] test(Classifier<String,String> classifier, List<TLinkDatum> testData) {
    if( classifier == null ) {
      System.out.println("NULL CLASSIFIER in TLinkClassifier.test()");
      System.exit(1);
    }
    
    // Create classified datums.
    ClassifiedDatum results[] = new ClassifiedDatum[testData.size()];
    int ii = 0;
    for( TLinkDatum datum : testData ) {
    	RVFDatum<String,String> rvf = datum.createRVFDatum();
    	String predicted = classifier.classOf(rvf);
    	results[ii++] = new ClassifiedDatum(rvf, predicted);
    }
    
    // Confusion matrix.
    Tester.printConfusionMatrix(results, new PrintWriter(System.out), 8);
    // Accuracy.
    System.out.println("Accuracy = " + Tester.accuracy(results));

    // P/R/F1.
    for( String label : getUniqueLabels(testData) ) {
      PrecisionRecallStats pr = Tester.precisionRecallStats(results, label);
      System.out.printf("%s\t%.2f\t%.2f\t%.2f\n", label, pr.getPrecision(), pr.getRecall(), pr.getFMeasure());
    }
    
    return results;
  }
  
  /**
   * Convert our list of LinkDatum objects to a JavaNLP GeneralDataset.
   * @param dataList List of our LinkDatum objects from Timebank.
   * @return A GeneralDataset with the same object features.
   */
  public static GeneralDataset<String,String> listToDataset(List<TLinkDatum> dataList) {
    GeneralDataset<String,String> dataset = null;
    boolean useRVF = true;
    
    // We want to use RVFDatums because it seems most classifiers actually expect this...
    if( useRVF ) {
      dataset = new RVFDataset<String,String>(); 
      for( TLinkDatum datum : dataList )
        dataset.add(datum.createRVFDatum());
    }
    else {
      // Create the DataSet objects to use in classifications.
      dataset = new Dataset<String,String>();
      for( TLinkDatum datum : dataList )
        dataset.add(datum.createBasicDatum());
    }

    return dataset;
  }
  
  /**
   * @return True if the link is an original TimeBank or TempEval labeled link. False if it came from
   *         closure or some other dataset.
   */
  private boolean isNormalLink(TLink link) {
    // No closed or empty links.
    if( !link.closed && link.getRelation() != TLink.Type.NONE ) {
      // Only TimeBank or TempEval labeled links.
      if( link.getOrigin() == null || link.getOrigin().equals("null") ||
          link.getOrigin().contains("tempeval")      ||
          link.getOrigin().equals("tempeval-main")   ||
          link.getOrigin().equals("tempeval-sub") )
        return true;
    }
    return false;
  }
  
  /**
   * Creates datums for every possible event-event pair within sentences. Every possible. Those that have
   * a corresponding TLink are marked as OVERLAP. All others are NONE.
   */
  public List<TLinkDatum> createDatasetEventDCTExists(SieveDocuments docs, Set<String> docnames) {
    System.out.println("createDataset Event DCT Exists");
    List<TLinkDatum> alldata = new ArrayList<TLinkDatum>();

    for( SieveDocument doc : docs.getDocuments() ) {
      if( docnames == null || docnames.contains(doc.getDocname()) ) {
        System.out.println("\n--------------------------------------------------");
        System.out.println("File " + doc.getDocname());
        List<TLinkDatum> datums = new ArrayList<TLinkDatum>();
        List<Timex> dcts = doc.getDocstamp();
        String dctid = dcts.get(0).getTid(); // assume only the first DCT timex
        
        // Hash the known DCT tlinks.
        Set<String> links = new HashSet<String>();
        for( TLink link : doc.getTlinks() ) {
          if( TimebankUtil.isEventDCTLink(link, dcts) ) {
            if( isNormalLink(link) ) {
              links.add(link.getId1() + " " + link.getId2());
              links.add(link.getId2() + " " + link.getId1());
            } else System.out.println("Skipping event-dct not-normal: " + link);
          }
        }
        
        // Get parse trees.
        List<SieveSentence> sentences = doc.getSentences();
        List<Tree> trees = doc.getAllParseTrees();
        
        for( SieveSentence sent : sentences ) {
          List<TextEvent> events = sent.events();

          for( int ii = 0; ii < events.size(); ii++ ) {
            TextEvent event = events.get(ii);
            if( links.contains(event.getEiid() + " " + dctid) )
              datums.add(featurizer.createEventDocumentTimeDatum(doc, event, dcts.get(0), TLink.Type.OVERLAP));
            else
              datums.add(featurizer.createEventDocumentTimeDatum(doc, event, dcts.get(0), TLink.Type.NONE));
          }
        }

//        System.out.println("DATUMS");
//        for( TLinkDatum datum : datums ) System.out.println(datum);
        alldata.addAll(datums);
      }
    }

    return alldata;
  }
  
  /**
   * Creates datums for every possible event-event pair within sentences. Every possible. Those that have
   * a corresponding TLink are marked as OVERLAP. All others are NONE.
   */
  public List<TLinkDatum> createDatasetEventEventSameSentExists(SieveDocuments docs, Set<String> docnames) {
    System.out.println("createDataset Event Event Same Sent Exists");
    List<TLinkDatum> alldata = new ArrayList<TLinkDatum>();

    for( SieveDocument doc : docs.getDocuments() ) {
      if( docnames == null || docnames.contains(doc.getDocname()) ) {
        System.out.println("\n--------------------------------------------------");
        System.out.println("File " + doc.getDocname());
        List<TLinkDatum> datums = new ArrayList<TLinkDatum>();

        // Hash the known tlinks.
        Set<String> links = new HashSet<String>();
        for( TLink link : doc.getTlinks() ) {
          if( isNormalLink(link) ) {          
            links.add(link.getId1() + " " + link.getId2());
            links.add(link.getId2() + " " + link.getId1());
            //          System.out.println("Added: *" + link.getId1() + " " + link.getId2() + "*");
          } else System.out.println("Skipping event-event same not-normal: " + link);
        }
        
        // Get parse trees.
        List<SieveSentence> sentences = doc.getSentences();
        List<Tree> trees = doc.getAllParseTrees();
        
        int sid = 0;
        for( SieveSentence sent : sentences ) {
          List<TextEvent> events = sent.events();
          List<TypedDependency> deps = sent.getDeps();
          
          for( int ii = 0; ii < events.size()-1; ii++ ) {
            TextEvent event1 = events.get(ii);
            for( int jj = ii+1; jj < events.size(); jj++ ) {
              TextEvent event2 = events.get(jj);
              if( links.contains(event1.getEiid() + " " + event2.getEiid()) )
                datums.add(featurizer.createEventEventDatum(doc, event1, event2, TLink.Type.OVERLAP));
              else
                datums.add(featurizer.createEventEventDatum(doc, event1, event2, TLink.Type.NONE));
            }
          }
          sid++;
        }
//        System.out.println("DATUMS");
//        for( TLinkDatum datum : datums ) System.out.println(datum);
        alldata.addAll(datums);
      }
    }

    return alldata;
  }

  /**
   * Creates datums for every possible event-time pair within sentences. Every possible. Those that have
   * a corresponding TLink are marked as OVERLAP. All others are NONE.
   */
  public List<TLinkDatum> createDatasetEventTimeSameSentExists(SieveDocuments docs, Set<String> docnames) {
    System.out.println("createDataset Event Time Same Sent Exists");
    List<TLinkDatum> alldata = new ArrayList<TLinkDatum>();

    for( SieveDocument doc : docs.getDocuments() ) {
      if( docnames == null || docnames.contains(doc.getDocname()) ) {
        System.out.println("\n--------------------------------------------------");
        System.out.println("File " + doc.getDocname());
        List<TLinkDatum> datums = new ArrayList<TLinkDatum>();

        // Hash the known tlinks.
        Set<String> links = new HashSet<String>();
        for( TLink link : doc.getTlinks() ) {
          if( link instanceof EventTimeLink ) {
            if( isNormalLink(link) ) {
              links.add(link.getId1() + " " + link.getId2());
              links.add(link.getId2() + " " + link.getId1());
            } else System.out.println("Skipping event-time same not-normal: " + link);
          } 
        }
        
        // Get parse trees.
        List<Tree> trees = doc.getAllParseTrees();
        
        int sid = 0;
        for( SieveSentence sent : doc.getSentences() ) {
          List<TextEvent> events = sent.events();
          List<Timex> timexes= sent.timexes();
          List<TypedDependency> deps = sent.getDeps();
          
          for( TextEvent event : events ) {
            for( Timex timex : timexes ) {
              if( links.contains(event.getEiid() + " " + timex.getTid()) )
                datums.add(featurizer.createEventTimeDatum(doc, event, timex, TLink.Type.OVERLAP));
              else
                datums.add(featurizer.createEventTimeDatum(doc, event, timex, TLink.Type.NONE));
            }
          }
          sid++;
        }
//        System.out.println("E-T EXISTS DATUMS");
//        for( TLinkDatum datum : datums ) System.out.println(datum);
        alldata.addAll(datums);
      }
    }

    return alldata;
  }
  
  /**
   * Put event-time links into the global .info file between same sentence event-time links.
   */
  public List<TLink> extractEventDCTLinks(String docname) {
  	SieveDocument doc = docs.getDocument(docname);
    System.out.println("doc (e-dct) = " + docname);
    List<SieveSentence> sentences = doc.getSentences();
    List<Timex> dcts = doc.getDocstamp();
    System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    if( dcts != null && dcts.size() > 0 ) {

      // Grab all the parse trees.
      List<Tree> trees = doc.getAllParseTrees();

      // Loop over each sentence and get TLinks.
      for( SieveSentence sent : sentences ) {
        List<TextEvent> events = sent.events();

        if( events != null && events.size() > 0 ) {

          // Find the closest event to the left of this timex.
          for( TextEvent event : events ) {
            TLinkDatum datum = featurizer.createEventDocumentTimeDatum(doc, event, dcts.get(0), null);
            RVFDatum<String,String> rvf = datum.createRVFDatum();
            String label = null;

            // Label e-dct links if our classifier says so.
            if( !_onlyDCTSaid )
              label = etDCTExistsClassifier.classOf(rvf);

            // Only label "said" verbs.
            if( (_onlyDCTSaid && event.getString().equalsIgnoreCase("said")) ||
                (!_onlyDCTSaid && TLink.Type.valueOf(label) == TLink.Type.OVERLAP) ) {
              
              // Get winning label and its probability.
//              label = etDCTClassifier.classOf(rvf);
              Pair<String,Double> labelProb = getLabelProb(etDCTClassifier, rvf);
              label = labelProb.first();
              TLink link = new EventTimeLink(event.getEiid(), dcts.get(0).getTid(), TLink.Type.valueOf(label));
              link.setRelationConfidence(labelProb.second());
              tlinks.add(link);
            }
          }
        }
      }
    }
    System.out.println("Returning e-t tlinks: " + tlinks);
    return tlinks;
  }
  
  /**
   * Put event-time links into the global .info file between same sentence event-time links.
   */
  public List<TLink> extractSameSentenceEventTimeLinks(String docname) {
  	SieveDocument doc = docs.getDocument(docname);
    System.out.println("doc (samesent e-t) = " + docname);
    List<SieveSentence> sentences = doc.getSentences();
    System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Grab all the parse trees.
    List<Tree> trees = doc.getAllParseTrees();

    // Grab the dependencies.
    List<List<TypedDependency>> alldeps = doc.getAllDependencies();
    
    // Loop over each sentence and get TLinks.
    int sid = 0;
    for( SieveSentence sent : sentences ) {
      List<TextEvent> events = sent.events();
      List<Timex> timexes = sent.timexes();

      if( timexes != null && events != null && events.size() > 0 && timexes.size() > 0 ) {
        for( Timex timex : timexes ) {

          // Standard process. First detect if event-time pair should have a label, and then label it.
          if( !_etDeterministic ) {
            for( TextEvent event : events ) {
              TLinkDatum datum = featurizer.createEventTimeDatum(doc, event, timex, null);
              RVFDatum<String, String> rvf = datum.createRVFDatum();
              String label = eeSameSentExistsClassifier.classOf(rvf);
              if( TLink.Type.valueOf(label) == TLink.Type.OVERLAP ) {
                Pair<String,Double> labelProb = getLabelProb(etSameSentClassifier, rvf);
                TLink link = new EventTimeLink(event.getEiid(), timex.getTid(), TLink.Type.valueOf(labelProb.first()));
                link.setRelationConfidence(labelProb.second());
                tlinks.add(link);
              }
            }
          }
          
          // Deterministic. Only label neighboring event-time and time-event pairs.
          else {
            TextEvent best = null;
            int bestdist = Integer.MAX_VALUE;
            
            // Find the closest event to the left of this timex.
            for( TextEvent event : events )
              if( event.getIndex() < timex.getTokenOffset() && (timex.getTokenOffset()-event.getIndex() < bestdist) && (timex.getTokenOffset() != event.getIndex()) ) {
                bestdist = timex.getTokenOffset()-event.getIndex();
                best = event;
              }

            // Create the event-time datum.
            if( best != null ) {
              TLinkDatum datum = featurizer.createEventTimeDatum(doc, best, timex, null);
              //              System.out.println("e-t datum=" + datum);
              //            String label = etSameSentClassifier.classOf(datum.createRVFDatum());
              Pair<String,Double> labelProb = getLabelProb(etSameSentClassifier, datum.createRVFDatum());
              TLink link = new EventTimeLink(best.getEiid(), timex.getTid(), TLink.Type.valueOf(labelProb.first()));
              link.setRelationConfidence(labelProb.second());
              tlinks.add(link);
            }

            // Find the closest event to the right of this timex.
            best = null;
            bestdist = Integer.MAX_VALUE;
            for( TextEvent event : events )
              if( event.getIndex() > timex.getTokenOffset() && (event.getIndex()-timex.getTokenOffset() < bestdist) && (timex.getTokenOffset() != event.getIndex()) ) {
                bestdist = timex.getTokenOffset()-event.getIndex();
                best = event;
              }

            // Create the event-time datum.
            if( best != null ) {
              TLinkDatum datum = featurizer.createEventTimeDatum(doc, best, timex, null);
              //              System.out.println("e-t datum=" + datum);
              //            String label = etSameSentClassifier.classOf(datum.createRVFDatum());
              Pair<String,Double> labelProb = getLabelProb(etSameSentClassifier, datum.createRVFDatum());
              TLink link = new EventTimeLink(best.getEiid(), timex.getTid(), TLink.Type.valueOf(labelProb.first()));
              link.setRelationConfidence(labelProb.second());
              tlinks.add(link);
            }
          }
        }
      }
      sid++;
    }
    System.out.println("Returning e-t tlinks: " + tlinks);
    return tlinks;
  }
  
  /**
   * Put event-event links into the global .info file between same sentence event-event links.
   */
  public List<TLink> extractSameSentenceEventEventLinks(String docname) {
    System.out.println("doc (same sent e-e)= " + docname);
    SieveDocument doc = docs.getDocument(docname);
    List<SieveSentence> sentences = doc.getSentences();
    System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Grab all the parse trees.
    List<Tree> trees = docs.getDocument(docname).getAllParseTrees();

    // Loop over each sentence and get TLinks.
    int sid = 0;
    for( SieveSentence sent : sentences ) {
      List<TextEvent> events = sent.events();
      List<TypedDependency> deps = sent.getDeps();

      System.out.println("events: " + events);
      for( int ii = 0; ii < events.size()-1; ii++ ) {
        TextEvent event1 = events.get(ii);
        
        if( !_eeDeterministic ) {
          for( int jj = ii+1; jj < events.size(); jj++ ) {
            TextEvent event2 = events.get(jj);
            TLinkDatum datum = featurizer.createEventEventDatum(doc, event1, event2, null);
            //            System.out.println("datum=" + datum);
            RVFDatum<String,String> rvf = datum.createRVFDatum();

            // Check if this event-event pair should receive *any* label at all.
            String label = TLink.Type.OVERLAP.toString();
            label = eeSameSentExistsClassifier.classOf(rvf);

            // Label it with a temporal relation.
            if( TLink.Type.valueOf(label) == TLink.Type.OVERLAP ) {
//              // Normal, 1 classifier for all event-event links.
//              Classifier<String,String> targetClassifier = eeSameSentClassifier;
//
//              // Use 2 classifiers for event-event links. One for syntactic dominance, the other for general.
//              if( props.containsKey("eesplit") ) {
//                if( featurizer.oneEventDominates(event1, event2, trees) )
//                  targetClassifier = eeSameSentDominatesClassifier;
//                else
//                  targetClassifier = eeSameSentNoDominatesClassifier;
//              }
//
//              // Get the best label and its probability.
//              Pair<String,Double> labelProb = getLabelProb(targetClassifier, rvf);
//              label = labelProb.first();
//
//              // Create the actual link with the classified label.
//              TLink link = new EventEventLink(event1.eiid(), event2.eiid(), TLink.Type.valueOf(label));
//              link.setRelationConfidence(labelProb.second());
//              tlinks.add(link);
              
              TLink link = createIntraSentenceEELink(doc, event1, event2);
              tlinks.add(link);
            }
            else System.out.println("Skipping event-event same sentence, not exists: " + event1.getId() + " " + event2.getId());
          }
        }
        
        // Deterministic event-event extraction.
        else {
          // Find the closest event to the left of this event.
          int bestdist = Integer.MAX_VALUE;
          TextEvent best = null;
          for( TextEvent event2 : events )
            if( event2.getIndex() < event1.getIndex() && (event1.getIndex()-event2.getIndex() < bestdist) ) {
              bestdist = event1.getIndex()-event2.getIndex();
              best = event2;
            }
         
          if( best != null ) {
            TLink link = createIntraSentenceEELink(doc, best, event1);
            tlinks.add(link);
          }
        }
      }
      sid++;
    }
    System.out.println("Returning same e-e tlinks: " + tlinks);
    return tlinks;
  }
  
  private EventEventLink createIntraSentenceEELink(SieveDocument doc, TextEvent event1, TextEvent event2) {
    // Normal, 1 classifier for all event-event links.
    Classifier<String,String> targetClassifier = eeSameSentClassifier;

    // Use 2 classifiers for event-event links. One for syntactic dominance, the other for general.
    if( props.containsKey("eesplit") ) {
      if( featurizer.oneEventDominates(event1, event2, doc.getAllParseTrees()) )
        targetClassifier = eeSameSentDominatesClassifier;
      else
        targetClassifier = eeSameSentNoDominatesClassifier;
    }

    // Get the best label and its probability.
    TLinkDatum datum = featurizer.createEventEventDatum(doc, event1, event2, null);
    RVFDatum<String,String> rvf = datum.createRVFDatum();
    Pair<String,Double> labelProb = getLabelProb(targetClassifier, rvf);
    String label = labelProb.first();

    // Create the actual link with the classified label.
    EventEventLink link = new EventEventLink(event1.getEiid(), event2.getEiid(), TLink.Type.valueOf(label));
    link.setRelationConfidence(labelProb.second());
    return link;
  }
  
  /**
   * Put event-event links from "main events" of neighboring sentences.
   */
  public List<TLink> extractNeighborSentenceEventEventLinks(String docname) {
    System.out.println("doc = (diff sent e-e)" + docname);
    SieveDocument doc = docs.getDocument(docname);
    List<SieveSentence> sentences = doc.getSentences();
    System.out.println(sentences.size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Grab all the parse trees.
    List<Tree> trees = docs.getDocument(docname).getAllParseTrees();

    TextEvent[] mainevents = new TextEvent[trees.size()];
    int sid = 0;

    // Loop over each sentence and get TLinks.
    for( SieveSentence sent : sentences ) {
      List<TextEvent> events = sent.events();
      System.out.println("TEXT: " + sent.sentence());
      //        System.out.println("EVENTS: " + events);

      // If only one event, then use it.
      if( events.size() == 1 ) {
        //          System.out.println("Main event sentence single event: " + events.get(0));
        mainevents[sid] = events.get(0);
      }

      // Otherwise, find the main event.
      else if( events.size() > 1 ) {
        int index = findMainEventIndex(trees.get(sid), trees.get(sid));
        if( index > -1 ) {
          for( TextEvent event : events ) {
            if( event.getIndex() == index )
              mainevents[sid] = event;
          }

          // Didn't match an event, then find the closest one.
          if( mainevents[sid] == null ) {
            TextEvent closest = null;
            int dist = Integer.MAX_VALUE;
            for( TextEvent event : events ) {
              if( Math.abs(event.getIndex()-index) < dist ) {
                dist = Math.abs(event.getIndex()-index);
                closest = event;
              }
            }
            if( closest != null ) mainevents[sid] = closest;
          }
        }
        //          System.out.println("Main event sentence " + sid + " is " + index);
        //          System.out.println("\t--> " + mainevents[sid]);
      }        

      sid++;
    }

    System.out.println("MAIN EVENTS: " + Arrays.toString(mainevents));

    // We now have an array of main events. Create links between neighbors
    for( int xx = 0; xx < mainevents.length-1; xx++ ) {
      if( mainevents[xx] != null && mainevents[xx+1] != null) {
        TLinkDatum datum = featurizer.createEventEventDatum(doc, mainevents[xx], mainevents[xx+1], null);
        System.out.println("e-e datum=" + datum);
//        String label = eeDiffSentClassifier.classOf(datum.createRVFDatum());
        Pair<String,Double> labelProb = getLabelProb(eeDiffSentClassifier, datum.createRVFDatum());
        TLink link = new EventEventLink(mainevents[xx].getEiid(), mainevents[xx+1].getEiid(), TLink.Type.valueOf(labelProb.first()));
        link.setRelationConfidence(labelProb.second());
        tlinks.add(link);
      }
    }
    System.out.println("Returning neighbor e-e tlinks: " + tlinks);
    return tlinks;
  }
  
  private int findMainEventInVP(Tree vp, Tree full) {
    // Grab all children of the VP, and recurse if it contains a nested VP.
    List<Tree> verbs = vp.getChildrenAsList();
    for( Tree verb : verbs ) {
      if( verb.label().value().equals("VP") )
        return findMainEventInVP(verb, full);
    }
    
    // Find the node with a VB* tag, the rightmost one.
    Tree lastVB = null;
    for( Tree verb : verbs ) {
      if( verb.label().value().startsWith("VB") )
        lastVB = verb;
    }

    // If we found a VB* tag, return the index of its token.
    if( lastVB != null)
      return TreeOperator.wordIndex(full, lastVB)+1;
    else return -1;
  }
  
  
  private int findMainEventIndex(Tree tree, Tree full) {
    // If ROOT, then hopefully grab the S child.
    if( tree.label().value().equals("ROOT") && tree.numChildren() == 1 )
      tree = tree.getChild(0);

//    System.out.println(tree.label().value() + "\tnumkids=" + tree.children().length + "\tfrom tree = " + tree);
    
    // Only get main events from normal S trees.
    if( tree.label().value().equals("S") || tree.label().value().equals("SINV") ) {
      Tree vp = null;
      List<Tree> children = tree.getChildrenAsList();
      for( Tree child : children ) {
        if( child.label().value().equals("VP") ) {
          vp = child;
          break;
        }
      }
      
      // We found a normal VP.
      if( vp != null ) {
        return findMainEventInVP(vp, full);
      }
      
      // There was no VP, but there is another S node...recurse.
      else if( children != null && children.get(0).label().value().equals("S") ) {
        return findMainEventIndex(children.get(0), full);
      }
    }
    return -1;
  }
  
  /**
   * Destructively add tlinks to the global .info file.
   */
  public void extractTLinks() {
    extractTLinks(null);
  }
  
  public void extractTLinks(Collection<String> docnames) {
    System.out.println("*** Classifier-Based TLink Extraction ***");
    if( docnames == null ) docnames = docs.getFileNames();
    
    for( String doc : docnames ) {
      List<TLink> links = new ArrayList<TLink>();
      if( props.containsKey("tlinks") || props.containsKey("eesame") )
        links.addAll(extractSameSentenceEventEventLinks(doc));
      if( props.containsKey("tlinks") || props.containsKey("etsame") )
        links.addAll(extractSameSentenceEventTimeLinks(doc));
      if( props.containsKey("tlinks") || props.containsKey("eediff") )
        links.addAll(extractNeighborSentenceEventEventLinks(doc));
      if( props.containsKey("tlinks") || props.containsKey("edct") )
        links.addAll(extractEventDCTLinks(doc));
      
      // Sort by probability.
      links = sortTLinks(links);
      List<TLink> keep = new ArrayList<TLink>();
      for( TLink link : links ) {
        System.out.print("link: " + link + "\t" + link.getRelationConfidence());
        if( link.getRelationConfidence() >= _tlinkProbabilityCutoff ) {
          keep.add(link);
          System.out.print("**");
        }
        System.out.println();
      }
      docs.getDocument(doc).addTlinks(keep);
    }
    System.out.println("Extracted tlinks, finished.");
  }
  
  /**
   * This function assumes the global info file already has TLinks in it. Our task here is
   * to guess the relation labels of those TLinks, replacing the current ones with our guesses.
   * @param docnames The list of documents to label. If null, label all documents.
   */
  public void labelKnownTLinks(Collection<String> docnames) {
    System.out.println("*** Classifier-Based TLink Labeling ***");
    if( docnames == null ) docnames = docs.getFileNames();
    int eediff = 0, etdiff = 0, edct = 0, etsame=0, eesame=0, eeneigh=0;
    Counter<String> saids = new ClassicCounter<String>();
    Counter<String> mysaids = new ClassicCounter<String>();
    
    for( String docname : docnames ) {
    	SieveDocument doc = docs.getDocument(docname);
      List<TLink> links = doc.getTlinks();
      List<TextEvent> events = doc.getEvents();
      List<Timex> timexes = doc.getTimexes();
      List<Tree> trees = doc.getAllParseTrees();
      List<List<TypedDependency>> alldeps = doc.getAllDependencies();

      if( links != null && links.size() > 0 ) {
        Map<String,TextEvent> idToEvent = new HashMap<String,TextEvent>();
        Map<String,Timex> idToTimex     = new HashMap<String,Timex>();
        for( TextEvent event : events ) { 
        	idToEvent.put(event.getId(), event);
        	for( String eiid : event.getAllEiids() )
        	  idToEvent.put(eiid, event);
        }
        for( Timex timex : timexes)     idToTimex.put(timex.getTid(), timex);
        
        for( TLink link : links ) {
          String newLabel = null;
          System.out.println("link = " + link);
          
          // Event-Time links.
          if( link instanceof EventTimeLink ) {
            // event-dct
            if( TimebankUtil.isEventDCTLink(link, doc.getDocstamp()) ) {
              TextEvent e1 = (link.getId1().startsWith("e") ? idToEvent.get(link.getId1()) : idToEvent.get(link.getId2()));
              TLinkDatum datum = featurizer.createEventDocumentTimeDatum(doc, e1, doc.getDocstamp().get(0), null);
              newLabel = etDCTClassifier.classOf(datum.createRVFDatum());
              edct++;
            }
            // event-time
            else {
              TextEvent e1 = (link.getId1().startsWith("e") ? idToEvent.get(link.getId1()) : idToEvent.get(link.getId2()));
              Timex timex  = (link.getId1().startsWith("e") ? idToTimex.get(link.getId2()) : idToTimex.get(link.getId1()));
              System.out.println(e1 + " and " + timex);
              if( e1.getSid() == timex.getSid() ) {
                TLinkDatum datum = featurizer.createEventTimeDatum(doc, e1, timex, null);
                newLabel = etSameSentClassifier.classOf(datum.createRVFDatum());
                etsame++;
              }
              else {
                TLinkDatum datum = featurizer.createEventTimeDatum(doc, e1, timex, null);
                newLabel = etDiffSentClassifier.classOf(datum.createRVFDatum());
                etdiff++;
              }
            }
          }

          // Event-Event links.
          else if( link instanceof EventEventLink ) {
            TextEvent e1 = idToEvent.get(link.getId1());
            TextEvent e2 = idToEvent.get(link.getId2());
            if( TimebankUtil.isBeforeInText(e2, e1) ) {
              TextEvent tt = e1;
              e1 = e2;
              e2 = tt;
            }
            
            // Exact same event. Don't auto-label...hardcode simultaneous!
            if( e1 == e2 || e1.getId() == e2.getId() )
              newLabel = TLink.Type.SIMULTANEOUS.toString();
            
            // event-event same sentence
            else if( e1.getSid() == e2.getSid() ) {
              TLinkDatum datum = featurizer.createEventEventDatum(doc, e1, e2, null);
              newLabel = eeSameSentClassifier.classOf(datum.createRVFDatum());     
              eesame++;
            }
            // event-event neighbor sentence
            else {
              if( TimebankUtil.isNeighborSentence(e1, e2) ) eeneigh++;
              else eediff++;
              TLinkDatum datum = featurizer.createEventEventDatum(doc, e1, e2, null);
              newLabel = eeDiffSentClassifier.classOf(datum.createRVFDatum());
              
              // DEBUG
              if( e1.getString().toLowerCase().equals("said") && e2.getString().toLowerCase().equals("said") ) {
                saids.incrementCount(link.getRelation().toString());
                mysaids.incrementCount(newLabel);
              }
            }
          }
          
          else if( link instanceof TimeTimeLink ) {
            // Get the timexes and do a simple date compare.
            Timex t1 = idToTimex.get(link.getId1());
            Timex t2 = idToTimex.get(link.getId2());
            if( t1.before(t2) )
              newLabel = TLink.Type.BEFORE.toString();
            else if( t2.before(t1) )
              newLabel = TLink.Type.AFTER.toString();
            else if( t1.includes(t2) )
              newLabel = TLink.Type.INCLUDES.toString();
            else if( t2.includes(t1) )
              newLabel = TLink.Type.IS_INCLUDED.toString();
            else // Just guess this one.
              newLabel = TLink.Type.SIMULTANEOUS.toString();
          }
          
          // Set the new relation for this TLink!
          link.setRelation(TLink.Type.valueOf(newLabel));
        }
      }
      
      doc.removeTlinks();
      doc.addTlinks(links);
    }
    System.out.println("Labeled known tlinks, finished.");
    System.out.println("# event-time inter-sentence = " + etdiff);
    System.out.println("# event-time intra-sentence = " + etsame);
    System.out.println("# event-dct = " + edct);
    System.out.println("# event-event intra-sentence = " + eesame);
    System.out.println("# event-event neighbor = " + eeneigh);
    System.out.println("# event-event links across > 1 sentence boundaries = " + eediff);
    System.out.println("saids: " + Counters.toString(saids, 100));
    System.out.println("my guessed saids: " + Counters.toString(mysaids, 100));
  }
  
  private List<TLink> sortTLinks(List<TLink> links) {
    List<TLink> sorted = new ArrayList<TLink>(links.size());
    
    PriorityQueue<TLink> q = new PriorityQueue<TLink>();
    for( TLink link : links ) q.add(link);
    
    for( int xx = 0; xx < links.size(); xx++ )
      sorted.add(q.poll());
    
    return sorted;
  }
  
  /**
   * Using the given classifier, return the highest scoring label with its probability.
   * @param classifier The classifier to use.
   * @param rvf The datum to classify.
   * @return A pair: (1) the top label, (2) the probability of the top label
   */
  public static Pair<String,Double> getLabelProb(Classifier<String,String> classifier, RVFDatum<String,String> rvf) {
    Counter<String> scores = classifier.scoresOf(rvf);
    Counters.logNormalizeInPlace(scores);
    for (String label : scores.keySet())
      scores.setCount(label, Math.exp(scores.getCount(label)));
    
    String label = Counters.argmax(scores);
//    System.out.println("Returning: " + new Pair<String,Double>(label, scores.getCount(label)));
    return new Pair<String,Double>(label, scores.getCount(label));
  }
  
  /**
   * Get all of the class labels used in the dataset, return them as a set.
   */
  public static Set<String> getUniqueLabels(List<TLinkDatum> datums) {
    Set<String> labels = new HashSet<String>();
    for( TLinkDatum datum : datums )
      labels.add(datum.getLabelAsString());
    return labels;
  }
  
  
  public void readClassifiersFromDirectory(String dirpath) {
    try {
      eeSameSentClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.samesent.classifier-all");
      eeSameSentExistsClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.samesent.exists.classifier-all");
      eeSameSentDominatesClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.samesent.dominate.classifier-all");
      eeSameSentNoDominatesClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.samesent.nodominate.classifier-all");
      eeDiffSentClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.ee.diffsent.classifier-all");
      etSameSentClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.et.samesent.classifier-all");
      etSameSentExistsClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.et.samesent.exists.classifier-all");
      etDiffSentClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.et.diffsent.classifier-all");
      etDCTClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.et.dct.classifier-all");
      etDCTExistsClassifier = (Classifier<String,String>)IOUtils.readObjectFromFile(dirpath + File.separator + "tlink.et.dct.exists.classifier-all");
    } catch(Exception ex) { 
      System.out.println("Had fatal trouble loading " + dirpath);
      ex.printStackTrace(); System.exit(1); 
    }
  }
  
  public void writeClassifiersToFile() {
    try {
      // Create the directory of classifiers, if necessary.
      File dir = new File(modelDir);
      if( !dir.exists() ) dir.mkdir();

      // Write the classifiers.
      IOUtils.writeObjectToFile(eeSameSentClassifier, modelDir + File.separator + "tlink.ee.samesent.classifier-all");
      IOUtils.writeObjectToFile(eeSameSentExistsClassifier, modelDir + File.separator + "tlink.ee.samesent.exists.classifier-all");
      IOUtils.writeObjectToFile(eeSameSentDominatesClassifier, modelDir + File.separator + "tlink.ee.samesent.dominate.classifier-all");
      IOUtils.writeObjectToFile(eeSameSentNoDominatesClassifier, modelDir + File.separator + "tlink.ee.samesent.nodominate.classifier-all");
      IOUtils.writeObjectToFile(eeDiffSentClassifier, modelDir + File.separator + "tlink.ee.diffsent.classifier-all");
      IOUtils.writeObjectToFile(etSameSentClassifier, modelDir + File.separator + "tlink.et.samesent.classifier-all");
      IOUtils.writeObjectToFile(etSameSentExistsClassifier, modelDir + File.separator + "tlink.et.samesent.exists.classifier-all");
      IOUtils.writeObjectToFile(etDiffSentClassifier, modelDir + File.separator + "tlink.et.diffsent.classifier-all");
      IOUtils.writeObjectToFile(etDCTClassifier, modelDir + File.separator + "tlink.et.dct.classifier-all");
      IOUtils.writeObjectToFile(etDCTExistsClassifier, modelDir + File.separator + "tlink.et.dct.exists.classifier-all");
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if( args[args.length-1].equals("traininfo") ) {
      TLinkClassifier classifier = new TLinkClassifier();
      classifier.trainInfo(args);
      classifier.writeClassifiersToFile();
    }
    else if( args[args.length-1].equals("trainfeats") ) {
      TLinkClassifier classifier = new TLinkClassifier();
      classifier.trainFeats(args[args.length-2]);
      classifier.writeClassifiersToFile();
    }
    else if( args[args.length-1].equals("testinfo") ) {
      Properties props = StringUtils.argsToProperties(args);
      TLinkClassifier classifier = new TLinkClassifier(new SieveDocuments(args[args.length-3]), args[args.length-2], props);
      classifier.extractTLinks();
      classifier.docs.writeToXML(new File("tlinks.info.xml"));
    }
    else if( args[args.length-1].equals("testknownlinks") ) {
      Properties props = StringUtils.argsToProperties(args);
      TLinkClassifier classifier = new TLinkClassifier(new SieveDocuments(args[args.length-3]), args[args.length-2], props);
      classifier.labelKnownTLinks(null);
      String out = "tlinks.info.xml";
      if( props.containsKey("out") ) out = props.getProperty("out");
      System.out.println("Writing to " + out);
      classifier.docs.writeToXML(new File(out));
    }
    else {
      System.out.println("Unknown command!");
      System.out.println("TLinkClassifier traininfo | trainfeats | testinfo | testknownlinks");
    }
  }

}
