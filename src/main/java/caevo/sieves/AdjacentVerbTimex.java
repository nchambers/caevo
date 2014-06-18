package caevo.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.Timex;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.util.TimeSieveProperties;
import caevo.util.TreeOperator;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * 
 * AdjacentVerbTimex intends to classify event/time pairs from the same sentence
 * in which the two are "adjacent" in some sense, and the event is a verb.
 * Two notions of adjacency implemented thus far are based on surface distance 
 * and relative position in a dependency parse graph.
 * 
 * 
 * Parameters
 * These parameters are booleans. If true, classify the corresponding pairs
 * 	AdjacentVerbTimex.EVENT_BEFORE_TIMEX - classify a pair if the event comes before the timex in the text
 * 	AdjacentVerbTimex.TIMEX_BEFORE_EVENT
 * 	AdjacentVerbTimex.EVENT_GOVERNS_TIMEX - classidy a pair if the event governs the timex
 * 	AdjacentVerbTimex.TIMEX_GOVERNS_EVENT
 * 
 * This parameter specifies the maximum number of words that may intervene between
 * the verb and timex such that they are still considered "adjacent".
 * 	AdjacentVerbTimex.numInterWords
 * 
 *timex before event - true
 *p=0.64	89 of 139	Non-VAGUE:	p=0.75	89 of 119
 * timex before event - false
 * p=0.70	74 of 106	Non-VAGUE:	p=0.86	74 of 86
 *
 * The biggest problem is when the system says IS_INCLUDED and the correct answer is VAGUE.
 * I attribute this to annotation difficulties; I think in many such cases the system is correct. 
 * More analysis to come.
 * 
 * EVENT_BEFORE_TIMEX uses preposition-based rules (default is_included)
 * TIMEX_BEFORE_EVENT default is_included; vague for event "now"; vague when "said" precedes timex
 * 	TODO: generalize the third rule to reporting verbs
 *  TODO: check if that reporting verb governs the event after the timex
 * EVENT_GOVERNS_TIMEX just returns is_included
 * 	TODO: add more rules; this is hard because many errors are with event = "now",
 * 				or vague
 * 
 * @author cassidy
 */
public class AdjacentVerbTimex implements Sieve {
	
	public boolean debug = false;
	private boolean EVENT_BEFORE_TIMEX = true;
	private boolean TIMEX_BEFORE_EVENT = true;
	private boolean EVENT_GOVERNS_TIMEX = true;
	private boolean TIMEX_GOVERNS_EVENT = true;
	private int numInterWords = 0;
	
	// Exclude timex that refer to "quarters" using this regex to be
	// applied to timex.value, since such a timex usually modifies an 
	// argument of the event verb, as opposed to serving as a stand-alone
	// temporal argument of the verb.
	private String valQuarterRegex = "\\d{4}-Q\\d";
	private Pattern valQuarter = Pattern.compile(valQuarterRegex);
	
	
	
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
			// Parameter Value Code
		try {
			EVENT_BEFORE_TIMEX = TimeSieveProperties.getBoolean("AdjacentVerbTimex.EVENT_BEFORE_TIMEX", true);
			TIMEX_BEFORE_EVENT = TimeSieveProperties.getBoolean("AdjacentVerbTimex.TIMEX_BEFORE_EVENT", true);
			EVENT_GOVERNS_TIMEX = TimeSieveProperties.getBoolean("AdjacentVerbTimex.EVENT_GOVERNS_TIMEX", true);
			TIMEX_GOVERNS_EVENT = TimeSieveProperties.getBoolean("AdjacentVerbTimex.TIMEX_GOVERNS_EVENT", true);
			numInterWords = TimeSieveProperties.getInt("AdjacentVerbTimex.numInterWords", 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// SIEVE CODE
		// List of proposed links
		// List of sentences in doc
		List<TLink> proposed = new ArrayList<TLink>();
		List<SieveSentence> sentList = doc.getSentences(); 
	
		// Iterate over sentences in doc and classify verb/timex pairs
		for( SieveSentence sent : sentList ) {
			// Get a list of all dependencies in the sentence
		  // We'll need the parse tree from each sentence to calculate a word's POS
			List<TypedDependency> deps = sent.getDeps();
			Tree tree = null;  // initialize to null so we don't end up loading it unless timexes are in the sentence
			
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
				}
			
			// Check timex/event pairs against our criteria
			// Iterate over timexes
			for (Timex timex : sent.timexes()) {
				// Ensure timex passes eligibility criteria specified in validateTimex
				if (!validateTimex(timex)) continue;
				
				// Iterate over events for fixed timex...
				for (TextEvent event : sent.events()) {
					// Ensure event passes eligibility criteria specified in validateEvent
					if (!validateEvent(event, tree, sent)) continue;
					
					// Get parameters used for "flat" notion of adjacency 
					// Distance from event to timex (positive if event is before timex)
					int eventToTimexDist = timex.getTokenOffset() - event.getIndex();
					// booleans for ordering
					boolean verbIsBeforeTimex = 
							(eventToTimexDist <= (numInterWords + 1) && eventToTimexDist >= 1);
					boolean timexIsBeforeVerb = 
							(eventToTimexDist*(-1) <= (numInterWords + 1) && eventToTimexDist <= -1); 
					
					// Get parameters used for "structured" notion of adjacency
				  // Dependency relation between event and time if applicable
					// Initialize booleans for dependency relation direction, updated below
					// Dependency relation if applicable
					boolean eventDoesGovernTimex = false;
					boolean timexDoesGovernEvent = false;
					GrammaticalRelation depRel = null;
					TypedDependency eventTimeDep = null;
					// Update above booleans
					// Check if event governs timex, and if so save the dependency relation depRel
					eventTimeDep = getDepSentIndexPair(deps, event.getIndex(), timex.getTokenOffset());
					if (eventTimeDep != null) { 
						depRel = eventTimeDep.reln();
						eventDoesGovernTimex = true;
					}
					// If not, check if timex governs event, and if so save the dependency relation depRel
					else {
						eventTimeDep = getDepSentIndexPair(deps, timex.getTokenOffset(), event.getIndex());
						if (eventTimeDep != null) {
							depRel = eventTimeDep.reln();
							timexDoesGovernEvent = true;
						}
					}
					
					
/*					 At this point, if there's a dependency relationship between the event and the time
					 We know what it is (depRel) and the direction (eventGovernsTimex vs timexGovernsEvent)*/
					
	
					
					// Now, we determine what TLink (if any)  to add to proposed for the event/timex pair
					TLink tlink = null;
					TLink flatTlink_et = null;
					TLink depTlink_et = null;
					TLink flatTlink_te = null;
					TLink depTlink_te = null;
					// Now, classify pairs for various parameter settings
					
					 if (EVENT_BEFORE_TIMEX && verbIsBeforeTimex) {
						flatTlink_et = eventBeforeTimex(eventToTimexDist, event, timex, sent, tree);
					}
					// if timex is before verb, use these rules...
					else if (TIMEX_BEFORE_EVENT && timexIsBeforeVerb) {
						if (eventDoesGovernTimex == false) {
							// If event governs a timex that comes before it, label it vague - 8/13
							//probably can be fixed with lexical features
							//flatTlink_te = null;
							flatTlink_te = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
						}
						else {
							flatTlink_te = timexBeforeEvent(eventToTimexDist, event, timex, sent, tree); // 8/11
							if (debug == true) {
							System.out.printf("E GOV T: %s(%s) %s(%s)\n%s", event.getString(), event.getId(), timex.getText(), timex.getTid(), sent.sentence());
							}
						}
					}
					
				// if verb governs timex, use these rules
					else if (EVENT_GOVERNS_TIMEX && eventDoesGovernTimex) {
						depTlink_et = eventGovernsTimex(eventToTimexDist, event, timex, sent, tree, depRel);
						
					}
					// if timex governs verb, use these rules
					else if (TIMEX_GOVERNS_EVENT && timexDoesGovernEvent) {
						// TIMEX_GOVERNS_EVENT is never true in the data!
						depTlink_te = timexGovernsEvent(eventToTimexDist, event, timex, sent, tree, depRel);
					}
					
					
					
					// heriarchy of preference is already determined in the above conditional blocks
					// take flat unless the event and timex are not within the numInterWords range,
					// in which case back off to dep. By setting numInterWords low we only use the 
					// flat method when the event and timex are very close together. This seems
					// to work best.
					if (depTlink_te != null)
						tlink = depTlink_te;
					if (flatTlink_te != null)
						tlink = flatTlink_te;
					if (flatTlink_et != null) 	
						tlink = flatTlink_et;
					if (depTlink_et != null) 
						tlink = depTlink_et;
					
					
					
					// Finally add tlink (as long as there is one)
					if (tlink != null) {
						proposed.add(tlink);
					}
					}
				}
			}
		
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
			}
		return proposed;
	}
	
/**
 * 
 * @param eventToTimexDist
 * @param event
 * @param timex
 * @param sent
 * @param tree
 * @param depRel
 * @criteria is_included no matter what!
 * @return a non-null tlink just in case the implemented criteria are satisfied
 */
 private TLink timexGovernsEvent(int eventToTimexDist, TextEvent event,
		Timex timex, SieveSentence sent, Tree tree, GrammaticalRelation depRel) {
	 TLink tlink = null;
	 tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
	 if (debug == true) {
		System.out.printf("Timex-Governs-Event: %s(%s) <%s> %s(%s)\n%s\n", 
											 event.getString(), event.getId(), depRel.getShortName(),
											 timex.getText(), timex.getTokenOffset(), sent.sentence());
	  }
	  return tlink;
	}

/**
 * 
 * @param eventToTimexDist
 * @param event
 * @param timex
 * @param sent
 * @param tree
 * @param depRel
 * @criteria is_included no matter what!
 * @return a non-null tlink just in case the implemented criteria are satisfied
 */
private TLink eventGovernsTimex(int eventToTimexDist, TextEvent event,
			Timex timex, SieveSentence sent, Tree tree, GrammaticalRelation depRel) {
	TLink tlink = null;
	if (timex.getText().toLowerCase().equals("now")) {
 		if (event.getAspect() == TextEvent.Aspect.PROGRESSIVE)
 			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.INCLUDES);
 		else
 			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
 }
	else
		tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
	//tlink = eventBeforeTimex(eventToTimexDist,event,timex,sent,tree);
	if (debug == true) {
		System.out.printf("Event-Governs-Timex: %s(%s) <%s> %s(%s)\n%s\n", 
											 event.getString(), event.getId(), depRel.getShortName(),
											 timex.getText(), timex.getTokenOffset(), sent.sentence());
	 }
	
	 return tlink;
	  
	}

/**
 * 
 * @param eventToTimexDist
 * @param event
 * @param timex
 * @param sent
 * @param tree
 * @criteria generally is_included, with two special cases yielding vague  
 * @return a non-null tlink just in case the implemented criteria are satisfied
 */
private TLink timexBeforeEvent(int eventToTimexDist, TextEvent event, Timex timex, SieveSentence sent, Tree tree) {
	 	TLink tlink = null;
		// if the timex is "now", return a vague tlink unless the event is in the progressive tense,
	 	// in which case we return includes.
	 	if (timex.getText().toLowerCase().equals("now")) {
	 		if (event.getAspect() == TextEvent.Aspect.PROGRESSIVE)
	 			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.INCLUDES);
	 		else
	 			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
	 		
		}
	 	// if the words are directly adjecent then is_included
	 	else if (eventToTimexDist == -1) {
			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
		}
	 	// otherwise, return is_included unless the word directly preceding the timex is "said".
	 	// this is the naive (flat) way to cover a common case that looks like this:
	 	// 
		else {
			if (timex.getTokenOffset() > 1 && getTextAtIndex(timex.getTokenOffset() - 1, sent).equals("said")){
				tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
			}
			else
				//return eventBeforeTimex(eventToTimexDist,event,timex,sent,tree);
				tlink = new EventTimeLink(event.getEiid(), timex.getTid(), TLink.Type.IS_INCLUDED);
		}
		if (debug == true) {
			System.out.printf("TimexVerb: %s\n%s | %s", sent.sentence(), event.getString(), timex.getText());
		}
		return tlink;
	}


private TLink eventBeforeTimex(int eventToTimexDist, TextEvent event, Timex timex, SieveSentence sent, Tree tree) {
// If there are no intervening words, label is_included
	 TLink tlink = null;
	 
	 		if (eventToTimexDist == 1) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
		}
		else {
		// First, determine if there is a preposition immediately 
		// preceding the timex
		int precTimexIndex = timex.getTokenOffset() - 1;
		String precTimexPos = posTagFromTree(tree, sent, precTimexIndex);
		// IN is the tag for prepositions and suborinating conjunctions
		if (precTimexPos != null && precTimexPos.equals("IN")) {
			// Different rules for different prepositions
			// As of now the rules are solely based on preposition string
			// TODO: add more details to rules based on properties of event,
			// timex, and surrounding context.
			String prepText = getTextAtIndex(precTimexIndex, sent); 
			tlink = applyPrepVerbTimexRules(event, timex, prepText);	
		}
		// If the word right before the timex is not IN
		else {
			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
		}
	 }
		return tlink;
	}

/**
 * 
 * @param deps
 * @param sentIndex1
 * @param sentIndex2
 * @return null unless there is a dependency in which the item at sentIndex1 governs the item
 * at sentIndex2
 */
private TypedDependency getDepSentIndexPair(List<TypedDependency> deps, int sentIndex1, int sentIndex2) {
	 // sentIndex_i conforms to the convention that index starts at 1!
	 TypedDependency foundDep = null;
	 for (TypedDependency dep : deps) {
			if (sentIndex1 == dep.gov().index() && sentIndex2 == dep.dep().index()) {
				foundDep = dep;
			}
		}
	 return foundDep;
 }

	// validateTime ensures that timex value meets criteria
	private Boolean validateTimex(Timex timex){
		String val = timex.getValue();
		// Return false if timex value is not a date or is a quarter
		Matcher m = valQuarter.matcher(val);
		if (!m.matches()) return true;
		else return false;
	}
	/**
	 * 
	 * @param event
	 * @param tree
	 * @param sent
	 * @criteria only allow verbal events
	 * @return true just in case event criteria are satisfied
	 */
	private boolean validateEvent(TextEvent event, Tree tree, SieveSentence sent) {
		String eventPos = posTagFromTree(tree, sent, event.getIndex());
		if (!eventPos.startsWith("VB")) return false;
		else return true;
	}
	
	// Given a sentence parse tree and an (sentence) index, return
	// the pos of the corresponding word.
	private String posTagFromTree(Tree tree, SieveSentence sent, int index) {
		// tree might be null; we keep it null until we need a pos for the first time for a given sentence
		if (tree == null) tree = sent.getParseTree(); 
		String pos = TreeOperator.indexToPOSTag(tree, index);
		return pos;
	}
	
	private TLink applyPrepVerbTimexRules(TextEvent event, Timex timex, String prepText) {
		if (prepText.equals("in")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
		}
		else if (prepText.equals("on")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
		}
		else if (prepText.equals("for")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.SIMULTANEOUS);
		}
		else if (prepText.equals("at")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.SIMULTANEOUS) ;
		}
		else if (prepText.equals("by"))  {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE) ;
		}
		else if (prepText.equals("over")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED) ;
		}
		else if (prepText.equals("during")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED) ;
		}
		else if (prepText.equals("throughout")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.SIMULTANEOUS) ;
		}
		else if (prepText.equals("within")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED) ;
		}
		else if (prepText.equals("until")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.BEFORE) ;
		}
		else if (prepText.equals("from")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.AFTER) ;
		}
		else if (prepText.equals("after")) {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.AFTER) ;
		}
		// If we encounter a different IN (prep/sub conj)
		else {
			return new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED) ;
		}
	}

	// Return the text of the token at a given index
	// Here, the index given to the function assumes that the first
	// index is 1. sent.tokens() does not count this way, so we
	// need to subtract 1 from index when retrieving our core label.
	private String getTextAtIndex(int index, SieveSentence sent) {
		CoreLabel cl = sent.tokens().get(index - 1);
		String text = cl.originalText();
		return text;
	}
	
//	public void checkTLink(TLink tlink, List<TLink> proposed) throws IllegalStateException{
//		int numTLinks = proposed.size();
//		for (int t = 0; t < numTLinks; t++) {
//			if (tlink.coversSamePair(proposed.get(t))) { 
//				throw new IllegalStateException("Cannot add a tlink between a pair of events for which there is already a tlink in proposed");
//			}
//		}
//	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
