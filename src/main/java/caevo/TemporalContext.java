package caevo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import caevo.util.TreeOperator;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * A TemporalContext contains partitions the TextEvents in a SieveDocument that share the same temporal context.
 * 
 * 
 * @author tcassidy
 *
 */


public class TemporalContext {
	private SieveDocument doc;
	private HashMap<String,List<TextEvent>> contextMap;
	private static String creationTimeRegex = "(\\d{4}-\\d{2}-\\d{2}).*";
	private static Pattern creationTimePattern = Pattern.compile(creationTimeRegex);
	private boolean debug = true;
	// Return a new TemporalContext
	public TemporalContext(SieveDocument doc) {
		this.doc = doc;
		this.contextMap = new HashMap<String,List<TextEvent>>();
	}
	// Add a single TextEvent to a context by contextId
	private void addEventToContext(String contextIdString, TextEvent event) {
		List<TextEvent> context = this.contextMap.get(contextIdString);
		if (context != null)
			context.add(event);
		else {
			this.createContext(contextIdString);
			this.addEventToContext(contextIdString, event);
		}
	}
	// Add a list of TextEvents to a context by contextId
	private void addEventsToContext(String contextIdString, List<TextEvent> eventList) {
		List<TextEvent> context = this.contextMap.get(contextIdString); 
		if (context != null)
			context.addAll(eventList);
		else {
			this.createContext(contextIdString);
			this.addEventsToContext(contextIdString, eventList); // infinite recursion possibility?
		}
	}
	
	// Create a new context with id contextId
	private void createContext(String contextIdString) {
		this.contextMap.put(contextIdString, new ArrayList<TextEvent>());
	}
	
	// Return the context with id contextId
	public List<TextEvent> getContext(String contextIdString) {
		return this.contextMap.get(contextIdString);
	}
	
	// Return the entire context hashmap
	public HashMap<String, List<TextEvent>> getContextMap() {
		return this.contextMap;
	}
	
	// This context puts any two events in the same sentence in the same context
	public void addContextSameSent() {
		// Iterate over lists of events in our doc by sent;
		// add each list as a single temporal context
		List<List<TextEvent>> eventsBySent = doc.getEventsBySentence();
		Integer c = 0;
		for (List<TextEvent> eventList : eventsBySent) {
			this.addEventsToContext(c.toString(), eventList);
			c++;
		}
	}
	
	// 
	//public void addContextMostRecentTimex() {
		
//	}
	
	private String getDocCreationDate(Timex dctTimex) {
		String value = dctTimex.getValue();
		Matcher matcher = creationTimePattern.matcher(value);
		if (matcher.matches()) {
			String dcd = matcher.group(1);
			return dcd;
		}
		return null;
	}
	
	public void addContextSimpleDep() {
		// First get the dct Timex
		// It's value (up to the day granularity)
		// will serve as the default contextId, for events that don't govern a time expression
		List<Timex> dctTimexList = doc.getDocstamp();
		if( dctTimexList == null ) return;
		Timex dct = dctTimexList.get(0);
		if (dct == null) return;
		
		// Now we want to map each event to either dctDayValue, or the timex it governs.
		// If it governs more than one timex, ???
		
		List<List<Timex>> timexesBySentId = doc.getTimexesBySentence();
		List<List<TextEvent>> eventsBySentId = doc.getEventsBySentence();
		List<List<TypedDependency>> depsBySentId = doc.getAllDependencies();
		List<SieveSentence> sentsById = null;
		if (this.debug) {
			sentsById = doc.getSentences();
		}
		int numSents = depsBySentId.size();
		
		for (int sid = 0; sid < numSents; sid++) {
			for (TextEvent event : eventsBySentId.get(sid)) {
				// First check if the event governs a timex.
				// put them all into a list called timexesGovernedByEvent
				List<Timex> timexesGovernedByEvent = new ArrayList<Timex>();
				for (Timex timex : timexesBySentId.get(sid)) {
					// First try to add timexes in the sentences that event governs...
					for (TypedDependency td : depsBySentId.get(sid)) {
						if (event.getIndex() == td.gov().index() && timex.getTokenOffset() == td.dep().index()) {
							timexesGovernedByEvent.add(timex);
							if (this.debug) {
								System.out.printf("%s(%s) governs %s(%s)\n%s\n", event.getString(), event.getId(), timex.getText(), timex.getTid(), sentsById.get(sid).sentence());
							}
						}
					}
				}
				
				// If no timexes were added to timexesGovernedByEvent, then add the default dct
				if (timexesGovernedByEvent.size() == 0) {
					timexesGovernedByEvent.add(dct);
				}	
				// Now, add the current event to the appropriate temporal context(s).
				// Note that if the event governed more than one timex it will be added to more than one context!
				for (Timex timex : timexesGovernedByEvent) {
					this.addEventToContext(this.getValueFromTimex(timex), event);
				}
			} 
		}
		
	}
	/**
	 * For each event, obtain the shortest dependency path between the event each timex in its sentence.
	 * If no such paths exist, assign the event to the DCT
	 * Else, choose the shortest path.
	 * If same length, choose one with most gov-to-dep pointing arrows
	 * If still more than one, choose the first one in the list 
	 */
	public void addContextNearestTimexInPath() {
		// First get the DCT Timex
		List<Timex> dctTimexList = doc.getDocstamp();
		Timex dct = dctTimexList.get(0);
		if (dct == null) {
			return;
		}
		
		// Get timexes, events, and dependencies (by sid) for the document.
		List<List<Timex>> timexesBySentId = doc.getTimexesBySentence();
		List<List<TextEvent>> eventsBySentId = doc.getEventsBySentence();
		List<List<TypedDependency>> depsBySentId = doc.getAllDependencies();
		
		// Loop
		int numSents = depsBySentId.size();
		for (int sid = 0; sid < numSents; sid++) {
			for (TextEvent event : eventsBySentId.get(sid)) {
				// We want to map each Timex t to an array of ints representing
				// (1) the length of the path between event and t, and
				// (2) the number of right arrows
				HashMap<Timex, int[]> timexToCounts = new HashMap<Timex, int[]>();
				for (Timex timex : timexesBySentId.get(sid)) {
					// Get the shortest path from event to timex:
					List<TypedDependency> deps = depsBySentId.get(sid);
					int start = event.getIndex();
					int end = timex.getTokenOffset();
					String shortestPath = TreeOperator.dependencyPath(start, end, deps);
					// to be continued?
				}
			}
			
		}
		
	}
	
	
	/**
	 * Note that getValueFromTimex returns the value attribute of a timex except the case of dct,
	 * in which case a truncated value is returned.
	 **/
	public String getValueFromTimex(Timex timex) {
		// Helper function to deal with the fact that there is no Timex with document creation day.
		if (timex.isDCT()) {
			return this.getDocCreationDate(timex);
		}
		else {
			return timex.getValue();
		}
	}
	
	/**
	 * 
	 * @param path
	 * @return the length of a dependency path (provided as a String)
	 */
	public int getDepPathLength(String path) {
		int count = path.split("->").length;
    count += path.split("<-").length;
    return count;
	}

}
