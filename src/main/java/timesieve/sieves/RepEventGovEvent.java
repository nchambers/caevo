package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.TypedDependency;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;

import timesieve.util.TimeSieveProperties;
import timesieve.util.TreeOperator;

/**
 * This sieve deals with event pairs in a dependency relationship,
 * when one of the verbs is a reporting verb.
 * 
 * 
 * 
 * @author cassidy
 */
public class RepEventGovEvent implements Sieve {
	public boolean debug = false;
	
	// Property values 
	private boolean caseGovPast = true;
	private boolean caseGovPres = true;
	private boolean caseGovFuture = true;
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// PROPERTIES CODE
		try {
			TimeSieveProperties.load();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			caseGovPast = TimeSieveProperties.getBoolean("RepEventGovEvent.caseGovPast", true);
			caseGovPres = TimeSieveProperties.getBoolean("RepEventGovEvent.caseGovPres", true);
			caseGovFuture = TimeSieveProperties.getBoolean("RepEventGovEvent.caseGovFuture", true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//Sieve Code
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// for each sentence, send its events and dependencies to applySieve, which
		// finds pairs of the form dep(reporting_verb, event) and checks them against
		// criteria in terms of additional properties of both events
		for( SieveSentence sent : doc.getSentences() ) {
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
			}
			proposed.addAll(applySieve(sent.events(), sent.getDeps()));
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
					// if e1 governs e2
					if (e1.getIndex() == td.gov().index() && e2.getIndex() == td.dep().index() 
							&& e1.getTheClass().equals("REPORTING")) {
						classifyEventPair(e1, e2, proposed);
					}
					// if e2 governs e1
					else if (e2.getIndex() == td.gov().index() && e1.getIndex() == td.dep().index()
									 && e2.getTheClass().equals("REPORTING")) {
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
		TextEvent.Tense eGovTense = eGov.getTense();
		TextEvent.Tense eDepTense = eDep.getTense();
		TextEvent.Class eDepClass = eDep.getTheClass();
		TextEvent.Aspect eDepAspect = eDep.getAspect();
		
		// caseGovPast
		if (caseGovPast && eGovTense == TextEvent.Tense.PAST) {
			if (eDepTense == TextEvent.Tense.PAST && eDepClass == TextEvent.Class.OCCURRENCE
					&& eDepAspect != TextEvent.Aspect.PROGRESSIVE) {
						proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER));	
			}
			else if (eDepTense.equals("FUTURE")) {
				proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE));
		}
			// Add anything here?
			else
			{}
		}
		
		// caseGovPres
		else if (caseGovPres && eGovTense.equals("PRESENT")) {
			if (eDepTense.equals("PAST") && eDepClass.equals("OCCURRENCE")) {
				if (!eDepAspect.equals("PROGRESSIVE")) {
						proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER));
				}
			}
			else if (eDepTense.equals("PRESENT")) {
				proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED));
			}
			else if (eDepTense.equals("FUTURE")) {
					proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE));
				}
		}
		
		// caseGovFuture
		else if (caseGovFuture && eGovTense.equals("FUTURE")) {
			if (eDepTense.equals("PAST") && eDepClass.equals("OCCURRENCE")
					&& !eDepAspect.equals("PROGRESSIVE")) {
						proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER));	
			}
		}
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
