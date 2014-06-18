package caevo.tlink;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import caevo.Main;
import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.Timex;
import caevo.util.HandleParameters;
import caevo.util.TimebankUtil;
import caevo.util.TreeOperator;
import caevo.util.WordNet;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.Synset;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * This is the NEWEST (2012) best code to learn from TimeBank annotations.
 * It uses the JavaNLP toolkit and the code to build features is a lot cleaner than
 * my earlier ACL 2007 work back in the day.
 * 
 * INPUT:
 *  (1) A .info file of TimeBank preprocessed with parse trees and event information.
 * 
 * OUTPUT: 
 *  (1) a text file with all of the TLink features, one tlink per line
 *  (2) a second text file identical to the first, but for debugging, and with the TLink event IDs
 *      at the start of the line.
 *
 * TimebankFeaturizer -wordnet <jwnlfile> -output <path> -info <infofile>
 *      [-dobethard] [-doturk [<int>]] [-maxtb <int>] [-nosamesent] [-dohappened] [-tempeval] [-tempevalE] [-tempevalF]
 */
public class TLinkFeaturizer {
  SieveDocuments _infoDocs;
  String _infoPath;
  WordNet _wordnet;
  String _wordnetPath = "/home/nchamber/code/lib/jwnl_file_properties.xml";
  TreeFactory _tf;
  public String _outpath = null;

  public boolean debug = false;
  
  // If all false, we only featurize the TLinks that were hand labeled in TimeBank.
  public boolean _doBethard = false;
  public boolean _doTurk = false;
  public boolean _doTempeval = false;
  public boolean _doHappened = false;
  public boolean _doClosed = false;   // NOTE: closed might have been derived with Bethard/Tempeval links, so 
                              //       setting this to true should have those true too for consistency.

  public boolean _noEventFeats = true; // if true, don't add tense/aspect/polarity
  
  public boolean _ignoreSameSentence = false; // skips any tlink with both events in the same sentence
  public boolean _sameSentenceOnly = false;
  public boolean _diffSentenceOnly = false; // all cross-sentence links
  public boolean _neighborSentenceOnly = false; // all cross-sentence links
  public boolean _eventEventOnly = true;
  public boolean _eventEventDominates = false;
  public boolean _eventEventNoDominates = false;
  public boolean _eventTimeOnly   = false;
  public boolean _noEventTimeDiff = true;
  public boolean _eventDCTOnly    = false; // event-documentCreationTime links only.
  public boolean _noEventDCT      = false;
  public boolean _noTimeTime      = true;
  int _timebankMaxSentenceSpan = Integer.MAX_VALUE;
  int _turkMaxSentenceSpan = Integer.MAX_VALUE;
  
  boolean tempevalTaskE = false; // event-event in adjacent sentences
  boolean tempevalTaskF = false; // event-event in current sentence, syntactically dominating

  boolean _tempeval2Mode = false; // if true, converts all TLink labels to BEFORE/AFTER/OVERLAP if not already
  
  public TLinkFeaturizer() {
    _tf = new LabeledScoredTreeFactory();
    init();
  }
  
  public TLinkFeaturizer(String[] args) {
    _tf = new LabeledScoredTreeFactory();
    handleParams(args);
    init();
  }

  private void init() {
  	// Use the Main function's WordNet so we don't keep loading multiple copies of it.
    if( _wordnet == null ) _wordnet = Main.wordnet;
  }
  
  private void handleParams(String[] args) {
    HandleParameters params = new HandleParameters(args);
    
    if( params.hasFlag("-wordnet") ) 
      _wordnet = new WordNet(params.get("-wordnet"));
    
    if( params.hasFlag("-tempeval") )
      _doTempeval = true;
    if( params.hasFlag("-tempevalE") )
      tempevalTaskE = true;
    if( params.hasFlag("-tempevalF") )
      tempevalTaskF= true;

    if( params.hasFlag("-dobethard") || params.hasFlag("-bethard") )
      _doBethard = true;
    
    if( params.hasFlag("-dohappened") || params.hasFlag("-dohappen") )
      _doHappened = true;
    
    if( params.hasFlag("-nosamesent") || params.hasFlag("-nosamesentence") ) // skip same sentence tlinks
      _ignoreSameSentence = true;
      
    if( params.hasFlag("-noeventfeats") ) // skip tense/aspect/etc. features
      _noEventFeats = true;    

    if( params.hasFlag("-doturk") ) {
      _doTurk = true;
      // Users can give an integer which is the most sentences the Turk link can span to be included in the experiment.
      if( params.get("-doturk") != null && params.get("-doturk").length() > 0 ) 
        _turkMaxSentenceSpan = Integer.parseInt(params.get("-doturk"));
    }
    
    // Maximum sentence span a tlink can be to be output as a datum.
    if( params.hasFlag("-maxtb") ) 
      _timebankMaxSentenceSpan = Integer.parseInt(params.get("-maxtb"));
    
    if( params.hasFlag("-output") ) 
      _outpath = params.get("-output");
    else {
      System.out.println("No output path given on -output flag.");
      System.exit(-1);
    }
    
    if( params.hasFlag("-info") ) 
      _infoPath = params.get("-info");
    else {
      System.out.println("No info file given on -info flag.");
      System.exit(-1);
    }
  }
  
  /**
   * Create a datum for each TLink in the given InfoFile.
   * Chooses TLinks to include based on a series of global flags.
   * @param infoDocs The infofile with all of our TimeBank information.
   * @param docnames List of documents you want to featurize, or null if you want ALL featurized.
   */
  public List<TLinkDatum> infoToTLinkFeatures(SieveDocuments infoDocs, Set<String> docnames) {
    int ii = 0;
    List<TLinkDatum> data = new ArrayList<TLinkDatum>();
    
    for( SieveDocument doc : infoDocs.getDocuments() ) {
      if( docnames == null || docnames.contains(doc.getDocname()) ) {
        System.out.println("\n--------------------------------------------------");
        System.out.println("File " + doc.getDocname() + " (" + ++ii + " of " + infoDocs.getDocuments().size() + ")");

        List<SieveSentence> sentences = doc.getSentences();
        Collection<TLink> tlinks = doc.getTlinks();
        List<Timex> dcts = doc.getDocstamp();
        List<List<TypedDependency>> alldeps = doc.getAllDependencies();
        List<Tree> trees = doc.getAllParseTrees();
        List<TextEvent> events = doc.getEvents();
        List<Timex> timexes = doc.getTimexes();
        int sid = 0;
        int numBethard = 0;
        int numTurk = 0;
        int numTimebank = 0;

        // --- Create the TLink features ---

        for( TLink link : tlinks ) {
//          System.out.println("tlink: " + link);

          if( (_noTimeTime && link instanceof TimeTimeLink) )
            continue;
          
          if( (_eventEventOnly && link instanceof EventTimeLink) )
            continue;

          if( (_eventTimeOnly || _eventDCTOnly) && link instanceof EventEventLink )
            continue;
          
          //          System.out.println("Checking link=" + link);
          boolean isdctlink = TimebankUtil.isEventDCTLink(link, dcts);
          boolean eventsDominate = oneEventDominates(link, events, trees);

          // Skip event-time links with the document timestamp unless we specifically want them.
          if( _noEventDCT && link instanceof EventTimeLink && isdctlink ) {
            if( debug ) System.out.println("Skipping event-DCT link: " + link);
            continue;
          }

          // Skip links that don't syntactically dominate, if desired in global booleans.
          if( link instanceof EventEventLink && eventsDominate && _eventEventNoDominates ) {
            if( debug ) System.out.println("Skipping event-event dominates link: " + link);
            continue;
          }
          if( link instanceof EventEventLink && !eventsDominate && _eventEventDominates ) {
            if( debug ) System.out.println("Skipping event-event doesn't dominate link: " + link);
            continue;
          }

          // Statistics tracking.
          if( !link.closed && !isdctlink && sentenceSpan(doc, link) == 0 ) {
            if( link.getOrigin() == null ) numTimebank++;
            else if( link.getOrigin().equals("bethard") ) numBethard++;
            else if( link.getOrigin().equals("turk") ) numTurk++;
          }

          // If we only want TimeBank original files.
          if( link.getOrigin() == null || link.getOrigin().equals("null")    ||
              (_doBethard && link.getOrigin().equals("bethard"))          ||
              (_doTurk && link.getOrigin().equals("turk"))                ||
              (_doTempeval && link.getOrigin().contains("tempeval"))      ||
              (tempevalTaskE && link.getOrigin().equals("tempeval-main")) ||
              (tempevalTaskF && link.getOrigin().equals("tempeval-sub"))
          ) {

            // Skip closed links.
            if( !link.closed ) {
              // Only do links that have labels for now. Skip OVERLAP from Bethard...Tempeval3 does not have this relation.
              if( link.getRelation() != TLink.Type.NONE && link.getRelation() != TLink.Type.OVERLAP ) {
                int sentenceSpan = (isdctlink ? -1 : sentenceSpan(doc, link));

                System.out.println("link: " + link + "\tspan=" + sentenceSpan);

                if( _eventDCTOnly && !isdctlink ) {
                  if( debug ) System.out.println("Skipping non-DCT link " + link);
                  continue;
                }

                // Skip intra-sentence links. Only keep cross-sentence links.
                if( (_ignoreSameSentence || _diffSentenceOnly || _neighborSentenceOnly) && sentenceSpan == 0 ) {
                  if( debug ) System.out.println("Skipping intra-sentence link " + link);
                  continue;
                }

                if( _neighborSentenceOnly && sentenceSpan != 1 ) {
                  if( debug ) System.out.println("Skipping non-neighbor sentence link " + link);
                  continue;
                }
                
                if( _sameSentenceOnly && sentenceSpan > 0 ) {
                  if( debug ) System.out.println("Skipping inter-sentence link " + link);
                  //                  System.out.println("\tspanned " + sentenceSpan(link, events, timexes) + "....link=" + link);
                  continue;
                }

                if( _noEventTimeDiff && link instanceof EventTimeLink && !isdctlink && sentenceSpan > 0 )
                  continue;
                
                // Skip links from Turk if they are greater than our experiment's limited sentence span.
                if( link.getOrigin() != null && link.getOrigin().equals("turk") && sentenceSpan > _turkMaxSentenceSpan ) {
                  if( debug ) System.out.println("Skipping turk link " + link + "\n\tIt spans " + sentenceSpan + " sentences.");
                  continue;
                }

                // Skip links from TimeBank if they are greater than our experiment's limited sentence span.
                if( !isdctlink && (link.getOrigin() == null || link.getOrigin().equalsIgnoreCase("timebank")) && sentenceSpan > _timebankMaxSentenceSpan ) {
                  if( debug ) System.out.println("Skipping timebank link " + link + "\n\tIt spans " + sentenceSpan + " sentences.");
                  continue;
                }

//                System.out.println("Will create link=" + link);
                TLinkDatum datum = createTLinkDatum(doc, link, isdctlink);
                datum.setDocSource(doc.getDocname());
                data.add(datum);
                if( debug ) System.out.println("link: " + link);
                if( debug ) System.out.println("\tdatum: " + datum);
              }
            }
          }
        }
      
        System.out.println("Doc " + doc.getDocname() + "\tnumbethard=" + numBethard + "\tnumturk=" + numTurk + "\tnumtimebank=" + numTimebank);
      }
      
//      break;
    }
    
    return data;
  }
  
  /**
   * Better function that uses the SieveDocument properly.
   * @param doc The document from which the link derives.
   * @param link The link we care about.
   * @return The number of sentences that are spanned by this link (0 is the same sentence).
   */
  private int sentenceSpan(SieveDocument doc, TLink link) {
    if( link instanceof EventEventLink ) {
      TextEvent event1 = doc.getEventByEiid(link.getId1());
      TextEvent event2 = doc.getEventByEiid(link.getId2());
      return Math.abs(event1.getSid() - event2.getSid());
    } 
    else if( link instanceof EventTimeLink ) {
      int first = 0, second = 0;
      if( link.getId1().startsWith("e") ) {
        TextEvent event = doc.getEventByEiid(link.getId1());
        first = event.getSid();
        second = doc.getTimexByTid(link.getId2()).getSid();
      } else {
        TextEvent event = doc.getEventByEiid(link.getId2());
        first = event.getSid();
        second = doc.getTimexByTid(link.getId1()).getSid();
      }
      return Math.abs(first - second);
    }
    else {
      System.out.println("ERROR unknown tlink type in sentenceSpan(): " + link);
      System.exit(1);
    }
    return -1;
  }
  
  /**
   * Create a datum object for the given TLink link with all of its features.
   * Creates the features from the parse trees and event attributes.
   * @param link The TLink to featurize.
   * @param trees All phrase trees from one document.
   * @param events All of the events in the document (will find the two from the TLink).
   * @param timexes All of the timexes in the document.
   * @return A new TLinkDatum representing the given TLink.
   */
  private TLinkDatum createTLinkDatum(SieveDocument doc, TLink link, boolean isdctlink) {
    // Change to TempEval labels.
    if( _tempeval2Mode ) {
      link.fullToTempeval();
      TLink.changeMode(TLink.Mode.TEMPEVAL);
    }
    
    // EVENT-EVENT links.
    if( link instanceof EventEventLink ) {
      TextEvent event1 = doc.getEventByEiid(link.getId1());
      TextEvent event2 = doc.getEventByEiid(link.getId2());
      TLink.Type label = link.getRelation();

      // Sanity check
      if( event1 == null || event2 == null ) {
        System.out.println("Didn't find event (" + link.getId1() + "," + link.getId2() + ")! event1=" + event1 + " event2=" + event2);
      }

      TLinkDatum datum = createEventEventDatum(doc, event1, event2, label);
      datum.setOriginalTLink(link);
      return datum;
    }
    
    // EVENT-TIME links.
    else if( link instanceof EventTimeLink ) {
      TLink.Type label = link.getRelation();

      TextEvent event = null;
      Timex timex = null;
      if( link.getId1().startsWith("e") ) {
        event = doc.getEventByEiid(link.getId1());
        timex = doc.getTimexByTid(link.getId2());
      } else if( link.getId1().startsWith("t") ) {
        event = doc.getEventByEiid(link.getId2());
        timex = doc.getTimexByTid(link.getId1());
        label = EventTimeLink.invertRelation(label); // Always make it the (e,t) relation, not the (t,e) relation.
      } else {
        System.out.println("TLinkFeaturizer: Don't know how to handle this event id: " + link.getId1());
        System.exit(1);
      }

      // Sanity check
      if( event == null || (!isdctlink && timex == null) ) {
        System.out.println("Didn't find event-time (" + link.getId1() + "," + link.getId2() + ")! event1=" + event + " event2=" + timex);
      }
      
      TLinkDatum datum;
      if( isdctlink ) datum = createEventDocumentTimeDatum(doc, event, timex, label);
      else datum = createEventTimeDatum(doc, event, timex, label);
      datum.setOriginalTLink(link);
      return datum;
    }
    
    else {
      System.out.println("TLinkFeaturizer: we don't handle this type of TLink: " + link.getClass());
      System.exit(1);
    }
    return null;
  }
  
  /**
   * The main function to featurize a TLink. Given two events, featurize both and create a single TLinkDatum.
   * The "label" is optional and can be null if we don't know it for testing. For training, you'll want to
   * include the gold label here.
   * NOTE: **** the label should agree with the order of "event LABEL time" ***
   * @param event1 The first event.
   * @param event2 The second event. (doesn't have to be second in textual order)
   * @param label The gold label, or null if unknown.
   * @param trees All parse trees for the entire document where these two events reside.
   * @return A single TLinkDatum object with relevant features.
   */
  public TLinkDatum createEventTimeDatum(SieveDocument doc, TextEvent event, Timex time, TLink.Type label) {
    Counter<String> feats = new ClassicCounter<String>();
    List<Tree> trees = doc.getAllParseTrees();

    // Sanity check
    if( event == null || time == null ) 
      System.out.println("Null events in createEventTimeDatum(): " + event + " and " + time);
    
    // Textual order.
    if( TimebankUtil.isBeforeInText(event, time) )
      feats.incrementCount("event-first");
    else
      feats.incrementCount("time-first");
    
    feats.addAll(getSingleEventPOSFeatures("pos1", event, trees));
    feats.addAll(getSingleEventFeatures(event, trees));      // tense, modality, etc.
    feats.addAll(getSingleEventTokenFeatures(1, event, trees));      // token, lemma, wordnet
    feats.addAll(getTimexFeatures(time, trees));
    feats.addAll(getEventTimeBigram(event, time, trees));
    feats.addAll(getEventTimeTokenPathFeature(event, time, trees));
    feats.addAll(getParsePathFeatures(event, time, trees));
    feats.addAll(getDepsPathFeatures(event, time, doc.getSentences().get(event.getSid()).getDeps()));
    feats.addAll(getDominanceFeatures(event, time, trees)); // always based on the event's dominance or not
        
    TLinkDatum datum = new TLinkDatum(label);
    datum.addFeatures(feats);
    if( event.getSid() == time.getSid() )
      datum.setType(TLinkDatum.TYPE.ETSAME);
    else
      datum.setType(TLinkDatum.TYPE.ETDIFF);
    if( debug ) System.out.println("et datum: " + datum);
    return datum;
  }
  
  public TLinkDatum createEventDocumentTimeDatum(SieveDocument doc, TextEvent event, Timex time, TLink.Type label) {
    Counter<String> feats = new ClassicCounter<String>();
    List<Tree> trees = doc.getAllParseTrees();
    
    // Sanity check
    if( event == null ) 
      System.out.println("Null event in createEventDocumentTimeDatum(): " + event + " and " + time);
    
    feats.addAll(getSingleEventPOSFeatures("pos1", event, trees));
    feats.addAll(getSingleEventFeatures(event, trees));      // tense, modality, etc.
    feats.addAll(getSingleEventTokenFeatures(1, event, trees));      // token, lemma, wordnet
    feats.addAll(getSingleEventNearbyBOWFeatures(event, trees)); // +.01 accuracy, very minimal.

    TLinkDatum datum = new TLinkDatum(label);
    datum.addFeatures(feats);
    datum.setType(TLinkDatum.TYPE.EDCT);
    return datum;
  }
  
  /**
   * The main function to featurize a TLink. Given two events, featurize both and create a single TLinkDatum.
   * The "label" is optional and can be null if we don't know it for testing. For training, you'll want to
   * include the gold label here.
   * @param event1 The first event.
   * @param event2 The second event. (doesn't have to be second in textual order)
   * @param label The gold label, or null if unknown.
   * @param events The events in the single sentence of these 2 events. If they are in diff sentences, just set to null.
   * @param trees All parse trees for the entire document where these two events reside.
   * @return A single TLinkDatum object with relevant features.
   */
  public TLinkDatum createEventEventDatum(SieveDocument doc, TextEvent event1, TextEvent event2, TLink.Type label) {
    Counter<String> feats = new ClassicCounter<String>();
    List<Tree> trees = doc.getAllParseTrees();
    List<TextEvent> events = doc.getEvents();

    // Sanity check
    if( event1 == null || event2 == null ) 
      System.out.println("Null events in createTLinkDatum(): " + event1 + " and " + event2);
    
    // Flip the order to the natural text order.
    if( !TimebankUtil.isBeforeInText(event1, event2) ) {
      TextEvent temp = event1;
      event1 = event2;
      event2 = temp;
      if( label != null ) label = TLink.invertRelation(label);
    }
    
    feats.addAll(getPOSFeatures(event1, event2, trees));
    feats.addAll(getEventFeatures(event1, event2, trees));      // tense, modality, etc.
    feats.addAll(getTokenFeatures(event1, event2, trees, events));   // token, lemma, wordnet
    feats.addAll(getSyntacticFeatures(event1, event2, trees));  // prep phrases?
    feats.addAll(getDominanceFeatures(event1, event2, trees));
    feats.addAll(getTextOrderFeatures(event1, event2, trees));
    feats.addAll(getEventInterferenceFeatures(event1, event2, events));
    feats.addAll(getParsePathFeatures(event1, event2, trees));
    feats.addAll(getDepsPathFeatures(event1, event2, doc.getSentences().get(event1.getSid()).getDeps()));
        
    TLinkDatum datum = new TLinkDatum(label);
    datum.addFeatures(feats);
    if( event1.getSid() != event2.getSid() )
    	datum.setType(TLinkDatum.TYPE.EEDIFF);
    else if( oneEventDominates(event1, event2, trees) )
    	datum.setType(TLinkDatum.TYPE.EESAMEDOMINATES);
    else
    	datum.setType(TLinkDatum.TYPE.EESAMENODOMINATE);
    
//    System.out.println("e1: " + event1 + "\te2: " + event2);
    if( debug ) System.out.println("datum: " + datum);
    return datum;
  }
  
  
  /**
   * Event features using just its event POS tags.
   */
  private Counter<String> getSingleEventPOSFeatures(String featprefix, TextEvent event1, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    
    Tree tree1 = trees.get(event1.getSid());

    String pos10 = TreeOperator.indexToPOSTag(tree1, event1.getIndex());
    String pos11 = TreeOperator.indexToPOSTag(tree1, event1.getIndex()-1);
    String pos12 = TreeOperator.indexToPOSTag(tree1, event1.getIndex()-2);
    if( event1.getIndex() == 2 ) {
      pos12 = "<s>";
    } else if( event1.getIndex() == 1 ) {
      pos11 = "<s>";
      pos12 = "<pre-s>";
    }
    feats.incrementCount(featprefix + "-0-" + pos10);
    feats.incrementCount(featprefix + "-1-" + pos11);
    feats.incrementCount(featprefix + "-2-" + pos12);
    feats.incrementCount(featprefix + "-bi-" + pos11 + "-" + pos10);
    
    return feats;
  }
  
  /**
   * All features using just the event POS tags.
   */
  private Counter<String> getPOSFeatures(TextEvent event1, TextEvent event2, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    
    feats.addAll(getSingleEventPOSFeatures("pos1", event1, trees));
    feats.addAll(getSingleEventPOSFeatures("pos2", event2, trees));

    // bigram
    Tree tree1 = trees.get(event1.getSid());
    Tree tree2 = trees.get(event2.getSid());
    String pos10 = TreeOperator.indexToPOSTag(tree1, event1.getIndex());
    String pos20 = TreeOperator.indexToPOSTag(tree2, event2.getIndex());
    feats.incrementCount("posBi-" + pos10 + "-" + pos20);
    
    return feats;
  }
  
  /**
   * Create features around each event's labeled attributes (tense, aspect, etc)
   */
  private Counter<String> getSingleEventFeatures(TextEvent event, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    
    if( !_noEventFeats ) {
      feats.incrementCount("ev1Tense-" + event.getTense());
      feats.incrementCount("ev1Aspect-" + event.getAspect());
      if( event.getModality() != null ) feats.incrementCount("ev1Modality-" + event.getModality());
      feats.incrementCount("ev1Class-" + event.getTheClass());
      if( event.getPolarity() != null ) feats.incrementCount("ev1Polarity-" + event.getPolarity());
    }
    
    // These are from Turker experiments. Will include if the .info file has them!
    if( _doHappened && event.getHappened() != null ) {
      feats.incrementCount("ev1Happened-" + event.getHappened());
    }
    
    return feats;
  }
  
  private Counter<String> getEventTimeTokenPathFeature(TextEvent event, Timex time, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
   
    if( event.getSid() == time.getSid() ) {
      // If they are near each other, grab the intervening n-gram.
      if( Math.abs(event.getIndex()-time.getTokenOffset()) < 5 || 
          Math.abs(event.getIndex()-time.getTokenOffset()+time.getTokenLength()-1) < 5 ) {
        List<String> tokens = TreeOperator.stringLeavesFromTree(trees.get(event.getSid()));
        String ngram = "EVENT";
        String tail = "TIME";
        //      System.out.println("token path! " + tokens);
        //      System.out.println("\t" + event + "\t" + time);

        // Figure out if the event or time is first.
        int start = event.getIndex();
        int end = time.getTokenOffset();
        if( start > time.getTokenOffset() ) { 
          start = time.getTokenOffset()+time.getTokenLength()-1;
          end = event.getIndex();
          ngram = "TIME";
          tail = "EVENT";
        }
        //      System.out.println("start=" + start + " end=" + end);
        // Because are indices are 1 indexed, but the list of tokens is of course 0 indexed.
        start--;
        end--;

        // Build the intervening string.
        for( int xx = start+1; xx < end; xx++ )
          ngram += "_" + tokens.get(xx);
        ngram += "_" + tail;
        feats.incrementCount("tokenpath-" + ngram);
      }
    }
    
    return feats;
  }
  
  /**
   * Create features around each event's labeled attributes (tense, aspect, etc)
   */
  private Counter<String> getEventFeatures(TextEvent event1, TextEvent event2, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    
    if( !_noEventFeats ) {
      feats.incrementCount("ev1Tense-" + event1.getTense());
      feats.incrementCount("ev2Tense-" + event2.getTense());
      feats.incrementCount("tenses-" + event1.getTense() + "-" + event2.getTense());
      if( event1.getTense() == event2.getTense() )
        feats.incrementCount("tensematch-YES");
      else
        feats.incrementCount("tensematch-NO");

      feats.incrementCount("ev1Aspect-" + event1.getAspect());
      feats.incrementCount("ev2Aspect-" + event2.getAspect());
      feats.incrementCount("aspects-" + event1.getAspect() + "-" + event2.getAspect());
      if( event1.getAspect() == event2.getAspect() )
        feats.incrementCount("aspectmatch-YES");
      else
        feats.incrementCount("aspectmatch-NO");

      feats.incrementCount("ev1Modality-" + event1.getModality());
      feats.incrementCount("ev2Modality-" + event2.getModality());
      feats.incrementCount("modalities-" + event1.getModality() + "-" + event2.getModality());
      if( event1.getModality().equalsIgnoreCase(event2.getModality()) )
        feats.incrementCount("modalmatch-YES");
      else
        feats.incrementCount("modalmatch-NO");

      feats.incrementCount("ev1Class-" + event1.getTheClass());
      feats.incrementCount("ev2Class-" + event2.getTheClass());
      feats.incrementCount("classes-" + event1.getTheClass() + "-" + event2.getTheClass());
      if( event1.getTheClass() == event2.getTheClass() )
        feats.incrementCount("classmatch-YES");
      else
        feats.incrementCount("classmatch-NO");

      feats.incrementCount("ev1Polarity-" + event1.getPolarity());
      feats.incrementCount("ev2Polarity-" + event2.getPolarity());
      feats.incrementCount("polarities-" + event1.getPolarity() + "-" + event2.getPolarity());
      if( event1.getPolarity() == event2.getPolarity() )
        feats.incrementCount("polaritymatch-YES");
      else
        feats.incrementCount("polaritymatch-NO");
    }
    
    // These are from Turker experiments. Will include if the .info file has them!
    if( _doHappened && event1.getHappened() != null ) {
      feats.incrementCount("ev1Happened-" + event1.getHappened());
      feats.incrementCount("ev2Happened-" + event2.getHappened());
      feats.incrementCount("happeneds-" + event1.getHappened() + "-" + event2.getHappened());
      if( event1.getHappened().equalsIgnoreCase(event2.getHappened()) )
        feats.incrementCount("happenedmatch-YES");
      else
        feats.incrementCount("happenedmatch-NO");
    }
    
    return feats;
  }
  
  /**
   * Get the single tokens around the target event within a window size.
   * @param event The event to link to the document time.
   * @param trees All the parse trees of the entire document.
   */
  private Counter<String> getSingleEventNearbyBOWFeatures(TextEvent event, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    Tree tree = trees.get(event.getSid());
    List<String> tokens = TreeOperator.stringLeavesFromTree(tree);
    
    int window = 2;
    int start = Math.max(0, event.getIndex()-1-window);
    int end = Math.min(tokens.size()-1, event.getIndex()-1+window);

    for( int xx = 0; xx < window; xx++ ) {
      if( start+xx < event.getIndex()-1 ) feats.incrementCount("bow-" + tokens.get(start+xx).toLowerCase());
      if( end-xx > event.getIndex()-1 )   feats.incrementCount("bow-" + tokens.get(end-xx).toLowerCase());
    }
    
    return feats;
  }
  
  
  /**
   * Create token/lemma/synset features for an event.
   * @param eventIndex Either 1 or 2, the first or second event in your link. This differentiates the feature names.
   */
  private Counter<String> getSingleEventTokenFeatures(int eventIndex, TextEvent event1, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    
    String token = event1.getString();
    String postag = TreeOperator.indexToPOSTag(trees.get(event1.getSid()), event1.getIndex());
    String lemma = _wordnet.lemmatizeTaggedWord(token, postag);

    // Token and Lemma
    feats.incrementCount("token" + eventIndex + "-" + token);
    feats.incrementCount("lemma" + eventIndex + "-" + lemma);
    
    // WordNet synset
    Synset[] synsets = null;
    if( postag.startsWith("VB") )
      synsets = _wordnet.synsetsOf(token, POS.VERB);
    else if( postag.startsWith("NN") )
      synsets = _wordnet.synsetsOf(token, POS.NOUN);
    if( synsets != null && synsets.length > 0 )
      feats.incrementCount("synset" + eventIndex + "-" + synsets[0].getOffset());
    
    return feats;
  }
  
  private Counter<String> getEventEventBigram(TextEvent event1, TextEvent event2, List<TextEvent> events) {
    Counter<String> feats = new ClassicCounter<String>();
    feats.incrementCount("BI-" + event1.getString() + "_" + event2.getString());

  	// Bigram with generic "event" tokens between them, based on how many other events separate them.
    if( event1.getSid() == event2.getSid() ) {
    	int numInterlopers = countInterlopers(event1, event2, events);
    	String str = "SEQ-" + event1.getString();
    	for( int xx = 0; xx < numInterlopers; xx++ )
    		str += "_EVENT";
    	str += "_" + event2.getString();
    }
    
    return feats;
  }
  
  /**
   * Create token/lemma/synset features with the events.
   */
  private Counter<String> getTokenFeatures(TextEvent event1, TextEvent event2, List<Tree> trees, List<TextEvent> events) {
    Counter<String> feats = new ClassicCounter<String>();
    
    feats.addAll(getSingleEventTokenFeatures(1, event1, trees));
    feats.addAll(getSingleEventTokenFeatures(2, event2, trees));
    feats.addAll(getEventEventBigram(event1, event2, events));
    
    return feats;
  }
  
  private Counter<String> getTimexFeatures(Timex timex, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    List<String> tokens = TreeOperator.stringLeavesFromTree(trees.get(timex.getSid()));
    int start = timex.getTokenOffset()-1;
    int end = start + timex.getTokenLength()-1; // inclusive
      
    // Leftmost token in the time phrase.
    if( TimebankUtil.isDayOfWeek(tokens.get(end)) )
      feats.incrementCount("timetoken-DAYOFWEEK");
    else
      feats.incrementCount("timetoken-" + tokens.get(end));
    
    // Entire time phrase.
    if( timex.getTokenLength() > 1 ) {
      String phrase = tokens.get(start);
      for( int xx = 1; xx < timex.getTokenLength(); xx++ )
        phrase += "_" + tokens.get(start+xx);
      feats.incrementCount("timephrase-" + phrase);
    }
    
    // Is the timex the last phrase in the sentence?
    if( tokens.size()-1 == end )
      feats.incrementCount("timeEOS");
    
    return feats;
  }

  /**
   * Create one feature string, the bigram of the event word and the rightmost token in the timex phrase.
   * The bigram is ordered by text order.
   */
  private Counter<String> getEventTimeBigram(TextEvent event, Timex timex, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    List<String> tokens = TreeOperator.stringLeavesFromTree(trees.get(timex.getSid()));
    String timeToken = tokens.get(timex.getTokenOffset()-1);
    if( TimebankUtil.isDayOfWeek(timeToken) )
      timeToken = "DAYOFWEEK";
    
    if( event.getSid() == timex.getSid() && event.getIndex() < timex.getTokenOffset() )
      feats.incrementCount("bi-" + tokens.get(event.getIndex()-1) + "_" + timeToken);
    else if( event.getSid() == timex.getSid() )
      feats.incrementCount("bi-" + timeToken + "_" + tokens.get(event.getIndex()-1));

    // In different sentences.
    else {
      List<String> eventTokens = TreeOperator.stringLeavesFromTree(trees.get(event.getSid()));
      if( event.getSid() < timex.getSid() )
        feats.incrementCount("bi-" + eventTokens.get(event.getIndex()-1) + "_" + timeToken);
      else
        feats.incrementCount("bi-" + timeToken + "_" + eventTokens.get(event.getIndex()-1));
    }
    
    return feats;
  }
  
  /**
   * Check if one of the events syntactically dominates the other. True if yes, false if no.
   */
  public boolean oneEventDominates(TLink link, List<TextEvent> events, List<Tree> trees) {
  	if( link instanceof EventEventLink ) {
  		TextEvent event1 = findEvent(link.getId1(), events);
  		TextEvent event2 = findEvent(link.getId2(), events);
  		if( event1 == null || event2 == null ) {
  		  System.out.println("null event!! " + event1 + " " + event2 + "\tfrom tlink " + link);
  		  return false;
  		}
  		return oneEventDominates(event1, event2, trees);
  	}
  	return false;
  }
  
  public boolean oneEventDominates(TextEvent event1, TextEvent event2, List<Tree> trees) {
  	if( event1 != null ) {
  		Tree tree = trees.get(event1.getSid());
  		return oneEventDominates(event1, event2, tree);
  	}
    return false;
  }
  
  public boolean oneEventDominates(TextEvent event1, TextEvent event2, Tree tree) {
  	// Must be in the same sentence.
  	if( event1.getSid() == event2.getSid() ) {
  		Tree tree1 = TreeOperator.indexToSubtree(tree, event1.getIndex());
  		Tree tree2 = TreeOperator.indexToSubtree(tree, event2.getIndex());

  		// Dominance.
  		if( treeDominates(tree1, tree2, tree) || treeDominates(tree2, tree1, tree) )
  			return true;
  	}
    return false;
  }
  
  /**
   * Features about one event syntactically dominating the other, and sentence distance.
   */
  private Counter<String> getDominanceFeatures(TextEvent event1, TextEvent event2, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    
    // Must be in the same sentence.
    if( event1.getSid() == event2.getSid() ) {
      Tree tree = trees.get(event1.getSid());
      Tree tree1 = TreeOperator.indexToSubtree(tree, event1.getIndex());
      Tree tree2 = TreeOperator.indexToSubtree(tree, event2.getIndex());

      // Dominance
      if( treeDominates(tree1, tree2, tree) )
        feats.incrementCount("dominates");
      else if( treeDominates(tree2, tree1, tree) )
        feats.incrementCount("isDominated");
    }
    
    return feats;
  }
  
  /**
   * Features about one event syntactically dominating the other, and sentence distance.
   */
  private Counter<String> getDominanceFeatures(TextEvent event, Timex timex, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();
    
    // Must be in the same sentence.
    if( event.getSid() == timex.getSid() ) {
      Tree tree = trees.get(event.getSid());
      Tree tree1 = TreeOperator.indexToSubtree(tree, event.getIndex());
      Tree tree2 = TreeOperator.indexToSubtree(tree, timex.getTokenOffset());
      
      // Dominance
      if( treeDominates(tree1, tree2, tree) )
        feats.incrementCount("dominates");
      else if( treeDominates(tree2, tree1, tree) )
        feats.incrementCount("isDominated");
    }
    
    return feats;
  }
  
  private Counter<String> getParsePathFeatures(TextEvent event, Timex timex, List<Tree> trees) {
    // Only works for same-sentence.
    if( event.getSid() == timex.getSid() ) {
      if( event.getIndex() < timex.getTokenOffset() )
        return getParsePathFeatures(event.getIndex(), timex.getTokenOffset()+timex.getTokenLength()-1, "EVENT", "TIME", trees.get(event.getSid()));
      else
        return getParsePathFeatures(timex.getTokenOffset()+timex.getTokenLength()-1, event.getIndex(), "TIME", "EVENT", trees.get(event.getSid()));
    }
    else
      return new ClassicCounter<String>();
  }

  private Counter<String> getParsePathFeatures(TextEvent event1, TextEvent event2, List<Tree> trees) {
    // Only works for same-sentence.
    if( event1.getSid() == event2.getSid() )
      return getParsePathFeatures(event1.getIndex(), event2.getIndex(), null, null, trees.get(event1.getSid()));
    else
      return new ClassicCounter<String>();
  }
  
  private Counter<String> getParsePathFeatures(int index1, int index2, String pre1, String pre2, Tree tree) {
    Counter<String> feats = new ClassicCounter<String>();

    Tree tree1 = TreeOperator.indexToSubtree(tree, index1);
    Tree tree2 = TreeOperator.indexToSubtree(tree, index2);

    String path = TreeOperator.pathNodeToNode(tree, tree1, tree2, false);
    if( pre1 != null ) path = pre1 + "_" + path + "_" + pre2; 
    feats.incrementCount("pathfull-" + path);
    path = TreeOperator.pathNodeToNode(tree, tree1, tree2, true);
    if( pre1 != null ) path = pre1 + "_" + path + "_" + pre2; 
    feats.incrementCount("pathnopos-" + path);
    
    return feats;
  }
  
  private Counter<String> getDepsPathFeatures(TextEvent event, Timex time, List<TypedDependency> deps) {
    if( event.getSid() == time.getSid() )
      return getDepsPathFeatures(event.getIndex(), time.getTokenOffset()+time.getTokenLength()-1, deps);
    else
      return new ClassicCounter<String>();
  }
  
  private Counter<String> getDepsPathFeatures(TextEvent event1, TextEvent event2, List<TypedDependency> deps) {
    if( event1.getSid() == event2.getSid() )
      return getDepsPathFeatures(event1.getIndex(), event2.getIndex(), deps);
    else
      return new ClassicCounter<String>();
  }
  
  private Counter<String> getDepsPathFeatures(int index1, int index2, List<TypedDependency> deps) {
  	Counter<String> feats = new ClassicCounter<String>();

    String path = TreeOperator.dependencyPath(index1, index2, deps);
    if( path != null ) {
      feats.incrementCount(path);

      // If the path is long, make a short version: 
      // ccomp->dobj->prep_of->nsubj<-dobj->  BECOMES  ccomp->...dobj->
      /*
       * Determined that this doesn't change classification results.
       * 
      int numparts = path.split("->").length + path.split("<-").length;
      if( numparts > 4 ) {
        int first = Math.min(path.indexOf("->"), path.indexOf("<-"));
        int last  = Math.max(path.lastIndexOf("->", path.length()-3), path.lastIndexOf("<-", path.length()-3));
        // This should never be false. Sanity check.
        if( first != -1 && last != -1 ) {
          String abbr = path.substring(0, first+2) + "..." + path.substring(last+2);
          feats.incrementCount(abbr);
        }
      }
      */
    }
    
    return feats;
  }
  
  /**
   * Prepositional phrases features.
   */
  private Counter<String> getSyntacticFeatures(TextEvent event1, TextEvent event2, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();

    Tree tree = trees.get(event1.getSid());
    Tree subtree = TreeOperator.indexToSubtree(tree, event1.getIndex());
    String prep = isPrepClause(tree, subtree);
    if( prep != null )
      feats.incrementCount("prep1-" + prep);
    
    tree = trees.get(event2.getSid());
    subtree = TreeOperator.indexToSubtree(tree, event2.getIndex());
    prep = isPrepClause(tree, subtree);
    if( prep != null )
      feats.incrementCount("prep2-" + prep);
    
    return feats;
  }
  
  /**
   * Prepositional phrases features.
   */
  private Counter<String> getTextOrderFeatures(TextEvent event1, TextEvent event2, List<Tree> trees) {
    Counter<String> feats = new ClassicCounter<String>();

    // Same sentence
    if( event1.getSid() == event2.getSid() ) {
      feats.incrementCount("order-sameSent");
      if( event1.getIndex() < event2.getIndex() ) {
        feats.incrementCount("order-before");
        feats.incrementCount("order-sameSent-before");
      }
      else { 
        feats.incrementCount("order-after");
        feats.incrementCount("order-sameSent-after");
      }
    }
    // Different sentence
    else {
      feats.incrementCount("order-diffSent");
      if( event1.getSid() < event2.getSid() ) {
        feats.incrementCount("order-before");
        feats.incrementCount("order-diffSent-before");
      }
      else {
        feats.incrementCount("order-after");
        feats.incrementCount("order-diffSent-after");
      }
    }    
    
    return feats;
  }
  
  /**
   * A single feature: whether the two events are next to each other or if another event is in between them.
   * @param events Assumes these are the events in the sentence that both given events are in. Null is ok if the two
   *               key events are in different sentences.
   */
  private Counter<String> getEventInterferenceFeatures(TextEvent event1, TextEvent event2, List<TextEvent> events) {
    Counter<String> feats = new ClassicCounter<String>();

    if( event1.getSid() == event2.getSid() ) {
    	int numInterlopers = countInterlopers(event1, event2, events);
      
      if( numInterlopers > 0 )
        feats.incrementCount("notsequential");
      else
        feats.incrementCount("sequential");
    }
    
    return feats;
  }

  /**
   * If the two events are in the same sentence, count how many other events occur between them.
   * If different sentences, return -1;
   */
  private int countInterlopers(TextEvent event1, TextEvent event2, List<TextEvent> events) {
  	if( event1.getSid() == event2.getSid() ) {
      int start = event1.getIndex();
      int end = event2.getIndex();
      if( event2.getIndex() < start ) {
        start = event2.getIndex();
        end = event1.getIndex();
      }

      int interlopers = 0;
      for( TextEvent event : events )
        if( event.getSid() == event1.getSid() && 
            event.getIndex() > start && event.getIndex() < end )
          interlopers++;

      return interlopers;
    }
  	return -1;
  }
  
  /**
   * @desc Check if the tree is a clause in a prepositional phrase.
   * @returns The string preposition that heads the PP
   */
  public static String isPrepClause(Tree root, Tree tree) {
//    System.out.println("isPrepClause: subtree=" + tree);
    if( tree != null ) {
      Tree p = tree.parent(root).parent(root);
//      System.out.println("parent=" + p);
      String pos = p.label().value();
//      System.out.println("parent pos=" + pos);

      if( !pos.equals("PP") ) {
        // Keep moving up the tree till we hit a new type of POS
        while( p != null && p.label().toString().equals(pos) ) 
        	p = p.parent(root);
      }
      
      // We can hit one sentence, but the S must be the PP clause
      if( p != null && p.label().value().equals("S") ) {
        p = p.parent(root);
        if( p == null || !p.label().value().equals("PP") ) 
        	return null;
      }

      // We found the PP, return the preposition
      if( p != null && p.label().value().equals("PP") ) {
        List<Tree> list = p.getChildrenAsList();
        for( Tree node : list ) {
          if( node.label().value().equals("IN") ) 
            return node.firstChild().toString();
        }
      }
    }
    return null;
  }
    
  /**
   * Checks if the first tree1 syntactically dominates the second tree2.
   * @param tree1 A subtree.
   * @param tree2 A subtree.
   * @param tree The full sentence's parse tree.
   * @returns True if the first tree dominates the second, false otherwise.
   */
  private boolean treeDominates(Tree tree1, Tree tree2, Tree tree) {
    if( tree1 != null && tree2 != null ) {
      // Find parent tree of event1, check dominance
      Tree p = tree1.parent(tree); // parent is POS tag
      if( p.dominates(tree2) ) 
        return true;
    }
    else 
      System.out.println("WARNING: no tree1 or no tree2");
    return false;
  }
  
  /**
   * Find the event with ID eventID in the given list of event objects.
   * @param eventID The ID of the event we want.
   * @param events All the events in a document.
   * @return A single TextEvent object with the desired ID.
   */
  public static TextEvent findEvent(String eventID, List<TextEvent> events) {
//    System.out.println("find event eventID=" + eventID);
    if( events != null )
      for( TextEvent event : events )
        if( eventID.equals(event.getId()) || event.containsEiid(eventID) )
          return event;
    return null;
  }
  
  /**
   * Find the Timex with ID timeID in the given list of timex objects.
   * @param eventID The ID of the timex we want.
   * @param events All the timexes in a document.
   * @return A single Timex object with the desired ID.
   */
  public static Timex findTimex(String timeID, List<Timex> timexes) {
    if( timexes != null )
      for( Timex timex : timexes )
        if( timeID.equals(timex.getTid()) )
          return timex;
//    System.out.println("findTimex null: " + timeID);
    return null;
  }
  
  /**
   * Write all of the TLinkDatum objects to file, one per line.
   * @param data The list of datum objects to write.
   * @param path The file path to overwrite.
   */
  private void dataToFile(List<TLinkDatum> data, String path) {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(path));
      BufferedWriter outDebug = new BufferedWriter(new FileWriter(path + "-debug"));

      for( TLinkDatum datum : data ) {
        String strDatum = datum.toStringSorted();
        out.write(strDatum + "\n");
//        System.out.println(strDatum + "\n");

        outDebug.write(datum._originalTLink.getId1() + " " + datum._originalTLink.getId2() + "\t");
        outDebug.write(datum.getSourceDoc() + "\t");
        outDebug.write(strDatum + "\n");
      }
      out.close();
      outDebug.close();
    } catch (Exception e) { e.printStackTrace(); }
  }
  
  /**
   * Load feature data from file, the same format created by the above dataToFile method.
   * @param path File to load created by dataToFile.
   * @return A list of TLinkDatum objects.
   */
  public static List<TLinkDatum> readFromFile(String path) {
    List<TLinkDatum> data = new ArrayList<TLinkDatum>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;
      while( (line = in.readLine()) != null ) {
        TLinkDatum datum = TLinkDatum.fromString(line);
        data.add(datum);
      }
      in.close();
    } catch( IOException ex ) {
      ex.printStackTrace();
      System.exit(-1);
    }
    return data;
  }
  
  /**
   * Reads in the InfoFile and calls the correct feature function.
   */
  public void processInput() {
    if( _infoPath.length() <= 0 ) 
      System.err.println("No info file given");
    else {
      System.out.println("Processing info file " + _infoPath);
      _infoDocs = new SieveDocuments(_infoPath);

      List<TLinkDatum> data = infoToTLinkFeatures(_infoDocs, null);
      dataToFile(data, _outpath);
    }
  }
  

  
  /**
   * MAIN
   */
  public static void main(String[] args) {
    if( args.length < 2 ) {
      System.out.println("TimebankFeaturizer [-wordnet <jwnlfile>] -output <outfile> -info <infofile>");
      System.exit(-1);
    }
    
    TLinkFeaturizer tbf = new TLinkFeaturizer(args);
    tbf.processInput();
  }

}
