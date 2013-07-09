package timesieve;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import timesieve.sieves.Sieve;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;
import timesieve.util.TimeSieveProperties;
import timesieve.util.Util;
import timesieve.util.WordNet;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
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
 * @author chambers
 */
public class Main {
	private TextEventClassifier eventClassifier;
	private TimexClassifier timexClassifier;
	public static WordNet wordnet;
	
	SieveDocuments info;
	SieveDocuments infoUnchanged; // for evaluating if TLinks are in the input
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
			info = new SieveDocuments(infopath);
			infoUnchanged = new SieveDocuments(infopath);
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
		runSieves(info);
	}
    
	public void runSieves(SieveDocuments info) {
		List<TLink> currentTLinks = new ArrayList<TLink>();
        
		// Create all the sieves first.
		Sieve sieves[] = createAllSieves(sieveClasses);
		
		// Data
		SieveDocuments docs = Evaluate.getDevSet(info);
		if( runOnTrain ) docs = Evaluate.getTrainSet(info);
		else if( runOnAll ) docs = info;

		// Do each file independently.        
		for( SieveDocument doc : docs.getDocuments() ) {
			System.out.println("Processing " + doc.getDocname() + "...");
			
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
				int numRemoved = removeConflicts(currentTLinks, newLinks);
                
				if( debug ) System.out.println("\t\tRemoved " + numRemoved + " proposed links.");
				
				// Add the good links to our current list.
				currentTLinks.addAll(newLinks);
				
				// Run closure.
				if( newLinks.size() > 0 ) {
					int numClosed = closureExpand(currentTLinks);
					if( debug ) System.out.println("\t\tClosure produced " + numClosed + " links.");
				}
			}
			
			// Add links to InfoFile.
			doc.addTlinks(currentTLinks);
			currentTLinks.clear();
		}
		
		System.out.println("Writing output: " + outpath);
		info.writeToXML(new File(outpath));
	}
    
	
	/**
	 * Test each sieve's precision independently.
	 * Runs each sieve and evaluates its proposed links against the input -info file.
	 * You must have loaded an -info file that has gold TLinks in it.
	 */
	public void runPrecisionGauntlet() {
		if( info == null ) {
			System.out.println("ERROR: no info file given as input for the precision gauntlet.");
			System.exit(1);
		}
        
		// Create all the sieves first.
		Sieve sieves[] = createAllSieves(sieveClasses);
		
		// Data
		SieveDocuments docs = Evaluate.getDevSet(info);
		if( runOnTrain ) docs = Evaluate.getTrainSet(info);
		else if( runOnAll ) docs = info;
		
		// Empty TLink list and counts.
		List<TLink> currentTLinks = new ArrayList<TLink>();
		Counter<String> numCorrect = new ClassicCounter<String>();
		Counter<String> numIncorrect = new ClassicCounter<String>();
		Counter<String> numIncorrectNonVague = new ClassicCounter<String>();
		
		// Loop over documents.
		for( SieveDocument doc : docs.getDocuments() ) {
			System.out.println("doc: " + doc.getDocname());
			List<SieveSentence> sents = doc.getSentences();
			// Gold links.
			List<TLink> goldLinks = doc.getTlinks(true);
			Map<Set<String>, TLink> goldUnorderedIdPairs = new HashMap<Set<String>, TLink>();
			for (TLink tlink : goldLinks) {
				goldUnorderedIdPairs.put(unorderedIdPair(tlink), tlink);
			}
			
			// Loop over sieves.
			for( int xx = 0; xx < sieveClasses.length; xx++ ) {
				Sieve sieve = sieves[xx];
				if( sieve != null ) {
					
					// Run it.
					List<TLink> proposed = sieve.annotate(doc, currentTLinks);
					
                    //					System.out.println("Proposed: " + proposed);
                    //					System.out.println("Gold links: " + goldLinks);
					
					// Check proposed links.
					for( TLink pp : proposed ) {
						Set<String> unorderedIdPair = unorderedIdPair(pp);
						TLink goldLink = goldUnorderedIdPairs.get(unorderedIdPair);
						if( Evaluate.isLinkCorrect(pp, goldLinks) ) {
							numCorrect.incrementCount(sieveClasses[xx]);
						// only mark relations wrong if there's a conflicting human annotation
						// (if there's no human annotation, we don't know if it's right or wrong)
						} else if (goldLink != null) {
							if (!goldLink.getRelation().equals(TLink.Type.VAGUE)) {
								numIncorrectNonVague.incrementCount(sieveClasses[xx]);
							}
							numIncorrect.incrementCount(sieveClasses[xx]);
							if (debug) {
								System.out.printf(
                                                  "%s: %s: Incorrect Link: expected %s, found %s\nDebug info: %s\n",
                                                  sieveClasses[xx], doc.getDocname(), goldUnorderedIdPairs.get(unorderedIdPair), pp,
                                                  getLinkDebugInfo(pp, sents, doc));
								
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
	}
	
	/**
	 * Calls the train() function on all of the listed sieves. 
	 */
	public void trainSieves() {
		if( info == null ) {
			System.out.println("ERROR: no info file given as input for the precision gauntlet.");
			System.exit(1);
		}
        
		// Create all the sieves first.
		Sieve sieves[] = createAllSieves(sieveClasses);
		
		// Data
		SieveDocuments docs = Evaluate.getDevSet(info);
		if( runOnTrain ) docs = Evaluate.getTrainSet(info);
		else if( runOnAll ) docs = info;

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
				TextEvent.Tense e2Tense = e2.getTense();
				TextEvent.Aspect e2Aspect = e2.getAspect();
			// simple display of relation, anchor texts, and anchor ids.
			builder.append(String.format("\n%s[%s-%s], %s[%s-%s]", 
					normId1, e1Tense, e1Aspect, normId2, e2Tense, e2Aspect));
			}
			builder.append(String.format("\n%s\n%s", sents.get(sid1).sentence(), sents.get(sid2).sentence()));
		}
		
		return builder.toString();
	}
    
	/**
	 * DESTRUCTIVE FUNCTION (proposedLinks will be modified)
	 * Removes any links from the proposed list that already have links between the same pairs in currentLinks.
	 * @param currentLinks The list of current "good" links.
	 * @param proposedLinks The list of proposed new links.
	 * @return The number of links removed.
	 */
	private int removeConflicts(List<TLink> currentLinks, List<TLink> proposedLinks) {
		List<TLink> removals = new ArrayList<TLink>();
		for( TLink proposed : proposedLinks ) {
			for( TLink current : currentLinks ) {
				if( current.coversSamePair(proposed) )
					removals.add(proposed);
			}
		}
		
		for( TLink remove : removals )
			proposedLinks.remove(remove);
		
		return removals.size();
	}
	
	/**
	 * DESTRUCTIVE FUNCTION (links may have new TLink objects appended to it)
	 * Run transitive closure and add any inferred links.
	 * @param links The list of TLinks to expand with transitive closure.
	 */
	private int closureExpand(List<TLink> links) {
		List<TLink> newlinks = closure.computeClosure(links);
        
		links.addAll(newlinks);
		return newlinks.size();
	}
	
	/**
	 * Assumes the InfoFile has its text parsed.
	 */
	public void markupAll() {
		markupAll(info);
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
			eventClassifier = new TextEventClassifier(info);
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
	
	private static Set<String> unorderedIdPair(TLink tlink) {
		return new HashSet<String>(Arrays.asList(tlink.getId1(), tlink.getId2()));
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
