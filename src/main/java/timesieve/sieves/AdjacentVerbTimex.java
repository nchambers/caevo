package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
import timesieve.util.TimeSieveProperties;
import timesieve.util.TreeOperator;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * 
 * AdjacentVerbTimex intends to classify event/time pairs from the same sentence
 * in which the two are fairly close within the sentence and the event is a verb.
 * Syntactic features are naive in that they are specified at the surface level.
 * Semantic features have not yet been specified.
 * 
 * The order of the event and the timex (which comes first in the sentence),
 * as well as the maximum distance between the two entities, is parameterized:
 * 1. adjType --> VerbTimex, TimexVerb, unordered
 * 		specifies the allowed ordering for event and timex
 * 2. numInterWords --> 0, 1, ...
 * 		specifies the maximum number of words allowed between event and timex.
 * 		if a given event/timex pair exceeds this number it is not labeled.
 * 
 * Initially we had the following results:
 ***adjType = VerbTimex*** (N is numInterWords)
 * N    prec.   c     t
 * 0 		p=0.95	20 of 21
 * 1 		p=0.84	31 of 37	
 * 2 		p=0.72	39 of 54
 * 3 		p=0.63	41 of 65
 * 4 		p=0.73	49 of 76
 * 
 ***adjType = TimexVerb*** (N is numInterWords)
 * 0		p=0.45	5 of 11
 * 
 ***adjType = Uunordered*** (N is numInterWords)
 * 0		p=0.78	25 of 32
 * 
 * After adding simple rules based on the preposition immediately
 * preceding the timex:
 * 
 *  N    prec.   c     t
 * 0 		p=0.95	20 of 21
 * 1 		p=0.89	33 of 37
 * 2 		p=0.83	45 of 54
 * 3 		p=0.75	49 of 65
 * 4 		p=0.75	57 of 76
 * 5		p=0.65	62 of 95  (just for fun, no comparison to above) 
 * 6		p=0.63	65 of 104 (just for fun, no comparison to above)
 * 
 * Regarding performance drop for N = 3. These happened to include several cases 
 * that were labeled Vague because of annotator disagreement on how to treat events
 * financial indicators (e.g. "unemployment *declined* to 3.8% *last month*").
 * 
 * More to come for other values of adjType...
 * 
 * @author cassidy
 */
public class AdjacentVerbTimex implements Sieve {
	
	public boolean debug = true;
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
		// PROPERTIES CODE
	
		// load properties file
		try {
			TimeSieveProperties.load();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		// fill in properties values
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
		  // We'll need the parse tree from sentence to calculate a word's POS
			List<TypedDependency> deps = sent.getDeps();
			Tree tree = null;  // initialize to null so we don't end up loading it (e.g. if no timexes are in the sentence)
			
			if (debug == true) {
				System.out.println("DEBUG: adding tlinks from " + doc.getDocname() + " sentence " + sent.sentence());
				}
			
			
			// Check timex/event pairs against our criteria
			// Iterate over timexes
			for (Timex timex : sent.timexes()) {
				// Ensure timex passes eligibility criteria
				if (!validateTimex(timex)) continue;
				
				// Iterate over events for fixed timex...
				for (TextEvent event : sent.events()) {
					// Ensure event passes eligibility criteria
					if (!validateEvent(event, tree, sent)) continue;
					
					// Get some useful parameters for checking against criteria
					// Distance from event to timex (positive if event is before timex)
					int eventToTimexDist = timex.getTokenOffset() - event.getIndex();
					// booleans for ordering
					boolean verbIsBeforeTimex = 
							(eventToTimexDist <= (numInterWords + 1) && eventToTimexDist >= 1);
					boolean timexIsBeforeVerb = 
							(eventToTimexDist*(-1) <= (numInterWords + 1) && eventToTimexDist <= -1); 
				  // Dependency relation between event and time if applicable
					// booleans for dependency relation direction, updated below
					// Dependency relation if applicable
					boolean eventDoesGovernTimex = false;
					boolean timexDoesGovernEvent = false;
					GrammaticalRelation depRel = null;
					TypedDependency eventTimeDep = null;
					// Update above booleans
					eventTimeDep = getDepSentIndexPair(deps, event.getIndex(), timex.getTokenOffset());
					if (eventTimeDep != null) { 
						depRel = eventTimeDep.reln();
						eventDoesGovernTimex = true;
					}
					else {
						eventTimeDep = getDepSentIndexPair(deps, timex.getTokenOffset(), event.getIndex());
						if (eventTimeDep != null) {
							depRel = eventTimeDep.reln();
							timexDoesGovernEvent = true;
						}
					}
/*					 Now, if there's a dependency relationship between the event and the time
					 We know what it is (depRel) and the direction (eventGovernsTimex vs timexGovernsEvent)*/
					
					
					
					
					// Now we determine what TLink to add (if any) for the event/timex pair
					TLink tlink = null;
					TLink flatTlink = null;
					TLink depTlink = null;
					// Now, classify pairs for various parameter settings
					
				
					// if verb is before timex, use the following rules...
					if (EVENT_BEFORE_TIMEX && verbIsBeforeTimex) {
						flatTlink = eventBeforeTimex(eventToTimexDist, event, timex, sent, tree);
					}
					else if (TIMEX_BEFORE_EVENT && timexIsBeforeVerb) {
						flatTlink = timexBeforeEvent(eventToTimexDist, event, timex, sent, tree);
					}
					if (EVENT_GOVERNS_TIMEX && eventDoesGovernTimex) {
						depTlink = eventGovernsTimex(eventToTimexDist, event, timex, sent, tree, depRel);
						
					}
					else if (TIMEX_GOVERNS_EVENT && timexDoesGovernEvent) {
						// TIMEX_GOVERNS_EVENT is never true in the data!
						depTlink = timexGovernsEvent(eventToTimexDist, event, timex, sent, tree, depRel);
					}
				
					// TODO Decide which tlink to take depending on parameters
					// For now, take flatTlink, backoff to depTlink
					if (flatTlink != null) tlink = flatTlink;
					else tlink = depTlink;
					
					// Finally add tlink (as long as there is one)
					if (tlink != null) proposed.add(tlink);
					}
				}
			}
		
		if (debug == true) {
			System.out.println("TLINKS: " + proposed);
			}
		return proposed;
	}
	
	
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


private TLink eventGovernsTimex(int eventToTimexDist, TextEvent event,
			Timex timex, SieveSentence sent, Tree tree, GrammaticalRelation depRel) {
	TLink tlink = null;
	tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
	
	if (debug == true) {
		System.out.printf("Event-Governs-Timex: %s(%s) <%s> %s(%s)\n%s\n", 
											 event.getString(), event.getId(), depRel.getShortName(),
											 timex.getText(), timex.getTokenOffset(), sent.sentence());
	 }
	
	 return tlink;
	  
	}


private boolean validateEvent(TextEvent event, Tree tree, SieveSentence sent) {
		String eventPos = posTagFromTree(tree, sent, event.getIndex());
		if (!eventPos.startsWith("VB")) return false;
		else return true;
	}


private TLink timexBeforeEvent(int eventToTimexDist, TextEvent event, Timex timex, SieveSentence sent, Tree tree) {
	 	TLink tlink = null;
		// If there are no intervening words, label is_included
	 	if (timex.getText().toLowerCase().equals("now")) {
			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
		}
	 	else if (eventToTimexDist == -1) {
			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
		}
		else {
			if (timex.getTokenOffset() > 1 && getTextAtIndex(timex.getTokenOffset() - 1, sent).equals("said"))
				tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
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
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
