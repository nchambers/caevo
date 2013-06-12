package timesieve;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.io.IOUtils;
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

import timesieve.util.Directory;
import timesieve.util.HandleParameters;
import timesieve.util.Ling;
import timesieve.util.TreeOperator;
import timesieve.util.WordNet;

/**
 * Featurizes single tokens for the task of determining EVENT or NOT EVENT.
 * Also provides training function to build and save a classifier for this task.
 * Input is a pre-built .info file, output is a classifier.
 *
 * TextEventClassifier [-model <dir>] [-wordnet <path>] -info <path> [train]
 * 
 * -model 
 * Both read and write: path to write the new trained model, or where to read from when testing.
 * 
 * -rules
 * If given, will run event extraction using basic rules.
 * 
 */
public class TextEventClassifier {
  InfoFile info;
  WordNet wordnet;
  String wordnetPath = "/home/nchamber/code/lib/jwnl_file_properties.xml";
  String serializedGrammar = "/home/nchamber/code/resources/englishPCFG.ser.gz";
  
  boolean ruleBased = false;
  String modelDir = "eventmodels";
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
      info = new InfoFile(params.get("-info"));
    else if( args.length < 1 || (!args[args.length-1].equalsIgnoreCase("raw") && !args[args.length-1].equalsIgnoreCase("parsed")) ){
      System.out.println("TextEventClassifier [-model <dir>] -info <file> [train]");
      System.exit(1);
    }

    if( params.hasFlag("-wordnet") )
    	wordnetPath = params.get("-wordnet");
    if( params.hasFlag("-grammar") )
      serializedGrammar = params.get("-grammar");
    if( params.hasFlag("-model") )
    	modelDir = params.get("-model");
    if( params.hasFlag("-rules") )
    	ruleBased = true;
    if( params.hasFlag("-eventmin") )
      minFeatCutoff = Integer.parseInt(params.get("-eventmin"));
    if( params.hasFlag("-coe") )
    	coeMarkFormat = true;
    
    if( wordnetPath != null )
    	wordnet = new WordNet(wordnetPath);
    else
    	wordnet = new WordNet(WordNet.findWordnetPath());
  }

  public TextEventClassifier(InfoFile info, String eventmodelDir) {
  	this.info = info;
  	this.modelDir = eventmodelDir;
  	if( wordnetPath != null )
  	  wordnet = new WordNet(wordnetPath);
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
  private Counter<String> getEventFeatures(Sentence sentence, Tree tree, List<TypedDependency> deps, int wordIndex) {
    Counter<String> features = new ClassicCounter<String>();
    String[] tokens = sentence.sentence().toLowerCase().split("\\s+");
    int size = tokens.length;

    String token = tokens[wordIndex-1];
    String tokenPre1 = "<s>";
    String tokenPre2 = "<s>";
    if( wordIndex > 1 ) tokenPre1 = tokens[wordIndex-2];
    if( wordIndex > 2 ) tokenPre2 = tokens[wordIndex-3];
    String tokenPost1 = "</s>";
    String tokenPost2 = "</s>";
    if( wordIndex < size ) tokenPost1 = tokens[wordIndex];
    if( wordIndex < size-1 ) tokenPost2 = tokens[wordIndex+1];

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
  	return train(info, docnames);
  }
  
  /**
   * Trains 4 classifiers about events, all using the same features.
   * 1. main event identifier: is a token an event or not?
   * 2-4. Given a token is an event: tense, aspect, and its class.
   * @param info A pre-processed .info file.
   * @return
   */
  public Classifier<String,String> train(InfoFile info, Set<String> docnames) {
    RVFDataset<String, String> eventDataset  = new RVFDataset<String, String>();
    RVFDataset<String, String> tenseDataset  = new RVFDataset<String, String>();
    RVFDataset<String, String> aspectDataset = new RVFDataset<String, String>();
    RVFDataset<String, String> classDataset  = new RVFDataset<String, String>();
    TreeFactory tf = new LabeledScoredTreeFactory();

    for( String docname : info.getFiles() ) {
      if( docnames == null || docnames.contains(docname) ) {
        System.out.println("train docname: " + docname);
        List<String> strdeps = info.getDependencies(docname);
        List<List<TypedDependency>> alldeps = new ArrayList<List<TypedDependency>>();
        for( String str : strdeps ) alldeps.add(InfoFile.stringToDependencies(str));
        
        List<Sentence> sentences = info.getSentences(docname);
        int sid = 0;
        for( Sentence sentence : sentences ) {
          //        List<CoreLabel> tokens = sentence.tokens();
          String[] tokens = sentence.sentence().split("\\s+");
          List<TextEvent> events = sentence.events();
          Tree tree = TreeOperator.stringToTree(sentence.parse(), tf);

          // Grab the word indices of each event.
          Map<Integer,TextEvent> index = new HashMap<Integer,TextEvent>();
          for( TextEvent event : events )
            index.put(event.index(), event);

          // Create the dataset!
          for( int xx = 1; xx <= tokens.length; xx++ ) {
            Counter<String> features = getEventFeatures(sentence, tree, alldeps.get(sid), xx);
            RVFDatum<String,String> datum = new RVFDatum<String,String>(features, (index.containsKey(xx) ? "event" : "notevent"));
            eventDataset.add(datum);
//            System.out.println("event datum: " + datum);

            if( index.containsKey(xx) ) {
              TextEvent ev = index.get(xx);
//              System.out.println("event: " + index.get(xx) + "\tt=" + ev.getTense() + "\ta=" + ev.getAspect() + "\tc=" + ev.getTheClass());
              if( ev.getTense() != null ) {
                datum = new RVFDatum<String,String>(features, ev.getTense());
                tenseDataset.add(datum);
              }
              if( ev.getAspect() != null ) {
                datum = new RVFDatum<String,String>(features, ev.getAspect());
                aspectDataset.add(datum);
              }
              if( ev.getTheClass() != null ) {
                datum = new RVFDatum<String,String>(features, ev.getTheClass());
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
    String path = modelDir + File.separator + baseModelName;
    System.out.println("Saving the classifier to disk (" + path + ")...");
    try {
      // Create the directory of classifiers, if necessary.
      File dir = new File(modelDir);
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
  public boolean isEvent(Classifier<String, String> classifier, Sentence sentence, Tree tree, List<TypedDependency> deps, int wordi) {
  	Counter<String> features = getEventFeatures(sentence, tree, deps, wordi);
    RVFDatum<String,String> datum = new RVFDatum<String,String>(features, null);
    String guess = classifier.classOf(datum);
    
    return guess.equals("event");
  }
  
  private RVFDatum<String,String> wordToDatum(Sentence sentence, Tree tree, List<TypedDependency> deps, int wordi) {
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
  private boolean isEventDeterministic(Tree tree, String[] tokens, int wordi) {
    String POS = TreeOperator.indexToPOSTag(tree, wordi); 
//    String prePOS = (wordi > 1 ? TreeOperator.indexToPOSTag(tree, wordi-1) : null);
    String postPOS = (wordi < tokens.length-1 ? TreeOperator.indexToPOSTag(tree, wordi+1) : null);

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
        for( int ii = 0; ii < timex.length(); ii++ )
          indices.add(timex.offset()+ii);
      }
    return indices;
  }
  
  public void extractEvents() {
    extractEvents(info, null);
  }

  /**
   * Destructively add events to the global .info file.
   * @param docnames Limit extraction to a set of documents in the info file. Use null if you want all docs.
   */
  public void extractEvents(InfoFile info, Set<String> docnames) {
  	if( !ruleBased ) {
  		System.out.println("*** Classifier-Based Event Extraction ***");
  		extractEvents(info, docnames, false);
  	} else {
  		System.out.println("*** Deterministic Event Extraction ***");
  		extractEvents(info, docnames, true);
  	}
  }
  
  /**
   * Destructively add events to the global .info file.
   * @param docnames Limit extraction to a set of documents in the info file. Use null if you want all docs.
   */
  public void extractEvents(InfoFile info, Set<String> docnames, boolean useDeterministic) {
    TreeFactory tf = new LabeledScoredTreeFactory();
    
    for( String docname : info.getFiles() ) {
      if( docnames == null || docnames.contains(docname) ) {
        System.out.println("doc = " + docname);
        List<Sentence> sentences = info.getSentences(docname);
        int eventi = 1;
        System.out.println(sentences.size() + " sentences.");

        // Build the typed dependencies.
        List<String> strdeps = info.getDependencies(docname);
        List<List<TypedDependency>> alldeps = new ArrayList<List<TypedDependency>>();
        for( String str : strdeps ) alldeps.add(InfoFile.stringToDependencies(str));
        
        // Each sentence.
        int sid = 0;
        for( Sentence sent : sentences ) {
          String[] tokens = sent.sentence().split("\\s+");
          Tree tree = TreeOperator.stringToTree(sent.parse(), tf);
          List<TextEvent> newevents = new ArrayList<TextEvent>();
          Set<Integer> timexIndices = indicesCoveredByTimexes(sent.timexes());

          if( tree != null && tree.size() > 1 ) {
          	// Each token.
          	int wordi = 1; // first word is index 1
          	for( String token : tokens ) {
          		// Skip tokens that are already tagged by a timex.
          		if( !timexIndices.contains(wordi) ) {

          			if( useDeterministic && isEventDeterministic(tree, tokens, wordi) ) {
          				TextEvent event = new TextEvent(token, "e" + eventi, sid, wordi);
          				event.setEiid("ei" + eventi);
          				newevents.add(event);
          				//                System.out.println("Created event: " + event);
          				eventi++;
          			}

          			if( !useDeterministic && isEvent(eventClassifier, sent, tree, alldeps.get(sid), wordi) ) {
          				TextEvent event = new TextEvent(token, "e" + eventi, sid, wordi);
          				event.setEiid("ei" + eventi);

          				// Set the event attributes.
          				RVFDatum<String,String> datum = wordToDatum(sent, tree, alldeps.get(sid), wordi);
          				//                System.out.println("datum: " + datum);
          				//                System.out.println("\taspect: " + aspectClassifier.classOf(datum));
          				event.setTense(tenseClassifier.classOf(datum));
          				event.setAspect(aspectClassifier.classOf(datum));
          				event.setTheClass(classClassifier.classOf(datum));

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
            info.addEvents(docname, sid, newevents);
          sid++;
        }
      }
    }
  }
  
  /**
   * Ouputs only the events that are in a document in the given InfoFile. 
   * It outputs a one per line format with indices:
   *     <sentence-id> <token-index> <token-string> <event-class> <event-tense>
   * @param outpath File path to create.
   * @param file Name of the file in the InfoFile that you want.
   * @param info The InfoFile itself.
   */
  public void createEventsOnlyFile(String outpath, String file, InfoFile info) {
    try {
      System.out.println("Writing to " + outpath + "...");
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outpath)));
      
      int sid = 0;
      for( Sentence sent : info.getSentences(file) ) {
        for( TextEvent event : sent.events() ) {
          writer.write(sid + "\t" + (event.index()-1) + "\t" + event.string());
//          writer.write("\t" + event.getTheClass() + "\t" + event.getTense() + "\t" + event.getAspect());
          writer.write("\t" + event.getTheClass() + "\t" + event.getTense());
          writer.write("\n");
        }
        sid++;
      }
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
    
  }
  
  public void readClassifiersFromDirectory() {
    readClassifiersFromDirectory(modelDir);
  }

  public void readClassifiersFromDirectory(String dir) {
  	if( !(new File(dir)).isDirectory() )
  		System.out.println("Not a directory: " + dir);
  	else {
  		eventClassifier  = readClassifierFromFile(dir + File.separator + baseModelName);
  		tenseClassifier  = readClassifierFromFile(dir + File.separator + baseModelName + "-tense");
  		aspectClassifier = readClassifierFromFile(dir + File.separator + baseModelName + "-aspect");
  		classClassifier  = readClassifierFromFile(dir + File.separator + baseModelName + "-class");  		
  	}
  }
  
  public Classifier<String,String> readClassifierFromFile(String path) {
  	try {
  		Classifier<String,String> classifier = (Classifier<String,String>)IOUtils.readObjectFromFile(path);
  		return classifier;
  	} catch(Exception ex) { 
  		System.out.println("Had fatal trouble loading " + path);
  		ex.printStackTrace(); System.exit(1); 
  	}
  	return null;
  }
  
  public void infoToFile(String path) {
    info.writeToFile(new File(path));
  }
  
  public void markupRawText(String filepath) {
    // Initialize the parser.
    LexicalizedParser parser = Ling.createParser(serializedGrammar);
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
    
    // Parse the file.
    info = Tempeval3Parser.rawTextFileToParsed(filepath, parser, gsf);
    readClassifiersFromDirectory();
    extractEvents();

    // Output the InfoFile with the events in it.
    String outpath = filepath + ".info.xml";
    infoToFile(outpath);
    System.out.println("Created " + outpath);

    // Output just the text with the events marked as XML elements.
    String markup = info.markupOriginalText(filepath);
    outpath = filepath + ".withevents";
    Directory.stringToFile(outpath, markup);
    System.out.println("Created " + outpath);
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
    info = Tempeval3Parser.lexParsedFileToDepParsed(filepath, tf, gsf);
    readClassifiersFromDirectory();
    extractEvents();

    // Output the InfoFile with the events in it.
//    String outpath = filepath + ".info.xml";
//    infoToFile(outpath);
//    System.out.println("Created " + outpath);

    // Output just the text with the events marked as XML elements.
    String markup = "";
    if( coeMarkFormat )
    	markup = info.markupOriginalText(filepath, "MARK", "ID", true, true, false, true);
    else
    	markup = info.markupOriginalText(filepath);
    
    Directory.stringToFile(filepath + ".withevents", markup);
//    System.out.println("Created " + outpath);
    
    // Output a text file with just event sentence/word indices.
    createEventsOnlyFile(filepath + ".onlyevents", filepath, info);
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
      classifier.markupRawText(args[args.length-2]);
    }
    
    // TextEventClassifier -model <dir> <file-of-parses> parsed
    else if( args[args.length-1].equalsIgnoreCase("parsed") ) {
      System.out.println("Marking up pre-parsed text input.");
      classifier.markupPreParsedText(args[args.length-2]);
    }
    
    // TextEventClassifier -info <infofile> 
    else {
      classifier.readClassifiersFromDirectory();
      classifier.extractEvents();
    	classifier.infoToFile("withevents.info.xml");
    	System.out.println("Created withevents.info.xml");
    }
  }

}
