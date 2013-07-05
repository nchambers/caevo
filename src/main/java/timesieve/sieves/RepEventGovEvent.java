package timesieve.sieves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
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
						Type relation = null;
						if (depEvent.getTheClass().equals(TextEvent.Class.REPORTING)) {
							// no clear pattern for relations between speech events;
							// in our annotations, AFTER gives (p=0.50 5 of 10) and VAGUE
							// gives (p=0.40)
						} else {
							switch (govEvent.getTense()) {
							
							// gov=PAST
							case PAST:
								switch (depEvent.getTense()) {

								// gov=PAST, dep=PAST
								case PAST:
									switch (depEvent.getAspect()) {
									case NONE: // p=0.95 20 of 21 (ignoring VAGUE)
									case PERFECTIVE: // p=1.00 1 of 1 (ignoring VAGUE)
										relation = Type.AFTER;
										break;
									case PROGRESSIVE: // p=1.00 1 of 1
										relation = Type.IS_INCLUDED;
										break;
									// never occurs in training data
									case PERFECTIVE_PROGRESSIVE:
									case IMPERFECTIVE:
									case IMPERFECTIVE_PROGRESSIVE:
										break;
									}
									break;

								// gov=PAST, dep=PRESENT
								case PRESENT:
									switch (depEvent.getAspect()) {
									case PERFECTIVE: // p=1.00 6 of 6 (ignoring VAGUE)
										relation = Type.AFTER;
										break;
									case PROGRESSIVE: // p=1.00 4 of 4 (ignoring VAGUE)
									case PERFECTIVE_PROGRESSIVE: // p=1.00 2 of 2 (ignoring VAGUE)
										relation = Type.IS_INCLUDED;
										break;
									case NONE: // p=0.71 22 of 31, but no linguistic reason why
										// relation = Type.AFTER;
									case IMPERFECTIVE: // p=0.50 5 of 10, but no linguistic reason
										// relation = Type.IS_INCLUDED;
										break;

									// never occurs in training data
									case IMPERFECTIVE_PROGRESSIVE:
										break;
									}
									break;

								// gov=PAST, dep=FUTURE
								case FUTURE: // p=1.00 5 of 5 (ignoring VAGUE)
									relation = Type.BEFORE;
									break;

								// gov=PAST, dep=NONE
								case NONE: // p=0.70 14 of 20, but no linguistic reason why
									// relation = Type.BEFORE;
									break;

								// never occurs in training data
								case INFINITIVE:
								case PASSIVE:
								case PASTPART:
								case PRESPART:
									break;
								}
								break;

							// gov=PRESENT
							case PRESENT:
								switch (depEvent.getTense()) {

								// gov=PRESENT, dep=PAST
								case PAST: // not in data, but makes linguistic sense
									relation = Type.AFTER;
									break;

								// gov=PRESENT, dep=PRESENT
								case PRESENT:
									switch (depEvent.getAspect()) {
									case PERFECTIVE: // p=1.00 1 of 1 (ignoring VAGUE)
										relation = Type.AFTER;
										break;

									case NONE: // p=1.00 2 of 2, but no linguistic reason why
										// relation = Type.IS_INCLUDED;
										break;

									// never occurs in training data
									case PROGRESSIVE:
									case PERFECTIVE_PROGRESSIVE:
									case IMPERFECTIVE:
									case IMPERFECTIVE_PROGRESSIVE:
										break;
									}
									break;

								// gov=PRESENT, dep=FUTURE
								case FUTURE: // p=1.00 2 of 2 (ignoring VAGUE)
									relation = Type.BEFORE;
									break;

								// gov=PRESENT, dep=... only 0-1 occurrences in training data
								case NONE:
								case INFINITIVE:
								case PASSIVE:
								case PASTPART:
								case PRESPART:
									break;
								}
								break;

							// gov=... only 0-1 occurrences in training data
							case NONE:
							case PRESPART:
							case FUTURE:
							case INFINITIVE:
							case PASSIVE:
							case PASTPART:
								break;
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
