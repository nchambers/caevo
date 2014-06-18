package caevo.sieves;

import java.util.ArrayList;
import java.util.List;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.tlink.EventEventLink;
import caevo.tlink.TLink;
import caevo.util.TreeOperator;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * 
 * 
 * @author cassidy
 */
public class Dependencies182 implements Sieve {
	public boolean debug = false;
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument info, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence();
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Make BEFORE links between all intra-sentence pairs.
		int sid = 0;
		for( SieveSentence sent : info.getSentences() ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + info.getDocname() + " sentence " + sent.sentence());
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
			for( int yy = xx+1; yy < events.size(); yy++ ) {
				for (TypedDependency td : deps) {
					if (events.get(xx).getIndex() == td.gov().index() && events.get(yy).getIndex() == td.dep().index() 
						&& events.get(xx).getTheClass() == TextEvent.Class.STATE && events.get(yy).getTheClass() == TextEvent.Class.STATE) {
						proposed.add(new EventEventLink(events.get(xx).getEiid(), events.get(yy).getEiid(), TLink.Type.SIMULTANEOUS));
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
