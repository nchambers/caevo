package caevo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import caevo.util.Directory;
import caevo.util.HandleParameters;
import caevo.util.Ling;
import caevo.util.TreeOperator;
import caevo.util.Util;
import caevo.util.WordNet;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * Featurizes single tokens for the task of determining EVENT or NOT EVENT.
 * Also provides training function to build and save a classifier for this task.
 * Input is a pre-built .info file, output is a classifier.
 *
 * TextEventClassifier [-wordnet <path>] -info <path> [train]
 * 
 * -model 
 * Both read and write: path to write the new trained model, or where to read from when testing.
 * 
 * -rules
 * If given, will run event extraction using basic rules.
 * 
 * -wordnet
 * Can specify the path to WordNet. The code also looks for the environment variable JWNL if
 * you don't give this flag.
 * 
 */
public class TextEventClassifier {
  SieveDocuments docs;
  WordNet wordnet;
  String wordnetPath = null;
  
  boolean ruleBased = false;
  String modelOutDir = "eventmodels";
  String baseModelName = "event.classifier";
  int minFeatCutoff = 2;

  boolean coeMarkFormat = false; // COE format (use "MARK" as the event element)
  
  Classifier<String,String> eventClassifier = null;
  Classifier<String,String> tenseClassifier = null;
  Classifier<String,String> aspectClassifier = null;
  Classifier<String,String> classClassifier = null;

  
  public TextEventClassifier(String[] args) {
    HandleParameters params = new HandleParameters(args);
    
    if( params.hasFlag("-info") )
      docs = new SieveDocuments(params.get("-info"));
    else if( args.length < 1 || (!args[args.length-1].equalsIgnoreCase("raw") && !args[args.length-1].equalsIgnoreCase("parsed")) ){
      System.out.println("TextEventClassifier [-model <dir>] -info <file> [train]");
      System.exit(1);
    }

    if( params.hasFlag("-wordnet") )
    	wordnetPath = params.get("-wordnet");
    if( params.hasFlag("-rules") )
    	ruleBased = true;
    if( params.hasFlag("-eventmin") )
      minFeatCutoff = Integer.parseInt(params.get("-eventmin"));
    if( params.hasFlag("-coe") )
    	coeMarkFormat = true;
    
    loadWordNet();
  }
  
  public TextEventClassifier(SieveDocuments docs) {
  	this.docs = docs;
  	loadWordNet();
  }

  public TextEventClassifier(SieveDocuments docs, WordNet wordnet) {
  	this.docs = docs;
  	this.wordnet = wordnet;
  }

	/**
	 * Load WordNet. Default looks for the JWNL environment variable.
	 */
  private void loadWordNet() {
  	// If a path was passed as a command-line parameter.
  	if( wordnetPath != null )
  		wordnet = new WordNet(wordnetPath);
  	// Otherwise, use the Main.java wordnet instance.
  	else if( Main.wordnet != null )
  		wordnet = Main.wordnet;
  	// Create a new one as a last resort.
  	else
  		wordnet = new WordNet();
  }
  
  public void setMinFeatureCutoff(int min) {
    minFeatCutoff = min;
    System.out.println("TextEventClassifer: set min cutoff = " + min);
  }
  
  /**
   * Find the path from the current word, up to the first seen S node.
   * @param tree
   * @param wordIndex
   * @return
   */
  private String pathToSTag(Tree tree, int wordIndex) {
  	Tree subtree = TreeOperator.indexToSubtree(tree, wordIndex);
  	if( subtree == null ) {
  	  System.out.println("ERROR: couldn't find subtree for word index " + wordIndex + " in tree: " + tree);
  	  return null;
  	}
  	List<String> tags = new ArrayList<String>();
  	tags.add(subtree.label().value());
  	
  	Tree parentTree = subtree.parent(tree);
		String tag = "";
  	while( parentTree != null && !tag.equalsIgnoreCase("S") && !tag.equalsIgnoreCase("SBAR") ) {
  		tag = parentTree.label().value();
  		tags.add(tag);
  		parentTree = parentTree.parent(tree);
  	}

  	// Built the feature string by reversing the list.
		String path = tags.get(tags.size()-1);
		for( int xx = tags.size()-2; xx >= 0; xx-- )
			path += "-" + tags.get(xx);

  	return path;
  }
  
  /**
   * Extract features for a single token in a sentence in order to identify whether or
   * not it is an event. 
   * @param sentence The sentence data structure with all parse information filled in.
   * @param wordIndex Starting from 1.
   * @return
   */
  private Counter<String> getEventFeatures(SieveSentence sentence, Tree tree, List<TypedDependency> deps, int wordIndex) {
    Counter<String> features = new ClassicCounter<String>();
    List<CoreLabel> tokens = sentence.tokens();//sentence.sentence().toLowerCase().split("\\s+");
    int size = tokens.size();

    String token = tokens.get(wordIndex-1).getString(CoreAnnotations.OriginalTextAnnotation.class).toLowerCase();
    String tokenPre1 = "<s>";
    String tokenPre2 = "<s>";
    if( wordIndex > 1 ) tokenPre1 = tokens.get(wordIndex-2).getString(CoreAnnotations.OriginalTextAnnotation.class).toLowerCase();
    if( wordIndex > 2 ) tokenPre2 = tokens.get(wordIndex-3).getString(CoreAnnotations.OriginalTextAnnotation.class).toLowerCase();
    String tokenPost1 = "</s>";
    String tokenPost2 = "</s>";
    if( wordIndex < size ) tokenPost1 = tokens.get(wordIndex).getString(CoreAnnotations.OriginalTextAnnotation.class).toLowerCase();
    if( wordIndex < size-1 ) tokenPost2 = tokens.get(wordIndex+1).getString(CoreAnnotations.OriginalTextAnnotation.class).toLowerCase();

    // N-grams.
    features.incrementCount(token);
    features.incrementCount(tokenPre1 + "-" + token);
    features.incrementCount(tokenPre2 + "-" + tokenPre1 + "-" + token);
    
    // N-grams before the target token.
    features.incrementCount("PRE-" + tokenPre1);
    features.incrementCount("PRE-" + tokenPre2 + "-" + tokenPre1);

    // N-grams following the target token.
    features.incrementCount("POST-" + tokenPost1);
    features.incrementCount("POST-" + tokenPost1 + "-" + tokenPost2);

    // POS n-grams. (1, 2, 3-gram)
    String pos = TreeOperator.indexToPOSTag(tree, wordIndex);
    String posPre1 = "<s>";
    String posPre2 = "<s>";
    if( wordIndex > 1 ) posPre1 = TreeOperator.indexToPOSTag(tree, wordIndex-1);
    if( wordIndex > 2 ) posPre2 = TreeOperator.indexToPOSTag(tree, wordIndex-2);
    features.incrementCount(pos);
    features.incrementCount(posPre1 + "-" + pos);
    features.incrementCount(posPre2 + "-" + posPre1 + "-" + pos);
    
    // WordNet lookup
    features.incrementCount("LEM-" + wordnet.lemmatizeTaggedWord(token, pos));
    if( pos != null && pos.startsWith("NN") ) features.incrementCount("IS-WORDNET-EV-" + wordnet.isNounEvent(token));
    
    // Parse path to Sentence node.
    String path = pathToSTag(tree, wordIndex);
    features.incrementCount("PATH-" + path);

    // Typed Dependency triples with which this word is involved.
    for( TypedDependency dep : deps ) {
    	if( dep.gov().index() == wordIndex )
    		features.incrementCount("DEPG-" + dep.reln());
    	else if( dep.dep().index() == wordIndex )
    		features.incrementCount("DEPD-" + dep.reln());
    }
    	
    return features;
  }
  
  public Classifier<String,String> train(Set<String> docnames) {
  	return train(docs, docnames);
  }
  
  /**
   * Trains 4 classifiers about events, all using the same features.
   * 1. main event identifier: is a token an event or not?
   * 2-4. Given a token is an event: tense, aspect, and its class.
   * @param docs A pre-processed .info file.
   * @return
   */
  public Classifier<String,String> train(SieveDocuments docs, Set<String> docnames) {
    RVFDataset<String, String> eventDataset  = new RVFDataset<String, String>();
    RVFDataset<String, String> tenseDataset  = new RVFDataset<String, String>();
    RVFDataset<String, String> aspectDataset = new RVFDataset<String, String>();
    RVFDataset<String, String> classDataset  = new RVFDataset<String, String>();

    for( SieveDocument doc : docs.getDocuments() ) {
      if( docnames == null || docnames.contains(doc.getDocname()) ) {
        System.out.println("train docname: " + doc.getDocname());
        List<List<TypedDependency>> alldeps = doc.getAllDependencies();
        
        List<SieveSentence> sentences = doc.getSentences();
        int sid = 0;
        for( SieveSentence sentence : sentences ) {
        	List<CoreLabel> tokens = sentence.tokens();
//          String[] tokens = sentence.sentence().split("\\s+");
          List<TextEvent> events = sentence.events();
          Tree tree = sentence.getParseTree();

          // Grab the word indices of each event.
          Map<Integer,TextEvent> index = new HashMap<Integer,TextEvent>();
          for( TextEvent event : events )
            index.put(event.getIndex(), event);

          // Create the dataset!
          for( int xx = 1; xx <= tokens.size(); xx++ ) {
            Counter<String> features = getEventFeatures(sentence, tree, alldeps.get(sid), xx);
            RVFDatum<String,String> datum = new RVFDatum<String,String>(features, (index.containsKey(xx) ? "event" : "notevent"));
            eventDataset.add(datum);
//            System.out.println("event datum: " + datum);

            if( index.containsKey(xx) ) {
              TextEvent ev = index.get(xx);
//              System.out.println("event: " + index.get(xx) + "\tt=" + ev.getTense() + "\ta=" + ev.getAspect() + "\tc=" + ev.getTheClass());
              if( ev.getTense() != null ) {
                datum = new RVFDatum<String,String>(features, ev.getTense().toString());
                tenseDataset.add(datum);
              }
              if( ev.getAspect() != null ) {
                datum = new RVFDatum<String,String>(features, ev.getAspect().toString());
                aspectDataset.add(datum);
              }
              if( ev.getTheClass() != null ) {
                datum = new RVFDatum<String,String>(features, ev.getTheClass().toString());
                classDataset.add(datum);
              }
            }
          }
          sid++;
        }
      }
    }

    // Threshold the features.
    if( minFeatCutoff > 1 ) {
      System.out.println("Removing sparse features < " + minFeatCutoff);
      eventDataset.applyFeatureCountThreshold(minFeatCutoff);
      tenseDataset.applyFeatureCountThreshold(minFeatCutoff);
      aspectDataset.applyFeatureCountThreshold(minFeatCutoff);
      classDataset.applyFeatureCountThreshold(minFeatCutoff);
    }
    
    // Create the classifiers.
    System.out.println("Training the classifiers...");
    LinearClassifierFactory<String,String> linearFactory = new LinearClassifierFactory<String,String>();
    eventClassifier = linearFactory.trainClassifier(eventDataset);			
    tenseClassifier = linearFactory.trainClassifier(tenseDataset);
    aspectClassifier = linearFactory.trainClassifier(aspectDataset);
    classClassifier = linearFactory.trainClassifier(classDataset);
		
    return null;
  }

  public void writeClassifiersToFile() {
    String path = modelOutDir + File.separator + baseModelName;
    System.out.println("Saving the classifier to disk (" + path + ")...");
    try {
      // Create the directory of classifiers, if necessary.
      File dir = new File(modelOutDir);
      if( !dir.exists() ) dir.mkdir();
        
      IOUtils.writeObjectToFile(eventClassifier, path);
      IOUtils.writeObjectToFile(tenseClassifier, path + "-tense");
      IOUtils.writeObjectToFile(aspectClassifier, path + "-aspect");
      IOUtils.writeObjectToFile(classClassifier, path + "-class");
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
  
  /**
   * Classifies a single word in a sentence, returns true if it is an event, false otherwise.
   * @param classifier A classifier for events.
   * @param sentence A sentence from a .info file
   * @param tree A parse tree
   * @param wordi The word index in the sentence, starting from 1
   * @return True if the word at wordi is an event, false otherwise.
   */
  public boolean isEvent(Classifier<String, String> classifier, SieveSentence sentence, Tree tree, List<TypedDependency> deps, int wordi) {
  	String postag = TreeOperator.indexToPOSTag(tree, wordi);

  	// Only consider tokens with specific POS tags.
  	if( postag.startsWith("NN") || postag.startsWith("VB") || postag.startsWith("J") ||
  			// "behind* the killings"
  			postag.equalsIgnoreCase("IN") ||
  			// "the lights are out*"
  			postag.equalsIgnoreCase("RP") ) {
  		Counter<String> features = getEventFeatures(sentence, tree, deps, wordi);
  		RVFDatum<String,String> datum = new RVFDatum<String,String>(features, null);
  		String guess = classifier.classOf(datum);
  		return guess.equals("event");
  	}
  	else return false;
  }
  
  private RVFDatum<String,String> wordToDatum(SieveSentence sentence, Tree tree, List<TypedDependency> deps, int wordi) {
  	Counter<String> features = getEventFeatures(sentence, tree, deps, wordi);
    RVFDatum<String,String> datum = new RVFDatum<String,String>(features, null);
    return datum;
  }
  
  /**
   * Deterministic simple rule-based approach to identify event words. POS tags only.
   * @param tree The parse tree for our sentence.
   * @param tokens All tokens in the sentence.
   * @param wordi Index of the word you want to label as event/not-event. Index starts at 1
   * @return True if the word is an event.
   */
  private boolean isEventDeterministic(Tree tree, List<CoreLabel> tokens, int wordi) {
    String POS = TreeOperator.indexToPOSTag(tree, wordi); 
//    String prePOS = (wordi > 1 ? TreeOperator.indexToPOSTag(tree, wordi-1) : null);
    String postPOS = (wordi < tokens.size()-1 ? TreeOperator.indexToPOSTag(tree, wordi+1) : null);

    System.out.println("tree: " + tree);
    System.out.println("wordi = " + wordi + " POS = " + POS);
    if( POS.startsWith("VB") && (postPOS == null || !postPOS.startsWith("VB")) )
      return true;
    else
      return false;
  }

  /**
   * 1 indexed. The first token in a sentence is index 1.
   * @return All token indices that are covered by a timex.
   */
  private Set<Integer> indicesCoveredByTimexes(List<Timex> timexes) {
    Set<Integer> indices = new HashSet<Integer>();
    if( timexes != null )
      for( Timex timex : timexes ) {
        for( int ii = 0; ii < timex.getTokenLength(); ii++ )
          indices.add(timex.getTokenOffset()+ii);
      }
    return indices;
  }
  
  public void extractEvents() {
    extractEvents(docs, null);
  }

  public void extractEvents(SieveDocuments docs) {
    extractEvents(docs, null);
  }
  
  /**
   * Destructively add events to the global .info file.
   * @param docnames Limit extraction to a set of documents in the info file. Use null if you want all docs.
   */
  public void extractEvents(SieveDocuments docs, Collection<String> docnames) {
  	if( !ruleBased ) {
  		System.out.println("*** Classifier-Based Event Extraction ***");
  		extractEvents(docs, docnames, false);
  	} else {
  		System.out.println("*** Deterministic Event Extraction ***");
  		extractEvents(docs, docnames, true);
  	}
  }
  
  /**
   * Destructively add events to the global .info file.
   * ASSUMES the InfoFile already has parses and typed dependencies in it.
   * @param docnames Limit extraction to a set of documents in the info file. Use null if you want all docs.
   */
  public void extractEvents(SieveDocuments docs, Collection<String> docnames, boolean useDeterministic) {
    for( SieveDocument doc : docs.getDocuments() ) {
      if( docnames == null || docnames.contains(doc.getDocname()) ) {
        System.out.println("doc = " + doc.getDocname());
        List<SieveSentence> sentences = doc.getSentences();
        int eventi = 1;
        System.out.println(sentences.size() + " sentences.");

        // Build the typed dependencies.
        List<List<TypedDependency>> alldeps = doc.getAllDependencies();
        
        // Each sentence.
        int sid = 0;
        for( SieveSentence sent : sentences ) {
          Tree tree = sent.getParseTree();
          List<TextEvent> newevents = new ArrayList<TextEvent>();
          Set<Integer> timexIndices = indicesCoveredByTimexes(sent.timexes());

          if( tree != null && tree.size() > 1 ) {
          	// Each token.
          	int wordi = 1; // first word is index 1
          	for( CoreLabel token : sent.tokens() ) {
          		
          		// Skip tokens that are already tagged by a timex.
          		if( !timexIndices.contains(wordi) ) {

          			if( useDeterministic && isEventDeterministic(tree, sent.tokens(), wordi) ) {
              		String tokenStr = token.getString(CoreAnnotations.OriginalTextAnnotation.class);
          				TextEvent event = new TextEvent(tokenStr, "e" + eventi, sid, wordi);
          				event.addEiid("ei" + eventi);
          				newevents.add(event);
          				//                System.out.println("Created event: " + event);
          				eventi++;
          			}

          			if( !useDeterministic && isEvent(eventClassifier, sent, tree, alldeps.get(sid), wordi) ) {
              		String tokenStr = token.getString(CoreAnnotations.OriginalTextAnnotation.class);
          				TextEvent event = new TextEvent(tokenStr, "e" + eventi, sid, wordi);
          				event.addEiid("ei" + eventi);

          				// Set the event attributes.
          				RVFDatum<String,String> datum = wordToDatum(sent, tree, alldeps.get(sid), wordi);
          				//                System.out.println("datum: " + datum);
          				//                System.out.println("\taspect: " + aspectClassifier.classOf(datum));
          				event.setTense(TextEvent.Tense.valueOf(tenseClassifier.classOf(datum)));
          				event.setAspect(TextEvent.Aspect.valueOf(aspectClassifier.classOf(datum)));
          				event.setTheClass(TextEvent.Class.valueOf(classClassifier.classOf(datum)));

          				newevents.add(event);
          				//                System.out.println("Created event: " + event);
          				eventi++;
          			}
          		}
          		wordi++;
          	}
          }
          // Add the new events to this .info file.
          if( newevents.size() > 0 ) 
            doc.addEvents(sid, newevents);
          sid++;
        }
      }
    }
  }
  
  /**
   * Outputs only the events that are in a document in the given InfoFile. 
   * It outputs a one per line format with indices:
   *     <sentence-id> <token-index> <token-string> <event-class> <event-tense>
   * @param outpath File path to create.
   * @param file Name of the file in the InfoFile that you want.
   * @param info The InfoFile itself.
   */
  public void createEventsOnlyFile(String outpath, String file, SieveDocuments docs) {
    try {
      System.out.println("Writing to " + outpath + "...");
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outpath)));
      
      int sid = 0;
      for( SieveSentence sent : docs.getDocument(file).getSentences() ) {
        for( TextEvent event : sent.events() ) {
          writer.write(sid + "\t" + (event.getIndex()-1) + "\t" + event.getString());
//          writer.write("\t" + event.getTheClass() + "\t" + event.getTense() + "\t" + event.getAspect());
          writer.write("\t" + event.getTheClass() + "\t" + event.getTense());
          writer.write("\n");
        }
        sid++;
      }
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
    
  }
  
  /**
   * Read all serialized classifiers into memory.
   */
  public void loadClassifiers() {
  	String base = "/models/" + baseModelName;
  	eventClassifier  = Util.readClassifierFromFile(this.getClass().getResource(base));
  	tenseClassifier  = Util.readClassifierFromFile(this.getClass().getResource(base + "-tense"));
  	aspectClassifier = Util.readClassifierFromFile(this.getClass().getResource(base + "-aspect"));
  	classClassifier  = Util.readClassifierFromFile(this.getClass().getResource(base + "-class"));
  }
  
  public void readClassifiersFromDirectory(String dir) {
  	if( !(new File(dir)).isDirectory() )
  		System.out.println("Not a directory: " + dir);
  	else {
  		eventClassifier  = Util.readClassifierFromFile(dir + File.separator + baseModelName);
  		tenseClassifier  = Util.readClassifierFromFile(dir + File.separator + baseModelName + "-tense");
  		aspectClassifier = Util.readClassifierFromFile(dir + File.separator + baseModelName + "-aspect");
  		classClassifier  = Util.readClassifierFromFile(dir + File.separator + baseModelName + "-class");  		
  	}
  }
  
  public void docsToFile(String path) {
    docs.writeToXML(path);
  }
  
  public void markupRawText(String filepath) {
    SieveDocument doc = markupRawTextToSieveDocument(filepath);

    // Output the InfoFile with the events in it.
    String outpath = filepath + ".info.xml";
    docsToFile(outpath);
    System.out.println("Created " + outpath);

    // Output just the text with the events marked as XML elements.
    String markup = doc.markupOriginalText();
    outpath = filepath + ".withevents";
    Directory.stringToFile(outpath, markup);
    System.out.println("Created " + outpath);
  }
  
  public SieveDocument markupRawTextToSieveDocument(String filepath) {
 // Initialize the parser.
    LexicalizedParser parser = Ling.createParser(Main.serializedGrammar);
    if( parser == null ) {
    	System.out.println("Failed to create parser from " + Main.serializedGrammar);
    	System.exit(1);
    }
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
    
    // Parse the file.
    SieveDocument doc = Tempeval3Parser.rawTextFileToParsed(filepath, parser, gsf);
    if( docs == null ) docs = new SieveDocuments();
    docs.addDocument(doc);
    loadClassifiers();
    extractEvents();
    
    return doc;
  }

  public void markupPreParsedText(String path) {
    // Initialize the dependency rulebase.
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
    TreeFactory tf = new LabeledScoredTreeFactory();
    
    // If it's a directory of files.
    File dirobj = new File(path);
  	if( dirobj.exists() && dirobj.isDirectory() ) {
  		for( String file : Directory.getFilesSorted(path) ) {
  			if( file.endsWith(".parse") )
  				markupPreParsedText(path + File.separator + file, tf, gsf);
  		}
  	}
  	// A single file to parse.
  	else markupPreParsedText(path, tf, gsf);
  }
  
  /**
   * Given a single file that contains one parse per line, create a new file that is just the raw text
   * from the parse tree leaves, marked up with XML elements around the events.
   */
  public void markupPreParsedText(String filepath, TreeFactory tf, GrammaticalStructureFactory gsf) {
    // Parse the file.
    SieveDocument doc = Tempeval3Parser.lexParsedFileToDepParsed(filepath, tf, gsf);
    if( docs == null ) docs = new SieveDocuments();
    docs.addDocument(doc);
    loadClassifiers();
    extractEvents();

    // Output the InfoFile with the events in it.
//    String outpath = filepath + ".info.xml";
//    infoToFile(outpath);
//    System.out.println("Created " + outpath);

    // Output just the text with the events marked as XML elements.
    String markup = "";
    if( coeMarkFormat )
    	markup = doc.markupOriginalText("MARK", "ID", true, true, false, true);
    else
    	markup = doc.markupOriginalText();
    
    Directory.stringToFile(filepath + ".withevents", markup);
//    System.out.println("Created " + outpath);
    
    // Output a text file with just event sentence/word indices.
    createEventsOnlyFile(filepath + ".onlyevents", filepath, docs);
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    TextEventClassifier classifier = new TextEventClassifier(args);
    
    // TextEventClassifier -info <infofile> train
    if( args[args.length-1].equalsIgnoreCase("train") ) {
    	classifier.train(null);
    	classifier.writeClassifiersToFile();
    }
    
    // TextEventClassifier -model <dir> <rawfile> raw
    else if( args[args.length-1].equalsIgnoreCase("raw") ) {
      System.out.println("Marking up raw text input.");
      classifier.loadClassifiers();
      classifier.markupRawText(args[args.length-2]);
    }
    
    // TextEventClassifier -model <dir> <file-of-parses> parsed
    else if( args[args.length-1].equalsIgnoreCase("parsed") ) {
      System.out.println("Marking up pre-parsed text input.");
      classifier.loadClassifiers();
      classifier.markupPreParsedText(args[args.length-2]);
    }
    
    // TextEventClassifier -info <infofile> 
    else {
      classifier.loadClassifiers();
      classifier.extractEvents();
    	classifier.docsToFile("withevents.info.xml");
    	System.out.println("Created withevents.info.xml");
    }
  }

}
