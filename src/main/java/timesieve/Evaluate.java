package timesieve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import timesieve.sieves.Sieve;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;
import timesieve.util.SieveStats;
import timesieve.util.Util;

/**
 * Evaluation functions for TLink classification.
 *
 * @author chambers
 */
public class Evaluate {
	public static final TLink.Type[] labels = { TLink.Type.BEFORE, TLink.Type.AFTER, TLink.Type.SIMULTANEOUS,
		TLink.Type.INCLUDES, TLink.Type.IS_INCLUDED, TLink.Type.VAGUE };
	
	public static final String[] devDocs = { 
		"APW19980227.0487.tml", 
		"CNN19980223.1130.0960.tml", 
		"NYT19980212.0019.tml",  
		"PRI19980216.2000.0170.tml", 
		"ed980111.1130.0089.tml" 
	};
	
	public static final String[] testDocs = { 
		"APW19980227.0489.tml",
		"APW19980227.0494.tml",
		"APW19980308.0201.tml",
		"APW19980418.0210.tml",
		"CNN19980126.1600.1104.tml",
		"CNN19980213.2130.0155.tml",
		"NYT19980402.0453.tml",
		"PRI19980115.2000.0186.tml",
		"PRI19980306.2000.1675.tml" 
	};
	
	/**
	 * Determines if the given TLink exists in the goldLinks, and its relation is equal
	 * to or compatible (invertible or a more general relation) with the gold link.
	 * @param guessed A single TLink between two events.
	 * @param goldLinks A list of gold tlinks.
	 * @return True if guessed appears as is or inverted in goldLinks.
	 */
	public static boolean isLinkCorrect(TLink guessed, List<TLink> goldLinks) {
		if( guessed == null || goldLinks == null || goldLinks.size() == 0 ) 
			return false;
		
		for( TLink gold : goldLinks ) {
			if( gold.compareToTLink(guessed) ) {
//				System.out.println("Match! guess=" + guessed + "\tgold=" + gold);
				return true;
			}
		}
	
		return false;
	}
	
	public static SieveDocuments getTrainSet(SieveDocuments docs) {
		SieveDocuments newdocs = new SieveDocuments();
		for( SieveDocument doc : docs.getDocuments() )
			if( !exists(doc.getDocname(), devDocs) && !exists(doc.getDocname(), testDocs) )
				newdocs.addDocument(doc);
		return newdocs;
	}

	public static SieveDocuments getDevSet(SieveDocuments docs) {
		SieveDocuments newdocs = new SieveDocuments();
		for( SieveDocument doc : docs.getDocuments() )
			if( exists(doc.getDocname(), devDocs) )
				newdocs.addDocument(doc);
		return newdocs;		
	}

	public static SieveDocuments getTestSet(SieveDocuments docs) {
		SieveDocuments newdocs = new SieveDocuments();
		for( SieveDocument doc : docs.getDocuments() )
			if( exists(doc.getDocname(), testDocs) )
				newdocs.addDocument(doc);
		return newdocs;		
	}

	private static boolean exists(String name, String[] names) {
		for( String nn : names )
			if( name.equals(nn) ) return true;
		return false;
	}
	
	/**
	 * This function makes sure that the two events in each TLink follow document order.
	 * If not, then we invert the order so event 1 is 2 and 2 is 1, and we also invert the ordering relation.
	 * @param docs The documents to normalize.
	 */
	public static void normalizeAllTlinksByTextOrder(SieveDocuments docs) {
		for( SieveDocument doc : docs.getDocuments() ) {
			List<TLink> removal = new ArrayList<TLink>();
			List<TLink> addition = new ArrayList<TLink>();
			
			for( TLink link : doc.getTlinks() ) {
//				System.out.println("normalizing " + link + " instance=" + link.getClass().toString());
				
				// Event-event links.
				if( link instanceof EventEventLink ) {
					TextEvent first = doc.getEventByEiid(link.getId1());
					TextEvent second = doc.getEventByEiid(link.getId2());
					if( first == null || second == null )
						System.out.println("WARNING: document " + doc.getDocname() + " has a link with a null event: " + link);
					else if( !first.isBeforeInText(second) ) {
						removal.add(link);
						TLink.Type invertedRelation = TLink.invertRelation(link.getRelation());
						TLink newlink = TLink.clone(link);
						newlink.setId1(link.getId2());						
						newlink.setId2(link.getId1());
						newlink.setRelation(invertedRelation);
						addition.add(newlink);
					}
				}
				
				// Time-Time links.
				else if( link instanceof TimeTimeLink ) {
					Timex first = doc.getTimexByTid(link.getId1());
					Timex second = doc.getTimexByTid(link.getId2());
					boolean flip = false;
					if( first == null || second == null )
						System.out.println("WARNING: document " + doc.getDocname() + " has a link with a null timex: " + link);
					else if( first.getDocumentFunction() == Timex.DocumentFunction.CREATION_TIME &&
							     second.getDocumentFunction() != Timex.DocumentFunction.CREATION_TIME) {
						flip = true;
					}
					else if( first.getDocumentFunction() != Timex.DocumentFunction.CREATION_TIME && 
							     second.getDocumentFunction() != Timex.DocumentFunction.CREATION_TIME &&
							     !first.isBeforeInText(second) ) {
						flip = true;
					}
					
					if( flip ) {
						removal.add(link);
						TLink.Type invertedRelation = TLink.invertRelation(link.getRelation());
						TLink newlink = TLink.clone(link);
						newlink.setId1(link.getId2());						
						newlink.setId2(link.getId1());
						newlink.setRelation(invertedRelation);
						addition.add(newlink);
					}
				}
				
				// Event-Time links.
				else if( link instanceof EventTimeLink ) {
					TextEvent event;
					Timex time;
					boolean flip = false;
					
					// event - time
					if( link.getId1().startsWith("e") ) {
						event = doc.getEventByEiid(link.getId1());
						time = doc.getTimexByTid(link.getId2());

						if( event == null || time == null )
							System.out.println("WARNING: document " + doc.getDocname() + " has a link with a null event or timex: " + link);

						else if( !time.isDCT() && 
								(time.getSid() < event.getSid() || 
										(time.getSid() == event.getSid() && time.getTokenOffset() < event.getIndex()) ))
							flip = true;						
					}
					// time - event
					else {
						event = doc.getEventByEiid(link.getId2());
						time = doc.getTimexByTid(link.getId1());

						if( event == null || time == null )
							System.out.println("WARNING: document " + doc.getDocname() + " has a link with a null event or timex: " + link);

						else if( time.isDCT() ) flip = true;
						else if( event.getSid() < time.getSid() ||
								(event.getSid() == time.getSid() && event.getIndex() < time.getTokenOffset()) )
							flip = true;
					}

					if( flip ) {
						removal.add(link);
						TLink.Type invertedRelation = TLink.invertRelation(link.getRelation());
						TLink newlink = TLink.clone(link);
						newlink.setId1(link.getId2());						
						newlink.setId2(link.getId1());
						newlink.setRelation(invertedRelation);
						addition.add(newlink);
					}
				}
				
				else {  //if( link instanceof TLink )
					System.out.println("WARNING: a sieve is producing generic TLink instances. These must be specific! Evaluation is now unreliable.");
				}
			}
			
			for( TLink link : removal )
				doc.removeTlink(link);
			for( TLink link : addition )
				doc.addTlink(link);
		}
	}
	
	/**
	 * Full evaluation of guesses to gold links. This penalizes guesses for not labeling everything.
	 * The goldDocs and guessedDocs should cover the same docs.
	 * @param goldDocs Gold tlinks in every document.
	 * @param guessedDocs The guessed tlinks in every document.
	 * @param sieveNames The list of sieve names, in order that they were applied to each document.
	 * @param sieveStats A map from sieve names to their SieveStats objects.
	 */
	public static void evaluate(SieveDocuments goldDocs, SieveDocuments guessedDocs, String[] sieveNames, Map<String,SieveStats> sieveStats) {
		Counter<String> guessCounts = new ClassicCounter<String>();
		Counter<TLink.Type> goldLabelCounts = new ClassicCounter<TLink.Type>();
		int numCorrect = 0;
		int numIncorrect = 0;
		int numIncorrectNonVague = 0;
		int numMissed = 0;

		if (goldDocs == null)
			return;
		
		// Make sure all TLinks follow text order and invert relations that don't.
//		normalizeAllTlinksByTextOrder(goldDocs);
		normalizeAllTlinksByTextOrder(guessedDocs);
		
		// Loop over documents.
		for( SieveDocument guessedDoc : guessedDocs.getDocuments() ) {
			SieveDocument goldDoc = goldDocs.getDocument(guessedDoc.getDocname());
			Set<String> seenGoldLinks = new HashSet<String>();

//			System.out.println("evaluating " + guessedDoc.getDocname());
//			System.out.println("\t-> " + guessedDoc.getTlinks().size() + " guessed links with " + goldDoc.getTlinks().size() + " gold links.");

			// Gold links.
			List<TLink> goldLinks = goldDoc.getTlinksNoClosures();
			Map<String, TLink> goldPairLookup = new HashMap<String, TLink>();
			for (TLink tlink : goldLinks) 
				goldPairLookup.put(tlink.getId1() + "," + tlink.getId2(), tlink);
			
			// Run it.
			List<TLink> proposed = guessedDoc.getTlinks();
//			System.out.println("EVALUATE: proposed links in " + guessedDoc.getDocname() + " = " + proposed);

			// Check proposed links.
			for( TLink pp : proposed ) {
				TLink goldLink = goldPairLookup.get(pp.getId1() + "," + pp.getId2());

				if( goldLink != null ) {
					guessCounts.incrementCount(goldLink.getRelation()+" "+pp.getRelation());
					goldLabelCounts.incrementCount(goldLink.getRelation());
					seenGoldLinks.add(pp.getId1() + "," + pp.getId2());
				}

				// Guessed link is correct!
				if( Evaluate.isLinkCorrect(pp, goldLinks) ) {
					numCorrect++;
					if( pp.getOrigin() != null ) {
						sieveStats.get(pp.getOrigin()).addCorrect(pp);
					}
					else System.out.println("EVALUATE: unknown link origin: " + pp);
				} 
				// Gold and guessed link disagree!
				// Only mark relations wrong if there's a conflicting human annotation.
				// (if there's no human annotation, we don't know if it's right or wrong)
				else if (goldLink != null) {
					if (!goldLink.getRelation().equals(TLink.Type.VAGUE)) {
						numIncorrectNonVague++;
					}
					numIncorrect++;
					if( pp.getOrigin() != null ) sieveStats.get(pp.getOrigin()).addIncorrect(pp, goldLink);
					else System.out.println("EVALUATE: unknown link origin: " + pp);
				}
				// No gold link. We don't penalize for guessed links that aren't in gold.
				else {
					sieveStats.get(pp.getOrigin()).addNoGold(pp);
//					System.out.println("No gold link: " + pp);
				}
			}
			
			// Check for gold links that were not predicted. Penalize for them being missed.
			for( TLink gold : goldLinks ) {
				if( !seenGoldLinks.contains(gold.getId1() + "," + gold.getId2()) ) {
					numMissed++;
//					System.out.println("Unlabeled gold: " + guessedDoc.getDocname() + " " + gold);
				}
			}
		}
		
		// Print performance for each individual sieve.
		System.out.println("\nBrief Sieve Stats");
		for( String sieveName : sieveNames )
			sieveStats.get(sieveName).printOneLineStats();
		System.out.println("\nDetailed Sieve Stats");
		for( String sieveName : sieveNames )
			sieveStats.get(sieveName).printStats();

		// Calculate precision and output the sorted sieves.
		int totalGuessed = numCorrect + numIncorrect;
		int totalGold = numCorrect + numIncorrect + numMissed;
		double precision = (totalGuessed > 0 ? (double)numCorrect / totalGuessed : 0.0);
		double recall = (totalGold > 0 ? (double)numCorrect / totalGold : 0.0);
		double f1 = (precision+recall > 0 ? 2.0 * precision * recall / (precision+recall) : 0.0);
		int totalGuessedNonVague = numCorrect + numIncorrectNonVague;
		double precisionNonVague = (totalGuessedNonVague > 0 ? (double)numCorrect / (double)totalGuessedNonVague : 0.0);

		// Print full system performance.
		System.out.println("\n*********************************************************************");
		System.out.println("************************** FULL RESULTS *****************************");
		System.out.println("*********************************************************************");
		System.out.printf("precision\t= %.3f\t %d of %d\nrecall\t\t= %.3f\t %d of %d\nF1\t\t= %.3f\n",
				precision, numCorrect, totalGuessed,
				recall, numCorrect, totalGold,
				f1);
		System.out.printf("precision (non vague)= %.3f\t %d of %d\n", precisionNonVague, numCorrect, totalGuessedNonVague);
		System.out.println();

		printBaseline(goldLabelCounts);
		printDatasetStats(goldLabelCounts);
		confusionMatrix(guessCounts);
		System.out.println("*********************************************************************\n");
	}
	
	
	/**
	 * Find the label with the most counts, and print the baseline if you always guessed that one.
	 */
	public static void printBaseline(Counter<TLink.Type> labelCounts) {
		double total = labelCounts.totalCount();
		TLink.Type best = null;
		double bestc = -1.0;
		for( TLink.Type label : labelCounts.keySet() ) {
			if( labelCounts.getCount(label) > bestc ) {
				bestc = labelCounts.getCount(label);
				best = label;
			}
		}
		System.out.printf("Local Baseline (%s): precision = recall = F1 = %.3f\n", best, (bestc/total));		
	}

	/**
	 * Print how many times each label is in the counts list.
	 */
	public static void printDatasetStats(Counter<TLink.Type> labelCounts) {
		System.out.println("GOLD LABEL COUNTS (out of " + (int)labelCounts.totalCount() + ")");
		for( TLink.Type label : labels ) {
			int numtabs = (label.toString().length() > 7 ? 1 : 2);
			System.out.print(label + "\t");
			if( numtabs == 2 ) System.out.print("\t");
			System.out.printf("%d\t%.0f%%\n", (int)labelCounts.getCount(label), (100.0*labelCounts.getCount(label)/labelCounts.totalCount()));
		}
	}

	/**
	 * Prin the confusion matrix for the 6 label types. Each String key should be a pair separated
	 * by a single space, representing a guess for a gold label: e.g., "before after" 
	 * @param guessCounts Number of times each guessed label was made for each gold label.
	 */
	public static void confusionMatrix(Counter<String> guessCounts) {
		for( TLink.Type label2 : labels )
			System.out.print("\t" + label2.toString().substring(0,Math.min(label2.toString().length(), 6)));
		System.out.println("\t(guesses)");
		
		for( TLink.Type label1 : labels ) {
			System.out.print(label1.toString().substring(0, Math.min(label1.toString().length(), 6)) + "\t");
			
			for( TLink.Type label2 : labels ) {
				if( guessCounts.containsKey(label1+" "+label2) )
					System.out.print((int)guessCounts.getCount(label1+" "+label2) + "\t");
				else System.out.print("0\t");
			}
			System.out.println();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
	}

}
