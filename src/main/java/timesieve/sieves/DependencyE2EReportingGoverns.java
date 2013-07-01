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
 * This sieve deals with event pairs in a dependency relationship,
 * when one of the verbs is a reporting verb.
 * 
 * 
 * 
 * @author cassidy
 */
public class DependencyE2EReportingGoverns implements Sieve {
	public boolean debug = false;
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// For each SieveSentence in the SieveDocument, get its events and dependencies.
		// Apply sieve criteria to pairs of events that are in a dep relation in addPairsEvents
		int sid = 0;
		for( SieveSentence sent : doc.getSentences() ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
			}
			proposed.addAll(applySieve(sent.events(), sent.getDeps()));
			sid++;
		}

		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
	}

	/**
	 * For each event-event pair such that one event governs the other in a dep relation,
	 * classify as follows: if the governor is a past tense reporting verb...
	 * label the pair AFTER if the dependent is in the past tense, an occurrence, and not
	 * the progressive aspect. If the dependent is in the future tense, label BEFORE
	 * regardless of its class or aspect.
	 */
	private List<TLink> applySieve(List<TextEvent> events, List<TypedDependency> deps) {
		List<TLink> proposed = new ArrayList<TLink>();
		
		for( int xx = 0; xx < events.size(); xx++ ) {
			TextEvent e1 = events.get(xx);
			for( int yy = xx+1; yy < events.size(); yy++ ) {
				TextEvent e2 = events.get(yy);
				// We have a pair of event e1 and e2.
				// check a given TypedDependency involves both events,
				// and if so check event properties against criteria.
				for (TypedDependency td : deps) {
					if (e1.getIndex() == td.gov().index() && e2.getIndex() == td.dep().index()) {
						classifyEventPair(e1, e2, proposed);
					}
					else if (e2.getIndex() == td.gov().index() && e1.getIndex() == td.dep().index()) {
						classifyEventPair(e2, e1, proposed);
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
	
	private void classifyEventPair(TextEvent eGov, TextEvent eDep, List<TLink> proposed ) {
		if (eGov.getTheClass() == TextEvent.Class.REPORTING && eGov.getTense() == TextEvent.Tense.PAST) {
			if (eDep.getTense() == TextEvent.Tense.PAST && eDep.getTheClass() == TextEvent.Class.OCCURRENCE
					&& eDep.getAspect() != TextEvent.Aspect.PROGRESSIVE) {
						proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER));	
			}
			else if (eDep.getTense().equals("FUTURE")) {
				proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE));
		}
			// Add anything here?
			else
			{}
		}
		//else if (eDep.getTense().equals("FUTURE")) {
			//proposed.add(new EventEventLink(eGov.eiid(), eDep.eiid(), TLink.TYPE.BEFORE));
		//}
		// Add anything here?
		else
		{}

		
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
