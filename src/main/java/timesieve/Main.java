package timesieve;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import timesieve.sieves.Sieve;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;
import timesieve.util.Directory;
import timesieve.util.Ling;
import timesieve.util.TimeSieveProperties;
import timesieve.util.Util;
import timesieve.util.WordNet;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.StringUtils;

/**
 * Controls all Sieve processing including TLink annotating, closure, and the core programming.
 *
 * REQUIREMENTS:
 * - An environment variable JWNL that points to your jwnl_file_properties.xml file.
 *
 * HOW TO RUN:
 * java Main -info <filepath> [-set all|train|dev] gauntlet
 * - Tests the sieves independently and calculates individual precision.
 *
 * java Main -info <filepath> parsed
 * - Run event, time, and TLink extraction. The given infofile contains parses.
 *
 * java Main -info <filepath> [-set all|train|dev]
 * - Runs only the tlink sieve pipeline. Assumes the given infofile has events and times.
 *
 * java Main -info <filepath> [-set all|train|dev] trainall
 * - Runs the train() function on all of the sieves.
 *
 * java Main <file-or-dir> raw
 * - Takes a text file and runs the NLP pipeline, then our event/timex/tlink extraction.
 *
 * @author chambers
 */
public class Main {
    public static final String serializedGrammar = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    
	private TextEventClassifier eventClassifier;
	private TimexClassifier timexClassifier;
	public static WordNet wordnet;
	
	SieveDocuments thedocs;
	SieveDocuments thedocsUnchanged; // for evaluating if TLinks are in the input
	Closure closure;
	String outpath = "sieve-output.xml";
	boolean debug = true;
    
	// If none are true, then it runs on dev
	boolean runOnTrain = true;
	boolean runOnAll = false;
	boolean runOnTest = false;
	
	// List the sieve class names in your desired order.
	private String[] sieveClasses;
    
	
	/**
	 * Constructor: give it the command-line arguments.
	 */
	public Main(String[] args) {
		Properties cmdlineProps = StringUtils.argsToProperties(args);
		String infopath = null;
		
		// Read the properties from disk at the location specified by -Dprops=XXXXX
		try {
			TimeSieveProperties.load();
			// Look for a given pre-processed InfoFile
			infopath = TimeSieveProperties.getString("info");
		} catch (IOException e) { }
        
		// -info on the command line?
		if( cmdlineProps.containsKey("info") )
			infopath = cmdlineProps.getProperty("info");
        
		if( infopath != null ) {
			System.out.println("Checking for infofile at " + infopath);
			thedocs = new SieveDocuments(infopath);
			thedocsUnchanged = new SieveDocuments(infopath);
		}
        
		// -set on the command line?
		if( cmdlineProps.containsKey("set") ) {
			String type = cmdlineProps.getProperty("set");
			System.out.println("CMD SET = " + type);
			if( type.equalsIgnoreCase("train") ) {
				runOnTrain = true; runOnTest = false; runOnAll = false;
			}
			else if( type.equalsIgnoreCase("dev") ) {
				runOnTrain = false; runOnTest = false; runOnAll = false;
			}
			else if( type.equalsIgnoreCase("all") ) {
				runOnTrain = false; runOnTest = false; runOnAll = true;
			}
		}
		
		init();
	}
	
	/**
	 * Empty Constructor.
	 */
	public Main() {
		init();
	}
	
	private void init() {
		// Initialize the transitive closure code.
		try {
			closure = new Closure();
		} catch( IOException ex ) {
			System.out.println("ERROR: couldn't load Closure utility.");
			ex.printStackTrace();
			System.exit(1);
		}
		
		// Load WordNet for any and all sieves.
		wordnet = new WordNet();
        
		// Load the sieve list.
		sieveClasses = loadSieveList();
	}
	
	
	private String[] loadSieveList() {
        String filename = System.getProperty("sieves");
        if( filename == null ) filename = "default.sieves";
        
        System.out.println("Reading sieve list from: " + filename);
        
        List<String> sieveNames = new ArrayList<String>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
            String line;
            while( (line = reader.readLine()) != null ) {
                if( !line.matches("^\\s*$") && !line.matches("^\\s*//.*$") ) {
                	// Remove trailing comments if they exist.
                	if( line.indexOf("//") > -1 )
                		line = line.substring(0, line.indexOf("//"));
                	String name = line.trim();
                	sieveNames.add(name);
                }
            }
            reader.close();
        } catch( Exception ex ) {
            System.out.println("ERROR: no sieve list found");
            ex.printStackTrace();
            System.exit(1);
        }
        
        String[] arr = new String[sieveNames.size()];
        return sieveNames.toArray(arr);
	}
	
	/**
	 * Turns a string class name into an actual Sieve Instance of the class.
	 * @param sieveClass
	 * @return
	 */
	private Sieve createSieveInstance(String sieveClass) {
		try {
			Class<?> c = Class.forName("timesieve.sieves." + sieveClass);
			Sieve sieve = (Sieve)c.newInstance();
			return sieve;
		} catch (InstantiationException e) {
			System.out.println("ERROR: couldn't load sieve: " + sieveClass);
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			System.out.println("ERROR: couldn't load sieve: " + sieveClass);
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			System.out.println("ERROR: couldn't load sieve: " + sieveClass);
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			System.out.println("ERROR: couldn't load sieve: " + sieveClass);
			e.printStackTrace();
		}
		return null;
	}
	
	private Sieve[] createAllSieves(String[] stringClasses) {
		Sieve sieves[] = new Sieve[stringClasses.length];
		for( int xx = 0; xx < stringClasses.length; xx++ ) {
			sieves[xx] = createSieveInstance(stringClasses[xx]);
			System.out.println("Added sieve: " + stringClasses[xx]);
		}
		return sieves;
	}
	
	/**
	 * Assumes the global InfoFile is set.
	 * Run all sieves!! On all documents!!
	 */
	public void runSieves() {
		runSieves(thedocs);
	}
    
	public void runSieves(SieveDocuments info) {
		info.removeAllTLinks(); // because we will be adding our own

		// Start with zero links.
		List<TLink> currentTLinks = new ArrayList<TLink>();
		Map<String,TLink> currentTLinksHash = new HashMap<String,TLink>();
        
		// Create all the sieves first.
		Sieve sieves[] = createAllSieves(sieveClasses);
		
		// Data
		SieveDocuments docs = Evaluate.getDevSet(info);
		if( runOnTrain ) docs = Evaluate.getTrainSet(info);
		else if( runOnAll ) docs = info;
        
		// Do each file independently.
		for( SieveDocument doc : docs.getDocuments() ) {
			System.out.println("Processing " + doc.getDocname() + "...");
//			System.out.println("Number of gold links: " + thedocsUnchanged.getDocument(doc.getDocname()).getTlinks().size());
			
			// Loop over the sieves in order.
			for( int xx = 0; xx < sieves.length; xx++ ) {
				Sieve sieve = sieves[xx];
				if( sieve == null ) continue;
				System.out.println("\tSieve " + sieve.getClass().toString());
                
				// Run this sieve
				List<TLink> newLinks = sieve.annotate(doc, currentTLinks);
				if( debug ) System.out.println("\t\t" + newLinks.size() + " new links.");
//				if( debug ) System.out.println("\t\t" + newLinks);
				
				// Verify the links as non-conflicting.
				int numRemoved = removeConflicts(currentTLinksHash, newLinks);
				if( debug ) System.out.println("\t\tRemoved " + numRemoved + " proposed links.");
//				if( debug ) System.out.println("\t\t" + newLinks);
				
				// Run closure.
				if( newLinks.size() > 0 ) {
					// Add the good links to our current list.
					addProposedToCurrentList(sieveClasses[xx], newLinks, currentTLinks, currentTLinksHash);//currentTLinks.addAll(newLinks);
					// Closure
					List<TLink> closedLinks = closureExpand(currentTLinks, currentTLinksHash);
					if( debug ) System.out.println("\t\tClosure produced " + closedLinks.size() + " links.");
//					if( debug ) System.out.println("\t\tclosed=" + closedLinks);
				}
				if( debug ) System.out.println("\t\tDoc now has " + currentTLinks.size() + " links.");
			}
			
			// Add links to InfoFile.
			doc.addTlinks(currentTLinks);
			if( debug ) System.out.println("Adding links: " + currentTLinks);
			currentTLinks.clear();
			currentTLinksHash.clear();
		}
		
		System.out.println("Writing output: " + outpath);
		docs.writeToXML(new File(outpath));
		
		// Evaluate it if the input file had tlinks in it.
		Evaluate.evaluate(thedocsUnchanged, docs);
	}
    
	/**
	 * Test each sieve's precision independently.
	 * Runs each sieve and evaluates its proposed links against the input -info file.
	 * You must have loaded an -info file that has gold TLinks in it.
	 */
	public void runPrecisionGauntlet() {
		if( thedocs == null ) {
			System.out.println("ERROR: no info file given as input for the precision gauntlet.");
			System.exit(1);
		}
		thedocs.removeAllTLinks(); // because we will be adding our own
		
		// Create all the sieves first.
		Sieve sieves[] = createAllSieves(sieveClasses);
		
		// Data
		SieveDocuments docs = Evaluate.getDevSet(thedocs);
		if( runOnTrain ) docs = Evaluate.getTrainSet(thedocs);
		else if( runOnAll ) docs = thedocs;
		
		// Empty TLink list and counts.
		List<TLink> currentTLinks = new ArrayList<TLink>();
		Counter<String> numCorrect = new ClassicCounter<String>();
		Counter<String> numIncorrect = new ClassicCounter<String>();
		Counter<String> numIncorrectNonVague = new ClassicCounter<String>();
		Map<String,Counter<TLink.Type>> goldLabelCounts = new HashMap<String,Counter<TLink.Type>>();
		for( String sc : sieveClasses ) goldLabelCounts.put(sc, new ClassicCounter<TLink.Type>());
		Map<String,Counter<String>> guessCounts = new HashMap<String,Counter<String>>();
		for( String sc : sieveClasses ) guessCounts.put(sc, new ClassicCounter<String>());
        
		
		// Loop over documents.
		for( SieveDocument doc : docs.getDocuments() ) {
			System.out.println("doc: " + doc.getDocname());
			List<SieveSentence> sents = doc.getSentences();
			// Gold links.
			List<TLink> goldLinks = thedocsUnchanged.getDocument(doc.getDocname()).getTlinks(true);
			Map<String, TLink> goldOrderedIdPairs = new HashMap<String, TLink>();
			for (TLink tlink : goldLinks) {
//				System.out.println("adding gold: " + tlink + " order=" + TLink.orderedIdPair(tlink));
				goldOrderedIdPairs.put(TLink.orderedIdPair(tlink), tlink);
			}
            
			// Loop over sieves.
			for( int xx = 0; xx < sieveClasses.length; xx++ ) {
				String sieveName = sieveClasses[xx];
				Sieve sieve = sieves[xx];
				if( sieve != null ) {
                    
					// Run it.
					List<TLink> proposed = sieve.annotate(doc, currentTLinks);
					if( debug ) System.out.println(sieveName + " proposed: " + proposed);

                    
					// Check proposed links.
					if( proposed != null ) {
						for( TLink pp : proposed ) {
							String orderedIdPair = TLink.orderedIdPair(pp);
//							System.out.println("looking up gold for " + pp + " order=" + TLink.orderedIdPair(pp));
							TLink goldLink = goldOrderedIdPairs.get(orderedIdPair);

							if( goldLink != null ) {
								guessCounts.get(sieveName).incrementCount(goldLink.getOrderedRelation()+" "+pp.getOrderedRelation());
								goldLabelCounts.get(sieveName).incrementCount(goldLink.getRelation());
							}

							// Guessed link is correct!
							if( Evaluate.isLinkCorrect(pp, goldLinks) ) {
								numCorrect.incrementCount(sieveClasses[xx]);
							} 
							// Gold and guessed link disagree!
							// Only mark relations wrong if there's a conflicting human annotation.
							// (if there's no human annotation, we don't know if it's right or wrong)
							else if (goldLink != null) {
								if (!goldLink.getRelation().equals(TLink.Type.VAGUE)) {
									numIncorrectNonVague.incrementCount(sieveClasses[xx]);
								}
								numIncorrect.incrementCount(sieveClasses[xx]);
								if (debug) {
									System.out.printf("%s: %s: Incorrect Link: expected %s, found %s\nDebug info: %s\n",
											sieveClasses[xx], doc.getDocname(), goldOrderedIdPairs.get(orderedIdPair), pp,
											getLinkDebugInfo(pp, sents, doc));
								}
							}
							// No gold link.
							else {
								System.out.println("No gold link (" + sieveName + "): " + pp);
							}
						}
					}
				}
			}
		}
		
		// Calculate precision and output the sorted sieves.
		Counter<String> precision = new ClassicCounter<String>();
		Counter<String> precisionNonVague = new ClassicCounter<String>();
		for (String sieveClass : sieveClasses) {
			double correct = numCorrect.getCount(sieveClass);
			double incorrect = numIncorrect.getCount(sieveClass);
			double incorrectNonVague = numIncorrectNonVague.getCount(sieveClass);
			double total = correct + incorrect;
			precision.incrementCount(sieveClass, total > 0 ? correct / total : 0.0);
			double totalNonVague = correct + incorrectNonVague;
			precisionNonVague.incrementCount(sieveClass, totalNonVague > 0 ? correct / totalNonVague : 0.0);
		}
		List<String> sortedKeys = Util.sortCounterKeys(precision);
		for( String key : sortedKeys ) {
			double correct = numCorrect.getCount(key);
			int numtabs = Math.max(1, 4 - key.length() / 8);
			System.out.print(key);
			for( int tt = 0; tt < numtabs; tt++ ) System.out.print("\t");
			System.out.printf("p=%.2f\t%.0f of %.0f\tNon-VAGUE:\tp=%.2f\t%.0f of %.0f\n",
                              precision.getCount(key), correct, correct + numIncorrect.getCount(key),
                              precisionNonVague.getCount(key), correct, correct + numIncorrectNonVague.getCount(key));
		}
		for( String key : sortedKeys ) {
			System.out.println("**** " + key + "****");
			Evaluate.printBaseline(goldLabelCounts.get(key));			
			Evaluate.confusionMatrix(guessCounts.get(key));
		}
	}

	
	
	/**
	 * Calls the train() function on all of the listed sieves.
	 */
	public void trainSieves() {
		if( thedocs == null ) {
			System.out.println("ERROR: no info file given as input for the precision gauntlet.");
			System.exit(1);
		}
        
		// Create all the sieves first.
		Sieve sieves[] = createAllSieves(sieveClasses);
		
		// Data
		SieveDocuments docs = Evaluate.getDevSet(thedocs);
		if( runOnTrain ) docs = Evaluate.getTrainSet(thedocs);
		else if( runOnAll ) docs = thedocs;
        
		// Train them!
		for( Sieve sieve : sieves ) {
			if( debug ) System.out.println("Training sieve: " + sieve.getClass().toString());
			sieve.train(docs);
		}
	}
	
	private String getLinkDebugInfo(TLink link, List<SieveSentence> sents, SieveDocument doc) {
		StringBuilder builder = new StringBuilder();
		
		if (link instanceof TimeTimeLink) {
			TimeTimeLink ttLink = (TimeTimeLink)link;
			Timex t1 = doc.getTimexByTid(ttLink.getId1());
			Timex t2 = doc.getTimexByTid(ttLink.getId2());
			
			builder.append("Time-Time " + ttLink.getRelation() + "\t");
			builder.append(t1.getTid() + ": " + t1.getValue() + " (" + t1.getText() + ")\t");
			builder.append(t2.getTid() + ": " + t2.getValue() + " (" + t2.getText() + ")");
		} else {
			// complex code because Timex and TextEvent don't share a common parent or common APIs
			String id1 = link.getId1();
			Timex t1 = doc.getTimexByTid(id1);
			TextEvent e1 = doc.getEventByEiid(id1);
			String normId1 = t1 != null ? t1.getTid() : e1.getId();
			String text1 = t1 != null ? t1.getText() : e1.getString();
			int sid1 = t1 != null ? t1.getSid() : e1.getSid();
			String id2 = link.getId2();
			Timex t2 = doc.getTimexByTid(id2);
			TextEvent e2 = doc.getEventByEiid(id2);
			String normId2 = t2 != null ? t2.getTid() : e2.getId();
			String text2 = t2 != null ? t2.getText() : e2.getString();
			int sid2 = t2 != null ? t2.getSid() : e2.getSid();
			builder.append(String.format("%s(%s[%s],%s[%s])", link.getRelation(),
                                         text1, normId1, text2, normId2));
			if (e1 != null && e2 != null) {
				TextEvent.Tense e1Tense = e1.getTense();
				TextEvent.Aspect e1Aspect = e1.getAspect();
				TextEvent.Class e1Class = e1.getTheClass();
				TextEvent.Tense e2Tense = e2.getTense();
				TextEvent.Aspect e2Aspect = e2.getAspect();
				TextEvent.Class e2Class = e2.getTheClass();
                // simple display of relation, anchor texts, and anchor ids.
                builder.append(String.format("\n%s[%s-%s-%s], %s[%s-%s-%s]",
                                             normId1, e1Tense, e1Aspect, e1Class, normId2, e2Tense, e2Aspect, e2Class));
			}
			builder.append(String.format("\n%s\n%s", sents.get(sid1).sentence(), sents.get(sid2).sentence()));
		}
		
		return builder.toString();
	}
    
	private void addProposedToCurrentList(String sieveName, List<TLink> proposed, List<TLink> current, Map<String,TLink> currentHash) {
		for( TLink newlink : proposed ) {
			current.add(newlink);
			currentHash.put(newlink.getId1()+newlink.getId2(), newlink);
			currentHash.put(newlink.getId2()+newlink.getId1(), newlink);
			newlink.setOrigin(sieveName);
		}
	}

	/**
	 * DESTRUCTIVE FUNCTION (proposedLinks will be modified)
	 * Removes any links from the proposed list that already have links between the same pairs in currentLinks.
	 * @param currentLinks The list of current "good" links.
	 * @param proposedLinks The list of proposed new links.
	 * @return The number of links removed.
	 */
	private int removeConflicts(Map<String,TLink> currentLinksHash, List<TLink> proposedLinks) {
		List<TLink> removals = new ArrayList<TLink>();
		for( TLink proposed : proposedLinks ) {

			// Make sure we have a valid link with 2 events!
			if( proposed.getId1() == null || proposed.getId2() == null || 
					proposed.getId1().length() == 0 || proposed.getId2().length() == 0 ) {
				removals.add(proposed);
				System.out.println("WARNING (proposed an invalid link): " + proposed);
				continue;
			}

			// Look for a current link that conflicts with this proposed link.
			TLink current = currentLinksHash.get(proposed.getId1()+proposed.getId2());
			if( current != null && current.coversSamePair(proposed) )
				removals.add(proposed);
		}

		for( TLink remove : removals )
			proposedLinks.remove(remove);

		return removals.size();
	}
	
	/**
	 * DESTRUCTIVE FUNCTION (links may have new TLink objects appended to it)
	 * Run transitive closure and add any inferred links.
	 * @param links The list of TLinks to expand with transitive closure.
	 * @return The list of new links from closure (these are already added to the given lists)
	 */
	private List<TLink> closureExpand(List<TLink> links, Map<String,TLink> linksHash) {
		List<TLink> newlinks = closure.computeClosure(links, false);
		addProposedToCurrentList("closure", newlinks, links, linksHash);
		return newlinks;
	}
    
	/**
	 * Given a path to a directory, assumes every file in the directory is a text file with no
	 * XML markup. This function will treat each text file as a separate document and perform
	 * full event, time, and tlink markup.
	 * @param dirpath Directory of text files.
	 */
	public SieveDocuments markupRawTextDir(String dirpath) {
		// Initialize the parser.
		LexicalizedParser parser = Ling.createParser(serializedGrammar);
		if( parser == null ) {
			System.out.println("Failed to create parser from " + serializedGrammar);
			System.exit(1);
		}
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
		GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

		SieveDocuments docs = new SieveDocuments();
		for( String file : Directory.getFilesSorted(dirpath) ) {
			String path = dirpath + File.separator + file;
			SieveDocument doc = Tempeval3Parser.rawTextFileToParsed(path, parser, gsf);
			docs.addDocument(doc);
		}

		// Markup events, times, and tlinks.
		markupAll(docs);
		return docs;
	}
	
	/**
	 * Assumes the path is to a text-only file with no XML markup.
	 * @param filepath Path to the text file.
	 * @return A SieveDocuments instance with one SieveDocument in it, namely, the marked up file.
	 */
	public SieveDocuments markupRawTextFile(String filepath) {
		// Initialize the parser.
		LexicalizedParser parser = Ling.createParser(serializedGrammar);
		if( parser == null ) {
			System.out.println("Failed to create parser from " + serializedGrammar);
			System.exit(1);
		}
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
		GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

		// Parse the file.
		SieveDocument doc = Tempeval3Parser.rawTextFileToParsed(filepath, parser, gsf);
		SieveDocuments docs = new SieveDocuments();
		docs.addDocument(doc);

		// Markup events, times, and tlinks.
		markupAll(docs);
		return docs;
	}

	public void markupRawText(String path) {
		File file = new File(path);
		SieveDocuments docs = null;
		if( file.isDirectory() ) docs = markupRawTextDir(path);
		else docs = markupRawTextFile(path);
		
        // Output the InfoFile with the events in it.
		if( docs != null ) {
			String outpath = path + ".info.xml";
			if( file.isDirectory() ) outpath = Directory.lastSubdirectory(path) + "-dir.info.xml";
			
			docs.writeToXML(outpath);
			System.out.println("Created " + outpath);
		}
		else System.out.println("Couldn't create anything from: " + path);
	}
	
	/**
	 * Assumes the InfoFile has its text parsed.
	 */
	public void markupAll() {
		markupAll(thedocs);
	}
	public void markupAll(SieveDocuments info) {
		markupEvents(info);
		markupTimexes(info);
		runSieves(info);
	}
	
	/**
	 * Assumes the SieveDocuments has its text parsed.
	 */
	public void markupEvents(SieveDocuments info) {
		if( eventClassifier == null ) {
			eventClassifier = new TextEventClassifier(info, wordnet);
			eventClassifier.loadClassifiers();
		}
		eventClassifier.extractEvents();
	}
	
	/**
	 * Assumes the SieveDocuments has its text parsed.
	 */
	public void markupTimexes(SieveDocuments info) {
		if( timexClassifier == null )
			timexClassifier = new TimexClassifier(info);
		timexClassifier.markupTimex3();
	}
	
	/**
	 * Main. Multiple run modes:
	 *
	 * main -info <filepath> gauntlet
	 * - Tests the sieves independently and calculates individual precision.
	 *
	 * main -info <filepath>
	 * - Runs the sieve pipeline.
	 *
	 */
	public static void main(String[] args) {
        //		Properties props = StringUtils.argsToProperties(args);
		Main main = new Main(args);
		
		// Test each sieve's precision independently.
		// Runs each sieve and evaluates its proposed links against the input -info file.
		if( args.length > 0 && args[args.length-1].equalsIgnoreCase("gauntlet") ) {
			main.runPrecisionGauntlet();
		}
		
		// The given SieveDocuments only has text and parses, so extract events/times first.
		else if( args.length > 0 && args[args.length-1].equalsIgnoreCase("parsed") ) {
			main.markupAll();
		}
        
		// Give a text file or a directory of text files. Parses and marks it up.
		else if( args.length > 1 && args[args.length-1].equalsIgnoreCase("raw") ) {
			main.markupRawText(args[args.length-2]);
		}
        
		// The given SieveDocuments only has text and parses, so extract events/times first.
		else if( args.length > 0 && args[args.length-1].equalsIgnoreCase("trainall") ) {
			main.trainSieves();
		}
        
		// Run just the TLink Sieve pipeline. Events/Timexes already in the given SieveDocuments.
		else {
			main.runSieves();
		}
	}
}