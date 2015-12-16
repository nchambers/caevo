package caevo.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.Timex;
import caevo.tlink.EventEventLink;
import caevo.tlink.TLink;
import caevo.tlink.TLink.Type;
import caevo.util.Pair;
import caevo.util.CaevoProperties;
import caevo.util.TimebankUtil;
import caevo.util.TreeOperator;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * update to ReichenbachDG13 that uses "temporal context" mapping; to be merged...
 * TODO: handle comparison across different temporal contexts; as of now there is a probelm
 * using methods used to compare timexes (e.g. e1ContextTimex.before(e2ContextTimex)) below)
 * 
 * @author cassidy
 */
public class ReichenbachDG13 implements Sieve {
	private static final boolean analysis = false;
	public boolean debug = false;
	private int sentWindow = 0;
	private boolean sameSentence = true;
	private String contextType = "naive";
	private String contextCompare = "none";
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
				contextType = CaevoProperties.getString("ReichenbachDG13.contextType", "naive"); // naive, temporalDep, closestTimexPath
				contextCompare = CaevoProperties.getString("ReichenbachDG13.contextCompare", "none"); // equals, equalsValue, all
				sameTense = CaevoProperties.getBoolean("ReichenbachDG13.sameTense", false);
				sameSentence = CaevoProperties.getBoolean("ReichenbachDG13.sameSentence", false);
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
		
//		// get all events by sentence
//		List<List<TextEvent>> eventsBySent = doc.getEventsBySentence();
//		List<TextEvent> allEvents = doc.getEvents();
//		// we need all trees in order to get pos tags
		List<Tree> trees = doc.getAllParseTrees();
//		
//		// we need the sentences and td's to pass to the "pseudoTense" util function
		
		List<SieveSentence> sents = doc.getSentences();

		
		// Get all event by sentence
		List<List<TextEvent>> eventsBySentId = doc.getEventsBySentence();
		// Get pairs of events to be classified (based on parameter )
		
		// Get document creation day timex
		List<Timex> dcts = doc.getDocstamp();
		Timex dct;
		if (dcts != null){
			dct = dcts.get(0);
		}
		else{
			dct = null;
			if (debug) System.err.println("WARNING: no dct found in ReichenbachDG13 sieve!");
		}
		
		// Build temporal context mapping - each event maps to a temporal context
		HashMap<TextEvent, ArrayList<Timex>> eventToContext = null;
		if (contextType.equals("temporalDep")) {
			eventToContext = getEventGovernsTimexMapping(doc, dct, eventsBySentId);
		}
		else if (contextType.equals("naive")) {
			eventToContext = getNaiveContextMapping(dct, eventsBySentId);
		}
		else if (contextType.equals("closestPathTimex")) {
			eventToContext = getClosestPathTimexMapping(doc, dct, eventsBySentId);
		}

		// Get event pair list based on sentWindow
		List<Pair<TextEvent, TextEvent>> eventPairs = getPairs(doc, eventsBySentId);
		
		for (Pair<TextEvent, TextEvent> eventPair : eventPairs) {
			if (compareContexts(eventPair, eventToContext)) {
				TextEvent e1 = eventPair.first();
				TextEvent e2 = eventPair.second();
				SieveSentence sent1 = sents.get(e1.getSid());
				SieveSentence sent2 = sents.get(e2.getSid());
				TLink.Type tlink = getLabel(e1, e2, sent1, sent2, trees, eventToContext);
				addPair(e1, e2, tlink, proposed, doc);
			}
		}
		
//		if (ReichenbachDG13.analysis == true) {
//			if (true) {//doc.getDocname().equals("APW19980219.0476.tml")
//				// tlinks only contain event ids, so we need mapping from event id to TextEvent
//				HashMap<String, TextEvent> idToEvent = new HashMap<String, TextEvent>();
//				for (int sid = 0; sid < eventsBySentId.size(); sid++) {
//					for (TextEvent event : eventsBySentId.get(sid)) {
//						idToEvent.put(event.getEiid(), event);
//						idToEvent.put(event.getId(), event);
//					}
//				}
//				if (debug) {
//				for (TLink tlink : proposed) {
//					TextEvent e1 = idToEvent.get(tlink.getId1());
//					TextEvent e2 = idToEvent.get(tlink.getId2());
//					System.out.printf("\ndoc:%s\nTLINK: %s(%s; t:%s  a:%s  c:%s) %s %s(%s; t:%s  a:%s  c:%s)\nSentence1: %s\nSentence2: %s\n", doc.getDocname(),
//														e1.getString(), e1.getId(), e1.getTense(), e1.getAspect(), eventToContext.get(e1).get(0).getValue(), tlink.getRelation().name(), 
//														e2.getString(), e2.getId(), e2.getTense(), e2.getAspect(), eventToContext.get(e2).get(0).getValue(), 
//														sents.get(e1.getSid()).sentence(), sents.get(e2.getSid()).sentence());
//			 }
//			}
//		 }
//		}
		
		return proposed;
	}
	


	private boolean compareContexts(Pair<TextEvent, TextEvent> eventPair, HashMap<TextEvent, ArrayList<Timex>> eventToContext) {
		// Error checking if no context was set.
		if( !eventToContext.containsKey(eventPair.first()) || !eventToContext.containsKey(eventPair.second()) ) {
			System.err.println("WARNING: no event context in Reichenbach sieve. This shouldn't happen.");
			return false;
		}
		
		String context1Value = null;
		String context2Value = null;
		if (this.contextCompare.equals("equalsValue")) {
			Timex context1 = eventToContext.get(eventPair.first()).get(0);
			Timex context2 = eventToContext.get(eventPair.second()).get(0);
			return TimebankUtil.compareTimexesByValue(context1, context2);
		}
		else if (this.contextCompare.equals("equals")) {
			context1Value =  eventToContext.get(eventPair.first()).get(0).toString();
			context2Value =  eventToContext.get(eventPair.second()).get(0).toString();
			if (context1Value.equals(context2Value)) return true;
			else return false;
		}
		else if (this.contextCompare.equals("differentValue")) {
			context1Value =  eventToContext.get(eventPair.first()).get(0).getValue();
			context2Value =  eventToContext.get(eventPair.second()).get(0).getValue();
			if (!context1Value.equals(context2Value)) return true;
			else return false;
		}
		else {
			return true;
		}
	}

	private List<Pair<TextEvent, TextEvent>> getPairs(SieveDocument doc, List<List<TextEvent>> eventsBySentId) {
		List<Pair<TextEvent, TextEvent>> eventPairs = new ArrayList<Pair<TextEvent, TextEvent>>();
		for (int sid = 0; sid < eventsBySentId.size(); sid++) {
			// get all pairs in this sentence
			for (int i = 0; i < eventsBySentId.get(sid).size(); i++) {
				if (sameSentence == true) {
					for (int j = i + 1; j < eventsBySentId.get(sid).size(); j++) {
						eventPairs.add( new Pair<TextEvent, TextEvent>(eventsBySentId.get(sid).get(i), eventsBySentId.get(sid).get(j)) );
					}
				}
				// get all pairs between (1) each event in sid, and (2) each event in all subsequent sentences
				// whose sentence id is within range (sid + sentWindow, inclusive)
				if (sentWindow <= 0 || sid == eventsBySentId.size() - 1) continue;
				int sid2Cieling = Math.min(sid + sentWindow + 1, eventsBySentId.size());
				for (int sid2 = sid + 1; sid2 < sid2Cieling; sid2++) {
					for (int k = 0; k < eventsBySentId.get(sid2).size(); k++) {
						eventPairs.add( new Pair<TextEvent, TextEvent>(eventsBySentId.get(sid).get(i), eventsBySentId.get(sid2).get(k)) );
					}
				}
			}
		}
		return eventPairs;
	}

private HashMap<TextEvent, ArrayList<Timex>> getNaiveContextMapping(Timex dct, List<List<TextEvent>> eventsBySentId) {
	HashMap<TextEvent, ArrayList<Timex>> eventToContext = new HashMap<TextEvent, ArrayList<Timex>>();
	for (int sid = 0; sid < eventsBySentId.size(); sid++) {
		for (TextEvent event : eventsBySentId.get(sid)) {
			ArrayList<Timex> eventValueList = new ArrayList<Timex>();
			eventValueList.add(dct);
			eventToContext.put(event, eventValueList);
		}
	}
		return eventToContext;
	}

private HashMap<TextEvent, ArrayList<Timex>> getClosestPathTimexMapping(SieveDocument doc, Timex dct, List<List<TextEvent>> eventsBySentId) {
	// Get timexes and dependency parses for each sentence
	List<List<Timex>> timexesBySentId = doc.getTimexesBySentence();
	List<List<TypedDependency>> depsBySentId = doc.getAllDependencies();
	// Construct event to timex mapping
	HashMap<TextEvent, ArrayList<Timex>> eventToTimex = new HashMap<TextEvent, ArrayList<Timex>>();
	int numSents = depsBySentId.size();
	for (int sid = 0; sid < numSents; sid++) {
		for (TextEvent event : eventsBySentId.get(sid)) {
			// Create value for event key, in the form of empty array list of timexes
			eventToTimex.put(event, new ArrayList<Timex>());
			// For each event, find the closest timex(es) based on dependency path
			List<Timex> closestTimexes = new ArrayList<Timex>(); // this holds the closest timex; there could be more than one, in the event of a tie
			closestTimexes.add(dct); // add the dct; if no others are added, default will be dct.
			Integer shortestOverall = null; // the length of the path between event and closest timex(es)
			for (Timex timex : timexesBySentId.get(sid)) {
				// calculate distance between the event in focus and each timex, saving the closest one(s).	
				String shortestPath = TreeOperator.dependencyPath(event.getIndex(), timex.getTokenOffset(), depsBySentId.get(event.getSid()));
				if (shortestPath == null) continue; // this could happen if event is a word that is removed during dependency parse collapsing, e.g. "in"
				// Simply skipping over such events and mapping them to dct is a naive quick fix.
				int shortestPathLength = shortestPath.split("->|<-").length; // length of the shortest path between event and timex
				if (shortestOverall == null || shortestPathLength < shortestOverall.intValue()) {
					// new shortest path!
					closestTimexes = new ArrayList<Timex>();
					closestTimexes.add(timex);
					shortestOverall = shortestPathLength;
			  	}
				else if (shortestPathLength == shortestOverall.intValue()) { //compare int and Integer
					// tie; add to the list
					closestTimexes.add(timex);
				 }
				else continue; // in this case the shortest path between event and timex is not the shortest overall (nor tied for the shortest overall)
			 	}	
			// Now we need to eliminate all but one of the timexes with the shortest path distance to event.
			if (closestTimexes.size() == 1) {
				// trivial case: only one path of the overall shortest path length
				eventToTimex.get(event).add(closestTimexes.get(0));
			}
			else if (closestTimexes.size() > 1) {
				// non-trivial case - there is more than one timex with the shortest path length to the event
				// eliminate non-dates, unless there are no timexes that are dates
				boolean noDate = true;
				// determine if there are any dates in closestTimexes; if so, switch to noDate = false
				for (Timex timex : closestTimexes) {
					if (timex.getType() == Timex.Type.DATE) {
						noDate = false;
						break;
					}
				}
				if (!noDate) { // if there is a date, eliminate all non-dates
					Iterator<Timex> ctIter = closestTimexes.iterator();
					while (ctIter.hasNext()) {
						Timex t = ctIter.next();
						if (t.getType() != Timex.Type.DATE) {
							ctIter.remove();
						}
					 }
					}
			// Get the leftmost timex in closestTimexes
					Timex leftMostTimex = closestTimexes.get(0); // first, pretend its the first one in the list
					for (Timex timex : closestTimexes) { // replace leftMostTimex if another one has a lesser tokenOffset
						if (timex.getTokenOffset() < leftMostTimex.getTokenOffset()) {
							leftMostTimex = timex;
					 }
					}
			// Add the final timex to value for eventToTimex(event); it should be the only element in that array list value!
				eventToTimex.get(event).add(leftMostTimex);
			}
			else { // this would happen if closestTimexes had no elements. but dct is added immediately after it is initialized, and should be a date
				// this should never happen; perhaps should be handled with exception anyway?
				System.out.println("this should never happen!"); // fix this
			}
		 }	
		}
	return eventToTimex;
	}

private HashMap<TextEvent, ArrayList<Timex>> getEventGovernsTimexMapping(SieveDocument doc, Timex dct, List<List<TextEvent>> eventsBySentId) {
	HashMap<TextEvent, ArrayList<Timex>> eventToTimex = new HashMap<TextEvent, ArrayList<Timex>>();
	List<List<Timex>> timexesBySentId = doc.getTimexesBySentence();
	List<List<TypedDependency>> depsBySentId = doc.getAllDependencies();

	int numSents = depsBySentId.size();
	for (int sid = 0; sid < numSents; sid++) {
		for (TextEvent event : eventsBySentId.get(sid)) {
			// First check if the event governs a timex.
			// put them all into a list called timexesGovernedByEvent
			eventToTimex.put(event, new ArrayList<Timex>());
			for (Timex timex : timexesBySentId.get(sid)) {
				// First try to add timexes in the sentences that event governs...
				for (TypedDependency td : depsBySentId.get(sid)) {
					if (event.getIndex() == td.gov().index() && timex.getTokenOffset() == td.dep().index()) {
							eventToTimex.get(event).add(timex);
					}
				 }
			  }
			// If no timexes were added to timexesGovernedByEvent, then add the default dct
			 if (eventToTimex.get(event).size() == 0) {
				 ArrayList<Timex> defaultValue = new ArrayList<Timex>();
				 defaultValue.add(dct);
				 eventToTimex.put(event, defaultValue);
			 }
			}	
		 } 
	if (this.debug == true) {
		// print out some stats if any events have a value in eventToTimex whose length is not equal to 1
//		for (TextEvent event : eventToTimex.keySet()) {
//			if (eventToTimex.get(event).size() != 1) {
//				System.out.printf("DEBUG: Event %s(%s) governs more than one timex", event.getId(), event.getString());
//			}
//		for (TextEvent event: eventToTimex.keySet()) {
//	
//		}
	}
	return eventToTimex;
 }


/**
 * add (e1, e2, label) to proposed list of TLINKs
 * 
 * @param e1
 * @param e2
 * @param label
 * @param proposed
 * @param doc
 */
	private void addPair(TextEvent e1, TextEvent e2, TLink.Type label, List<TLink> proposed, SieveDocument doc) {
		if (label != null) {
			
		EventEventLink tlink = new EventEventLink(e1.getEiid(), e2.getEiid(), label);
		proposed.add(tlink);
		}
	}
 // get the label indicated for (e1, e2) by the D&G mapping.
 // this method also applies filters that eliminate certain events and event pairs
	// from consideration.
	private TLink.Type getLabel(TextEvent e1, TextEvent e2, SieveSentence sent1, SieveSentence sent2, List<Tree> trees, HashMap<TextEvent, ArrayList<Timex>> eventToContext) {
		// get pos tags for e1 and e2
		String e1Pos = posTagFromTree(trees.get(e1.getSid()), e1.getIndex());
		String e2Pos = posTagFromTree(trees.get(e2.getSid()), e2.getIndex());
		// if e1 and e2 aren't both verbs, then label is null
		if (!e1Pos.startsWith("VB") || !e2Pos.startsWith("VB")) { 
			return null;
		}
		// if sameTense property is true then e1/e2 that don't share the same tense, 
		// the pair is automatically labeled null
		if (sameTense == true && !eventsShareTense(e1, e2)) {
			return null;
		}
		// if we've made it this far, apply the mapping to (e1, e2) using 
		return taToLabel(e1, e2, sent1, sent2, eventToContext);
	}

	// apply mapping adapted from D&G2013
	public TLink.Type taToLabel(TextEvent e1, TextEvent e2, SieveSentence sent1, SieveSentence sent2, HashMap<TextEvent, ArrayList<Timex>> eventToContext){
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
		// get simplified tense and aspect
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
		
		
		Timex e1ContextTimex = eventToContext.get(e1).get(0);
		Timex e2ContextTimex = eventToContext.get(e2).get(0);
		
		if (TimebankUtil.compareTimexesByValue(e1ContextTimex, e2ContextTimex)) {
			return compareContextsS(e1Past, e1Pres, e1Future, e1Perf, e1None, e2Past, e2Pres, e2Future, e2Perf, e2None);
		}
		else {
			return null;
		}
//		
//		boolean e1ContextSe2Context = false;
//		boolean e1ContextBe2Context = false;
//		boolean e1ContextAe2Context = false;
//		boolean e1ContextIe2Context = false;
//		boolean e1ContextIIe2Context = false;
//		boolean e1ContextXe2Context = false;
		
		
		//if (e1ContextTimex.getValue().equals(e2ContextTimex.getValue())) e1ContextSe2Context = true;
//		else if (e1ContextTimex.before(e2ContextTimex)) e1ContextBe2Context = true; // these conditions are broken because of Timex methods
//		else if (e2ContextTimex.before(e1ContextTimex)) e1ContextAe2Context = true; // I'm probably not using them correctly
//		else if (e1ContextTimex.includes((e2ContextTimex))) e1ContextIe2Context = true;
//		else if (e2ContextTimex.includes((e1ContextTimex))) e1ContextIIe2Context = true;
//		else e1ContextXe2Context = true;
		
//		if (e1ContextSe2Context) { 
//			return compareContextsS(e1Past, e1Pres, e1Future, e1Perf, e1None, e2Past, e2Pres, e2Future, e2Perf, e2None);
//		}
//		else if (e1ContextBe2Context) {
//			return compareContextsB(e1Past, e1Pres, e1Future, e1Perf, e1None, e2Past, e2Pres, e2Future, e2Perf, e2None);
//		}
//		else if (e1ContextAe2Context) {
//			return compareContextsB(e2Past, e2Pres, e2Future, e2Perf, e2None, e1Past, e1Pres, e1Future, e1Perf, e1None);
//		}
//		else if (e1ContextAe2Context) {
//			return compareContextsI(e1Past, e1Pres, e1Future, e1Perf, e1None, e2Past, e2Pres, e2Future, e2Perf, e2None);
//		}
//		else if (e1ContextAe2Context) {
//			return compareContextsI(e2Past, e2Pres, e2Future, e2Perf, e2None, e1Past, e1Pres, e1Future, e1Perf, e1None);
//		}
//		else return compareContextsS(e1Past, e1Pres, e1Future, e1Perf, e1None, e2Past, e2Pres, e2Future, e2Perf, e2None);
	}
	

	private Type compareContextsI(boolean e1Past, boolean e1Pres,
			boolean e1Future, boolean e1Perf, boolean e1None, boolean e2Past,
			boolean e2Pres, boolean e2Future, boolean e2Perf, boolean e2None) {
		// TODO Auto-generated method stub
		return null;
	}



	private Type compareContextsB(boolean e1Past, boolean e1Pres, boolean e1Future, boolean e1Perf, boolean e1None, boolean e2Past, boolean e2Pres, boolean e2Future, boolean e2Perf, boolean e2None) {

		
	// 
			if (e1Past && e1None && e2Past && e2Perf) return null;    
	//
			if (e1Past && e1None && e2Future && e2None) return TLink.Type.BEFORE;
	//
			if (e1Past && e1None && e2Future && e2Perf) return TLink.Type.BEFORE; 
//			// e1 Past/Perf
	//
			if (e1Past && e1Perf && e2Past && e2None) return TLink.Type.BEFORE ;
	//
			if (e1Past && e1Perf && e2Pres && e2None) return TLink.Type.BEFORE;
	// 
			if (e1Past && e1Perf && e2Pres && e2Perf) return TLink.Type.BEFORE; 
	//
			if (e1Past && e1Perf && e2Future && e2None) return TLink.Type.BEFORE;
	//
			if (e1Past && e1Perf && e2Future && e2Perf) return TLink.Type.BEFORE;
//			// e1 Pres/None
	//
			if (e1Pres && e1None && e2Past && e2Perf) return null;
	//
			if (e1Pres && e1None && e2Future && e2None) return TLink.Type.BEFORE; 
//			// e1 Pres/Perf
	//
			if (e1Pres && e1Perf && e2Past && e2Perf) return null;    
	//
			if (e1Pres && e1Perf && e2Future && e2None) return TLink.Type.BEFORE;
	//
			if (e1Pres && e1Perf && e2Future && e2Perf) return TLink.Type.BEFORE;
//			// e1 Future/None
	// 
			if (e1Future && e1None && e2Past && e2None) return null;
	//
			if (e1Future && e1None && e2Past && e2Perf) return null; 
	//
	    if (e1Future && e1None && e2Pres && e2None) return null; 
	//
	    if (e1Future && e1None && e2Pres && e2Perf) return null;
//			// e1 Future/Perf
	//
			if (e1Future && e1Perf && e2Past && e2None) return null;  
	//
			if (e1Future && e1Perf && e2Past && e2Perf) return null;
	//
			if (e1Future && e1Perf && e2Pres && e2Perf) return null;
			else return null;
		
	}



	private TLink.Type compareContextsS(boolean e1Past, boolean e1Pres, boolean e1Future, boolean e1Perf, boolean e1None, boolean e2Past, boolean e2Pres, boolean e2Future, boolean e2Perf, boolean e2None) {
		/* This is the mapping, implmented as a long if-block
		/ See reichenbach_relationmapping.xls.
		/ Note that we only consider cases where the result of applying
		/ the mapping is an interval disjunction that translates to only 
		/ one relation (according to our task spec). 
		/ See the table in the spreadsheet FreksaAllenUsInfo and mapping_FreksaAllenUs */
		// e1 Past/None
		
// p=0.86	12 of 14	Non-VAGUE:	p=1.00	12 of 12; p=0.45	9 of 20	Non-VAGUE:	p=0.75	9 of 12; p=0.62	21 of 34	Non-VAGUE:	p=0.88	21 of 24
		if (e1Past && e1None && e2Past && e2Perf) return TLink.Type.AFTER;    
//p=0.83	35 of 42	Non-VAGUE:	p=0.92	35 of 38; p=0.55	29 of 53	Non-VAGUE:	p=0.97	29 of 30; p=0.67	64 of 95	Non-VAGUE:	p=0.94	64 of 68;
		if (e1Past && e1None && e2Future && e2None) return TLink.Type.BEFORE;
//p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1; p=0.00	0 of 1	Non-VAGUE:	p=0.00	0 of 1; p=0.50	1 of 2	Non-VAGUE:	p=0.50	1 of 2
		if (e1Past && e1None && e2Future && e2Perf) return TLink.Type.BEFORE; 
//		// e1 Past/Perf
//p=0.25	2 of 8	Non-VAGUE:	p=0.40	2 of 5; p=0.48	12 of 25	Non-VAGUE:	p=0.63	12 of 19; p=0.42	14 of 33	Non-VAGUE:	p=0.58	14 of 24
		if (e1Past && e1Perf && e2Past && e2None) return TLink.Type.BEFORE ;
//p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0; p=0.17	1 of 6	Non-VAGUE:	p=1.00	1 of 1; p=0.17	1 of 6	Non-VAGUE:	p=1.00	1 of 1
		if (e1Past && e1Perf && e2Pres && e2None) return TLink.Type.BEFORE;
//p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0; p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1; p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1 
		if (e1Past && e1Perf && e2Pres && e2Perf) return TLink.Type.BEFORE; 
//p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1; p=0.80	4 of 5	Non-VAGUE:	p=1.00	4 of 4; p=0.83	5 of 6	Non-VAGUE:	p=1.00	5 of 5
		if (e1Past && e1Perf && e2Future && e2None) return TLink.Type.BEFORE;
//p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0; p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1; p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1
		if (e1Past && e1Perf && e2Future && e2Perf) return TLink.Type.BEFORE;
//		// e1 Pres/None
//p=0.50	1 of 2	Non-VAGUE:	p=1.00	1 of 1; p=0.50	3 of 6	Non-VAGUE:	p=1.00	3 of 3; p=0.50	4 of 8	Non-VAGUE:	p=1.00	4 of 4
		if (e1Pres && e1None && e2Past && e2Perf) return TLink.Type.AFTER;
//p=1.00	9 of 9	Non-VAGUE:	p=1.00	9 of 9; p=0.21	4 of 19	Non-VAGUE:	p=1.00	4 of 4; p=0.46	13 of 28	Non-VAGUE:	p=1.00	13 of 13
		if (e1Pres && e1None && e2Future && e2None) return TLink.Type.BEFORE; 
//		// e1 Pres/Perf
//p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1; p=0.00	0 of 2	Non-VAGUE:	p=0.00	0 of 0; p=0.33	1 of 3	Non-VAGUE:	p=1.00	1 of 1
		if (e1Pres && e1Perf && e2Past && e2Perf) return TLink.Type.AFTER;    
//p=0.50	1 of 2	Non-VAGUE:	p=1.00	1 of 1; p=0.64	7 of 11	Non-VAGUE:	p=1.00	7 of 7; p=0.62	8 of 13	Non-VAGUE:	p=1.00	8 of 8
		if (e1Pres && e1Perf && e2Future && e2None) return TLink.Type.BEFORE;
//p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0; p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0; p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0
		if (e1Pres && e1Perf && e2Future && e2Perf) return TLink.Type.BEFORE;
//		// e1 Future/None
//p=0.60	3 of 5	Non-VAGUE:	p=0.75	3 of 4; p=0.70	33 of 47	Non-VAGUE:	p=1.00	33 of 33; p=0.69	36 of 52	Non-VAGUE:	p=0.97	36 of 37; 
		if (e1Future && e1None && e2Past && e2None) return TLink.Type.AFTER;
//p=0.67	2 of 3	Non-VAGUE:	p=1.00	2 of 2; p=0.71	5 of 7	Non-VAGUE:	p=1.00	5 of 5; p=0.70	7 of 10	Non-VAGUE:	p=1.00	7 of 7
		if (e1Future && e1None && e2Past && e2Perf) return TLink.Type.AFTER; 
//p=0.25	1 of 4	Non-VAGUE:	p=0.50	1 of 2; p=0.38	5 of 13	Non-VAGUE:	p=1.00	5 of 5; p=0.35	6 of 17	Non-VAGUE:	p=0.86	6 of 7;
    if (e1Future && e1None && e2Pres && e2None) return TLink.Type.AFTER; 
//p=1.00	3 of 3	Non-VAGUE:	p=1.00	3 of 3; p=0.50	3 of 6	Non-VAGUE:	p=1.00	3 of 3; p=0.67	6 of 9	Non-VAGUE:	p=1.00	6 of 6
    if (e1Future && e1None && e2Pres && e2Perf) return TLink.Type.AFTER;
//		// e1 Future/Perf
//p=0.00	0 of 2	Non-VAGUE:	p=0.00	0 of 0; p=0.50	2 of 4	Non-VAGUE:	p=1.00	2 of 2; p=0.33	2 of 6	Non-VAGUE:	p=1.00	2 of 2
		if (e1Future && e1Perf && e2Past && e2None) return TLink.Type.AFTER;  
//p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0; p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1; p=1.00	1 of 1	Non-VAGUE:	p=1.00	1 of 1
		if (e1Future && e1Perf && e2Past && e2Perf) return TLink.Type.AFTER;
//p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0; p=0.00	0 of 1	Non-VAGUE:	p=0.00	0 of 0; p=0.00	0 of 1	Non-VAGUE:	p=0.00	0 of 0
		if (e1Future && e1Perf && e2Pres && e2Perf) return TLink.Type.AFTER;    //
		else return null;
	}
		
		
	
	/**
	 * Apply D&G's mapping to consolidate tense labels
	 * @param tense
	 * @return
	 */
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
	/**
	 * Apply D&G's mapping to consolidate aspect labels
	 * @param tense
	 * @return
	 */
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
	
	/**
	 * given a tree, return the pos tag for the element with TextEvent index "index"
	 * @param tree
	 * @param index
	 * @return
	 */
	private String posTagFromTree(Tree tree, int index) {
		String pos = TreeOperator.indexToPOSTag(tree, index);
		return pos;
	}
	/**
	 * check if two events e1 and e2 share tense
	 * @param e1
	 * @param e2
	 * @return
	 */
	public boolean eventsShareTense(TextEvent e1, TextEvent e2) {
		return e1.getTense() == e2.getTense();
		}
	
	
	public void train(SieveDocuments trainingInfo) {
		// no training
	}
}