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
 * 
 * Parameters
 * These parameters are booleans. If true, classify the corresponding pairs
 * 	AdjacentVerbTimex.EVENT_BEFORE_TIMEX
 * 	AdjacentVerbTimex.TIMEX_BEFORE_EVENT
 * 	AdjacentVerbTimex.EVENT_GOVERNS_TIMEX
 * 	AdjacentVerbTimex.TIMEX_GOVERNS_EVENT
 * This parameter specifies the maximum number of words that may intervene between
 * the verb and timex
 * 	AdjacentVerbTimex.numInterWords
 * 
 * numInterWords = 2
 * EVENT_BEFORE_TIMEX
 * AdjacentVerbTimex		p=0.83	38 of 46	Non-VAGUE:	p=0.95	38 of 40
 * 
 * TIMEX_BEFORE_EVENT
 * AdjacentVerbTimex		p=0.71	17 of 24	Non-VAGUE:	p=0.74	17 of 23
 * 
 * EVENT_GOVERNS_TIMEX
 * AdjacentVerbTimex		p=0.64	44 of 69	Non-VAGUE:	p=0.85	44 of 52
 * 
 * TIMEX_GOVERNS_EVENT - No attested cases! does this make sense?
 * AdjacentVerbTimex		p=0.00	0 of 0	Non-VAGUE:	p=0.00	0 of 0
 * 
 * EVENT_BEFORE_TIMEX and EVENT_GOVERNS_TIMEX
 * AdjacentVerbTimex		p=0.66	61 of 93	Non-VAGUE:	p=0.88	63 of 72
 * 
 * numInterWords
 * EVENT_BEFORE_TIMEX, TIMEX_BEFORE_EVENT, EVENT_GOVERNS_TIMEX
 * AdjacentVerbTimex		p=0.60	83 of 138	Non-VAGUE:	p=0.74	83 of 112
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
					TLink flatTlink_et = null;
					TLink depTlink_et = null;
					TLink flatTlink_te = null;
					TLink depTlink_te = null;
					// Now, classify pairs for various parameter settings
					
				
					// if verb is before timex, use the following rules...
					if (EVENT_BEFORE_TIMEX && verbIsBeforeTimex) {
						flatTlink_et = eventBeforeTimex(eventToTimexDist, event, timex, sent, tree);
					}
					else if (TIMEX_BEFORE_EVENT && timexIsBeforeVerb) {
						if (eventDoesGovernTimex == false)
							flatTlink_te = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
						else
							flatTlink_te = timexBeforeEvent(eventToTimexDist, event, timex, sent, tree);
					}
					if (EVENT_GOVERNS_TIMEX && eventDoesGovernTimex) {
						depTlink_et = eventGovernsTimex(eventToTimexDist, event, timex, sent, tree, depRel);
						
					}
					else if (TIMEX_GOVERNS_EVENT && timexDoesGovernEvent) {
						// TIMEX_GOVERNS_EVENT is never true in the data!
						depTlink_te = timexGovernsEvent(eventToTimexDist, event, timex, sent, tree, depRel);
					}
				
					// TODO Decide which tlink to take depending on parameters
					// For now, take flatTlink, backoff to depTlink
					if (depTlink_te != null)
						tlink = depTlink_te;
					if (flatTlink_te != null)
						tlink = flatTlink_te;
					if (flatTlink_et != null) 	
						tlink = flatTlink_et;
					if (depTlink_et != null) 
						tlink = depTlink_et;
					
					
					
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
	//tlink = eventBeforeTimex(eventToTimexDist,event,timex,sent,tree);
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
	 		if (event.getAspect() == TextEvent.Aspect.PROGRESSIVE)
	 			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.INCLUDES);
	 		else
	 			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
	 		
		}
	 	else if (eventToTimexDist == -1) {
			tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.IS_INCLUDED);
		}
		else {
			if (timex.getTokenOffset() > 1 && getTextAtIndex(timex.getTokenOffset() - 1, sent).equals("said"))
				tlink = new EventTimeLink(event.getEiid() , timex.getTid(), TLink.Type.VAGUE);
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
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
