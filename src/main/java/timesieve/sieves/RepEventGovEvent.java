package timesieve.sieves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.TextEvent.Aspect;
import timesieve.TextEvent.Tense;
import timesieve.tlink.TLink;
import timesieve.tlink.TLink.Type;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * This sieve deals with event pairs, where a REPORTING event governs another
 * event.
 * 
 * @author bethard
 */
public class RepEventGovEvent implements Sieve {

	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<TLink> proposed = new ArrayList<TLink>();
		for (SieveSentence sent : doc.getSentences()) {
			List<TextEvent> events = sent.events();
			List<TypedDependency> deps = sent.getDeps();

			Map<Integer, TextEvent> indexToEvent = new HashMap<Integer, TextEvent>();
			for (TextEvent event : events) {
				indexToEvent.put(event.getIndex(), event);
			}

			for (TypedDependency dep : deps) {
				TextEvent govEvent = indexToEvent.get(dep.gov().index());
				TextEvent depEvent = indexToEvent.get(dep.dep().index());
				if (govEvent != null && depEvent != null) {
					if (govEvent.getTheClass().equals(TextEvent.Class.REPORTING)) {
						Tense govTense = govEvent.getTense();
						Tense depTense = depEvent.getTense();
						Aspect depAspect = depEvent.getAspect();
						Type relation = null;
						if (depEvent.getTheClass().equals(TextEvent.Class.REPORTING)) {
							// annotation of relations between speech events is inconsistent;
							// best case is (p=0.67 12 of 18), when labeling all SIMULTANEOUS
							// relation = Type.SIMULTANEOUS;
						} else if (govTense.equals(Tense.PAST)) {
							if (depTense.equals(Tense.PAST)) {
								// p=0.81 22 of 27 on TimeBank
								relation = Type.AFTER;
							} else if (depTense.equals(Tense.PRESENT)
									&& depAspect.equals(Aspect.PERFECTIVE)) {
								// p=0.83 5 of 6
								relation = Type.AFTER;
							} else if (depTense.equals(Tense.FUTURE)) {
								// p=1.00 3 of 3
								relation = Type.BEFORE;
							}
						}
						// no rules so far for for PRESENT or FUTURE since:
						// only 5 instances of govTense=PRESENT in TimeBank
						// only 1 instance of govTense=FUTURE in TimeBank
						if (relation != null) {
							proposed.add(new TLink(govEvent.getEiid(), depEvent.getEiid(),
									relation));
						}
					}
				}
			}
		}
		return proposed;
	}

	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
