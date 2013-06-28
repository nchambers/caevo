package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.TypedDependency;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;

import timesieve.util.TreeOperator;

/**
 * JUST AN EXAMPLE
 * Stupid sieve that shows how to access basic data structures.
 * It generates BEFORE links between all intra-sentence pairs.
 * 
 * @author chambers
 */
public class DependencyE2EReportingGoverns implements Sieve {
	public boolean debug = false;
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = doc.getEventsBySentence();
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Make BEFORE links between all intra-sentence pairs.
		int sid = 0;
		for( SieveSentence sent : doc.getSentences() ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
			}
			proposed.addAll(allPairsEvents(sent.events(), sent.getDeps()));
			sid++;
		}

		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
	}

	/**
	 * All pairs of events are BEFORE relations based on text order!
	 */
	private List<TLink> allPairsEvents(List<TextEvent> events, List<TypedDependency> deps) {
		List<TLink> proposed = new ArrayList<TLink>();

		for( int xx = 0; xx < events.size(); xx++ ) {
			TextEvent e1 = events.get(xx);
			for( int yy = xx+1; yy < events.size(); yy++ ) {
				TextEvent e2 = events.get(yy);
				for (TypedDependency td : deps) {
					if (e1.index() == td.gov().index() && e2.index() == td.dep().index()
						 && e1.getTheClass().equals("REPORTING") && e1.getTense().equals("PAST")) {
							if (e2.getTense().equals("PAST") && e2.getTheClass().equals("OCCURRENCE")
									&& !e2.getAspect().equals("PROGRESSIVE")) {
							proposed.add(new EventEventLink(events.get(xx).eiid(), events.get(yy).eiid(), TLink.TYPE.AFTER));	
							}
							else if (e2.getTense().equals("FUTURE")) {
								proposed.add(new EventEventLink(events.get(xx).eiid(), events.get(yy).eiid(), TLink.TYPE.BEFORE));	
							}
						}
					}
				}
			}
		
		if (debug == true) {
			System.out.println("events: " + events);
			System.out.println("created tlinks: " + proposed);
		}
		return proposed;
	}
	
	private ArrayList<TypedDependency> getAllTypedDependencies(String dependencyParseString){
		ArrayList<TypedDependency> deps = new ArrayList<TypedDependency>();
		
		if (dependencyParseString.isEmpty())
			return deps;
		
		String[] depStrings = dependencyParseString.split("\\n");
		for (int i = 0; i < depStrings.length; i++){
			TypedDependency td = TreeOperator.stringParensToDependency(depStrings[i]);
			deps.add(td);
		}
		return deps;
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
