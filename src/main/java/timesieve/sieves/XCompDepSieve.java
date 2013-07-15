package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

import timesieve.Main;
import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;

import timesieve.util.TimeSieveProperties;
import timesieve.util.TreeOperator;

/**
 * 
 * CURRENT STATUS
 * outputs information: for each event pair where one governs the other via xcomp, 
 * print out stats. 
 * TODO double check that the printout has correct relation direction for all cases
 * TODO change so taht all dependencies are detected, and the label is just part of the printout
 * TODO tweak features that are printed out; e.g. at least print out the lemma of the word
 * 			anything else?
 * 
 * This sieve deals with event pairs in a dependency relationship,
 * when one of the verbs is a reporting verb.
 * 
 * 
 * 
 * @author cassidy
 */
public class XCompDepSieve implements Sieve {
	public boolean debug = true;
	public boolean printInfo = true;
	
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
		
		
		
		List<TLink> goldLinks = doc.getTlinks(true);
		
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
			proposed.addAll(applySieve(sent.events(), sent, doc, goldLinks));
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
	private List<TLink> applySieve(List<TextEvent> events, SieveSentence sent, SieveDocument doc, List<TLink> goldLinks) {
		List<TypedDependency> deps = sent.getDeps();
		List<Tree> trees = doc.getAllParseTrees();
		List<TLink> proposed = new ArrayList<TLink>();
		
		for( int xx = 0; xx < events.size(); xx++ ) {
			TextEvent e1 = events.get(xx);
			for( int yy = xx+1; yy < events.size(); yy++ ) {
				TextEvent e2 = events.get(yy);
				// We have a pair of event e1 and e2.
				// check a given TypedDependency involves both events,
				// and if so check event properties against criteria.
				
				// check out dependencyPath method

				for (TypedDependency td : deps) {
					// if e1 governs e2
				if ((e1.getIndex() == td.gov().index() && e2.getIndex() == td.dep().index()) || 
						(e2.getIndex() == td.gov().index() && e1.getIndex() == td.dep().index())){
				
						TextEvent eGov = e1;
						TextEvent eDep = e2;
		
					if (e2.getIndex() == td.gov().index() && e1.getIndex() == td.dep().index()) {
						// switch them if e2 governs e1!
						eGov = e2;
						eDep = e1;
					}
						String relType = td.reln().toString();
						
						if (relType.equals("xcomp"))
							classifyEventPair(eGov, eDep, sent, proposed);	
					}		
				}
			}
		if (debug == true) {
			System.out.println("events: " + events);
			System.out.println("created tlinks: " + proposed);
		}
		
		}
		return proposed;
	}
	
	private void classifyEventPair(TextEvent eGov, TextEvent eDep, SieveSentence sent, List<TLink> proposed ) {
		TextEvent.Tense eGovTense = eGov.getTense();
		TextEvent.Tense eDepTense = eDep.getTense();
		TextEvent.Class eDepClass = eDep.getTheClass();
		TextEvent.Class eGovClass = eGov.getTheClass();
		TextEvent.Aspect eDepAspect = eDep.getAspect();
		String govStr = eGov.getString();
		String depStr = eDep.getString();
		
		
		if (eDepTense == TextEvent.Tense.PRESPART)
			if (eGovClass == TextEvent.Class.OCCURRENCE){
				proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.SIMULTANEOUS));
			}
			else {
				proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED));
			}
		else if (eGovClass == TextEvent.Class.ASPECTUAL){
			proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED));
		}
		else
			proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE));
	}
	
	private String posTagFromTree(Tree sentParseTree, int tokenIndex){
		String posTag = TreeOperator.indexToPOSTag(sentParseTree, tokenIndex);
		return posTag;
	}
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
