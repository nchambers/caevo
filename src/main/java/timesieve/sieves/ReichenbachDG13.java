package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timesieve.InfoFile;
import timesieve.SieveDocument;
import timesieve.SieveDocuments;
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
	
	
	public List<TLink> annotate(SieveDocument info, List<TLink> currentTLinks) {
		
		// proposed will hold all TLinks proposed by the sieve
		List<TLink> proposed = new ArrayList<TLink>();
		
		// allEvents: outer list elements each correspond to a sentence;
		// inner list items each correspond to a token in a sentence
		
		// iterate; for each pair of events in the appropriate context (either within-sentence
		// or within-1-sentence), pass them their tense and aspect values to taProfileToLabel 
		// to return a TLink.Type based on the mapping implemented in taProfileToLabel.  
		// If null is returned then do nothing, otherwise add the label to proposed.
		
		return proposed;
	}
	
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

	// check if events have the same tense. this is used for the setting in which two events (verbs) are assumed
	// not to share their reference time (ie be a part of the same "temporal context") only if they have
	// the same tense
	public boolean eventsShareTense(TextEvent e1, TextEvent e2) {return e1.getTense() == e2.getTense();}

	public TLink.Type taProfileToLabel(TextEvent.Tense e1Tense, TextEvent.Aspect e1Aspect,
			 TextEvent.Tense e2Tense, TextEvent.Aspect e2Aspect){
		
		// First convert e1(2)Tense(Aspect) to their simplified forms 
		// as per D&G's mapping (via simplifyTense and simplifyAspect)
		TextEvent.Tense e1SimpTense = simplifyTense(e1Tense);
		TextEvent.Aspect e1SimpAspect = simplifyAspect(e1Aspect);
		TextEvent.Tense e2SimpTense = simplifyTense(e2Tense);
		TextEvent.Aspect e2SimpAspect = simplifyAspect(e2Aspect);
		
		// Next confirm that the simplified tense(aspect) is non-null
		if (e1SimpTense == null || e2SimpTense == null || 
				e1SimpAspect == null || e2SimpAspect == null)
			{return null;}
		
		// See mapping in reichenbach_relation_mapping for more details.
		// The following code does not check every possible e1(2)Tense(Aspect) pair
		// type, i.e., it uses else cases to save time.
	
		// Case1: e1 has past (simple) tense
		if (e1SimpTense == TextEvent.Tense.PAST) {
			if (e1SimpAspect == TextEvent.Aspect.PERFECTIVE) 
				{return TLink.Type.BEFORE;}
			else if (e1SimpAspect == TextEvent.Aspect.NONE) {
				if (e2SimpTense == TextEvent.Tense.PAST)
					{return TLink.Type.AFTER;}
				else
					{return TLink.Type.BEFORE;}
				}
			}
		
		// Case2: e1 has present (simple) tense
		else if (e1SimpTense == TextEvent.Tense.PRESENT) {
			if (e2SimpTense == TextEvent.Tense.PAST)
				{return TLink.Type.AFTER;}
			else
				{return TLink.Type.BEFORE;}
			}
		
		// Case3: e1 has future (simple) tense
		else if (e1SimpTense == TextEvent.Tense.FUTURE)
			{return TLink.Type.AFTER;}

		// We should never reach the else case here.
		else {
			System.out.println("DEBUG: D&G Simplified Tense reduced to innappropriate value: " + e1SimpTense.toString());
			return null;
			}
	 return null;
	}
	
	// Apply D&G's mapping to consolidate tense and aspect labels
	private TextEvent.Tense simplifyTense(TextEvent.Tense tense){
		// Return past, present, future, or none based on mapping in D&G13 (else null)
		if (tense == TextEvent.Tense.PAST ||
			  tense == TextEvent.Tense.PASTPART) 
			{return TextEvent.Tense.PAST;}
		else if (tense == TextEvent.Tense.PRESENT ||
						 tense == TextEvent.Tense.PRESPART) 
				{return TextEvent.Tense.PRESENT;}
		else if (tense == TextEvent.Tense.FUTURE ||
						 tense == TextEvent.Tense.NONE) 
				{return tense;}
		else return null; 
	}
		
	private TextEvent.Aspect simplifyAspect(TextEvent.Aspect aspect){
		// Return none or perfective based on mapping in D&G13 (else null)
		// Note that although their mapping includes progressive, we don't use
		// any tense/aspect profiles that include progressive because no 
		// tense/aspect profile that includes progressive aspect occurs in
		// any tense/aspect profile pair mapped to a single relation (in our
		// relation scheme)
		if (aspect.equals(TextEvent.Aspect.PERFECTIVE_PROGRESSIVE) ||
				aspect.equals(TextEvent.Aspect.PERFECTIVE))
			{return TextEvent.Aspect.PERFECTIVE;}
		else if (aspect.equals(TextEvent.Aspect.NONE)) 
			{return aspect;}
		else return null; 
	}
}