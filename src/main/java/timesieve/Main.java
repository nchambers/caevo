package timesieve;

import java.io.File;
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
 * Controls all Sieve processing including tlink annotating, closure, and the core programming.
 * 
 * java Main -info <filepath>  
 * 
 * @author chambers
 */
public class Main {
	InfoFile info;
	String outpath = "sieve-output.xml";
	
	// List the sieve class names in your desired order.
	public final static String[] sieveClasses = { 
			"QuarterSieveReporting",
			"AllVagueSieve",
			"StupidSieve"
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
	
	/**
	 * Assumes the global InfoFile is set.
	 * Run all sieves!! On all documents!!
	 */
	public void runSieves() {
		List<TLink> currentTLinks = new ArrayList<TLink>();
		
		// Do each file independently.
		for( String docname : info.getFiles() ) {
			System.out.println("Processing " + docname + "...");
			
			// Loop over the sieves in order.
			for( String sieveClass : sieveClasses ) {
					System.out.println("\tSieve " + sieveClass);
					
					// Create this sieve.
					Sieve sieve = createSieveInstance(sieveClass);
					if( sieve == null ) break;
					
					// Run this sieve
					List<TLink> newLinks = sieve.annotate(info, docname, currentTLinks);
					
					// Verify the links as non-conflicting.
					removeConflicts(currentTLinks, newLinks);
					
					// Add the good links to our current list.
					currentTLinks.addAll(newLinks);
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
		Sieve sieves[] = new Sieve[sieveClasses.length];
		for( int xx = 0; xx < sieveClasses.length; xx++ )
			sieves[xx] = createSieveInstance(sieveClasses[xx]);
		
		Counter<String> numCorrect = new ClassicCounter<String>();
		Counter<String> numIncorrect = new ClassicCounter<String>();
		
		// Empty tlink list.
		List<TLink> currentTLinks = new ArrayList<TLink>();
		
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
	 * Uses transitive closure rules to identify links that were proposed, but lead to conflicts.
	 * Removes any links from the proposed list that conflict.
	 * @param currentLinks The list of current "good" links.
	 * @param proposedLinks The list of proposed new links.
	 */
	private void removeConflicts(List<TLink> currentLinks, List<TLink> proposedLinks) {
		// TODO
	}
	
	
	public static void main(String[] args) {
		Properties props = StringUtils.argsToProperties(args);
		Main main = new Main(args);
		
		// Test each sieve's precision independently.
		// Runs each sieve and evaluates its proposed links against the input -info file.
		if( args.length > 0 && args[args.length-1].equalsIgnoreCase("gauntlet") ) {
			main.runPrecisionGauntlet();
		}
		
		// Run the normal sieve pipeline. 
		else {
			main.runSieves();
		}
	}
}
