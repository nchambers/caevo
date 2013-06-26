package timesieve;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import timesieve.sieves.Sieve;
import timesieve.tlink.TLink;
import timesieve.util.Util;

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
 * java Main -info <filepath> gauntlet
 * - Tests the sieves independently and calculates individual precision.
 * 
 * java Main -info <filepath> parsed
 * - Run event, time, and TLink extraction. The given infofile contains parses.
 * 
 * java Main -info <filepath>  
 * - Runs only the tlink sieve pipeline. Assumes the given infofile has events and times.
 * 
 * @author chambers
 */
public class Main {
	TextEventClassifier eventClassifier;
	TimexClassifier timexClassifier;
	String wordnetPath; // to the jwnl_file_properties.xml file
	
	InfoFile info;
	Closure closure;
	String outpath = "sieve-output.xml";
	boolean debug = true;
	
	// List the sieve class names in your desired order.
	public final static String[] sieveClasses = {
		  "DependencyE2EReportingGoverns",
		  //"DependencyAnalyze",
		  "Dependencies182",
			"WordFeatures5",
			"AllVagueSieve",
			"QuarterSieveReporting",
			"StupidSieve",
			"AdjacentVerbTimex",
			"ReichenbachDG13_1",
			"ReichenbachDG13_2",
			"ReichenbachDG13_3",
			"ReichenbachDG13_4",
			"WordFeatures64",
			"WordNet209",
			"ReportingCreationDay"
	};

	/**
	 * Constructor: give it the command-line arguments.
	 */
	public Main(String[] args) {
		Properties props = StringUtils.argsToProperties(args);

		if( props.containsKey("info") ) {
			System.out.println("Checking for infofile at " + props.getProperty("info"));
			info = new InfoFile(props.getProperty("info"));
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

		// Load WordNet.
		String path = System.getenv("JWNL");
		if( path == null ) {
			System.out.println("ERROR: couldn't find JWNL xml properties file: " + path);
		} else wordnetPath = path;
		
		System.out.println("WordNet path:\t\t" + wordnetPath);
	}
	
	/**
	 * Turns a string class name into an actual Sieve Instance of the class.
	 * @param sieveClass
	 * @return
	 */
	private Sieve createSieveInstance(String sieveClass) {
		try {
			Class c = Class.forName("timesieve.sieves." + sieveClass);
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

	public void runSieves(InfoFile info) {
		List<TLink> currentTLinks = new ArrayList<TLink>();

		// Create all the sieves first.
		Sieve sieves[] = createAllSieves(sieveClasses);
		
		// Do each file independently.
		for( String docname : info.getFiles() ) {
			System.out.println("Processing " + docname + "...");
			
			// Loop over the sieves in order.
			for( int xx = 0; xx < sieves.length; xx++ ) {
				Sieve sieve = sieves[xx];
				if( sieve == null ) continue;
				System.out.println("\tSieve " + sieve.getClass().toString());

				// Run this sieve
				List<TLink> newLinks = sieve.annotate(info, docname, currentTLinks);

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
			info.addTlinks(docname, currentTLinks);
			currentTLinks.clear();
		}
		
		System.out.println("Writing output: " + outpath);
		info.writeToFile(new File(outpath));
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
		
		// Empty TLink list and counts.
		List<TLink> currentTLinks = new ArrayList<TLink>();
		Counter<String> numCorrect = new ClassicCounter<String>();
		Counter<String> numIncorrect = new ClassicCounter<String>();
		
		// Loop over documents.
		for( String docname : info.getFiles() ) {
			System.out.println("doc: " + docname);
			
			// Gold links.
			List<TLink> goldLinks = info.getTlinks(docname, true);
			
			// Loop over sieves.
			for( int xx = 0; xx < sieveClasses.length; xx++ ) {
				Sieve sieve = sieves[xx];
				if( sieve != null ) {
					
					// Run it.
					List<TLink> proposed = sieve.annotate(info, docname, currentTLinks);
					
//					System.out.println("Proposed: " + proposed);
//					System.out.println("Gold links: " + goldLinks);
					
					// Check proposed links.
					for( TLink pp : proposed ) {
						if( Evaluate.isLinkCorrect(pp, goldLinks) )
							numCorrect.incrementCount(sieveClasses[xx]);
						else
							numIncorrect.incrementCount(sieveClasses[xx]);
					}					
				}
			}
		}
		
		// Calculate precision and output the sorted sieves.
		Counter<String> precision = new ClassicCounter<String>();
		for( int xx = 0; xx < sieveClasses.length; xx++ ) {
			double total = (numCorrect.getCount(sieveClasses[xx]) + numIncorrect.getCount(sieveClasses[xx]));
			precision.incrementCount(sieveClasses[xx], (total > 0 ? numCorrect.getCount(sieveClasses[xx]) / total : 0.0));
		}
		List<String> sortedKeys = Util.sortCounterKeys(precision);
		for( String key : sortedKeys ) {
			double total = (numCorrect.getCount(key) + numIncorrect.getCount(key));
			int numtabs = Math.max(1, 4 - key.length() / 8);
			System.out.print(key);
			for( int tt = 0; tt < numtabs; tt++ ) System.out.print("\t");
			System.out.printf("p=%.2f\t%.0f of %.0f\n", precision.getCount(key), numCorrect.getCount(key), total);
		}
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
	public void markupAll(InfoFile info) {
		markupEvents(info);
		markupTimexes(info);
		runSieves(info);
	}
	
	/**
	 * Assumes the InfoFile has its text parsed.
	 */
	public void markupEvents(InfoFile info) {
		if( eventClassifier == null )
			eventClassifier = new TextEventClassifier(info, wordnetPath);
		eventClassifier.extractEvents();
	}
	
	/**
	 * Assumes the InfoFile has its text parsed.
	 */
	public void markupTimexes(InfoFile info) {
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
		
		// The given InfoFile only has text and parses, so extract events/times first.
		else if( args.length > 0 && args[args.length-1].equalsIgnoreCase("parsed") ) {
			main.markupAll();
		}
		
		// Run just the TLink Sieve pipeline. Events/Timexes already in the given InfoFile.
		else {
			main.runSieves();
		}
	}
}
