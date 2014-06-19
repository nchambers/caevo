package caevo.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.tlink.EventEventLink;
import caevo.tlink.TLink;
import caevo.util.CaevoProperties;
import caevo.util.TimebankUtil;
import caevo.util.TreeOperator;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * This sieve deals with event pairs in various dependency relationships.
 * It looks for two events where a single dependency relation connects them.
 * Various methods handle the different relations: xcomp, ccomp, nsubj, dep, etc.
 * 
 * CURRENT STATUS
 * outputs information: for each event pair where one governs the other via xcomp, 
 * print out stats. 
 * TODO double check that the printout has correct relation direction for all cases
 * TODO change so taht all dependencies are detected, and the label is just part of the printout
 * TODO tweak features that are printed out; e.g. at least print out the lemma of the word
 * 			anything else?
 * 
 * 
 * 07/15/2013
 * train:
 * XCompDepSieve			p=0.68	111 of 164	Non-VAGUE:	p=0.80	111 of 138
 * dev:
 * XCompDepSieve			p=0.70	21 of 30	Non-VAGUE:	p=0.78	21 of 27
 * 
 * 07/16/2013
 * (after the // is after adding the case "else --> AFTER" for ccomp
 * train:
 * XCompDepSieve			p=0.71	127 of 180	Non-VAGUE:	p=0.84	127 of 152 // XCompDepSieve			p=0.59	148 of 249	Non-VAGUE:	p=0.74	148 of 200
 * dev:
 * XCompDepSieve			p=0.59	17 of 29	Non-VAGUE:	p=0.68	17 of 25 // XCompDepSieve			p=0.68	36 of 53	Non-VAGUE:	p=0.80	36 of 45
 * 
 * parameter useExtendedTense onyl applies to ccomp right now.
 * 
 * 07/23/2013
 * train
 * p=0.68	163 of 241	Non-VAGUE:	p=0.79	163 of 206
 * dev
 * p=0.63	34 of 54	Non-VAGUE:	p=0.77	34 of 44
 * 
 * 07/24/2013
 * train:
 * extended future:
 * XCompDepSieve			p=0.67	209 of 311	Non-VAGUE:	p=0.81	209 of 259
 * without:
 * p=0.68	199 of 294	Non-VAGUE:	p=0.81	199 of 247
 * dev:
 * extended future:
 * XCompDepSieve			p=0.63	37 of 59	Non-VAGUE:	p=0.76	37 of 49
 * without:
 * p=0.63	37 of 59	Non-VAGUE:	p=0.76	37 of 49
 * 
 * 
 * 
 * @author cassidy
 */
public class XCompDepSieve implements Sieve {
	public boolean debug = false;
	private boolean useExtendedTense = true;
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		try {
			useExtendedTense = CaevoProperties.getBoolean("XCompDepSieve.useExtendedTense", true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// End properties code
		
		//Sieve Code
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Get the list of sentences and parse trees for the document
		List<SieveSentence> sents = doc.getSentences();
		List<Tree> trees = doc.getAllParseTrees();
		
		// For each sentence, send its events and dependencies to applySieve, which
		// finds pairs of events where one event governs the other, and checks them against
		// criteria in terms of additional properties of both events as well as their dependency relation.
		for(SieveSentence sent : sents) {
			// Get the list of events and list of deps for the sentence
			List<TextEvent> events = sent.events();
			List<TypedDependency> deps = sent.getDeps();
			// Iterate over event pairs and determine if one governs the other;
			// if so, try to classify the pair.
			for( int xx = 0; xx < events.size(); xx++ ) {
			  TextEvent e1 = events.get(xx);
				for( int yy = xx+1; yy < events.size(); yy++ ) {
					TextEvent e2 = events.get(yy);
					// check if the two events are in a dependency relation and label them accordingly.
					for (TypedDependency td : deps) {
						// if e1 governs e2 (or vice versa)
						TextEvent eGov = null;
						TextEvent eDep = null;
						// Does e1 govern e1?
						if (e1.getIndex() == td.gov().index() && e2.getIndex() == td.dep().index()) {
							eGov = e1;
							eDep = e2;
						}
						// Does e2 govern e1?
						else if (e2.getIndex() == td.gov().index() && e1.getIndex() == td.dep().index()) {
							eGov = e2;
							eDep = e1;
						}
						// If neither event governs the other, keep iterating.
						else {
							continue;
						}
						
						// Get rel type for the dependency, and apply rules accordingly.
						String relType = td.reln().toString();
						
						EventEventLink tlink = null;
						
						if (relType.equals("xcomp")) {  
							// p=0.69	69 of 100	Non-VAGUE:	p=0.87	69 of 79
							tlink = classifyEventPair_xcomp(eGov, eDep, sent);
						}
						if (relType.equals("ccomp")) { 
							// p=0.71	70 of 99	Non-VAGUE:	p=0.85	70 of 82
							tlink = classifyEventPair_ccomp(eGov, eDep, sent, deps);
						}
						if (relType.equals("conj_and")) { 
							// p=0.69	24 of 35	Non-VAGUE:	p=0.69	24 of 35
							tlink = classifyEventPair_conj_and(eGov, eDep, sent);
						}
						if (relType.equals("nsubj")) { 
							// p=0.52	13 of 25	Non-VAGUE:	p=0.62	13 of 21
							tlink = classifyEventPair_nsubj(eGov, eDep, sent);
						}
						if (relType.equals("advcl")) { 
							// p=0.65	15 of 23	Non-VAGUE:	p=0.71	15 of 21
							tlink = classifyEventPair_advcl(eGov, eDep, sent, deps);
						}
						if (relType.equals("conj_but")) { 
							// p=0.50	7 of 14	Non-VAGUE:	p=0.78	7 of 9
							tlink = classifyEventPair_conj_but(eGov, eDep, sent, deps);
						}
						if (relType.equals("conj_or")) { 
								 //p=0.67	2 of 3	Non-VAGUE:	p=0.67	2 of 3
							tlink = classifyEventPair_conj_or(eGov, eDep, sent, deps);
						}
						if (relType.equals("dobj")) { 
							// p=0.75	9 of 12	Non-VAGUE:	p=1.00	9 of 9
							tlink = classifyEventPair_dobj(eGov, eDep, sent, deps);
						}

						if (tlink != null) {
//							boolean success = true;
//							try {
//								checkTLink(tlink, proposed);
//							}
//							catch (IllegalStateException E) {
//								success = false;
//								if (debug) {
//									System.out.printf("Tried to add tlink for pair: (%s[%s], %s[%s]), but link is already proposed",
//																		eGov.getId(), eGov.getEiid(), eDep.getId(), eDep.getEiid() );
//								}
//							}
//							finally {
//								if (success)
							
							
									proposed.add(tlink);
							//}
						}
					}		
				}
			}
		}
		
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
		}
		return proposed;
	}
	


	private EventEventLink classifyEventPair_conj_or(TextEvent eGov, TextEvent eDep, SieveSentence sent, List<TypedDependency> deps) {
		return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.VAGUE);
	}

	private EventEventLink classifyEventPair_dobj(TextEvent eGov, TextEvent eDep, SieveSentence sent, List<TypedDependency> deps) {
	 TextEvent.Class eGovClass = eGov.getTheClass();
	 TextEvent.Tense eDepTense = eDep.getTense();
	 TextEvent.Aspect eDepAspect = eDep.getAspect();
	 
	 if (eGovClass == TextEvent.Class.ASPECTUAL) {
		 return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED);
	 }
	 else if (eDepTense == TextEvent.Tense.NONE && eDepAspect == TextEvent.Aspect.NONE) {
		 for (TypedDependency td : deps) {
			 if (td.gov().index() == eDep.getIndex() && td.reln().toString().toLowerCase().equals("det")) {
				 if (td.dep().toString("value").toLowerCase().equals("a")) {
					 return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE);
				 }
//				 if (td.dep().toString("value").toLowerCase().equals("the")) { // how can we differentiate between AFTER and IS_INCLUDED? 
//					 return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
//				 }
			 }
		 }
	 }
	 else {
		return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.VAGUE);
	 }
	 return null;
	}

	private EventEventLink classifyEventPair_conj_but(TextEvent eGov, TextEvent eDep, SieveSentence sent, List<TypedDependency> deps) {
		return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE);
	}

	private EventEventLink classifyEventPair_advcl(TextEvent eGov, TextEvent eDep, SieveSentence sent, List<TypedDependency> deps) {
		// Find the "marker" (i.e. the word that introduces the adverbial clause complement (i.e. the dependent)).
		String mark = null;
		for (TypedDependency td : deps) {
			String rel = td.reln().toString();
			if (td.gov().index() == eDep.getIndex()) {
				if ( rel.equals("mark") ) { // sometimes advmod plays role of mark
					mark = td.dep().toString("value");
					if( debug ) System.out.printf("\ngov:%s dep:%s mark:%s\n%s\n", eGov.getString(), eDep.getString(), mark, sent.sentence());
				 }
			  }
		   }
		 if (mark == null) {
			 for (TypedDependency td : deps) {
					String rel = td.reln().toString();
					if (td.gov().index() == eDep.getIndex()) {
						if ( rel.equals("advmod") ) { // sometimes advmod plays role of mark
							mark = td.dep().toString("value");
							if( debug ) System.out.printf("\ngov:%s dep:%s mark:%s\n%s\n", eGov.getString(), eDep.getString(), mark, sent.sentence());
					 }
					}
				 }
		 		}
		 
		 
		 
		 
		 // Apply rules
		 
		if (mark != null && mark.toLowerCase().equals("until")) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE);
		}
		if (mark != null && mark.toLowerCase().equals("once")) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
		}
		if (mark != null && mark.toLowerCase().equals("after")) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
		}
		if (mark != null && mark.toLowerCase().equals("before")) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE);
		}
		if (mark != null && mark.toLowerCase().equals("since")) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
		}
		if (mark != null && mark.toLowerCase().equals("when")) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.VAGUE);
		}
		if (mark != null && mark.toLowerCase().equals("as")) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.SIMULTANEOUS);
		}
		if (mark != null && mark.toLowerCase().equals("because")) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE);
		}
		else {
		 //return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.VAGUE);
			return null;
		}
	}

	private EventEventLink classifyEventPair_nsubj(TextEvent eGov, TextEvent eDep, SieveSentence sent) {
		TextEvent.Class eGovClass = eGov.getTheClass();
		if (eGovClass == TextEvent.Class.ASPECTUAL) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED);
		}
		return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.VAGUE);
	}

	private EventEventLink classifyEventPair_conj_and(TextEvent eGov, TextEvent eDep, SieveSentence sent) {
		return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.VAGUE);
	}

	private EventEventLink classifyEventPair_xcomp(TextEvent eGov, TextEvent eDep, SieveSentence sent) {
		TextEvent.Tense eGovTense = eGov.getTense();
		TextEvent.Tense eDepTense = eDep.getTense();
		TextEvent.Class eDepClass = eDep.getTheClass();
		TextEvent.Class eGovClass = eGov.getTheClass();
		TextEvent.Aspect eDepAspect = eDep.getAspect();
		String govStr = eGov.getString();
		String depStr = eDep.getString();
		
		
		if (eDepTense == TextEvent.Tense.PRESPART)
			if (eGovClass == TextEvent.Class.OCCURRENCE){
				return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.SIMULTANEOUS);
			}
			else {
				return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED);
			}
		else if (eGovClass == TextEvent.Class.ASPECTUAL){
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED);
		}
		else
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE);
		
	}

	private EventEventLink classifyEventPair_ccomp(TextEvent eGov, TextEvent eDep, SieveSentence sent, List<TypedDependency> deps) {
		TextEvent.Tense eDepTense = null;
		TextEvent.Tense eGovTense = null;
		if (useExtendedTense == true) {
			eGovTense = TimebankUtil.pseudoTense(sent, deps, eGov);
			eDepTense = TimebankUtil.pseudoTense(sent, deps, eDep);
		}
		else {
			eGovTense = eGov.getTense();
			eDepTense = eDep.getTense();
		}
		TextEvent.Class eDepClass = eDep.getTheClass();
		TextEvent.Class eGovClass = eGov.getTheClass();
		TextEvent.Aspect eDepAspect = eDep.getAspect();
		TextEvent.Aspect eGovAspect = eGov.getAspect();
		String govStr = eGov.getString();
		String depStr = eDep.getString();
		
	
		if (eDepTense == TextEvent.Tense.FUTURE) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE);
		}
		else if (eDepAspect == TextEvent.Aspect.PERFECTIVE) {
				if (eGovAspect == TextEvent.Aspect.NONE)
					return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
				else if (eGovTense == TextEvent.Tense.PAST)
					return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
				else if (eGovClass == TextEvent.Class.REPORTING)
					return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
				else if (eDepClass == TextEvent.Class.OCCURRENCE)
					return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
			}
		else if (eGovAspect == TextEvent.Aspect.PERFECTIVE) {
			return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.VAGUE); 
		}
		else if (eDepTense == TextEvent.Tense.PAST) {
			if (eGovTense == TextEvent.Tense.PAST) {
				if (eGovAspect == TextEvent.Aspect.NONE && eDepAspect == TextEvent.Aspect.NONE) {
					return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
				}
			}
		}
		else if (eDepTense == TextEvent.Tense.PRESENT) {
			if (eGovClass == TextEvent.Class.REPORTING) {
				return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED);
			}
			else if ( (eDepClass == TextEvent.Class.STATE || eDepClass == TextEvent.Class.I_STATE) &&
								 (eGovTense == TextEvent.Tense.PRESENT && eGovAspect == TextEvent.Aspect.PROGRESSIVE)) {
				return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.IS_INCLUDED);
			}
			else
				return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.VAGUE);
		}
		return null;
//	return new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER);
		//else //commenting this out is great for the train set and terrible for the dev set!
			//proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER));
		

//		if (eGovTense == TextEvent.Tense.PAST && eGovAspect == TextEvent.Aspect.NONE &&
//				eDepTense == TextEvent.Tense.PAST && eDepAspect == TextEvent.Aspect.NONE // || eDepAspect == TextEvent.Aspect.PERFECTIVE
//				&& eDepClass == TextEvent.Class.OCCURRENCE) {
//			proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER));
//		}
//		if (eGovTense == TextEvent.Tense.PAST && eGovAspect == TextEvent.Aspect.NONE &&
//				eDepTense == TextEvent.Tense.NONE && eDepAspect == TextEvent.Aspect.NONE &&
//				eDepClass == TextEvent.Class.OCCURRENCE) {
//			proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.BEFORE));
//			
//		}
//		else
	//		proposed.add(new EventEventLink(eGov.getEiid(), eDep.getEiid(), TLink.Type.AFTER)); // 32/114 = 28%
	}
	
	private String posTagFromTree(Tree sentParseTree, int tokenIndex){
		String posTag = TreeOperator.indexToPOSTag(sentParseTree, tokenIndex);
		return posTag;
	}
	
	public void checkTLink(TLink tlink, List<TLink> proposed) throws IllegalStateException{
		int numTLinks = proposed.size();
		for (int t = 0; t < numTLinks; t++) {
			if (tlink.coversSamePair(proposed.get(t))) { 
				throw new IllegalStateException("Cannot add a tlink between a pair of events for which there is already a tlink in proposed");
			}
		}
	}

	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
