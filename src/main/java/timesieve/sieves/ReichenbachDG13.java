package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timesieve.InfoFile;
import timesieve.TextEvent;
import timesieve.tlink.TLink;

/**
 *description goes here
 *public static enum TENSE    { PRESENT, PRESPART, PAST, PASTPART, INFINITIVE, FUTURE, PASSIVE, NONE };
  public static enum ASPECT   { PROGRESSIVE, PERFECTIVE, IMPERFECTIVE, PERFECTIVE_PROGRESSIVE, IMPERFECTIVE_PROGRESSIVE, NONE };
 * 
 * @author cassidy
 */
public class ReichenbachDG13 implements Sieve {
	public boolean debug = false;
	
	
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		
		// proposed will hold all TLinks proposed by the sieve
		List<TLink> proposed = new ArrayList<TLink>();
		
		// allEvents: outer list elements each correspond to a sentence;
		// inner list items each correspond to a token in a sentence
		
		// iterate; for each pair of events in the appropriate context (either within-sentence
		// or within-1-sentence), pass them their tense and aspect values to taProfileToLabel 
		// to return a TLink.TYPE based on the mapping implemented in taProfileToLabel.  
		// If null is returned then do nothing, otherwise add the label to proposed.
		
		return proposed;
	}
	
	public void train(InfoFile trainingInfo) {
		// no training
	}

	// check if events have the same tense. this is used for the setting in which two events (verbs) are assumed
	// not to share their reference time (ie be a part of the same "temporal context") only if they have
	// the same tense
	public boolean eventsShareTense(TextEvent e1, TextEvent e2) {return e1.getTense() == e2.getTense();}

	public TLink.TYPE taProfileToLabel(TextEvent.TENSE e1Tense, TextEvent.ASPECT e1Aspect,
			 TextEvent.TENSE e2Tense, TextEvent.ASPECT e2Aspect){
		
		// First convert e1(2)Tense(Aspect) to their simplified forms 
		// as per D&G's mapping (via simplifyTense and simplifyAspect)
		TextEvent.TENSE e1SimpTense = simplifyTense(e1Tense);
		TextEvent.ASPECT e1SimpAspect = simplifyAspect(e1Aspect);
		TextEvent.TENSE e2SimpTense = simplifyTense(e2Tense);
		TextEvent.ASPECT e2SimpAspect = simplifyAspect(e2Aspect);
		
		// Next confirm that the simplified tense(aspect) is non-null
		if (e1SimpTense == null || e2SimpTense == null || 
				e1SimpAspect == null || e2SimpAspect == null)
			{return null;}
		
		// See mapping in reichenbach_relation_mapping for more details.
		// The following code does not check every possible e1(2)Tense(Aspect) pair
		// type, i.e., it uses else cases to save time.
	
		// Case1: e1 has past (simple) tense
		if (e1SimpTense == TextEvent.TENSE.PAST) {
			if (e1SimpAspect == TextEvent.ASPECT.PERFECTIVE) 
				{return TLink.TYPE.BEFORE;}
			else if (e1SimpAspect == TextEvent.ASPECT.NONE) {
				if (e2SimpTense == TextEvent.TENSE.PAST)
					{return TLink.TYPE.AFTER;}
				else
					{return TLink.TYPE.BEFORE;}
				}
			}
		
		// Case2: e1 has present (simple) tense
		else if (e1SimpTense == TextEvent.TENSE.PRESENT) {
			if (e2SimpTense == TextEvent.TENSE.PAST)
				{return TLink.TYPE.AFTER;}
			else
				{return TLink.TYPE.BEFORE;}
			}
		
		// Case3: e1 has future (simple) tense
		else if (e1SimpTense == TextEvent.TENSE.FUTURE)
			{return TLink.TYPE.AFTER;}

		// We should never reach the else case here.
		else {
			System.out.println("DEBUG: D&G Simplified Tense reduced to innappropriate value: " + e1SimpTense.toString());
			return null;
			}
	 return null;
	}
	
	// Apply D&G's mapping to consolidate tense and aspect labels
	private TextEvent.TENSE simplifyTense(TextEvent.TENSE tense){
		// Return past, present, future, or none based on mapping in D&G13 (else null)
		if (tense == TextEvent.TENSE.PAST ||
			  tense == TextEvent.TENSE.PASTPART) 
			{return TextEvent.TENSE.PAST;}
		else if (tense == TextEvent.TENSE.PRESENT ||
						 tense == TextEvent.TENSE.PRESPART) 
				{return TextEvent.TENSE.PRESENT;}
		else if (tense == TextEvent.TENSE.FUTURE ||
						 tense == TextEvent.TENSE.NONE) 
				{return tense;}
		else return null; 
	}
		
	private TextEvent.ASPECT simplifyAspect(TextEvent.ASPECT aspect){
		// Return none or perfective based on mapping in D&G13 (else null)
		// Note that although their mapping includes progressive, we don't use
		// any tense/aspect profiles that include progressive because no 
		// tense/aspect profile that includes progressive aspect occurs in
		// any tense/aspect profile pair mapped to a single relation (in our
		// relation scheme)
		if (aspect.equals(TextEvent.ASPECT.PERFECTIVE_PROGRESSIVE) ||
				aspect.equals(TextEvent.ASPECT.PERFECTIVE))
			{return TextEvent.ASPECT.PERFECTIVE;}
		else if (aspect.equals(TextEvent.ASPECT.NONE)) 
			{return aspect;}
		else return null; 
	}
}