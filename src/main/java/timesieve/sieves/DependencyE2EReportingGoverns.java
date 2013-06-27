package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.DependencyFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;

import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;

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
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence(docname);
		// get all dependency parse strings
		List<String> allDependencyStrings = info.getDependencies(docname);
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Make BEFORE links between all intra-sentence pairs.
		int sid = 0;
		for( Sentence sent : info.getSentences(docname) ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + docname + " sentence " + sent.sentence());
			}
			String dependencyParseString = allDependencyStrings.get(sid);
			ArrayList<TypedDependency> deps = getAllTypedDependencies(dependencyParseString);
			proposed.addAll(allPairsEvents(allEvents.get(sid), deps, docname, sent));
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
	private List<TLink> allPairsEvents(List<TextEvent> events, ArrayList<TypedDependency> deps, String docname, Sentence sent) {
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
	public void train(InfoFile trainingInfo) {
		// no training
	}

}
