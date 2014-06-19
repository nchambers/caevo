package caevo.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TemporalContext;
import caevo.TextEvent;
import caevo.tlink.EventEventLink;
import caevo.tlink.TLink;
import caevo.util.CaevoProperties;
import caevo.util.TimebankUtil;
import caevo.util.TreeOperator;
import edu.stanford.nlp.trees.Tree;

/**
 *SUMMARY:
 *This sieve labels pairs of verb events based on a mapping derived
 *from Reichanbach's theory of tense/aspect. The mapping is adapted from
 *Derczynski and Gaizauskas (D&G 2013).
 *
 *PARAMETERS:
 *sameTense - boolean; enforces D&G's "S/R" constraint; essentially,
 *					only pairs of verbs with the same tense will be considered.
 *sentWindow - int; only allow events that are within this many sentences of one another
 *simplifyPast, simplifyPresent, simplifyAspect - boolean; if false, don't apply
 *the relevant part of the normalization procedure as suggested in D&G13
 *(see function simplifyTense and simplifyAspect)
 *
 *SameSentence/SameTense			p=0.65	17 of 26
 *SameOrAdjSent/SameTense			p=0.58	47 of 81
 *SameSentence/AnyTense			  p=0.57	47 of 82
 *SameOrAdjSent/AnyTense			p=0.53	142 of 270
 *
 *--> "Simplify Past" = false
 *SameSentence/SameTense			p=0.65	17 of 26
 *SameOrAdjSent/SameTense			p=0.58	47 of 81
 *SameSentence/AnyTense				p=0.62	46 of 74
 *SameOrAdjSent/AnyTense			p=0.55	139 of 252
 *
 *--> "Simplify Present" = false
 *SameSentence/SameTense			p=0.65	17 of 26
 *SameOrAdjSent/SameTense			p=0.58	47 of 81
 *SameSentence/AnyTense				p=0.63	45 of 71
 *SameOrAdjSent/AnyTense			p=0.56	137 of 243
 *
 *--> "Simplify Aspect" = false
 *SameSentence/SameTense			p=0.65	17 of 26
 *SameOrAdjSent/SameTense			p=0.58	47 of 81
 *SameSentence/AnyTense			p=0.57	47 of 82
 *SameOrAdjSent/AnyTense			p=0.52	139 of 265
 *
 *--> "Simplify Present" = false; "Simplify Past" = false
 *SameSentence/SameTense			p=0.65	17 of 26
 *SameSentence/AnyTense	 p=0.70	44  of 63
 *SameOrAdjSent/AnyTense p=0.60	134 of 223
 *
 *
 *After adding the pseudoTense function that puts words that govern
 *a modal word in the future, new results:
 *
 *train:
 *ReichenbachDG13			p=0.59	192 of 326	Non-VAGUE:	p=0.90	192 of 213
 *dev:
 *ReichenbachDG13			p=0.63	20 of 32	Non-VAGUE:	p=0.77	20 of 26
 *
 *
 *DETAILS:
 *
 * D&G2013 report the percentage of TLINKs in timebank for which the following
 * criteria hold:
 * 1) The TLINK links two events that are Verbs
 * 2) The disjunction of allen relations (each of which is equivalent to a 
 * Freksa (1992) semi-interval) associated with a given pair of the form
 * < <event1.tense, event1.aspect>, <event2.tense, event2.aspect> >
 * contains the interval relation annotated in the TLINK's relType field.
 * 3) The two events are in the same "temporal context"
 *
 *A temporal context is a list of criteria in terms of the properties of
 *two or more events based on which it is inferred that the events share the same
 *reference time.
 *
 *D&G2013 report results for 5 methods of determining temporal context based on two parameters:
 *
 *Method0:
 *baseline - any two events are assumed to be in the same temporal context
 *
 *Other methods based on these parameters
 *   Sentence window: 
 *   		value 1: Same sentence - the two events in the same sentence share their temporal context
 *   		value 2:Same/adjacent sentence - two events within one sentence of one another share their
 *	 Same tense - the two events have the same tense (the idea is that the have the same
 *                relative ordering of their R(eference) and S(peech) times.
 *      value 1: true
 *      value 2: false 
 * 
 * Results can be found in D&G table 6
 * The percentage of TLINKs for each setting for which the mapping yields a set of
 * possible TLINK relTypes (interval relations) that contains the  relType in the
 * gold standard is reported (1. including cases where the mapping is trivial; 2. excluding
 * such cases; a case is trivial if the mapping yields a disjunction of all possible relTypes)
 * 
 * In this implementation, the four non-baseline methods can be used for determining
 * whether events share their temporal context with the sentWindow and sameTense
 * parameters.
 * 
 * @author cassidy
 */
public class ReichenbachDG13_update implements Sieve {
	public boolean debug = false;
	private int sentWindow = 0;
	private boolean sameTense = false;
	private boolean simplifyPast = true;
	private boolean simplifyPresent = true;
	private boolean simplifyAspect = true;
	private boolean useExtendedTense = true;
	private boolean useExtendedTenseAcrossSentence = true;
	
	
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		// Get property values from the config file
			try {
				sentWindow = CaevoProperties.getInt("ReichenbachDG13.sentWindow", 0);
				sameTense = CaevoProperties.getBoolean("ReichenbachDG13.sameTense", false);
				simplifyPast = CaevoProperties.getBoolean("ReichenbachDG13.simplifyPast", true);
				simplifyPresent = CaevoProperties.getBoolean("ReichenbachDG13.simplifyPresent", true);
				simplifyAspect = CaevoProperties.getBoolean("ReichenbachDG13.simplifyAspect", true);
				useExtendedTense = CaevoProperties.getBoolean("ReichenbachDG13.useExtendedTense", true);
				useExtendedTenseAcrossSentence = 
						CaevoProperties.getBoolean("ReichenbachDG13.useExtendedTenseAcrossSentence", true);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		// proposed will hold all TLinks proposed by the sieve
		List<TLink> proposed = new ArrayList<TLink>();
		
		// get all events by sentence
		List<List<TextEvent>> allEvents = doc.getEventsBySentence();
		
		// we need all trees in order to get pos tags
		List<Tree> trees = doc.getAllParseTrees();
		
		// we need the sentences and td's to pass to the "pseudoTense" util function
		List<SieveSentence> sents = doc.getSentences();
		
		// TO TEST TEMPORAL CONTEXT CODE
		TemporalContext tc = new TemporalContext(doc);
		tc.addContextSimpleDep();
		// END TEST TEMPORAL CONTEXT CODE
		
		// contextMap maps a context ID (which is a timex value) to a list of TextEvents that govern a
		// timex with that value as their value. If an event doesn't govern any timex,
		// it belongs to the set associated with the DCT. Note that the key (or contextId) associated
		// with the DCT is the document creation date; that is, if the DCT is specified beyond the
		// day granularity, the value is truncated to be of the form YYYY-MM-DD.
		// Note that this means that events that govern, for example, "today", will be in the same 
		// set as those that do not govern any time expressiopn.
		HashMap<String, List<TextEvent>> contextMap = tc.getContextMap();
		for (String key : contextMap.keySet()) {
			// there are no cases where two events govern a timex with the same dct and they are relatable according
			// to the reichenbach rules below, and that dct isn't the same as the dct_day. 
			//if (key.equals(tc.getValueFromTimex(doc.getDocstamp().get(0))))
				//continue;
			
			List<TextEvent> eventsInContext = contextMap.get(key);
			int numEvents = eventsInContext.size();
			for (int i = 0; i < numEvents; i++) {
				for (int j = i + 1; j < numEvents; j++) {
					TextEvent e1 = eventsInContext.get(i);
					TextEvent e2 = eventsInContext.get(j);
					TLink.Type label = this.getLabel(e1, e2, sents.get(e1.getSid()), sents.get(e2.getSid()), trees);
					if (label != null)
						addPair(e1, e2, label, proposed, doc);
				}
			}
		}
		
		
		return proposed;
	}
	
	public void train(SieveDocuments trainingInfo) {
		// no training
	}
	
	// given a tree, return the pos tag for the element with TextEvent index "index"
	private String posTagFromTree(Tree tree, int index) {
		String pos = TreeOperator.indexToPOSTag(tree, index);
		return pos;
	}
 // add (e1, e2, label) to proposed list of TLINKs
	private void addPair(TextEvent e1, TextEvent e2, TLink.Type label, List<TLink> proposed, SieveDocument doc) {
		EventEventLink tlink = new EventEventLink(e1.getEiid(), e2.getEiid(), label);
		tlink.setDocument(doc);
		proposed.add(tlink);
	}

 // get the label indicated for (e1, e2) by the D&G mapping.
 // this method also applies filters that eliminate certain events and event pairs
	// from consideration.
	private TLink.Type getLabel(TextEvent e1, TextEvent e2, SieveSentence sent1, SieveSentence sent2, List<Tree> trees) {
		// TEMPORARY FOR DEBUGGING - SEE IF I_STATEs and I_ACTIONs BEHAVE DIFFERENTLY!
		/*boolean e1IsISTATE = e1.getTheClass() == TextEvent.Class.I_STATE;
		boolean e2IsISTATE = e2.getTheClass() == TextEvent.Class.I_STATE;
		boolean e1IsIACTION = e1.getTheClass() == TextEvent.Class.I_ACTION;
		boolean e2IsIACTION = e2.getTheClass() == TextEvent.Class.I_ACTION;
		
		if (e1IsISTATE || e1IsIACTION || e2IsISTATE || e2IsIACTION) {
			return null;
		}*/
		
		// END TEMPORARY CODE HERE
		// get pos tags for e1 and e2
		String e1Pos = posTagFromTree(trees.get(e1.getSid()), e1.getIndex());
		String e2Pos = posTagFromTree(trees.get(e2.getSid()), e2.getIndex());
		// if e1 and e2 aren't both verbs, then label is null
		if (!e1Pos.startsWith("VB") || !e2Pos.startsWith("VB")) { 
			return null;
		}
		// if sameTense property is true then e1/e2 that don't share the same tense 
		// automatically are labeled null
		if (sameTense == true && e1.getTense() != e2.getTense()) {
			return null;
		}
		// if we've made it this far, apply the mapping to (e1, e2) using 
		return taToLabel(e1, e2, sent1, sent2);
	}
	// check if events have the same tense. this is used for the setting in which two events (verbs) are assumed
	// not to share their reference time (ie be a part of the same "temporal context") only if they have
	// the same tense
	public boolean eventsShareTense(TextEvent e1, TextEvent e2) {return e1.getTense() == e2.getTense();}

	// apply mapping adapted from D&G2013
	public TLink.Type taToLabel(TextEvent e1, TextEvent e2, SieveSentence sent1, SieveSentence sent2){
		// First convert e1(2)Tense(Aspect) to their simplified forms 
		// as per D&G's mapping (via simplifyTense and simplifyAspect)
		TextEvent.Tense e1Tense = null;
		TextEvent.Tense e2Tense = null;
		if (useExtendedTense == true) {
			if (!useExtendedTenseAcrossSentence && !sent1.equals(sent2)) {
				e1Tense = e1.getTense();
				e2Tense = e2.getTense();
			}
			else{
				e1Tense = TimebankUtil.pseudoTense(sent1, sent1.getDeps(), e1);
				e2Tense = TimebankUtil.pseudoTense(sent2, sent2.getDeps(), e2);
			
			}
		}
		else {
			e1Tense = e1.getTense();
			e2Tense = e2.getTense();
		}

		TextEvent.Tense e1SimpTense = simplifyTense(e1Tense);
		TextEvent.Aspect e1SimpAspect = simplifyAspect(e1.getAspect());
		TextEvent.Tense e2SimpTense = simplifyTense(e2Tense);
		TextEvent.Aspect e2SimpAspect = simplifyAspect(e2.getAspect());
		
		// define the boolean variables that we need to check to apply mapping
		// each one specifies whether e1 or e2 has a given tense or aspect (after simplification)
		boolean e1Past = (e1SimpTense == TextEvent.Tense.PAST);
		boolean e2Past = (e2SimpTense == TextEvent.Tense.PAST);
		boolean e1Pres = (e1SimpTense == TextEvent.Tense.PRESENT);
		boolean e2Pres = (e2SimpTense == TextEvent.Tense.PRESENT);

		
		boolean e1Future = (e1SimpTense == TextEvent.Tense.FUTURE);
		boolean e2Future = (e2SimpTense == TextEvent.Tense.FUTURE);
		
		boolean e1Perf = (e1SimpAspect == TextEvent.Aspect.PERFECTIVE);
		boolean e1None = (e1SimpAspect == TextEvent.Aspect.NONE);
		boolean e2Perf = (e2SimpAspect == TextEvent.Aspect.PERFECTIVE);
		boolean e2None = (e2SimpAspect == TextEvent.Aspect.NONE);
		
		// this is the mapping, implmented as a long if block
		// see reichenbach_relationmapping.xls
		// note that we only consider cases where the result of applying
		// the mapping is an interval disjunction that translates to only 
		// one relation according to our task spec. 
		// see the table in the spreadsheet FreksaAllenUsInfo and mapping_FreksaAllenUs
		
		if (e1Past && e1None && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Past && e1None && e2Future && e2None) return TLink.Type.BEFORE;
		else if (e1Past && e1None && e2Future && e2Perf) return TLink.Type.BEFORE;
		//
		else if (e1Past && e1Perf && e2Past && e2None) return TLink.Type.BEFORE ; 
		else if (e1Past && e1Perf && e2Pres && e2None) return TLink.Type.BEFORE; 
		else if (e1Past && e1Perf && e2Pres && e2Perf) return TLink.Type.BEFORE; 
		else if (e1Past && e1Perf && e2Future && e2None) return TLink.Type.BEFORE; 
		else if (e1Past && e1Perf && e2Future && e2Perf) return TLink.Type.BEFORE;
		//
		else if (e1Pres && e1None && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Pres && e1None && e2Future && e2None) return TLink.Type.BEFORE;
		//
		else if (e1Pres && e1Perf && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Pres && e1Perf && e2Future && e2None) return TLink.Type.BEFORE;
		else if (e1Pres && e1Perf && e2Future && e2Perf) return TLink.Type.BEFORE;
		//
		else if (e1Future && e1None && e2Past && e2None) return TLink.Type.AFTER;
		else if (e1Future && e1None && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Future && e1None && e2Pres && e2None) return TLink.Type.AFTER;
		else if (e1Future && e1None && e2Pres && e2Perf) return TLink.Type.AFTER;
		//
		else if (e1Future && e1Perf && e2Past && e2None) return TLink.Type.AFTER;
		else if (e1Future && e1Perf && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Future && e1Perf && e2Pres && e2Perf) return TLink.Type.AFTER;
		else return null;
	
	}
	

	
	// Apply D&G's mapping to consolidate tense and aspect labels
	private TextEvent.Tense simplifyTense(TextEvent.Tense tense){
		// simplify past
		if (tense == TextEvent.Tense.PAST ||
			  (tense == TextEvent.Tense.PASTPART && simplifyPast)) 
			{return TextEvent.Tense.PAST;}
		// simplify present
		else if (tense == TextEvent.Tense.PRESENT ||
						 (tense == TextEvent.Tense.PRESPART && simplifyPresent)) 
				{return TextEvent.Tense.PRESENT;}
		// future is trivially simplified
		else if (tense == TextEvent.Tense.FUTURE) 
				{return tense;}
		// no other tenses are considered.
		else return null; 
	}
		
	private TextEvent.Aspect simplifyAspect(TextEvent.Aspect aspect){
		// Return none or perfective based on mapping in D&G13 (else null)
		// Note that although their mapping includes progressive, we don't use
		// any tense/aspect profiles that include progressive because no 
		// tense/aspect profile that includes progressive aspect occurs in
		// any tense/aspect profile pair mapped to a single relation (in our
		// relation scheme)
		if ( (simplifyAspect && aspect.equals(TextEvent.Aspect.PERFECTIVE_PROGRESSIVE)) ||
				 aspect.equals(TextEvent.Aspect.PERFECTIVE))
			{return TextEvent.Aspect.PERFECTIVE;}
		else if (aspect.equals(TextEvent.Aspect.NONE)) 
			{return aspect;}
		else return null; 
	}
}