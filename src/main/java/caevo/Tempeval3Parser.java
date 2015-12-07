package caevo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import caevo.Timex.DocumentFunction;
import caevo.tlink.EventEventLink;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.tlink.TLinkClassifier;
import caevo.tlink.TimeTimeLink;
import caevo.util.Directory;
import caevo.util.HandleParameters;
import caevo.util.Ling;
import caevo.util.Pair;
import caevo.util.TimebankUtil;
import caevo.util.TreeOperator;
import caevo.util.Util;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.StringUtils;

/**
 * @author chambers
 * 
 * This code reads both Timebank and Aquant data files from Tempeval-3, and builds a 
 * single XML file with all of the events, timexes, and tlinks.  It also parses the 
 * sentences and includes the parsed trees/graphs in the XML.
 * 
 * *** NOTE: I change all "during" and "identity" relations immediately to "simultaneous"
 * 
 * INPUT: directory of Tempeval-3 files
 * OUTPUT: a single XML file, "tempeval3.xml", unless the -output flag is used
 *
 * Tempeval3Parser [-output <path>] -noauto -input <tempeval3-dir>
 * -- Read in the XML docs and all its markup. Don't do anything else to it.
 * 
 * Tempeval3Parser [-output <path>] [-timex] [-eventmodel <dir>] [-tlinkmodel <dir>] -traininfo <infopath> -info <infopath>
 * -- Start from a .info file with all parse info (-traininfo), and tag up another one (-info) with timex/events/tlinks.
 * 
 * Tempeval3Parser -relations -tlinks <trained-model-dir> -input <tempeval3-dir>
 * -- Read a directory of fully labeled docs (with tlinks), then relabel the tlinks using our classifiers.
 * 
 * 
 * -eventmin
 * Minimum count cutoff for features to event classifier.
 * 
 * -min
 * Minimum count cutoff for features to TLink classifiers.
 * 
 */
public class Tempeval3Parser {
  String baseDir = "/home/nchamber/corpora/tempeval3/TBAQ-cleaned";
  String _serializedGrammar = "/englishPCFG.ser.gz";
  String outputFile = "tempeval3.xml";
  Properties props;
  LexicalizedParser _parser;
  GrammaticalStructureFactory _gsf;
  LabeledScoredTreeFactory _tf;
  Map<String,Map<String,String>> idToAttributes;
  Map<String,String> eiidToID;
  Map<String,List<String>> idToEiids;
  Set<String> seenEventIDs;

  // Set to true if you want to run SUTime tagger.
  boolean sutime = false;
  AnnotationPipeline timexPipeline = null;
  String _bethardFile = null;
  // TextEventClassifier requires this path if you want event tagging on.
  String eventmodelDir = null;
  // TLinkClassifier requires this path if you want tlink tagging on.
  String tlinkmodelDir = null;
  boolean readEventsAndTimes = true;
  boolean readTLinks = true;
  boolean noauto = false; // don't run any auto-tagging or extraction
  boolean labelRelationsOnly = false; // label known tlink pairs and don't extract anything else
  boolean useEIIDinTLinks = true; // use the eiid if true, use eid if false.
  
  SieveDocuments _infodocs = null;
  SieveDocuments _trainInfodocs = null;

  boolean debug = false;
  

  public Tempeval3Parser(String[] args) {
    handleParameters(args);
    
    // Init parsers.
//    URL grammarURL = Tempeval3Parser.class.getResource(_serializedGrammar);
    _parser = Ling.createParser(_serializedGrammar);
    
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    _gsf = tlp.grammaticalStructureFactory();
    _tf = new LabeledScoredTreeFactory();
    
    System.out.println("Input:\t\t\t" + baseDir);
    System.out.println("Output:\t\t\t" + outputFile);
    System.out.println("Read Events/Times:\t" + readEventsAndTimes);
    System.out.println("Label Timex:\t\t" + sutime);
    System.out.println("Label Events:\t\t" + (eventmodelDir == null ? false : true));
    System.out.println("");
    System.out.println("");
  }
  
  public void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-pure") ) {
    	readEventsAndTimes = false;
    	readTLinks = false;
    }
    if( params.hasFlag("-grammar") )
      _serializedGrammar = params.get("-grammar");
    if( params.hasFlag("-input") ) {
      baseDir = params.get("-input"); // TBAQ-cleaned directory.
    }
    if( params.hasFlag("-output") )
      outputFile = params.get("-output");
    if( params.hasFlag("-out") )
      outputFile = params.get("-out");
    if( params.hasFlag("-timex") )
      sutime = true;
    if( params.hasFlag("-events") )
    	eventmodelDir = params.get("-events");
    if( params.hasFlag("-tlinks") )
      tlinkmodelDir = params.get("-tlinks");
    if( params.hasFlag("-bethard") )
      _bethardFile = params.get("-bethard");
    if( params.hasFlag("-relations") )
      labelRelationsOnly = true;
    if( params.hasFlag("-noauto") )
      noauto = true;
    
    if( params.hasFlag("-info") )
      _infodocs = new SieveDocuments(params.get("-info"));
    if( params.hasFlag("-traininfo") )
      _trainInfodocs = new SieveDocuments(params.get("-traininfo"));
    
    props = StringUtils.argsToProperties(args);
  }

  /**
   * Read the TempEval-3 data files, extract all of its event/tlink information, and parse the text.
   * Output a .info file with all of the data.
   */
  public void parse() {
    
    // If there already is an infofile, then we shouldn't reparse everything.
    if( _infodocs == null ) {
      _infodocs = new SieveDocuments();
      
      // If we are reading in a directory with subdirectories "TimeBank" and "AQUAINT".
      if( new File(baseDir + File.separator + "TimeBank").exists() ) {
        String[] dirs = { "AQUAINT", "TimeBank" };
        for( String dir : dirs ) {
          String base = baseDir + File.separator + dir;
          parseXMLDir(base);
        }
      }
      // Else we are just reading a single directory of XML files.
      else parseXMLDir(baseDir);
      
      // Check for Bethard tlinks to merge with tlinks already read in above.
      if( _bethardFile != null && readEventsAndTimes )
        TimebankUtil.mergeBethard(_bethardFile, _infodocs);
    }

    // Don't do anything else to this info file that we just created.
    if( noauto ) { }
    // Automatically identify Events and TLinks using cross fold validation.
    else if( _trainInfodocs != null )
      autoExtractEventsAndTimesWithFolds(_trainInfodocs, _infodocs);
    // Label tlink pairs that are already present in the info file.
    else if( labelRelationsOnly )
      labelRelationsOnly(_infodocs);
    // Identify Events and TLinks using already trained classifiers on disk.
    else
      autoExtractEventsAndTimes(_infodocs);
    
    System.out.println("Writing infofile " + outputFile);
    _infodocs.writeToXML(outputFile);
  }
  
  private void parseXMLDir(String base) {
  	int numdocs = 0;
    for( String docname : Directory.getFilesSorted(base) ) {
//    	if( doc.contains("wsj_0073") ) {
    	if( true ) {
    		docname = base + File.separator + docname;
    		System.out.println("parsing " + docname);

    		// Pull out the event attributes from the MAKEINSTANCE elements first.
    		eiidToID = new HashMap<String,String>();
    		idToEiids = new HashMap<String,List<String>>();
    		seenEventIDs = new HashSet<String>();
    		extractEventAttributes(docname);

    		// Process the sentences, extract events/timexes, and parse.
    		processXML(docname);

    		// Get the TLinks
    		if( readTLinks ) {
    			List<TLink> tlinks = getTLinks(docname);
    			_infodocs.getDocument(docname).addTlinks(tlinks);
    		}

    		// Set the document creation time.
    		Timex dct = getDocumentCreationTime(docname);
    		if( dct != null ) _infodocs.getDocument(docname).addCreationTime(dct);

//    		if( numdocs % 2 == 1 ) break;
    		numdocs++;
    	}
    }
  }
  
  public static Pair<Set<String>,Set<String>> getFold(int fold, int numfolds, Collection<String> setNames) {
    Set<String> train = new HashSet<String>();
    Set<String> test = new HashSet<String>();
    int foldsize = setNames.size() / numfolds;
    
    List<String> names = new ArrayList<String>(setNames);
    
    for( int xx = 0; xx < names.size(); xx++ ) {
      int foldstart = fold*foldsize;
      int foldend = foldstart + foldsize;
      if( fold == numfolds-1 ) foldend = names.size();
      
      if( xx < foldend && xx >= foldstart )
        test.add(names.get(xx));
      else
        train.add(names.get(xx));
    }

    return new Pair<Set<String>, Set<String>>(train, test);
  }
  
  /**
   * Core function to automatically find events, time expressions, and tlinks.
   * @param trainDocs An infofile that has all gold events, times, and tlinks in it.
   * @param labelDocs An infofile with no events/tlinks, but all text pre-processing information.
   */
  public void autoExtractEventsAndTimesWithFolds(SieveDocuments trainDocs, SieveDocuments labelDocs) {
    System.out.println("Using cross-fold experiment to extract events and times and tlinks...");

    // Identify all time expressions.
    if( sutime ) {
      TimexClassifier classifier = new TimexClassifier(labelDocs);
      classifier.markupTimex3();
    }

    // Train on 9 folds and label on the remaining 1 fold.
    int numfolds = 10;
    for( int fold = 0; fold < numfolds; fold++ ) {
      Pair<Set<String>,Set<String>> trainTest = getFold(fold, numfolds, labelDocs.getFileNames());
      System.out.println("--- Fold " + fold + " ---");
      
      // Identify all events.
      if( props.containsKey("events") ) {
        System.out.println("Now training all text events...");
        TextEventClassifier eventClassifier = new TextEventClassifier(trainDocs);
        if( props.containsKey("eventmin") ) eventClassifier.setMinFeatureCutoff(Integer.parseInt(props.getProperty("eventmin")));
        eventClassifier.train(trainDocs, trainTest.first());
        System.out.println("Now testing all text events...");
        eventClassifier.extractEvents(labelDocs, trainTest.second());
      }

      // Identify all tlinks.
      if( props.containsKey("tlinks") || props.containsKey("eesame") || props.containsKey("etsame") || props.containsKey("edct") || props.containsKey("eediff") ) {
        System.out.println("Now training all tlinks...");
        TLinkClassifier tlinkClassifier = new TLinkClassifier(trainDocs, null, props);
        tlinkClassifier.trainInfo(trainDocs, trainTest.first());
        tlinkClassifier.docs = labelDocs;
        System.out.println("Now testing all tlinks...");
        tlinkClassifier.extractTLinks(trainTest.second());
      }
    }
  }
  
  public void autoExtractEventsAndTimes(SieveDocuments docs) {
    System.out.println("Extracting events and times and tlinks...");
    
    // Identify all time expressions.
    if( sutime ) {
      TimexClassifier classifier = new TimexClassifier(docs);
      classifier.markupTimex3();
    }
    
    // Identify all events.
    if( eventmodelDir != null ) {
      System.out.println("Now labeling all text events automatically...");
      TextEventClassifier eventClassifier = new TextEventClassifier(docs);
      if( props.containsKey("eventmin") ) eventClassifier.setMinFeatureCutoff(Integer.parseInt(props.getProperty("eventmin")));
      eventClassifier.readClassifiersFromDirectory(eventmodelDir);
      eventClassifier.extractEvents();
    }

    // Identify all tlinks.
    if( tlinkmodelDir != null ) {
      System.out.println("Now labeling all TLinks automatically...");
      TLinkClassifier classifier = new TLinkClassifier(docs, tlinkmodelDir, props);
      classifier.extractTLinks();
    }
  }
  
  /**
   * Assume the info file has all events, times, and tlinks already in it. Our job is to simply give those
   * tlinks new labels using our trained classifiers.
   * @param info A fully labeled infofile.
   */
  private void labelRelationsOnly(SieveDocuments info) {
    TLinkClassifier classifier = new TLinkClassifier(info, props.getProperty("tlinks"), props);
    classifier.labelKnownTLinks(null);
  }
  
  public static String buildStringFromStrings(List<String> tokens) {
    StringBuffer buf = new StringBuffer();
    if( tokens != null ) {
	int xx = 0;
	for( String token : tokens ) {
	    if( xx++ > 0 ) buf.append(' ');
	    buf.append(token);
	}
    }
    return buf.toString();
  }
  
  public static String buildString(List<HasWord> sentence, int starti, int endi) {
    StringBuffer buf = new StringBuffer();
    for( int xx = starti; xx < endi; xx++ ) {
      if( xx > starti ) buf.append(' ');
      buf.append(sentence.get(xx).word());
    }
    return buf.toString();
  }

  private static Document getXMLDocFromPath(String path) {
    // PARSE the input XML document of events
    Document doc = TimebankUtil.getXMLDoc(path);
    if( doc == null ) {
      System.err.println("Woah couldn't read doc " + path);
    }
    return doc;
  }
  
  private String replaceStanfordWeirdSpaces(String str) {
    return str.replace((char)160, ' ');
  }
  
  /**
   * @returns A pair based on two stopping conditions:
   *          (1) index of the token we stopped at in the List, and false.
   *          (2) index of the character in rawtext where the tokens ran out, and true.
   */
  private Pair<Integer,Boolean> advance(String rawtext, List<HasWord> tokens, int rawi, int tokeni) {
    while( rawi < rawtext.length() && tokeni < tokens.size() ) {
      // Advance past any white space in raw text.
      while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
      String subraw = rawtext.substring(rawi);
    
      // Make sure the first token lines up.
      String token = tokens.get(tokeni).word();
      // If using CoreLabels, it means we are preserving the original text, so use the original text's token!
      if( tokens.get(tokeni) instanceof CoreLabel )
        token = ((CoreLabel)tokens.get(tokeni)).get(CoreAnnotations.OriginalTextAnnotation.class);
      
      if( debug ) System.out.println("Checking __" + subraw + "__ with __" + token + "__");
      
      if( subraw.startsWith(token) ) {
        rawi += token.length();
        // Skip whitespace after the token
        while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
      }
      // The Stanford token might have a period added to it (p.m.) and the raw text was missing it (p.m)
      else if( token.endsWith(".") && subraw.startsWith(token.substring(0,token.length()-1)) ) {
        rawi += token.length()-1;
        // Skip whitespace after the token
        while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
      }
      // Reached an end of sentence, where the Stanford tokenizer added punctuation that wasn't in the raw text.
      else if( token.equals(".") && tokeni == tokens.size()-1 ) {
        if( debug ) System.out.println("End of sentence reached in middle of text (rawi=" + rawi + ").");
        return new Pair<Integer,Boolean>(rawi, true);
      }
      else if( subraw.charAt(0) == '.' && (subraw.length() == 1 || subraw.matches("^\\.\\s.*")) ) {
        rawi += 1;
        // Skip whitespace after the token
        while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
        tokeni--; // backup one since we haven't checked this token yet
      }
      else {
        for( int xx = 0; xx < token.length(); xx++ )
          System.out.print("" + (int)subraw.charAt(xx) + " " + (int)token.charAt(xx) + " " + (subraw.charAt(xx)==token.charAt(xx)) + " ");
        System.out.println("****");
        
        System.out.println("Text doesn't line up (tokeni=" + tokeni + "):\n***\t" + rawtext + "\n***\t" + tokens);
        System.exit(1);
      }
      tokeni++;
    }
    
    // Finished all tokens, but not the raw text.
    if( rawi < rawtext.length() ) {
      if( debug ) {
      	System.out.println("Enddd of sentence reached in middle of text.");
      	System.out.println("rawtext=" + rawtext);
      	System.out.println("rawi=" + rawi);
      }
      return new Pair<Integer,Boolean>(rawi, true);
    }
      
    // Reached the end of the raw text. Not necessarily end of tokens, just return where we are.
    if( debug ) System.out.println("RETURNING " + tokeni + " of " + tokens.size());
    return new Pair<Integer,Boolean>(tokeni, false);
  } 
  
  /**
   * @returns A pair based on two stopping conditions:
   *          (1) index of the token we stopped at in the List, and false.
   *          (2) index of the character in rawtext where the tokens ran out, and true.

  private Pair<Integer,Boolean> advanceOld(String rawtext, List<HasWord> tokens, int rawi, int tokeni) {
//    rawtext = rawtext.trim().replaceAll("``", "\"").replaceAll("''", "\"");
//    String tokenizedText = appendWords(tokens, tokeni);
    
    while( rawi < rawtext.length() && tokeni < tokens.size() ) {
      // Advance past any white space in raw text.
      while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
      String subraw = rawtext.substring(rawi);
    
      // Make sure the first token lines up.
      String token = tokens.get(tokeni).word();
      // If using CoreLabels, it means we are preserving the original text, so use the original text's token!
      if( tokens.get(tokeni) instanceof CoreLabel )
        token = ((CoreLabel)tokens.get(tokeni)).get(CoreAnnotations.OriginalTextAnnotation.class);
      
      // Fix tokenized parentheses.
      token = token.replaceAll("-LRB-", "(").replaceAll("-RRB-", ")").replaceAll("``", "\"").replaceAll("''", "\"");
      token = token.replaceAll("-LCB-", "{").replaceAll("-RCB-", "}");
      token = replaceStanfordWeirdSpaces(token);
      // Fix forward slashes that Stanford turns into "\/"
      token = token.replaceAll("\\\\/", "\\/");
      System.out.println("Checking __" + subraw + "__ with __" + token + "__");
      
      // The raw text might have "---", but Stanford reduces that to just "--".
      // Can't do a regex check...strings weren't matching even ".*" patterns, so I explicitly check characters.
      // Didn't feel like spending forever figuring out what strange string properties were failing the regex.
      if( token.equals("--") && subraw.length() > 2 && subraw.charAt(0) == '-' && subraw.charAt(1) == '-' && subraw.charAt(2) == '-' ) {
        int spacech = subraw.indexOf(' ');
        if( spacech == -1 ) spacech = subraw.length();
        rawi += spacech;
        // Skip whitespace after the hyphens.
        while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
      }

      else if( token.equals("...") && subraw.startsWith(".") && !subraw.startsWith("...") ) {
        int start = 0;
        while( start < subraw.length() && (subraw.charAt(start) == '.' || subraw.charAt(start) == ' ') ) start++;
        rawi += start;
        System.out.println("Found ... in subraw, moved to start=" + start);
        System.out.println("subraw was = " + subraw);
      }

      // NORMAL match. This is 99.9% of the time.
      else if( subraw.startsWith(token) ) {
        rawi += token.length();
        // Skip whitespace after the token
        while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
      }
      // The raw text might have `` as its quotations, and we turned those into " in the tokenized version. This is an ok match, let it slide.
      else if( token.equals("\"") && (subraw.startsWith("``") || subraw.startsWith("''")) ) {
        rawi += 2;
        // Skip whitespace after the quotes.
        while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
      }
      // The Stanford token might have a period added to it (p.m.) and the raw text was missing it (p.m)
      else if( token.endsWith(".") && subraw.startsWith(token.substring(0,token.length()-1)) ) {
        rawi += token.length()-1;
        // Skip whitespace after the token
        while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
      }
      // Reached an end of sentence, where the Stanford tokenizer added punctuation that wasn't in the raw text.
      else if( token.equals(".") && tokeni == tokens.size()-1 ) {
        System.out.println("End of sentence reached in middle of text (rawi=" + rawi + ").");
        return new Pair<Integer,Boolean>(rawi, true);
      }
      else if( subraw.charAt(0) == '.' && (subraw.length() == 1 || subraw.matches("^\\.\\s.*")) ) {
        rawi += 1;
        // Skip whitespace after the token
        while( rawi < rawtext.length() && (rawtext.charAt(rawi) == ' ' || rawtext.charAt(rawi) == '\n') ) rawi++;
        tokeni--; // backup one since we haven't checked this token yet
      }
      else {
        for( int xx = 0; xx < token.length(); xx++ )
          System.out.print("" + (int)subraw.charAt(xx) + " " + (int)token.charAt(xx) + " " + (subraw.charAt(xx)==token.charAt(xx)) + " ");
        System.out.println("****");
        
        System.out.println("Text doesn't line up (tokeni=" + tokeni + "):\n***\t" + rawtext + "\n***\t" + tokens);
        System.exit(1);
      }
      tokeni++;
    }
    
    // Finished all tokens, but not the raw text.
    if( rawi < rawtext.length() ) {
      System.out.println("Enddd of sentence reached in middle of text.");
      System.out.println("rawtext=" + rawtext);
      System.out.println("rawi=" + rawi);
      return new Pair<Integer,Boolean>(rawi, true);
    }
      
    // Reached the end of the raw text. Also, reached end of tokens, except there is one extra period.
    // This period must be fake-inserted by Stanford, so ignore it and advance the token pointer to the end.
//    if( tokeni == tokens.size()-1 && tokens.get(tokeni).toString().equals(".") ) {
//      System.out.println("RETURNING PAST STANFORD PERIOD " + (tokeni+1));
//      return new Pair<Integer,Boolean>(tokeni+1, false);
//    }
    
    // Reached the end of the raw text. Not necessarily end of tokens, just return where we are.
    System.out.println("RETURNING " + tokeni + " of " + tokens.size());
    return new Pair<Integer,Boolean>(tokeni, false);
  } 
     */
  
  public static String trailingWhitespace(String str) {
    String white = "";
    for( int xx = str.length()-1; xx >= 0; xx-- ) {
      char ch = str.charAt(xx);
      if( ch == ' ' || ch == '\n' || ch == '\t' )
        white = ch + white;
      else
        break;
    }
    return white;
  }
  
  private void intrepetXMLText(Element textel, String docname) {
    NodeList children = textel.getChildNodes();
    int childi = 0, sid = 0, rawstart = 0, tid = 1;

    // Get the raw text, and split on multiple newlines, or just leave as is for Stanford splitter.
    String allraw = textel.getTextContent();
    //String[] timebankSplits = allraw.split("\\n\\s*\\n+");
    String[] timebankSplits = new String[1];
    timebankSplits[0] = allraw;

    //    List<List<HasWord>> sentences = new ArrayList<List<HasWord>>();
    List<List<HasWord>> sentencesNormInvertible = new ArrayList<List<HasWord>>();
    if( debug ) System.out.println(timebankSplits);
    for( String split : timebankSplits ) {
      if( debug ) System.out.println("***" + split + "+++");
//      sentences.addAll(Ling.getSentencesFromText(split));
      sentencesNormInvertible.addAll(Ling.getSentencesFromTextNormInvertible(split));
    }
    if( debug ) System.out.println("Got " + sentencesNormInvertible.size() + " sentences.");

    if( sentencesNormInvertible.size() > 0 ) {
      String trailingWhite = trailingWhitespace(allraw);
      List<HasWord> sentence = sentencesNormInvertible.get(sentencesNormInvertible.size()-1); 
      CoreLabel cl = (CoreLabel)sentence.get(sentence.size()-1);
      cl.set(CoreAnnotations.AfterAnnotation.class, trailingWhite);
    }
    
    if( debug ) System.out.println("Original:");
    if( debug ) System.out.println(allraw);
    
    // Loop over the Stanford tokenized sentences.
    for( List<HasWord> sentence : sentencesNormInvertible ) {
      List<TextEvent> localEvents = new ArrayList<TextEvent>();
      List<Timex> localTimex      = new ArrayList<Timex>();
      if( debug ) System.out.println("Checking sentence: " + listToString(sentence));
      if( debug ) System.out.println("rawstart = " + rawstart);
      int wordi = 0;
            
      while( childi < children.getLength() ) {
        Node child = children.item(childi);

        // Text node next.
        if( child.getNodeType() == Node.TEXT_NODE ) {
          String str = ((Text)child).getData();
          
          // Some text nodes are just white space (between two Elements in a sentence).
          if( str.matches("^\\s*$") ) { }
          
          else {
            //          if( rawstart > 0 ) str = str.substring(rawstart);
            //          rawstart = 0;
          	if( debug ) System.out.println("str=" + str + " and wordi=" + wordi);
            Pair<Integer,Boolean> pair = advance(str, sentence, rawstart, wordi);
            if( pair.second() ) { // sentence split in middle of this text node.
              rawstart = pair.first();
              break;
            }
            else {
              wordi = pair.first();
              rawstart = 0;
            }
          }
        }
        
        // Non-text node.
        else if( child.getNodeType() == Node.ELEMENT_NODE ) {
          String str = child.getTextContent();
          int starti = wordi;
          if( debug ) System.out.println("element with wordi=" + wordi);
          Pair<Integer,Boolean> pair = advance(str, sentence, 0, wordi);
          // If sentence split, but the raw text pointer didn't move...then it was the
          // Stanford parser adding extra periods. Just move on to start at the next sentence.
          if( pair.second() && pair.first() == 0 ) {
            rawstart = pair.first();
            break;
          }
          // Normally shouldn't split sentences in an element!
          else if( pair.second() ) {
            System.out.println("ERROR: sentence split in middle of element: ");
            System.exit(1);
          }
          else wordi = pair.first();
          
          Element el = (Element)child;
          
          // EVENT elements.
          if( readEventsAndTimes && el.getTagName().equals("EVENT") ) {
            TextEvent event = new TextEvent(el.getAttribute("eid"),sid,starti+1,el);
            fillInEventAttributes(event);
//            eiidToID.put(event.eiid(), event.id()); // used later in TLink creation.
            seenEventIDs.add(event.getId());            
            if( debug ) System.out.println("created event: " + event);
            localEvents.add(event);
          }
          // TIMEX elements.
          else if( readEventsAndTimes && el.getTagName().equals("TIMEX3") ) {
            Timex timex = new Timex();
            timex.setSpan(starti+1, wordi+1);
            timex.setText(buildString(sentence, starti, wordi));
            timex.saveAttributes(el);
            localTimex.add(timex);
          }
        }
        
        childi++;

        // Reached the end of the sentence.
        if( wordi >= sentence.size() )
          break;
      }
      
      /*
      // Call Timex
      if( sutime ) {
      	List<Timex> stanfordTimex = markupTimex3(sentence, tid);
      	tid += stanfordTimex.size();
      	System.out.println("Have " + localTimex.size() + " timexes.");
      	System.out.println("GOT " + stanfordTimex.size() + " new timexes.");
      	localTimex.addAll(stanfordTimex);
      	System.out.println("Now have " + localTimex.size());
      }
      */
      
      Pair<String,String> parseDep = parseDep(sentence);
//      String invertibleTokens = generateInvertibleString(sentence);
      if( debug ) System.out.println("before addSentence with events=" + localEvents);
//      System.out.println("invertibleTokens:\n" + invertibleTokens);
      List<CoreLabel> cls = new ArrayList<CoreLabel>();
      for( HasWord word : sentence ) cls.add((CoreLabel)word);
      _infodocs.getDocument(docname).addSentence(buildString(sentence, 0, sentence.size()), cls, parseDep.first(), parseDep.second(), localEvents, localTimex);
      
      sid++;
    }
    
    /*
    for( int j = 0; j < children.getLength(); j++ ) {
      Node child = children.item(j);
      List<Node> stuff = new ArrayList<Node>();
      stuff.add(child);

      while( !stuff.isEmpty() ) {
        child = stuff.remove(0);

        // If child is a tagged node
        if( child.getNodeType() == Node.ELEMENT_NODE ) {
          Element el = (Element)child;

          // Save the event strings
          if( el.getTagName().equals("EVENT") ) {
            //      System.out.println(el.getFirstChild());
            TextEvent event = new TextEvent(el.getAttribute("eid"),sid,loc,el);
            localEvents.add(event);
            List<Word> words = TimebankParser.getWords(el);
            for( Word word : words )
              text = text + " " + word;

            // RARE: There are nested EVENT tags...we take the innermost
            // ... sometimes 3 are nested!
            while( el.getFirstChild() instanceof Element &&
                ((Element)el.getFirstChild()).getTagName().equalsIgnoreCase("EVENT") ) {
              el = (Element)el.getFirstChild();
              // save the nested event
              event = new TextEvent(el.getAttribute("eid"),sid,loc,el);
              localEvents.add(event);
            }

            loc = loc + words.size();
          }
          // Save the entity tags
          else if( el.getTagName().equals("ENAMEX") ) {
//            System.out.println("PRE-ENAMEX text=" + text);
            String id = el.getAttribute("ID");
            List<Word> words = TimebankParser.getWords(el);
            int k = 0;
            // pad the entities entries for each word
            for( Word word : words ) {
              text = text + " " + word;
              k++;
            }
            // Check for EVENTs inside the ENAMEX...
            findEventInEnamex(el,localEvents,loc,sid);
            loc = loc + words.size();
//            System.out.println("POST-ENAMEX text=" + text);
          }
          // Save TIMEX tags
          else if( el.getTagName().equals("TIMEX3") ) {
            Timex timex = new Timex();
            List<Word> words = TimebankParser.getWords(el);
            timex.setSpan(loc, loc + words.size());
            timex.saveAttributes(el);
            localTimex.add(timex);

            String thetime = "";
            for( Word word : words ) thetime += " " + word;
            timex.setText(thetime.trim());
            text += thetime;
            loc += words.size();
          }
          else if( el.getTagName().equals("SIGNAL") || el.getTagName().equals("CARDINAL") ||
              el.getTagName().equals("NUMEX") ) {
            
            // Sometimes the NUMEX contains an event (a state like "30% full").
            if( el.getFirstChild() instanceof Element &&
                ((Element)el.getFirstChild()).getTagName().equalsIgnoreCase("EVENT") ) {
              Element tempel = (Element)el.getFirstChild();
              // save the nested event
              TextEvent event = new TextEvent(tempel.getAttribute("eid"),sid,loc,tempel);
              localEvents.add(event);
              System.out.println("NEW: added numex event: " + event);
            }
            
            List<Word> words = TimebankParser.getWords(el);
            for( Word word : words ) text = text + " " + word;
            loc = loc + words.size();
          }
          else {//if( sub.equals("NG") || sub.equals("VG") || sub.equals("JG") || sub.equals("PG") ) {
            // add all the children to the vector
            NodeList nodes = el.getChildNodes();
            for( int k = 0; k < nodes.getLength(); k++ ) stuff.add(nodes.item(k));
          }
        }

        // Else child is plain text
        else if( child.getNodeType() == Node.TEXT_NODE ) {
          String str = ((Text)child).getData();

          if( !str.matches("\\s+") ) {
            List<Word> words = Ling.getWordsFromString(str);
            // pad the entities entries for each word
            for( Word word : words ) {
              text = text + " " + word;
              loc++;
            }
          }
        }
      }
      */
  }
  
  /**
   * Takes the commmand line arguments and the starting index of the first
   * file to parse.
   */
  public void processXML(String timebankFile) {

    // PARSE the input XML document of events
    Document doc = getXMLDocFromPath(timebankFile);

    // Grab the only TEXT element.
    Element textElement = (Element)doc.getElementsByTagName("TEXT").item(0);
    
    String justtext = textElement.getTextContent();
    if( debug ) System.out.println("got: " + justtext);
    
    intrepetXMLText(textElement, timebankFile);
  }
  
  /**
   * Reads an XML file path that contains raw text in a <TEXT> element. Assumes the text in
   * this file is unlabeled for events. This looks for <TEXT> and <DCT> elements to load
   * the SieveDocument object.
   */
  public static SieveDocument rawXMLtoSieveDocument(String xmlFilePath, LexicalizedParser parser, GrammaticalStructureFactory gsf) {

    // PARSE the input XML document of events
    Document doc = getXMLDocFromPath(xmlFilePath);

    // Grab the TEXT element.
    Element textElement = null;
    String justtext = null;
    if( doc.getElementsByTagName("TEXT") != null ) {
    	textElement = (Element)doc.getElementsByTagName("TEXT").item(0);
    	justtext = textElement.getTextContent();
    }

    // Parse the text.
    SieveDocument sdoc = rawTextToParsed((new File(xmlFilePath)).getName(), justtext, parser, gsf);
    
    // Grab the DCT element.
    Element dctElement = null;
    if( doc.getElementsByTagName("DCT") != null ) {
    	dctElement = (Element)doc.getElementsByTagName("DCT").item(0);
    	NodeList timexes = dctElement.getChildNodes();
    	for( int ii = 0; ii < timexes.getLength(); ii++ ) {
    		// Only process TIMEX* elements.
    		if( timexes.item(ii).getNodeName().startsWith("TIMEX") ) {
    			// Add the DCT elements.
    			Timex timex = new Timex();
    			timex.saveAttributes((Element)timexes.item(ii));
    			sdoc.addCreationTime(timex);
    		}
    	}
    }
    
    return sdoc;
  }
  
  /**
   * Every Tempeval/Timebank document should have a document creation time <DCT> element. Each of these
   * elements is supposed to have a <TIMEX3> child which gives the document's resolved creation time.
   * @param docname The document to retrieve from.
   * @return The timex object for the doc's creation time, pulled from the XML.
   */
  private Timex getDocumentCreationTime(String docname) {
    // Grab the TIMEX3 element in the document representing the document's creation time.
    Document doc = getXMLDocFromPath(docname);
    NodeList dcts = doc.getElementsByTagName("DCT");
    if( dcts == null )
      System.err.println("ERROR: couldn't find DCT element in file: " + docname);
    Node dctNode = dcts.item(0);
    NodeList children = dctNode.getChildNodes();

    // Find all <TIMEX3> nodes in the parent <DCT> of this document. 
    List<Node> timex3list = new ArrayList<Node>();
    for( int xx = 0; xx < children.getLength(); xx++ ) {
      Node child = children.item(xx);
      if( child != null && child.getNodeName().equals("TIMEX3") )
        timex3list.add(child);
    }
    
    // Make sure we found only one TIMEX3 element.
    if( timex3list.size() == 0 )
      System.err.println("ERROR: couldn't find TIMEX3 child of DCT element: " + docname);
    else if( timex3list.size() > 1 ) {
      System.err.println("ERROR: too many TIMEX3 children of DCT element: " + docname);
      System.exit(1);
    }      

    Node timex3 = timex3list.get(0);

    // Create my timex object.
    Timex dct = new Timex();
    dct.setTid(((Element)timex3).getAttribute("tid"));
    dct.setText("");
    dct.setSpan(0, 1);
    dct.setType(Timex.Type.valueOf(((Element)timex3).getAttribute("type")));
    dct.setValue(((Element)timex3).getAttribute("value"));
    dct.setDocumentFunction(Timex.DocumentFunction.valueOf(((Element)timex3).getAttribute("functionInDocument")));
    
    return dct;
  }
  
  /**
   * Read all of the tlinks from a given document name from the Tempeval-3 XML file.
   * @param docname The name of the document from which we extract tlinks.
   * @return List of TLink objects.
   */
  private List<TLink> getTLinks(String docname) {
    Document doc = getXMLDocFromPath(docname);
    NodeList thelist = doc.getElementsByTagName("TLINK");
    
    List<TLink> links = new ArrayList<TLink>();

    for( int xx = 0; xx < thelist.getLength(); xx++ ) {
      Element link = (Element)thelist.item(xx);
      links.add(interpretTLink(link));
    }
    
    return links;
  }
  
  /**
   * Take a TLINK Element from the Tempeval-3 data and return a TLink object.
   * @param link An element.
   * @return A TLink object.
   */
  private TLink interpretTLink(Element link) {
    String event        = link.getAttribute("eventInstanceID");
    String relatedEvent = link.getAttribute("relatedToEventInstance");
    String relatedTime  = link.getAttribute("relatedToTime");
    String time         = link.getAttribute("timeID");
    String relType      = link.getAttribute("relType");
//    String linkID       = link.getAttribute("lid");
    String eiid = event;
    String relatedEiid = relatedEvent;
    
    if( debug ) System.out.println(event + "\t" + relatedEvent + "\t" + relatedTime + "\t" + time + "\t" + relType);

    // Map EIID to ID for the events. 
    if( event != null && event.length() > 0 ) {
//      if( !useEIIDinTLinks && eiidToID.containsKey(event) ) event = eiidToID.get(event);
//      else System.out.println("ERROR: event " + event + " not in the eiid to ID lookup!");
      if( !eiidToID.containsKey(event) || !seenEventIDs.contains(eiidToID.get(event)) )
        System.out.println("ERROR: event " + event + " not extracted from text, but used in TLink!");
    }
    if( relatedEvent != null && relatedEvent.length() > 0 ) {
//      if( !useEIIDinTLinks && eiidToID.containsKey(relatedEvent) ) relatedEvent = eiidToID.get(relatedEvent);
//      else System.out.println("ERROR: relatedEvent " + relatedEvent + " not extracted from text, but used in TLink!");
      if( !eiidToID.containsKey(relatedEvent) || !seenEventIDs.contains(eiidToID.get(relatedEvent)) )
        System.out.println("ERROR: relatedEvent " + relatedEvent + " not extracted from text, but used in TLink!");
    }
    
    // Make sure we have a known relation.
    if( relType == null ) {
      System.err.println("null relType in TLink: " + link);
      System.exit(-1);
    }

    if( relType.equalsIgnoreCase("during") ) relType = "SIMULTANEOUS"; // This is what Tempeval-3 eval code does.
    if( relType.equalsIgnoreCase("identity") ) relType = "SIMULTANEOUS"; // Tempeval-3 eval code does NOT do this, not sure...
    
    // Create the tlink object.
    if( event != null && event.length() > 0 && relatedEvent != null && relatedEvent.length() > 0 ) {
    	TLink thelink = new EventEventLink(event, relatedEvent, TLink.Type.valueOf(relType));
//    	thelink.eiid1 = eiid;
//    	thelink.eiid2 = relatedEiid;
    	return thelink;
    }
    if( event != null && event.length() > 0 && relatedTime != null && relatedTime.length() > 0 ) {
    	TLink thelink = new EventTimeLink(event, relatedTime, TLink.Type.valueOf(relType));
//    	thelink.eiid1 = eiid;
    	return thelink;
    }
    if( time != null && time.length() > 0 && relatedEvent != null && relatedEvent.length() > 0 ) {
    	TLink thelink = new EventTimeLink(time, relatedEvent, TLink.Type.valueOf(relType));
//    	thelink.eiid2 = relatedEiid;
    	return thelink;
    }
    if( time != null && time.length() > 0 && relatedTime != null && relatedTime.length() > 0 )
      return new TimeTimeLink(time, relatedTime, TLink.Type.valueOf(relType));

    // Shouldn't reach here.
    System.err.println("ERROR: TLINK didn't have the expected attributes: " + link);
    return null;
  }
  
  /**
   * Uses the global hash table to look up an event's ID, and grab all of its tense/aspect information.
   * This was pulled from MAKEINSTANCE elements in the TimeBank files. This function updates the
   * given TextEvent with all of the attributes.
   */
  private void fillInEventAttributes(TextEvent event) {
    Map<String,String> atts = idToAttributes.get(event.getId());
    if( atts != null ) {
      event.setTense(TextEvent.Tense.valueOf(atts.get("tense")));
      event.setAspect(TextEvent.Aspect.valueOf(atts.get("aspect")));
      event.setPolarity(TextEvent.Polarity.valueOf(atts.get("polarity")));
      event.setModality(atts.get("modality"));
      String eiid = atts.get("eiid");
      if( eiid != null && eiid.length() > 0 ) {
        // There might have been multiple MAKEINSTANCEs, so save all the EIIDs in the event.
      	List<String> eiids = idToEiids.get(event.getId());
      	for( String ee : eiids ) event.addEiid(ee);
      }
      // No MAKEINSTANCE for this event, so make up an eiid 
      else {
      	String id = event.getId();
      	eiid = "ei" + id.substring(1);
      	while( eiidToID.containsKey(eiid) ) eiid = eiid + "2";
      	eiidToID.put(eiid, id);
      	event.addEiid(eiid);
      }
    } else {
      System.out.println("ERROR: no event attributes from a MAKEINSTANCE for event: " + event);

      // This means the annotators messed up, and there is no MAKEINSTANCE in the file! Create dummy fillers.
      event.setTense(TextEvent.Tense.NONE);
      event.setAspect(TextEvent.Aspect.NONE);
      event.setPolarity(TextEvent.Polarity.POS);
      event.setModality("");
      // Create a unique eiid for the event based on its id.
      String id = event.getId();
      String eiid = "ei" + id.substring(1);
      while( eiidToID.containsKey(eiid) ) eiid = eiid + "2";
      eiidToID.put(eiid, id);
      event.addEiid(eiid);
    }
  }
  
  /**
   * Retrieve all of the timexes in a single sentence (sid index) in a document (docname).
   * This opens the raw TempEval-2 file and looks for the lines with that document and sentence.
   * @param docname The document to retrieve from.
   * @param sid The sentence index to retrieve events.
   * @param tree The parse tree of that sentence.
   * @return All events as TextEvent objects.
   */
  private void extractEventAttributes(String docname) {
    idToAttributes = new HashMap<String,Map<String,String>>();
    String[] names = { "eventID", "eiid", "tense", "aspect", "polarity", "modality", "pos" };
    
    Document doc = getXMLDocFromPath(docname);
    NodeList thelist = doc.getElementsByTagName("MAKEINSTANCE");

    for( int xx = 0; xx < thelist.getLength(); xx++ ) {
      Element mkinst  = (Element)thelist.item(xx);
      Map<String,String> atts = new HashMap<String,String>();
      for( String name : names ) {
        String value = mkinst.getAttribute(name);
        atts.put(name, value);
      }
      // If we don't already have a MAKEINSTANCE for this event. (semeval has duplicates)
      String eid = atts.get("eventID");
      String eiid = atts.get("eiid");
      if( !idToAttributes.containsKey(eid) ) {
      	idToAttributes.put(atts.get("eventID"), atts);
      }
    	eiidToID.put(eiid, eid);
    	if( !idToEiids.containsKey(eid) ) idToEiids.put(eid, new ArrayList<String>());
    	idToEiids.get(eid).add(eiid);
    	if( debug ) System.out.println("Added MakeInstance " + eid + " to " + eiid);
    }
  }

  private String listToString(List<HasWord> words) {
    StringBuffer buf = new StringBuffer();
    boolean first = true;
    for( HasWord word : words ) {
      if( !first ) buf.append(", ");
      buf.append(word.word());
      first = false;
    }
    return buf.toString();
  }

  /**
   * Retrieve all of the events in a single sentence (sid index) in a document (docname).
   * This relies on twothe raw TempEval-2 files, one contains the text span of the timex,
   * and the other contains the attributes of each timex. This function just looks for the
   * lines that start with the docname and sentence ID.
   * @param docname The document to retrieve from.
   * @param sid The sentence index to retrieve timexes.
   * @param tree The parse tree of that sentence.
   * @return All time expressions as Timex objects.

  private List<Timex> getTimexes(String docname, int sid, Tree tree) {
    Map<String,Timex> idToTimex = new HashMap<String,Timex>();

    // Read the Timex Extents file (tokens in a timex).
    List<String> lines = linesWithDocname(docname, timexExtentFile);
    for( String line : lines ) {
      String[] parts = line.split("\t");
      Integer linesid = Integer.valueOf(parts[1]);

      // If this line is in the sentence we desire.
      if( sid == linesid ) {
        String timexID = parts[4];
        Timex timex = idToTimex.get(timexID);
        if( timex == null ) {
          Integer index = Integer.valueOf(parts[2]);
          // Token index is 1-indexed in JavaNLP parse trees, but 0-indexed in TempEval2
          Tree subtree = TreeOperator.indexToSubtree(tree, index+1);
          try {
            String token = subtree.firstChild().value();
            timex = new Timex();
            timex.setTID(timexID);
            timex.setSID(sid);
            timex.setText(token);
            timex.setSpan(index+1, index+2); // Our parse trees 1-index, not 0-index like Tempeval2 input
            idToTimex.put(timexID, timex);
//            System.out.println("Created timex: " + timex);
          } catch( Exception ex ) {
            System.out.println("Woah bad index: " + index + " from tree: " + tree);
            ex.printStackTrace();
            System.exit(-1);
          }
        }
        // Another token in this timex.
        else {
          Integer index = Integer.valueOf(parts[2]);
          Tree subtree = TreeOperator.indexToSubtree(tree, index+1);
          String token = subtree.firstChild().value();

          timex.setSpan(timex.offset(), timex.offset()+timex.length()+1);
          timex.setText(timex.text() + " " + token);
//          System.out.println("  Incremented timex: " + timex);
        }
      }      
    }

    // Read the Timex Attributes file.
    lines = linesWithDocname(docname, timexAttsFile);
    for( String line : lines ) {
      String[] parts = line.split("\t");
      Integer linesid = Integer.valueOf(parts[1]);

      // If this line is in the sentence we desire.
      if( sid == linesid ) {
        String eventID = parts[4];
        Timex timex = idToTimex.get(eventID);
        if( timex == null ) {
          System.out.println("ERROR: found a timex in attributes that wasn't in extent: " + line);
          System.exit(-1);
        }
        // Save the attribute.
        String att = parts[6];
        String val = parts[7];
        if( att.equals("type") ) timex.setType(val);
        else if( att.equals("value") ) timex.setValue(val);
        else {
          System.out.println("Unknown timex attribute type: " + line);
          System.exit(-1);
        }
      }
    }
    
    List<Timex> list = new ArrayList<Timex>(idToTimex.values());
    return list;  }
   */
  
  
  /**
   * Given a document, find it in the Tempeval file, pull out all of its sentences, and
   * parse each one both phrasal and dependencies.
   * @param docname The name of the Tempeval document to parse.
   * @return A pair: (1) List of parse trees, (2) List of dependency graphs.
   *         The same number of trees and graphs are aligned.

  private Pair<List<String>,List<String>> getParsesAndDeps(String docname) {
    List<String> parses = new ArrayList<String>();
    List<String> deps = new ArrayList<String>();
    
    List<HasWord> sentence = null;
    int currentSID = -1;
    int index = 0;
    
    List<String> lines = linesWithDocname(docname, wordSegFile);
    for( String line : lines ) {
      System.out.println("line: " + line);
      String parts[] = line.split("\t");
      Integer sid = Integer.valueOf(parts[1]);
      
      // If a new sentence, save the old one.
      if( sid > currentSID ) {
        System.out.println("Sentence read: " + sentence);
        
        // PARSE the sentence
        if( sentence != null ) {
          Pair<String,String> parsedep = parseDep(sentence);
          parses.add(parsedep.first());
          deps.add(parsedep.second());
        }
        
        sentence = new ArrayList<HasWord>();
        index = 1;
        currentSID = sid;
      }
      
      // Append the next word.
      if( parts[3].matches("[\\[\\(]") ) parts[3] = "-LRB-";
      if( parts[3].matches("[\\]\\)]") ) parts[3] = "-RRB-";
//      System.out.println("sentence read: " + sentence);
      sentence.add(new Word(parts[3], index, 1));
      index++;
    }
    
    // Last sentence of the file.
    Pair<String,String> parsedep = parseDep(sentence);
    parses.add(parsedep.first());
    deps.add(parsedep.second());
   
    Pair<List<String>,List<String>> parsesDeps = new Pair<List<String>,List<String>>(parses, deps);    
    return parsesDeps;
  }
     */
  
  /**
   * The given file is assumed to contain lexical parse trees, one per line. Blank lines are ok.
   * This function creates an InfoFile object with the parse trees, one for each sentence, and it also
   * dependency parses each sentence using the given tree.
   */
  public static SieveDocument lexParsedFileToDepParsed(String filepath, TreeFactory tf, GrammaticalStructureFactory gsf) {
    List<String> lines = Util.readLinesFromFile(filepath);
    List<String> stringParses = new ArrayList<String>();
    for( String line : lines )
      if( line.startsWith("(") )
        stringParses.add(line);

    return lexParsedToDeps(filepath, stringParses, tf, gsf);
  }
  
  private static SieveDocument lexParsedToDeps(String filepath, List<String> stringParses, TreeFactory tf, GrammaticalStructureFactory gsf) {
  	SieveDocument doc = new SieveDocument(filepath);

  	int sid = 0;
  	for( String strParse : stringParses ) {
  		//      System.out.println("* " + strParse);

  		// Get deps, create infofile sentence.
  		Tree parseTree = TreeOperator.stringToTree(strParse, tf);
  		String strDeps = lexParseToDeps(parseTree, gsf);

  		List<String> tokens = new ArrayList<String>();
  		if( parseTree != null && parseTree.size() > 1 )
  			tokens = TreeOperator.stringLeavesFromTree(parseTree);

  		List<CoreLabel> cls = new ArrayList<CoreLabel>();
  		for( String token : tokens ) {
  			CoreLabel label = new CoreLabel();
  			label.set(CoreAnnotations.BeforeAnnotation.class, "");
  			label.set(CoreAnnotations.OriginalTextAnnotation.class, token);
  			label.set(CoreAnnotations.AfterAnnotation.class, " "); // put a space after every token...
  			cls.add(label);
  		}

  		if( cls.size() > 1 )
  			cls.get(cls.size()-1).set(CoreAnnotations.AfterAnnotation.class, "\n\n"); // new lines after each sentence

  		doc.addSentence(buildStringFromStrings(tokens), cls, strParse, strDeps, null, null);
  		sid++;
  	}

  	return doc; 
  }
  
  public static SieveDocument rawTextFileToParsed(String filepath, LexicalizedParser parser, GrammaticalStructureFactory gsf) {
    List<String> lines = Util.readLinesFromFile(filepath);
    String bigone = lines.get(0);
    for( int xx = 1; xx < lines.size(); xx++ ) bigone += "\n" + lines.get(xx);
//    System.out.println("bigone=" + bigone);
 
    return rawTextToParsed(filepath, bigone, parser, gsf);
  }
  
  private static SieveDocument rawTextToParsed(String filename, String text, LexicalizedParser parser, GrammaticalStructureFactory gsf) {
    List<List<HasWord>> sentencesNormInvertible = new ArrayList<List<HasWord>>();
    sentencesNormInvertible.addAll(Ling.getSentencesFromTextNormInvertible(text));
    System.out.println("Got " + sentencesNormInvertible.size() + " sentences.");

    if( sentencesNormInvertible.size() > 0 ) {
      String trailingWhite = trailingWhitespace(text);
      List<HasWord> sentence = sentencesNormInvertible.get(sentencesNormInvertible.size()-1); 
      CoreLabel cl = (CoreLabel)sentence.get(sentence.size()-1);
      cl.set(CoreAnnotations.AfterAnnotation.class, trailingWhite);
    }
    
    SieveDocument sdoc = new SieveDocument((new File(filename)).getName());

    int sid = 0;
    for( List<HasWord> sent : sentencesNormInvertible ) {
//    	System.out.println("* " + sent);
      Pair<String,String> parseDep = parseDep(sent, parser, gsf);
      List<CoreLabel> cls = new ArrayList<CoreLabel>();
      for( HasWord word : sent ) cls.add((CoreLabel)word);
      sdoc.addSentence(buildString(sent, 0, sent.size()), cls, parseDep.first(), parseDep.second(), null, null);
      sid++;
    }
    
    return sdoc; 
  }
  
  private Pair<String,String> parseDep(List<HasWord> sentence) {
    return parseDep(sentence, _parser, _gsf);
  }
  
  /**
   * Parse a given list of words, syntactic and dependency.
   * @param sentence The list of words.
   * @return A pair: (1) phrase tree, (2) dependency graph.
   */
  public static Pair<String,String> parseDep(List<HasWord> sentence, LexicalizedParser parser, GrammaticalStructureFactory gsf) {
    // PARSE the sentence
    if( sentence != null ) {
      Tree ansTree = parser.parseTree(sentence);
      if( ansTree == null ) {
        System.out.println("Sentence failed to parse: " + sentence);
        System.exit(-1);
      }

      String parseString = ansTree.toString();

      // This generally works to pretty print on multiple lines, but not on windows. Carriage returns are a nightmare.
//      StringWriter treeStrWriter = new StringWriter();
//      TreePrint tp = new TreePrint("penn");
//      tp.printTree(ansTree, new PrintWriter(treeStrWriter,true));
//      parseString = treeStrWriter.toString();
      
      List<Tree> leaves = TreeOperator.leavesFromTree(ansTree);
      int ii = 0;
      for( Tree leaf : leaves ) {
        CoreLabel cl = (CoreLabel)leaf.label();
//        System.out.println(sentence.get(ii) + "\n\t" + cl);       
//        System.out.print("orig=" + cl.originalText() + " word=" + cl.word() + " " + cl.get(CoreAnnotations.OriginalTextAnnotation.class));
        ii++;
      }
      
      // If Stanford's parser changes the size of the original tokenized sentence, halt immediately with an error.
      if( leaves.size() != sentence.size() ) {
        System.out.println("ERROR: number of leaves " + leaves.size() + " not the same as original sentence size " + sentence.size());
        System.exit(1);
      }

      // DEP PARSE the sentence - CAUTION: DESTRUCTIVE to parse tree
      String depString = lexParseToDeps(ansTree, gsf);

//      System.out.println(" - deps: " + depString);
     
      return new Pair<String,String>(parseString,depString);
    }
    return null;
  }
  
  /**
   * DEP PARSE the sentence - CAUTION: DESTRUCTIVE to parse tree
   */
  private static String lexParseToDeps(Tree lexTree, GrammaticalStructureFactory gsf) {
    String depString = "";
    if( lexTree != null && lexTree.size() > 1 ) {
    	try {
    		GrammaticalStructure gs = gsf.newGrammaticalStructure(lexTree);
    		if( gs != null ) {
    			List<TypedDependency> localdeps = gs.typedDependenciesCCprocessed(true);
    			if( localdeps != null )
    				for( TypedDependency dep : localdeps )
    					depString += dep + "\n";
    		}
    	} catch( Exception ex ) { 
    		System.out.println("ERROR: dependency tree creation failed...");
    		ex.printStackTrace();
    		System.exit(-1);
    	}
    }
    return depString;
  }
  
  
  /**
   * Given a single sentence (represented as a pre-tokenized list of HasWord objects), use stanford's 
   * SUTime to identify temporal entities and mark them up as TIMEX3 elements.
   * 
   * This function should preserve the given words, and result in the same number of words, just returning
   * Timex objects based on the given word indices. Timex objects start 1 indexed: the first word is at
   * position 1, not 0.
   * @param words A single sentence's words.
   * @param idcounter A number to use for an ID of the first timex, and increment from there.
   * @return A list of Timex objects with resolved time values.

  private List<Timex> markupTimex3(List<HasWord> words, int idcounter) {
    // Load the pipeline of annotations needed for Timex markup.
    if( timexPipeline == null )
      timexPipeline = getPipeline(true);
    
    // Extract TIMEX3 entities.
    Annotation annotation = SUTimeMain.textToAnnotation(timexPipeline, buildString(words, 0, words.size()), "19980807");

    // Create my Timex objects from Stanford's Timex objects.
    List<Timex> newtimexes = new ArrayList<Timex>();
    for( CoreMap label : annotation.get(TimeAnnotations.TimexAnnotations.class) ) {
      edu.stanford.nlp.time.Timex stanfordTimex = label.get(TimeAnnotations.TimexAnnotation.class);
      org.w3c.dom.Element stanfordElement = stanfordTimex.toXmlElement();
      Timex newtimex = new Timex();
      newtimex.setType(stanfordElement.getAttribute("type"));
      newtimex.setValue(stanfordElement.getAttribute("value"));
      newtimex.setTID("t" + idcounter++);
      newtimex.setText(stanfordElement.getTextContent());
      newtimex.setDocFunction(stanfordElement.getAttribute("functionInDocument"));
      // Stanford Timex starts at index 0 in the sentence, not index 1.
      newtimex.setSpan(label.get(CoreAnnotations.TokenBeginAnnotation.class)+1, label.get(CoreAnnotations.TokenEndAnnotation.class)+1);
      System.out.println("NEW TIMEX FROM STANFORD: " + newtimex);
      newtimexes.add(newtimex);
    }
    return newtimexes;
  }
   */
  
  /**
   * Adapted this from javanlp's SUTimeMain.java.
   * We could better integrate this with the parsing of the sentences, rather than starting from scratch again.
   * Performance gains would basically just avoid tokenizing and POS tagging.

  public AnnotationPipeline getPipeline(boolean tokenize) {
    Properties props = new Properties();
    props.setProperty("sutime.includeRange", "true");
    props.setProperty("sutime.markTimeRanges", "true");
    props.setProperty("sutime.includeNested", "false");
    props.setProperty("sutime.restrictToTimex3", "true");
    props.setProperty("sutime.teRelHeurLevel", RelativeHeuristicLevel.BASIC.name());
    props.setProperty("sutime.rules", "edu/stanford/nlp/time/rules/defs.sutime.txt,edu/stanford/nlp/time/rules/english.sutime.txt,edu/stanford/nlp/time/rules/english.holidays.sutime.txt");
    System.setProperty("pos.model", posTaggerData);
    
    AnnotationPipeline pipeline = new AnnotationPipeline();
    if (tokenize) {
      pipeline.addAnnotator(new PTBTokenizerAnnotator(false));
      pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
    }
    pipeline.addAnnotator(new POSTaggerAnnotator(false));
    pipeline.addAnnotator(new TimeAnnotator("sutime", props));

    return pipeline;
  }
   */  
  
  private String generateInvertibleString(List<HasWord> tokens) {
    StringBuffer buf = new StringBuffer();
    for( HasWord token : tokens ) {
      CoreLabel cl = (CoreLabel)token;
      buf.append('"');
      buf.append(cl.getString(CoreAnnotations.BeforeAnnotation.class));
      buf.append("\" \"");
      buf.append(cl.getString(CoreAnnotations.OriginalTextAnnotation.class));
      buf.append("\" \"");
      buf.append(cl.getString(CoreAnnotations.AfterAnnotation.class));
      buf.append("\"\n");
    }
    return buf.toString();
  }
  
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if( args.length < 1 ) 
      System.out.println("Tempeval3Parser [-output <path>] [-grammar <parser-grammar>] [-timex] -input <tempeval3-TBAQ-dir>");
    else {
      Tempeval3Parser tp3 = new Tempeval3Parser(args);
      tp3.parse();
    }
  }

}
