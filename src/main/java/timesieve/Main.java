package timesieve;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import timesieve.sieves.Sieve;
import timesieve.tlink.TLink;

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
	String[] sieveClasses = { 
			"QuarterSieveReporting"	
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
				try {
					System.out.println("\tSieve " + sieveClass);
					
					// Create this sieve.
					Class c = Class.forName("timesieve.sieves." + sieveClass);
					Sieve sieve = (Sieve)c.newInstance();

					// Run this sieve
					List<TLink> newLinks = sieve.annotate(info, docname, currentTLinks);
					
					// Verify the links as non-conflicting.
					removeConflicts(currentTLinks, newLinks);
					
					// Add the good links to our current list.
					currentTLinks.addAll(newLinks);

				} catch (InstantiationException e) {
					System.out.println("ERROR: couldn't load sieve: " + sieveClass);
					e.printStackTrace();
					break;
				} catch (IllegalAccessException e) {
					System.out.println("ERROR: couldn't load sieve: " + sieveClass);
					e.printStackTrace();
					break;
				} catch (IllegalArgumentException e) {
					System.out.println("ERROR: couldn't load sieve: " + sieveClass);
					e.printStackTrace();
					break;
				} catch (ClassNotFoundException e) {
					System.out.println("ERROR: couldn't load sieve: " + sieveClass);
					e.printStackTrace();
					break;
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
		
		main.runSieves();
	}
}
