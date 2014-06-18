package caevo.sieves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.Timex;
import caevo.tlink.EventEventLink;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.util.Pair;
import caevo.util.TreeOperator;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * This sieve deals with nominal events.
 * These are almost always vague without an understanding of the lexical semantics. We're stuck.
 * In the end, this sieve does some very precise but RARE things:
 * 1. Timex modifiers for event-time links: "three-year war"
 * 2. Temporal prepositions for event-event links: "left after the bombing" 
 * 
 * I tested lots of other things, such as indefinite and definite nominals. The distribution on both
 * is uniform with the DCT, and bares no obvious relationships with other verbal events.
 * 
 * 
 * precision       = 0.813  13 of 16
 * 
 * @author chambers
 */
public class NominalEvents implements Sieve {

	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<TLink> proposed = new ArrayList<TLink>();
		for (SieveSentence sent : doc.getSentences()) {
			List<TextEvent> events = sent.events();
			List<Tree> trees = doc.getAllParseTrees();
//			List<TypedDependency> deps = sent.getDeps();

			for( int ii = 0; ii < events.size(); ii++ ) {
				TextEvent event = events.get(ii);
				String pos = TreeOperator.indexToPOSTag(trees.get(event.getSid()), event.getIndex());
//				System.out.println("EVENT: " + event + " index: " + (event.getIndex()) + " pos: " + pos);
				
				if( pos != null && pos.startsWith("NN") ) {
//					System.out.println("Nominal event: " + event);
//					System.out.println("\t" + sent.sentence());

					// EVENT-TIMEX links: if it has a timex modifier
					Pair<Timex,TypedDependency> keytimex = getAttachedTimex(sent, event);
					if( keytimex != null ) {
						Timex theTimex = keytimex.first();
						TypedDependency dep = keytimex.second();
						String reln = dep.reln().toString(); 

						// "the two-hour meeting", "two-week crisis", "eight-year war"
						// (amod should be time duration modifiers, so these are simultaneous)
						if( reln.equalsIgnoreCase("amod") ) proposed.add(new EventTimeLink(event.getEiid(), theTimex.getTid(), TLink.Type.SIMULTANEOUS));

						// "Aug 9 power grab", "Aug 17 cease fire"
						if( reln.equalsIgnoreCase("nn") ) proposed.add(new EventTimeLink(event.getEiid(), theTimex.getTid(), TLink.Type.IS_INCLUDED));

						// "today's quiz"
						if( reln.equalsIgnoreCase("poss") ) proposed.add(new EventTimeLink(event.getEiid(), theTimex.getTid(), TLink.Type.IS_INCLUDED));

						// "kidnappings in recent years"
						if( reln.equalsIgnoreCase("prep_in") ) proposed.add(new EventTimeLink(event.getEiid(), theTimex.getTid(), TLink.Type.IS_INCLUDED));
					}

/*
					// Look for a determiner.
					boolean isDefinite = false, isIndefinite = false;
					String det = Ling.getDeterminer(sent.getDeps(), event.getIndex());
					if( det != null && det.equalsIgnoreCase("a") ) {
						isIndefinite = true;
						System.out.println("INDEFINITE NOMINAL: " + event);
					}
					if( det != null && det.equalsIgnoreCase("the") ) {
						isDefinite = true;
						System.out.println("DEFINITE NOMINAL: " + event);
					}

					if( isDefinite ) {
//						proposed.add(new EventTimeLink(event.getEiid(), doc.getDocstamp().get(0).getTid(), TLink.Type.BEFORE));
					}
					if( isIndefinite ) {
//						proposed.add(new EventTimeLink(event.getEiid(), doc.getDocstamp().get(0).getTid(), TLink.Type.BEFORE));
					}
*/
					
					Pair<TextEvent,TypedDependency> pair = getGoverningEvent(sent, event); 
					if( pair != null ) {
						TextEvent eventGov = pair.first();
						String reln = pair.second().reln().toString(); 
						//					System.out.println(sent.sentence());
//						System.out.println("Found governor for nominal: " + eventGov + " over " + event);
					
						// High precision. Very low recall.
						if( reln.equalsIgnoreCase("prep_during") ||
								reln.equalsIgnoreCase("prep_while") ) 
							proposed.add(new EventEventLink(eventGov.getEiid(), event.getEiid(), TLink.Type.IS_INCLUDED));
						if( reln.equalsIgnoreCase("prep_before") )
							proposed.add(new EventEventLink(eventGov.getEiid(), event.getEiid(), TLink.Type.BEFORE));
						if( reln.equalsIgnoreCase("prep_after") || 
								reln.equalsIgnoreCase("prep_since") )
							proposed.add(new EventEventLink(eventGov.getEiid(), event.getEiid(), TLink.Type.AFTER));
						
//						if( eventGov.getTense() == TextEvent.Tense.PAST )
//							proposed.add(new EventEventLink(eventGov.getEiid(), event.getEiid(), TLink.Type.BEFORE));
//						if( eventGov.getTheClass() == TextEvent.Class.ASPECTUAL )
//							proposed.add(new EventEventLink(eventGov.getEiid(), event.getEiid(), TLink.Type.IS_INCLUDED));
//						else if( eventGov.getTheClass() == TextEvent.Class.REPORTING )
//							proposed.add(new EventEventLink(eventGov.getEiid(), event.getEiid(), TLink.Type.AFTER));
//						else
//							proposed.add(new EventEventLink(eventGov.getEiid(), event.getEiid(), TLink.Type.VAGUE));
					}
				}
			}

			// Rules for Past tense, or Reporting verbs. I can't get any traction with these...			
//			for( int ii = 0; ii < events.size(); ii++ ) {
//				TextEvent event = events.get(ii);
//				for( int jj = ii+1; jj < events.size(); jj++ ) {
//					TextEvent event2 = events.get(jj);
//					if( event2.getTheClass() == TextEvent.Class.REPORTING ) {//if( event2.getString().equalsIgnoreCase("said") ) {
//						if( event.getTense() == TextEvent.Tense.PAST && event.getTheClass() != TextEvent.Class.REPORTING ) {
//							proposed.add(new EventEventLink(event.getEiid(), event2.getEiid(), TLink.Type.BEFORE));
//						}
//					}
//				}
//			}

			
		}
//		System.out.println("PROPOSED: " + proposed);
		return proposed;
	}

	/**
	 * Look through the typed dependencies for cases where the event dominates a Timex.
	 * @param sent
	 * @param event
	 * @return The timex that the given event dominates.
	 */
	private Pair<Timex,TypedDependency> getAttachedTimex(SieveSentence sent, TextEvent event) {
		List<Timex> timexes = sent.timexes();
		Map<Integer,Timex> positionToTimex = new HashMap<Integer,Timex>();
		for( Timex timex : timexes )
			for( int xx = timex.getTokenOffset(); xx < timex.getTokenOffset()+timex.getTokenLength(); xx++ )
				positionToTimex.put(xx, timex);
		
		for( TypedDependency dep : sent.getDeps() ) {
			if( dep.gov().index() == event.getIndex() ) {
				Timex matchedTimex = positionToTimex.get(dep.dep().index());
				if( matchedTimex != null )
					return new Pair<Timex,TypedDependency>(matchedTimex, dep);
			}
		}

		return null;
	}
	
	private Pair<TextEvent,TypedDependency> getGoverningEvent(SieveSentence sent, TextEvent event){
		List<TypedDependency> deps = sent.getDeps();
		for( TypedDependency dep : deps ) {
			if( dep.dep().index() == event.getIndex() ) {
				int govindex = dep.gov().index();
				for( TextEvent ee : sent.events() )
					if( ee.getIndex() == govindex )
						return new Pair<TextEvent,TypedDependency>(ee,dep);
			}
		}
		return null;
	}
	
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
