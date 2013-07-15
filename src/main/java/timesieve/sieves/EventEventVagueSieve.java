package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.tlink.TLink;
import timesieve.tlink.EventEventLink;
import timesieve.util.TimeSieveProperties;

/**
 * EventEventVagueSieve labels pairs of events as vague based on their tenses, aspects, and classes.
 * 
 * It optionally does the following:
 * - Label pairs of events vague if they both have a class in {REPORTING, I_ACTION, I_STATE, PERCEPTION}
 * 		- Or only look at a subset of this set of classes
 * - Label pairs of events vague if they have the same tense
 * - Label pairs of events vague if they have the same aspect
 * - Label pairs of events vague if they are in different sentences
 * 
 *  The following table contains results considering subsets of these rules.  A row of this 
 *  table corresponds to only considering some of the rules listed above. ~X in the table means don't consider
 *  X (e.g. ~Aspect means Aspect wasn't considered in labelling things vague).  Class(X) means that
 *  the pair is labelled vague if both events have class in X.  
 *  
 *  The default setting is to label pairs vague if they both have classes in {REPORTING, I_STATE, PERCEPTION}
 *  and they share tense and aspect and are in different sentences.  The result for this setting is in the 
 *  first row of the table below.  It gives decent precision and recall compared to other settings.
 *  
 *                                              train-set           dev-set
 * Tense  Aspect  Sentence  Class(All-I_ACTION) 0.74 (111 of 151)   0.36	(16 of 44)
 * Tense  Aspect  Sentence  Class(I_ACTION)     0.55 (32 of 58)	
 * Tense  Aspect  Sentence  Class(I_STATE)      0.75 (27 of 36)
 * Tense  Aspect  Sentence  Class(REPORTING)    0.71 (77 of 108)    0.29	(10 of 35)
 * Tense  Aspect  Sentence  Class(PERCEPTION)   1.00 (7 of 7)
 * Tense  Aspect  Sentence  Class(All)          0.68 (143 of 209)	
 * Tense  Aspect  ~Sentence Class(I_ACTION)     0.48 (45 of 94)
 * Tense  Aspect  ~Sentence Class(I_STATE)      0.72 (41 of 57)
 * Tense  Aspect  ~Sentence Class(REPORTING)    0.58 (87 of 149)
 * Tense  Aspect  ~Sentence Class(PERCEPTION)   0.88 (7 of 8)
 * Tense  Aspect  ~Sentence Class(All)          0.58 (180 of 308)			
 * ~Tense ~Aspect Sentence  Class(I_ACTION)     0.61 (207 of 338)
 * ~Tense ~Aspect Sentence  Class(I_STATE)      0.63 (105 of 167)
 * ~Tense ~Aspect Sentence  Class(REPORTING)    0.57 (204 of 360)
 * ~Tense ~Aspect Sentence  Class(PERCEPTION)   0.79 (15 of 19)
 * ~Tense ~Aspect Sentence  Class(All)          0.60 (531 of 884)
 * 
 * The change in precision from the train to the dev set suggests that at least reporting
 * verb pairs are labelled somewhat differently in the dev set.  
 * 
 * @author Bill McDowell
 */
public class EventEventVagueSieve implements Sieve {
	private List<TextEvent.Class> vagueClasses;
	private boolean considerTense;
	private boolean considerAspect;
	private boolean considerSentence;
	
	public EventEventVagueSieve() {
		this.vagueClasses = new ArrayList<TextEvent.Class>();
		
		try {
			if (TimeSieveProperties.getBoolean("EventEventVagueSieve.considerClassREPORTING", true))
				this.vagueClasses.add(TextEvent.Class.REPORTING);
			if (TimeSieveProperties.getBoolean("EventEventVagueSieve.considerClassI_STATE", true))
				this.vagueClasses.add(TextEvent.Class.I_STATE);
			if (TimeSieveProperties.getBoolean("EventEventVagueSieve.considerClassI_ACTION", false))
				this.vagueClasses.add(TextEvent.Class.I_ACTION);
			if (TimeSieveProperties.getBoolean("EventEventVagueSieve.considerClassPERCEPTION", true))
				this.vagueClasses.add(TextEvent.Class.PERCEPTION);
			
			this.considerTense = TimeSieveProperties.getBoolean("EventEventVagueSieve.considerTense", true);
			this.considerAspect = TimeSieveProperties.getBoolean("EventEventVagueSieve.considerAspect", true);
			this.considerSentence = TimeSieveProperties.getBoolean("EventEventVagueSieve.considerSentence", true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<TLink> proposed = new ArrayList<TLink>();
		
		List<TLink> sentencePairLinks = annotateBySentencePair(doc);
		
		if (sentencePairLinks != null)
			proposed.addAll(sentencePairLinks);

		return proposed;
	}
	
	public List<TLink> annotateBySentencePair(SieveDocument doc) {
		List<List<TextEvent>> sentenceEvents = doc.getEventsBySentence();
		List<TLink> proposed = new ArrayList<TLink>();
		
		for (int s = 0; s < sentenceEvents.size(); s++) {
			for (int e1 = 0; e1 < sentenceEvents.get(s).size(); e1++) {						
				for (int e2 = e1 + 1; e2 < sentenceEvents.get(s).size(); e2++) {
					TLink link = this.orderEvents(sentenceEvents.get(s).get(e1), sentenceEvents.get(s).get(e2));
					if (link != null) 
						proposed.add(link);
				}
				
				if (s + 1 < sentenceEvents.size()) {
					for (int t2 = 0; t2 < sentenceEvents.get(s+1).size(); t2++) {
						TLink link = this.orderEvents(sentenceEvents.get(s).get(e1), sentenceEvents.get(s+1).get(t2));
						if (link != null) 
							proposed.add(link);
					}
				}
			}
		}
		
		return proposed;
	}
	
	private TLink orderEvents(TextEvent event1, TextEvent event2) {
		if (this.vagueClasses.contains(event1.getTheClass())
				 && this.vagueClasses.contains(event1.getTheClass())
				 && (!this.considerTense || event1.getTense() == event2.getTense())
				 && (!this.considerAspect || event1.getAspect() == event2.getAspect())
				 && (!this.considerSentence || event1.getSid() != event2.getSid())
			  ) {				
			return new EventEventLink(event1.getEiid(), event2.getEiid(), TLink.Type.VAGUE);
		} else {
			return null;
		}
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}